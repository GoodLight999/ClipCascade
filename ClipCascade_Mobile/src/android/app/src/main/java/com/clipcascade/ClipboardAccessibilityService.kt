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
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.UUID

class ClipboardAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "ClipboardAccessibility"
        private const val SELECTION_TTL_MS = 60_000L
        private const val MAX_TEXT_LENGTH = 500_000
        private const val MAX_WINDOW_SCAN_NODES = 4_000
        private val COPY_MARKER_REGEX = Regex(
            pattern = "(?i)(?:^|[\\s:_-])(?:copy|copied|copy\\s+text|copy\\s+link)(?:$|[\\s:_-])|コピーしました|クリップボードにコピー|コピー済み|(?:^|\\s)コピー(?:$|\\s)",
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
                AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED or
                AccessibilityEvent.TYPE_ANNOUNCEMENT or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 25
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
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED,
            AccessibilityEvent.TYPE_ANNOUNCEMENT,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> if (looksLikeCopyConfirmation(current)) scheduleCapture(current, triggerFor(current))
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

    private fun triggerFor(event: AccessibilityEvent): String = when (event.eventType) {
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "click"
        AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED -> "context_click"
        AccessibilityEvent.TYPE_ANNOUNCEMENT -> "announcement"
        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "copy_notice"
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "window_state"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "window_content"
        else -> "copy_cue"
    }

    private fun rememberSelection(event: AccessibilityEvent) {
        val selected = selectedTextFromNode(event.source)
            ?: selectedTextFromEvent(event)
            ?: return

        rememberSelectedText(
            selected,
            event.packageName?.toString().orEmpty(),
        )
        RelayHealthStore.record(
            applicationContext,
            category = "clipboard",
            trigger = "selection",
            path = "accessibility_selection",
            result = "remembered",
        )
    }

    private fun selectedTextFromEvent(event: AccessibilityEvent): String? {
        val combined = event.text.joinToString("")
        val start = event.fromIndex
        val end = event.toIndex
        if (start < 0 || end <= start || end > combined.length) return null
        return combined.substring(start, end).takeIf { it.isNotBlank() }
    }

    private fun selectedTextFromNode(node: AccessibilityNodeInfo?): String? {
        val text = node?.text?.toString() ?: return null
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        if (start < 0 || end <= start || end > text.length) return null
        return text.substring(start, end).takeIf { it.isNotBlank() }
    }

    private fun rememberSelectedText(text: String, sourcePackage: String) {
        lastSelectedText = text.trim().take(MAX_TEXT_LENGTH)
        lastSelectionAt = System.currentTimeMillis()
        if (sourcePackage.isNotBlank()) {
            lastSourcePackage = sourcePackage
        }
    }

    private fun looksLikeCopyConfirmation(event: AccessibilityEvent): Boolean {
        if (event.action == AccessibilityNodeInfo.ACTION_COPY) return true
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
        // The selected range can collapse immediately after the floating toolbar's
        // Copy action. Try synchronously before Android updates the active windows,
        // then retry for apps that commit clipboard content asynchronously.
        listOf(0L, 40L, 120L, 300L, 700L).forEach { delay ->
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
            Log.d(TAG, "ClipboardManager access was denied; using Accessibility selection")
            null
        } catch (error: Exception) {
            Log.w(TAG, "Unable to inspect clipboard", error)
            null
        }

        val now = System.currentTimeMillis()
        val rememberedSelection = lastSelectedText?.takeIf {
            now - lastSelectionAt <= SELECTION_TTL_MS && it.isNotBlank()
        }
        val activeWindowSelection = if (rememberedSelection == null) {
            findSelectedTextInInteractiveWindows()
        } else {
            null
        }
        if (activeWindowSelection != null) {
            rememberSelectedText(activeWindowSelection, sourcePackage)
        }

        val managerValue = fromClipboard?.takeIf { it.isNotBlank() }
        val rawValue = managerValue ?: rememberedSelection ?: activeWindowSelection
        val capturePath = when {
            managerValue != null -> "clipboard_manager"
            rememberedSelection != null -> "selected_text_fallback"
            activeWindowSelection != null -> "active_window_selection"
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

    private fun findSelectedTextInInteractiveWindows(): String? {
        var scanned = 0
        for (window in windows.orEmpty()) {
            val root = window.root ?: continue
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty() && scanned < MAX_WINDOW_SCAN_NODES) {
                val node = queue.removeFirst()
                scanned += 1
                val selected = selectedTextFromNode(node)
                if (!selected.isNullOrBlank()) {
                    return selected.trim().take(MAX_TEXT_LENGTH)
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::addLast)
                }
            }
            if (scanned >= MAX_WINDOW_SCAN_NODES) break
        }
        return null
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
