package com.clipcascade

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Conservative background trigger for Android 10+ clipboard restrictions.
 *
 * This service does not inspect arbitrary screen contents and does not react to
 * generic clicks or text-selection changes. It only asks the existing
 * ClipboardFloatingActivity path to read the clipboard after a high-confidence
 * copy signal. The existing React Native foreground service remains the owner
 * of transport and deduplication.
 */
class ClipCascadeAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastTriggerAt: Long = 0L

    private val triggerClipboardRead = Runnable {
        if (!ClipboardListenerModule.isRuntimeActive()) {
            Log.d(TAG, "Ignoring copy trigger because the ClipCascade runtime is inactive")
            return@Runnable
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Ignoring copy trigger because overlay permission is missing")
            return@Runnable
        }

        try {
            startActivity(ClipboardFloatingActivity.getIntent(this))
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch the existing clipboard-read activity", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (!ClipboardListenerModule.isRuntimeActive()) return
        if (event.packageName?.toString() == packageName) return
        if (!isHighConfidenceCopySignal(event)) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < TRIGGER_DEBOUNCE_MS) return
        lastTriggerAt = now

        handler.removeCallbacks(triggerClipboardRead)
        handler.postDelayed(triggerClipboardRead, CLIPBOARD_WRITE_SETTLE_MS)
    }

    private fun isHighConfidenceCopySignal(event: AccessibilityEvent): Boolean {
        if (event.action == AccessibilityNodeInfo.ACTION_COPY) {
            return true
        }

        val labels = mutableListOf<String>()
        event.text.forEach { value ->
            value?.toString()?.let(labels::add)
        }
        event.contentDescription?.toString()?.let(labels::add)

        return when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> labels.any(::isCopyCommand)
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> labels.any(::isCopiedConfirmation)
            else -> false
        }
    }

    private fun isCopyCommand(value: String): Boolean {
        val normalized = normalize(value)
        if (normalized in EXACT_COPY_COMMANDS) return true

        return normalized.startsWith("copy ") ||
                normalized.endsWith(" copy") ||
                normalized.endsWith("をコピー") ||
                normalized.startsWith("コピー ") ||
                normalized.startsWith("复制") ||
                normalized.startsWith("複製") ||
                normalized.startsWith("복사")
    }

    private fun isCopiedConfirmation(value: String): Boolean {
        val normalized = normalize(value)
        return COPIED_CONFIRMATIONS.any(normalized::contains)
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    override fun onInterrupt() {
        handler.removeCallbacks(triggerClipboardRead)
    }

    override fun onDestroy() {
        handler.removeCallbacks(triggerClipboardRead)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClipCascadeA11y"
        private const val TRIGGER_DEBOUNCE_MS = 600L
        private const val CLIPBOARD_WRITE_SETTLE_MS = 250L

        private val EXACT_COPY_COMMANDS = setOf(
            "copy",
            "copy link",
            "copy text",
            "copy image",
            "コピー",
            "リンクをコピー",
            "テキストをコピー",
            "画像をコピー",
            "复制",
            "复制链接",
            "复制文本",
            "複製",
            "複製連結",
            "복사",
            "링크 복사",
            "텍스트 복사"
        )

        private val COPIED_CONFIRMATIONS = setOf(
            "copied",
            "copied to clipboard",
            "コピーしました",
            "クリップボードにコピー",
            "コピーされました",
            "已复制",
            "已複製",
            "복사됨",
            "클립보드에 복사"
        )
    }
}
