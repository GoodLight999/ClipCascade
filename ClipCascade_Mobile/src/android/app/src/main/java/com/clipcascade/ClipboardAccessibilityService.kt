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
import java.util.UUID

class ClipboardAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "ClipboardAccessibility"
        private const val SELECTION_TTL_MS = 15_000L
        private const val MAX_TEXT_LENGTH = 500_000
        private val COPY_MARKER_REGEX = Regex(
            pattern = "(?i)(?:^|[\\s:_-])(?:copy|copied|copy\\s+text|copy\\s+link)(?:$|[\\s:_-])|コピーしました|クリップボードにコピー|(?:^|\\s)コピー(?:$|\\s)",
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
        RelayHealthStore.record(
            applicationContext,
            category = "clipboard",
            trigger = "service_connected",
            path = "accessibility",
            result = "ready",
        )
        ClipboardRelayDispatcher.schedule(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val current = event ?: return
        if (!RelaySettingsStore.clipboardEnabled(this)) return
        if (current.packageName?.toString() == packageName) return

        when (current.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> rememberSelection(current)
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                if (looksLikeCopyConfirmation(current)) scheduleCapture(current, "click")
            AccessibilityEvent.TYPE_ANNOUNCEMENT ->
                if (looksLikeCopyConfirmation(current)) scheduleCapture(current, "announcement")
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED ->
                if (looksLikeCopyConfirmation(current)) scheduleCapture(current, "copy_notice")
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
            scheduleCapture(null, "ctrl_c")
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
            RelayHealthStore.record(
                applicationContext,
                category = "clipboard",
                trigger = "selection",
                path = "accessibility_selection",
                result = "remembered",
            )
        }
    }

    private fun looksLikeCopyConfirmation(event: AccessibilityEvent): Boolean {
        val node = event.source
        val text = buildString {
            append(event.text.joinToString(" "))
            append(' ')
            append(event.contentDescription?.toString().orEmpty())
            append(' ')
            append(node?.text?.toString().orEmpty())
            append(' ')
            append(node?.contentDescription?.toString().orEmpty())
            append(' ')
            append(node?.viewIdResourceName.orEmpty())
        }
        return COPY_MARKER_REGEX.containsMatchIn(text)
    }

    private fun scheduleCapture(event: AccessibilityEvent?, trigger: String) {
        val sourcePackage = event?.packageName?.toString().orEmpty()
        val completed = booleanArrayOf(false)
        listOf(80L, 250L, 700L).forEach { delay ->
            handler.postDelayed(
                {
                    if (!completed[0]) {
                        completed[0] = captureClipboard(sourcePackage, trigger)
                    }
                },
                delay,
            )
        }
    }

    private fun captureClipboard(sourcePackage: String, trigger: String): Boolean {
        if (!RelaySettingsStore.clipboardEnabled(this)) return true

        var managerDenied = false
        val fromClipboard = try {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = manager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this)?.toString()
            } else {
                null
            }
        } catch (error: SecurityException) {
            managerDenied = true
            Log.d(TAG, "ClipboardManager access was denied; using selected text fallback")
            null
        } catch (error: Exception) {
            Log.w(TAG, "Unable to inspect clipboard", error)
            null
        }

        val now = System.currentTimeMillis()
        val fallback = lastSelectedText?.takeIf {
            now - lastSelectionAt <= SELECTION_TTL_MS && it.isNotBlank()
        }
        val managerValue = fromClipboard?.takeIf { it.isNotBlank() }
        val rawValue = managerValue ?: fallback
        val capturePath = when {
            managerValue != null -> "clipboard_manager"
            fallback != null -> "selected_text_fallback"
            managerDenied -> "clipboard_denied_no_fallback"
            else -> "no_text_available"
        }
        val value = rawValue
            ?.trim()
            ?.take(MAX_TEXT_LENGTH)
            ?.takeIf { it.isNotEmpty() }

        if (value == null) {
            RelayHealthStore.record(
                applicationContext,
                category = "clipboard",
                trigger = trigger,
                path = capturePath,
                result = "retrying",
            )
            return false
        }

        val resolvedPackage = sourcePackage.ifBlank { lastSourcePackage }
        val item = ClipboardRelayStore.Item(
            id = UUID.randomUUID().toString(),
            text = value,
            sourcePackage = resolvedPackage,
            createdAt = now,
        )
        val queued = ClipboardRelayStore.enqueue(applicationContext, item)
        if (queued) {
            ClipboardRelayDispatcher.schedule(applicationContext)
        }
        RelayHealthStore.record(
            applicationContext,
            category = "clipboard",
            trigger = trigger,
            path = capturePath,
            result = if (queued) "queued" else "deduplicated",
        )
        return true
    }

    override fun onInterrupt() {
        RelayHealthStore.record(
            applicationContext,
            category = "clipboard",
            trigger = "service_interrupted",
            path = "accessibility",
            result = "interrupted",
        )
    }
}
