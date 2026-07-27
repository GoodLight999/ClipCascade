package com.clipcascade

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopySignalClassifierTest {
    @Test
    fun explicitAccessibilityCopyActionAlwaysTriggers() {
        assertTrue(
            CopySignalClassifier.isHighConfidenceCopySignal(
                action = AccessibilityNodeInfo.ACTION_COPY,
                eventType = AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
                labels = emptyList()
            )
        )
    }

    @Test
    fun copyLabelledClickTriggers() {
        assertTrue(
            CopySignalClassifier.isHighConfidenceCopySignal(
                action = 0,
                eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
                labels = listOf("リンクをコピー")
            )
        )
    }

    @Test
    fun copiedAnnouncementTriggers() {
        assertTrue(
            CopySignalClassifier.isHighConfidenceCopySignal(
                action = 0,
                eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT,
                labels = listOf("Copied to clipboard")
            )
        )
    }

    @Test
    fun genericAmazonSearchClickDoesNotTrigger() {
        assertFalse(
            CopySignalClassifier.isHighConfidenceCopySignal(
                action = 0,
                eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
                labels = listOf("Amazonで検索", "検索フィールド")
            )
        )
    }

    @Test
    fun genericTextSelectionDoesNotTrigger() {
        assertFalse(
            CopySignalClassifier.isHighConfidenceCopySignal(
                action = 0,
                eventType = AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
                labels = listOf("selected text")
            )
        )
    }

    @Test
    fun unrelatedNotificationContainingCopyAsPartOfWordDoesNotTrigger() {
        assertFalse(
            CopySignalClassifier.isHighConfidenceCopySignal(
                action = 0,
                eventType = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
                labels = listOf("Copyright information updated")
            )
        )
    }

    @Test
    fun multilingualCommandsRemainSupported() {
        assertTrue(CopySignalClassifier.isCopyCommand("复制链接"))
        assertTrue(CopySignalClassifier.isCopyCommand("텍스트 복사"))
        assertTrue(CopySignalClassifier.isCopiedConfirmation("クリップボードにコピーしました"))
    }
}
