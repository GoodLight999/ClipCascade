package com.clipcascade

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Exact Accessibility action classifier.
 *
 * Android documents AccessibilityEvent#getAction as the performed action and
 * AccessibilityNodeInfo.ACTION_COPY as the action that copies the current
 * selection. UI labels, announcements, translations, and substring matching
 * are intentionally not used because they are application-specific heuristics.
 */
internal object CopySignalClassifier {
    fun isCopyAction(action: Int): Boolean =
        action == AccessibilityNodeInfo.ACTION_COPY
}
