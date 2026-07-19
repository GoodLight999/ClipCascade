const fs = require('fs');
const path = require('path');

const notifierPath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
  'RelayDebugNotifier.kt',
);

let source = fs.readFileSync(notifierPath, 'utf8');
const before = '            .setSmallIcon(R.drawable.ic_small_icon)';
const after = '            .setSmallIcon(R.drawable.ic_notification_failure)';
if (!source.includes(before)) {
  throw new Error('Expected outbound debug notification icon reference was not found');
}
source = source.replace(before, after);
fs.writeFileSync(notifierPath, source, 'utf8');
console.log('Prepared existing native icon for outbound debug notification.');
