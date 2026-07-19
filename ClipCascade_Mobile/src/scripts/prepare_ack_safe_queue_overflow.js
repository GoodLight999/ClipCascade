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

const before = `        val queued = ClipboardRelayStore.enqueue(applicationContext, item)
        if (queued) {
            ClipboardRelayDispatcher.schedule(applicationContext)
        }
        RelayHealthStore.record(
            applicationContext,
            category = "clipboard",
            trigger = trigger,
            path = capturePath,
            result = if (queued) "queued" else "deduplicated",
        )`;

const after = `        val enqueueResult = ClipboardRelayStore.enqueue(applicationContext, item)
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
        )`;

if (!source.includes(before)) {
  throw new Error('Expected final clipboard enqueue block was not found');
}
source = source.replace(before, after);

if (!source.includes('ClipboardRelayStore.EnqueueResult.QUEUE_FULL -> "queue_full"')) {
  throw new Error('Queue-full diagnostic was not installed');
}

fs.writeFileSync(accessibilityPath, source, 'utf8');
console.log('Prepared ACK-safe bounded clipboard queue overflow handling.');
