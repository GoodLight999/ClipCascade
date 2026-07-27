package com.clipcascade

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/** Pure classifier kept separate from AccessibilityService for JVM regression tests. */
internal object CopySignalClassifier {
    fun isHighConfidenceCopySignal(
        action: Int,
        eventType: Int,
        labels: Iterable<String>
    ): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_COPY) {
            return true
        }

        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> labels.any(::isCopyCommand)
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> labels.any(::isCopiedConfirmation)
            else -> false
        }
    }

    internal fun isCopyCommand(value: String): Boolean {
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

    internal fun isCopiedConfirmation(value: String): Boolean {
        val normalized = normalize(value)
        return COPIED_CONFIRMATIONS.any(normalized::contains)
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

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
