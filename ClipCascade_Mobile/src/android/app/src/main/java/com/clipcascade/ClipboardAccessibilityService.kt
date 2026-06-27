package com.clipcascade

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class ClipboardAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "ClipboardAccessibility"
        private const val SELECTION_TTL_MS = 15_000L
        private const val MAX_TEXT_LENGTH = 500_000
        private val COPY_MARKERS = listOf(
            "copy",
            "copied",
            "copy text",
            "copy link",
            "コピー",
            "コピーしました",
            "クリップボードにコピー",
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastSelectedText: String? = null
    private var lastSelectionAt = 0L
    private var lastSourcePackage = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_ANNOUNCEMENT or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 50
        }
        ClipboardRelayDispatcher.schedule(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val current = event ?: return
        if (!RelaySettingsStore.clipboardEnabled(this)) return
        if (current.packageName?.toString() == packageName) return

        when (current.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> rememberSelection(current)
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_ANNOUNCEMENT,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            -> if (looksLikeCopyConfirmation(current)) scheduleCapture(current)
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        val current = event ?: return false
        if (!RelaySettingsStore.clipboardEnabled(this)) return false
        if (
            current.action == KeyEvent.ACTION_UP &&
            current.keyCode == KeyEvent.KEYCODE_C &&
            current.isCtrlPressed
        ) {
            scheduleCapture(null)
        }
        return false
    }

    private fun rememberSelection(event: AccessibilityEvent) {
        val node = event.source
        val fullText = node?.text?.toString()
            ?: event.text.joinToString("").takeIf { it.isNotBlank() }
            ?: return
        val start = node?.textSelectionStart ?: event.fromIndex
        val end = node?.textSelectionEnd ?: event.toIndex
        val selected = if (
            start >= 0 &&
            end > start &&
            end <= fullText.length
        ) {
            fullText.substring(start, end)
        } else {
            null
        }

        if (!selected.isNullOrBlank()) {
            lastSelectedText = selected.take(MAX_TEXT_LENGTH)
            lastSelectionAt = System.currentTimeMillis()
            lastSourcePackage = event.packageName?.toString().orEmpty()
        }
    }

    private fun looksLikeCopyConfirmation(event: AccessibilityEvent): Boolean {
        val node = event.source
        val text = buildString {
            append(event.text.joinToString(" "))
            append(' ')
            append(event.contentDescription.orEmpty())
            append(' ')
            append(node?.text.orEmpty())
            append(' ')
            append(node?.contentDescription.orEmpty())
            append(' ')
            append(node?.viewIdResourceName.orEmpty())
        }.lowercase()
        return COPY_MARKERS.any(text::contains)
    }

    private fun scheduleCapture(event: AccessibilityEvent?) {
        val sourcePackage = event?.packageName?.toString().orEmpty()
        listOf(80L, 250L, 700L).forEach { delay ->
            handler.postDelayed({ captureClipboard(sourcePackage) }, delay)
        }
    }

    private fun captureClipboard(sourcePackage: String) {
        if (!RelaySettingsStore.clipboardEnabled(this)) return

        val fromClipboard = try {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = manager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this)?.toString()
            } else {
                null
            }
        } catch (error: SecurityException) {
            Log.d(TAG, "ClipboardManager access was denied; using selected text fallback")
            null
        } catch (error: Exception) {
            Log.w(TAG, "Unable to inspect clipboard", error)
            null
        }

        val now = System.currentTimeMillis()
        val fallback = lastSelectedText?.takeIf {
            now - lastSelectionAt <= SELECTION_TTL_MS
        }
        val value = (fromClipboard ?: fallback)
            ?.trim()
            ?.take(MAX_TEXT_LENGTH)
            ?.takeIf { it.isNotEmpty() }
            ?: return

        val resolvedPackage = sourcePackage.ifBlank { lastSourcePackage }
        val item = ClipboardRelayStore.Item(
            id = "$resolvedPackage:$now:${value.hashCode()}",
            text = value,
            sourcePackage = resolvedPackage,
            createdAt = now,
        )
        if (ClipboardRelayStore.enqueue(applicationContext, item)) {
            ClipboardRelayDispatcher.schedule(applicationContext)
        }
    }

    override fun onInterrupt() {
        // The system calls this when accessibility feedback is interrupted.
    }
}
