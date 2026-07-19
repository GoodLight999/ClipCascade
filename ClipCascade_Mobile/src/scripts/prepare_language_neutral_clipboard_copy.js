const fs = require('fs');
const path = require('path');

const accessibilityPath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
  'ClipboardAccessibilityService.kt',
);
let source = fs.readFileSync(accessibilityPath, 'utf8');

source = source.replace(
  '        private const val CLIPBOARD_CHANGE_SELECTION_WINDOW_MS = 3_000L\n',
  '        private const val COPY_ACTION_FALLBACK_DELAY_MS = 700L\n',
);

const listenerStart = source.indexOf('    private var clipboardManager: ClipboardManager? = null\n');
const listenerEnd = source.indexOf('    override fun onServiceConnected() {\n', listenerStart);
if (listenerStart < 0 || listenerEnd < 0 || listenerEnd <= listenerStart) {
  throw new Error('Expected transformed clipboard-listener block was not found');
}
const languageNeutralListener = `    private var clipboardManager: ClipboardManager? = null
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

`;
source =
  source.slice(0, listenerStart) +
  languageNeutralListener +
  source.slice(listenerEnd);

const accessibilityEventStart = source.indexOf(
  '    override fun onAccessibilityEvent(event: AccessibilityEvent?) {\n',
);
const accessibilityEventEnd = source.indexOf(
  '    override fun onKeyEvent(event: KeyEvent?): Boolean {\n',
  accessibilityEventStart,
);
if (
  accessibilityEventStart < 0 ||
  accessibilityEventEnd < 0 ||
  accessibilityEventEnd <= accessibilityEventStart
) {
  throw new Error('Expected Accessibility event block was not found');
}
const languageNeutralAccessibilityEvents = `    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val current = event ?: return
        if (!RelaySettingsStore.clipboardEnabled(this)) return
        if (current.packageName?.toString() == packageName) return

        if (current.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            // Selection is state only. It never queues or sends by itself.
            rememberSelection(current)
            return
        }

        if (current.action == AccessibilityNodeInfo.ACTION_COPY) {
            scheduleCopyActionFallback("accessibility_action_copy")
        }
    }

`;
source =
  source.slice(0, accessibilityEventStart) +
  languageNeutralAccessibilityEvents +
  source.slice(accessibilityEventEnd);

const keyEventStart = source.indexOf(
  '    override fun onKeyEvent(event: KeyEvent?): Boolean {\n',
);
const rememberSelectionStart = source.indexOf(
  '    private fun rememberSelection(event: AccessibilityEvent) {\n',
  keyEventStart,
);
if (keyEventStart < 0 || rememberSelectionStart < 0 || rememberSelectionStart <= keyEventStart) {
  throw new Error('Expected key-event/helper block was not found');
}
const languageNeutralKeyAndFallback = `    override fun onKeyEvent(event: KeyEvent?): Boolean {
        val current = event ?: return false
        if (!RelaySettingsStore.clipboardEnabled(this)) return false
        if (
            current.action == KeyEvent.ACTION_UP &&
            current.keyCode == KeyEvent.KEYCODE_C &&
            current.isCtrlPressed
        ) {
            scheduleCopyActionFallback("ctrl_c")
        }
        return false
    }

    private fun scheduleCopyActionFallback(trigger: String) {
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

`;
source =
  source.slice(0, keyEventStart) +
  languageNeutralKeyAndFallback +
  source.slice(rememberSelectionStart);

const copyFunctionStart = source.indexOf(
  '    private fun looksLikeCopyConfirmation(event: AccessibilityEvent): Boolean {\n',
);
const scheduleCaptureStart = source.indexOf(
  '    private fun scheduleCapture(event: AccessibilityEvent?, trigger: String) {\n',
  copyFunctionStart,
);
if (copyFunctionStart >= 0) {
  if (scheduleCaptureStart < 0 || scheduleCaptureStart <= copyFunctionStart) {
    throw new Error('Expected scheduleCapture after obsolete copy-text classifier');
  }
  source = source.slice(0, copyFunctionStart) + source.slice(scheduleCaptureStart);
}

if (source.includes('CopyCueClassifier')) {
  throw new Error('Language-dependent CopyCueClassifier still referenced by transformed service');
}
if (source.includes('looksLikeCopyConfirmation')) {
  throw new Error('Language-dependent copy-confirmation function still remains');
}

fs.writeFileSync(accessibilityPath, source, 'utf8');
console.log('Prepared language-neutral clipboard-change and semantic-action copy confirmation.');
