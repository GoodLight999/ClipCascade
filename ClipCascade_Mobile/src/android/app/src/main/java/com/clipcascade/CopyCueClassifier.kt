package com.clipcascade

/**
 * Separates an actual Copy action/completion from merely displaying a Copy command.
 *
 * Text selection commonly opens a floating toolbar whose node text is simply
 * "Copy" / "コピー". Passive window-content events caused by that toolbar must
 * never be treated as a completed copy. Generic command labels are accepted only
 * on direct click/context-click events; passive events require completion wording.
 */
object CopyCueClassifier {
    private val copyCommandRegex = Regex(
        pattern = "(?i)(?:^|[\\s:_-])(?:copy|copy\\s+text|copy\\s+link)(?:$|[\\s:_-])|(?:^|\\s)コピー(?:$|\\s)",
    )

    private val copyCompletionRegex = Regex(
        pattern = "(?i)(?:^|[\\s:_-])copied(?:\\s+(?:text|link))?(?:\\s+to\\s+(?:the\\s+)?clipboard)?(?:$|[\\s:_.!-])|" +
            "copy(?:ing)?\\s+(?:complete|completed)|コピーしました|クリップボードにコピー(?:しました)?|コピー済み",
    )

    fun isDirectCopyInteraction(eventText: String, nodeText: String): Boolean =
        copyCommandRegex.containsMatchIn(eventText) ||
            copyCommandRegex.containsMatchIn(nodeText) ||
            copyCompletionRegex.containsMatchIn(eventText) ||
            copyCompletionRegex.containsMatchIn(nodeText)

    fun isPassiveCopyCompletion(eventText: String, nodeText: String): Boolean =
        copyCompletionRegex.containsMatchIn(eventText) ||
            copyCompletionRegex.containsMatchIn(nodeText)
}
