const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

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
let accessibility = fs.readFileSync(accessibilityPath, 'utf8');

accessibility = replaceRequired(
  accessibility,
  `        val queued = ClipboardRelayStore.enqueue(applicationContext, item)
        if (queued) {
            ClipboardRelayDispatcher.schedule(applicationContext)
        }
        RelayHealthStore.record(
            applicationContext,
            category = "clipboard",
            trigger = trigger,
            path = capturePath,
            result = if (queued) "queued" else "deduplicated",
        )`,
  `        val enqueueResult = ClipboardRelayStore.enqueue(applicationContext, item)
        if (enqueueResult == ClipboardRelayStore.EnqueueResult.QUEUED) {
            ClipboardRelayDispatcher.schedule(applicationContext)
        }
        RelayHealthStore.record(
            applicationContext,
            category = "clipboard",
            trigger = trigger,
            path = capturePath,
            result = when (enqueueResult) {
                ClipboardRelayStore.EnqueueResult.QUEUED -> "queued"
                ClipboardRelayStore.EnqueueResult.DEDUPLICATED -> "deduplicated"
                ClipboardRelayStore.EnqueueResult.QUEUE_FULL -> "queue_full"
            },
        )`,
  'final Accessibility clipboard enqueue block',
);
fs.writeFileSync(accessibilityPath, accessibility, 'utf8');

const settingsPath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
  'RelaySettingsActivity.kt',
);
let settings = fs.readFileSync(settingsPath, 'utf8');

settings = replaceRequired(
  settings,
  `        val queued = ClipboardRelayStore.enqueue(
            applicationContext,
            ClipboardRelayStore.Item(
                id = "settings-test:$now",
                text = getString(R.string.clipboard_test_value),
                sourcePackage = packageName,
                createdAt = now,
            ),
        )
        if (queued) {
            ClipboardRelayDispatcher.schedule(applicationContext)
            RelayHealthStore.record(
                applicationContext,
                category = "clipboard",
                trigger = "settings_test",
                path = "manual_test",
                result = "queued",
            )
        }
        Toast.makeText(
            this,
            getString(
                if (queued) R.string.clipboard_test_queued
                else R.string.clipboard_test_duplicate,
            ),
            Toast.LENGTH_SHORT,
        ).show()`,
  `        val enqueueResult = ClipboardRelayStore.enqueue(
            applicationContext,
            ClipboardRelayStore.Item(
                id = "settings-test:$now",
                text = getString(R.string.clipboard_test_value),
                sourcePackage = packageName,
                createdAt = now,
            ),
        )
        if (enqueueResult == ClipboardRelayStore.EnqueueResult.QUEUED) {
            ClipboardRelayDispatcher.schedule(applicationContext)
        }
        val result = when (enqueueResult) {
            ClipboardRelayStore.EnqueueResult.QUEUED -> "queued"
            ClipboardRelayStore.EnqueueResult.DEDUPLICATED -> "deduplicated"
            ClipboardRelayStore.EnqueueResult.QUEUE_FULL -> "queue_full"
        }
        RelayHealthStore.record(
            applicationContext,
            category = "clipboard",
            trigger = "settings_test",
            path = "manual_test",
            result = result,
        )
        val toastMessage = when (enqueueResult) {
            ClipboardRelayStore.EnqueueResult.QUEUED -> R.string.clipboard_test_queued
            ClipboardRelayStore.EnqueueResult.DEDUPLICATED -> R.string.clipboard_test_duplicate
            ClipboardRelayStore.EnqueueResult.QUEUE_FULL -> R.string.clipboard_test_queue_full
        }
        Toast.makeText(
            this,
            getString(toastMessage),
            Toast.LENGTH_SHORT,
        ).show()`,
  'settings clipboard-test enqueue block',
);

settings = replaceRequired(
  settings,
  `            "deduplicated" -> R.string.health_deduplicated
            "retrying" -> R.string.health_retrying`,
  `            "deduplicated" -> R.string.health_deduplicated
            "queue_full" -> R.string.health_queue_full
            "retrying" -> R.string.health_retrying`,
  'queue-full health label',
);
fs.writeFileSync(settingsPath, settings, 'utf8');

const stringFiles = [
  {
    file: path.resolve(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'values', 'strings.xml'),
    duplicate: '    <string name="clipboard_test_duplicate">The same test string is already pending</string>',
    replacement:
      '    <string name="clipboard_test_duplicate">The same test string is already pending</string>\n' +
      '    <string name="clipboard_test_queue_full">The relay queue is full. Reconnect or clear pending values before testing again.</string>',
    health: '    <string name="health_deduplicated">duplicate suppressed</string>',
    healthReplacement:
      '    <string name="health_deduplicated">duplicate suppressed</string>\n' +
      '    <string name="health_queue_full">queue full; new value rejected</string>',
  },
  {
    file: path.resolve(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'values-ja', 'strings.xml'),
    duplicate: '    <string name="clipboard_test_duplicate">同じテスト文字列がすでに保留中です</string>',
    replacement:
      '    <string name="clipboard_test_duplicate">同じテスト文字列がすでに保留中です</string>\n' +
      '    <string name="clipboard_test_queue_full">送信キューが満杯です。Windowsへ再接続するか、保留データを消去してから再試行してください。</string>',
    health: '    <string name="health_deduplicated">重複抑止</string>',
    healthReplacement:
      '    <string name="health_deduplicated">重複抑止</string>\n' +
      '    <string name="health_queue_full">キュー満杯・新規値を拒否</string>',
  },
];

for (const entry of stringFiles) {
  let strings = fs.readFileSync(entry.file, 'utf8');
  strings = replaceRequired(strings, entry.duplicate, entry.replacement, `${entry.file} test queue-full string`);
  strings = replaceRequired(strings, entry.health, entry.healthReplacement, `${entry.file} health queue-full string`);
  fs.writeFileSync(entry.file, strings, 'utf8');
}

console.log('Prepared ACK-safe bounded clipboard queue overflow handling and UI diagnostics.');
