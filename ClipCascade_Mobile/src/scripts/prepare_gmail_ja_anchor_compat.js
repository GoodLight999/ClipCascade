const fs = require('fs');
const path = require('path');

const stringsPath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'res',
  'values-ja',
  'strings.xml',
);

let source = fs.readFileSync(stringsPath, 'utf8');
const actual =
  '    <string name="notification_explanation">通知本文は端末内だけで解析し、抽出した短い認証値だけを送信キューへ入れます。</string>';
const canonical =
  '    <string name="notification_explanation">通知本文は端末内だけで解析し、短い認証値だけを送信キューへ入れます。</string>';

if (!source.includes(actual) && !source.includes(canonical)) {
  throw new Error('Expected Japanese notification explanation anchor was not found');
}
source = source.replace(actual, canonical);
fs.writeFileSync(stringsPath, source, 'utf8');
console.log('Normalized Japanese notification diagnostics transform anchor.');
