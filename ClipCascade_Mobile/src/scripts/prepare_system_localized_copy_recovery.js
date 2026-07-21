const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

const javaDir = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
);
const testDir = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'test',
  'java',
  'com',
  'clipcascade',
);

const cuePolicySource = String.raw`package com.clipcascade

import java.text.Normalizer
import java.util.Locale

/**
 * Matches only the framework-provided Copy / Copy URL labels for the active
 * Android locale. This avoids hard-coding Japanese and English while keeping
 * selection-only events and approximate text matches out of the send path.
 */
object SystemCopyCuePolicy {
    fun matchesAny(
        candidates: Iterable<CharSequence?>,
        copyLabel: CharSequence,
        copyUrlLabel: CharSequence,
    ): Boolean {
        val accepted = setOf(normalize(copyLabel), normalize(copyUrlLabel))
            .filterTo(linkedSetOf()) { it.isNotEmpty() }
        return candidates.any { candidate ->
            val normalized = normalize(candidate)
            normalized.isNotEmpty() && normalized in accepted
        }
    }

    fun normalize(value: CharSequence?): String = Normalizer.normalize(
        value?.toString().orEmpty(),
        Normalizer.Form.NFKC,
    )
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)
}
`;
fs.writeFileSync(
  path.join(javaDir, 'SystemCopyCuePolicy.kt'),
  cuePolicySource,
  'utf8',
);

const cuePolicyTestSource = String.raw`package com.clipcascade

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCopyCuePolicyTest {
    @Test
    fun frameworkLabelsMatchAcrossLocales() {
        assertTrue(
            SystemCopyCuePolicy.matchesAny(
                candidates = listOf("コピー"),
                copyLabel = "コピー",
                copyUrlLabel = "URLをコピー",
            ),
        )
        assertTrue(
            SystemCopyCuePolicy.matchesAny(
                candidates = listOf("복사"),
                copyLabel = "복사",
                copyUrlLabel = "URL 복사",
            ),
        )
    }

    @Test
    fun frameworkCopyUrlLabelAlsoMatches() {
        assertTrue(
            SystemCopyCuePolicy.matchesAny(
                candidates = listOf("Copy URL"),
                copyLabel = "Copy",
                copyUrlLabel = "Copy URL",
            ),
        )
    }

    @Test
    fun normalizationIsExactAfterWhitespaceAndNfkc() {
        assertTrue(
            SystemCopyCuePolicy.matchesAny(
                candidates = listOf("  Ｃｏｐｙ  "),
                copyLabel = "Copy",
                copyUrlLabel = "Copy URL",
            ),
        )
    }

    @Test
    fun approximateOrSelectionTextNeverMatches() {
        listOf("Copied", "Copy all", "Paste", "selected article text", "").forEach { value ->
            assertFalse(
                SystemCopyCuePolicy.matchesAny(
                    candidates = listOf(value),
                    copyLabel = "Copy",
                    copyUrlLabel = "Copy URL",
                ),
            )
        }
    }
}
`;
fs.mkdirSync(testDir, {recursive: true});
fs.writeFileSync(
  path.join(testDir, 'SystemCopyCuePolicyTest.kt'),
  cuePolicyTestSource,
  'utf8',
);

const accessibilityPath = path.join(javaDir, 'ClipboardAccessibilityService.kt');
let accessibility = fs.readFileSync(accessibilityPath, 'utf8');
accessibility = replaceRequired(
  accessibility,
  `    private var clipboardManager: ClipboardManager? = null
    private var clipboardChangeSerial = 0L
    private val clipboardChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        // Increment for every actual OS clipboard mutation, including internal
        // writes. This cancels any pending ACTION_COPY / Ctrl+C fallback.
        clipboardChangeSerial += 1L
        val internalWrite = ClipboardWriteGuard.consumeIfMarked()
        if (!RelaySettingsStore.clipboardEnabled(this)) {
            return@OnPrimaryClipChangedListener
        }
        val selected = lastSelectedText
        val age = System.currentTimeMillis() - lastSelectionAt
        if (
            !ClipboardCopySignalPolicy.shouldCaptureClipboardChange(
                internalWrite = internalWrite,
                hasSelectedText = !selected.isNullOrBlank(),
                selectionAgeMs = age,
            )
        ) {
            return@OnPrimaryClipChangedListener
        }
        // This callback is the language-neutral proof that clipboard state really
        // changed. The payload is read from ClipboardManager when allowed, with the
        // recent Accessibility selection as the Android background fallback.
        scheduleCapture(null, "clipboard_change")
    }
`,
  `    private var clipboardManager: ClipboardManager? = null
    private var clipboardChangeSerial = 0L
    private val clipboardChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        // Increment for every actual OS clipboard mutation, including internal
        // writes. This cancels any pending ACTION_COPY / Ctrl+C fallback.
        clipboardChangeSerial += 1L
        val internalWrite = ClipboardWriteGuard.consumeIfMarked()
        if (internalWrite || !RelaySettingsStore.clipboardEnabled(this)) {
            return@OnPrimaryClipChangedListener
        }
        val selected = lastSelectedText
        val age = System.currentTimeMillis() - lastSelectionAt
        val accepted = ClipboardCopySignalPolicy.shouldCaptureClipboardChange(
            internalWrite = false,
            hasSelectedText = !selected.isNullOrBlank(),
            selectionAgeMs = age,
        )
        RelayHealthStore.record(
            applicationContext,
            category = "copy_detection",
            trigger = "clipboard_change",
            path = "os_callback",
            result = if (accepted) "requested" else "declined",
        )
        if (!accepted) {
            return@OnPrimaryClipChangedListener
        }
        // Foreground clipboard callbacks remain the strongest signal. Modern
        // Android can suppress this callback when ClipCascade is not focused,
        // so a framework-localized Accessibility click cue is installed below as
        // a conservative fallback rather than assuming this callback is universal.
        scheduleCapture(null, "clipboard_change")
    }
`,
  'clipboard callback diagnostics and honest platform wording',
);

accessibility = replaceRequired(
  accessibility,
  `        if (current.action == AccessibilityNodeInfo.ACTION_COPY) {
            scheduleCopyActionFallback("accessibility_action_copy")
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
`,
  `        if (current.action == AccessibilityNodeInfo.ACTION_COPY) {
            RelayHealthStore.record(
                applicationContext,
                category = "copy_detection",
                trigger = "semantic_copy_action",
                path = "accessibility",
                result = "requested",
            )
            scheduleCopyActionFallback("accessibility_action_copy")
            return
        }

        if (isSystemLocalizedCopyCue(current)) {
            RelayHealthStore.record(
                applicationContext,
                category = "copy_detection",
                trigger = "system_copy_label",
                path = "accessibility_framework_label",
                result = "requested",
            )
            scheduleCopyActionFallback("system_localized_copy")
        }
    }

    private fun isSystemLocalizedCopyCue(event: AccessibilityEvent): Boolean {
        if (
            event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED
        ) {
            return false
        }
        val node = event.source
        val candidates = buildList<CharSequence?> {
            add(event.contentDescription)
            addAll(event.text)
            add(node?.text)
            add(node?.contentDescription)
        }
        return SystemCopyCuePolicy.matchesAny(
            candidates = candidates,
            copyLabel = getText(android.R.string.copy),
            copyUrlLabel = getText(android.R.string.copyUrl),
        )
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
`,
  'framework-localized Accessibility Copy cue',
);

accessibility = replaceRequired(
  accessibility,
  `    private fun scheduleCopyActionFallback(trigger: String) {
        val serialAtAction = clipboardChangeSerial
        handler.postDelayed(
            {
                val selected = lastSelectedText
                val age = System.currentTimeMillis() - lastSelectionAt
                if (
                    ClipboardCopySignalPolicy.shouldRunActionFallback(
                        hasSelectedText = !selected.isNullOrBlank(),
                        selectionAgeMs = age,
                        clipboardSerialAtAction = serialAtAction,
                        currentClipboardSerial = clipboardChangeSerial,
                    )
                ) {
                    // Some OEMs report semantic ACTION_COPY but suppress the
                    // clipboard callback to a background Accessibility process.
                    scheduleCapture(null, trigger + "_fallback")
                }
            },
            COPY_ACTION_FALLBACK_DELAY_MS,
        )
    }
`,
  `    private fun scheduleCopyActionFallback(trigger: String) {
        val serialAtAction = clipboardChangeSerial
        handler.postDelayed(
            {
                val selected = lastSelectedText
                val age = System.currentTimeMillis() - lastSelectionAt
                val shouldRun = ClipboardCopySignalPolicy.shouldRunActionFallback(
                    hasSelectedText = !selected.isNullOrBlank(),
                    selectionAgeMs = age,
                    clipboardSerialAtAction = serialAtAction,
                    currentClipboardSerial = clipboardChangeSerial,
                )
                RelayHealthStore.record(
                    applicationContext,
                    category = "copy_detection",
                    trigger = trigger,
                    path = "action_fallback",
                    result = if (shouldRun) "requested" else "declined",
                )
                if (shouldRun) {
                    // A direct clipboard callback did not arrive after an explicit
                    // Copy cue. Use only the remembered selection; selection alone
                    // can never reach this branch.
                    scheduleCapture(null, trigger + "_fallback")
                }
            },
            COPY_ACTION_FALLBACK_DELAY_MS,
        )
    }
`,
  'copy fallback diagnostics',
);
fs.writeFileSync(accessibilityPath, accessibility, 'utf8');

const settingsPath = path.join(javaDir, 'RelaySettingsActivity.kt');
let settings = fs.readFileSync(settingsPath, 'utf8');
settings = replaceRequired(
  settings,
  `            formatHealth(
                getString(R.string.health_clipboard),
                RelayHealthStore.read(this, "clipboard"),
            ),
            formatHealth(
                getString(R.string.health_verification),
`,
  `            formatHealth(
                getString(R.string.health_clipboard),
                RelayHealthStore.read(this, "clipboard"),
            ),
            formatHealth(
                getString(R.string.health_copy_detection),
                RelayHealthStore.read(this, "copy_detection"),
            ),
            formatHealth(
                getString(R.string.health_verification),
`,
  'copy-detection health row',
);
settings = replaceRequired(
  settings,
  `            "ctrl_c" -> R.string.health_ctrl_c
            "settings_test" -> R.string.health_settings_test
`,
  `            "ctrl_c" -> R.string.health_ctrl_c
            "clipboard_change" -> R.string.health_clipboard_change
            "semantic_copy_action" -> R.string.health_semantic_copy_action
            "system_copy_label" -> R.string.health_system_copy_label
            "system_localized_copy" -> R.string.health_system_copy_label
            "accessibility_action_copy" -> R.string.health_semantic_copy_action
            "os_callback" -> R.string.health_os_callback
            "accessibility_framework_label" -> R.string.health_accessibility_framework_label
            "action_fallback" -> R.string.health_action_fallback
            "settings_test" -> R.string.health_settings_test
`,
  'copy-detection health labels',
);
fs.writeFileSync(settingsPath, settings, 'utf8');

const stringFiles = [
  {
    file: path.resolve(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'values', 'strings.xml'),
    healthAnchor: '    <string name="health_clipboard">Clipboard</string>',
    healthReplacement:
      '    <string name="health_clipboard">Clipboard capture</string>\n' +
      '    <string name="health_copy_detection">Copy detection</string>\n' +
      '    <string name="health_clipboard_change">OS clipboard changed</string>\n' +
      '    <string name="health_semantic_copy_action">semantic Copy action</string>\n' +
      '    <string name="health_system_copy_label">system-localized Copy click</string>\n' +
      '    <string name="health_os_callback">OS callback</string>\n' +
      '    <string name="health_accessibility_framework_label">Accessibility + Android framework label</string>\n' +
      '    <string name="health_action_fallback">delayed Copy fallback</string>',
  },
  {
    file: path.resolve(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'values-ja', 'strings.xml'),
    healthAnchor: '    <string name="health_clipboard">通常コピー</string>',
    healthReplacement:
      '    <string name="health_clipboard">クリップボード取得</string>\n' +
      '    <string name="health_copy_detection">コピー操作の検出</string>\n' +
      '    <string name="health_clipboard_change">OSクリップボード変更</string>\n' +
      '    <string name="health_semantic_copy_action">意味的なコピー操作</string>\n' +
      '    <string name="health_system_copy_label">システム言語のコピーボタン</string>\n' +
      '    <string name="health_os_callback">OSコールバック</string>\n' +
      '    <string name="health_accessibility_framework_label">ユーザー補助＋Android標準ラベル</string>\n' +
      '    <string name="health_action_fallback">遅延コピー補助経路</string>',
  },
];
for (const entry of stringFiles) {
  let strings = fs.readFileSync(entry.file, 'utf8');
  strings = replaceRequired(
    strings,
    entry.healthAnchor,
    entry.healthReplacement,
    `${entry.file} copy-detection health strings`,
  );
  fs.writeFileSync(entry.file, strings, 'utf8');
}

const extractorTestPath = path.join(testDir, 'OtpCodeExtractorTest.kt');
let extractorTests = fs.readFileSync(extractorTestPath, 'utf8');
if (!extractorTests.includes('extractsDawnStandaloneNumericLoginCode')) {
  const finalBrace = extractorTests.lastIndexOf('\n}');
  if (finalBrace < 0) {
    throw new Error('Expected OTP extractor test class closing brace was not found');
  }
  const dawnTest = `

    @Test
    fun extractsDawnStandaloneNumericLoginCode() {
        assertEquals(
            "482951",
            OtpCodeExtractor.extract(
                """
                DAWN
                Log in to DAWN
                Your code is
                482951
                This code expires in 10 minutes. Do not share this code with anyone.
                """.trimIndent(),
            ),
        )
    }
`;
  extractorTests =
    extractorTests.slice(0, finalBrace) + dawnTest + extractorTests.slice(finalBrace);
}
fs.writeFileSync(extractorTestPath, extractorTests, 'utf8');

console.log(
  'Prepared system-localized Accessibility Copy fallback, copy-detection diagnostics, and DAWN-shaped extractor regression.',
);
