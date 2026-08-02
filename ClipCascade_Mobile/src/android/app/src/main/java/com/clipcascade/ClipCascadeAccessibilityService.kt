package com.clipcascade

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Exact-action trigger for the unified clipboard acquisition coordinator.
 *
 * This service does not inspect screen contents, labels, announcements, or
 * translated UI strings. It reacts only when Android reports ACTION_COPY as
 * the action that triggered an AccessibilityEvent.
 */
class ClipCascadeAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (!ClipboardListenerModule.isRuntimeActive()) return
        if (event.packageName?.toString() == packageName) return
        if (!CopySignalClassifier.isCopyAction(event.action)) return

        Log.d(TAG, "Documented ACTION_COPY received from ${event.packageName}")
        BackgroundClipboardCapture.request(this, "accessibility_action_copy")
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "ClipCascadeA11y"
    }
}
