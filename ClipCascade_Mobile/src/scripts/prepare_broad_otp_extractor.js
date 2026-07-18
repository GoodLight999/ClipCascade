const fs = require('fs');
const path = require('path');

const extractorPath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
  'OtpCodeExtractor.kt',
);

let source = fs.readFileSync(extractorPath, 'utf8');

const previous = `    private val labelThenCodeRegex = Regex(
        pattern = "(?i)(?:verification|security|authentication|auth|login|log[\\s-]?in|sign[\\s-]?in|" +
            "signin|one[\\s-]?time|temporary|authorization|approval|recovery|device|" +
            "認証|確認|ログイン|サインイン|ワンタイム|本人確認|验证码|驗證碼|인증)" +
            "[^\\nA-Z0-9]{0,24}(?:code|number|passcode|pin|token|コード|番号)?" +
            "[^\\nA-Z0-9]{0,12}([A-Z0-9][A-Z0-9\\s\\-–—]{2,16}[A-Z0-9])",
    )`;

const current = `    private val labelThenCodeRegex = Regex(
        pattern = "(?i)(?:verification|security|authentication|auth|login|log[\\s-]?in|sign[\\s-]?in|" +
            "signin|one[\\s-]?time|temporary|authorization|approval|recovery|device|" +
            "認証|確認|ログイン|サインイン|ワンタイム|本人確認|验证码|驗證碼|인증)" +
            "(?:[^\\nA-Z0-9]{0,24}(?:code|number|passcode|pin|token|コード|番号))?" +
            "(?:\\s*(?:is|are|was|:|：|=|\\-|–|—|は|が|です|になります|laute?t|est)\\s*)?" +
            "([A-Z0-9][A-Z0-9\\s\\-–—]{2,16}[A-Z0-9])",
    )`;

if (!source.includes(previous)) {
  throw new Error('Expected broad OTP label extractor block was not found');
}

source = source.replace(previous, current);
fs.writeFileSync(extractorPath, source, 'utf8');
console.log('Prepared broad OTP label extraction without swallowing relation words.');
