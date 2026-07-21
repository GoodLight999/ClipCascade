const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

const testManagerPath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
  'OtpTestNotificationManager.kt',
);
let testManager = fs.readFileSync(testManagerPath, 'utf8');
// The lifecycle transform lengthens the first synthetic notification. The
// listener-path notification is the remaining 60-second notification and must
// stay visible long enough for requestRebind plus delayed active rescans.
testManager = replaceRequired(
  testManager,
  '.setTimeoutAfter(60_000L)',
  '.setTimeoutAfter(5 * 60_000L)',
  'listener-path notification timeout',
);
fs.writeFileSync(testManagerPath, testManager, 'utf8');

console.log('Prepared listener-path timeout after foreground runner lifecycle transform.');
