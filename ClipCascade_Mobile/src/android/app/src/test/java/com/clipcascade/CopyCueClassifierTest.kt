package com.clipcascade

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyCueClassifierTest {
    @Test
    fun selectionToolbarLabelIsNotPassiveCopyCompletion() {
        assertFalse(CopyCueClassifier.isPassiveCopyCompletion("", "Copy"))
        assertFalse(CopyCueClassifier.isPassiveCopyCompletion("", "コピー"))
    }

    @Test
    fun directClickOnCopyCommandIsAccepted() {
        assertTrue(CopyCueClassifier.isDirectCopyInteraction("", "Copy"))
        assertTrue(CopyCueClassifier.isDirectCopyInteraction("", "コピー"))
    }

    @Test
    fun passiveCopiedConfirmationIsAccepted() {
        assertTrue(CopyCueClassifier.isPassiveCopyCompletion("Copied", ""))
        assertTrue(CopyCueClassifier.isPassiveCopyCompletion("Copied to clipboard", ""))
        assertTrue(CopyCueClassifier.isPassiveCopyCompletion("コピーしました", ""))
        assertTrue(CopyCueClassifier.isPassiveCopyCompletion("クリップボードにコピーしました", ""))
    }

    @Test
    fun ordinaryPageTextMentioningCopyIsNotCompletion() {
        assertFalse(
            CopyCueClassifier.isPassiveCopyCompletion(
                "Learn how to copy text from this page",
                "Copy text",
            ),
        )
    }
}
