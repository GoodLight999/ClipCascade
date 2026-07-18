const fs = require('fs');
const path = require('path');

const recoveryPath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
  'RecoveryCoordinator.kt',
);
let source = fs.readFileSync(recoveryPath, 'utf8');

const before = `        if (
            lastRequestElapsedMs > 0L &&
            now - lastRequestElapsedMs < MIN_REQUEST_INTERVAL_MS
        ) {
            Log.i(TAG, "Recovery request suppressed by cooldown")
            RelayHealthStore.record(
                applicationContext,
                category = "recovery",
                trigger = reason,
                path = "coordinator",
                result = "cooldown",
            )
            return true
        }
`;
const after = `        if (
            lastRequestElapsedMs > 0L &&
            now - lastRequestElapsedMs < MIN_REQUEST_INTERVAL_MS
        ) {
            // Do not start another Android Service during the bounded cooldown,
            // but a dead React generation must not remain unrecoverable for a full
            // minute while a persistent relay item is waiting.
            requestReactContextBootstrap(applicationContext, reason)
            Log.i(TAG, "Service recovery suppressed by cooldown; React bootstrap retained")
            RelayHealthStore.record(
                applicationContext,
                category = "recovery",
                trigger = reason,
                path = "react_bootstrap",
                result = "cooldown",
            )
            return true
        }
`;
if (!source.includes(before)) {
  throw new Error('Expected RecoveryCoordinator cooldown block was not found');
}
source = source.replace(before, after);
fs.writeFileSync(recoveryPath, source, 'utf8');
console.log('Kept in-process React bootstrap active during service-start cooldown.');
