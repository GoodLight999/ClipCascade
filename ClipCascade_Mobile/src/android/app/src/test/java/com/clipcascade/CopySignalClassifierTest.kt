package com.clipcascade

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopySignalClassifierTest {
    @Test
    fun documentedAccessibilityCopyActionTriggers() {
        assertTrue(
            CopySignalClassifier.isCopyAction(
                AccessibilityNodeInfo.ACTION_COPY
            )
        )
    }

    @Test
    fun clickActionDoesNotTrigger() {
        assertFalse(
            CopySignalClassifier.isCopyAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )
        )
    }

    @Test
    fun missingActionDoesNotTrigger() {
        assertFalse(CopySignalClassifier.isCopyAction(0))
    }

    @Test
    fun pasteAndCutActionsDoNotTrigger() {
        assertFalse(
            CopySignalClassifier.isCopyAction(
                AccessibilityNodeInfo.ACTION_PASTE
            )
        )
        assertFalse(
            CopySignalClassifier.isCopyAction(
                AccessibilityNodeInfo.ACTION_CUT
            )
        )
    }
}
