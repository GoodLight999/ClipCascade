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

const blockStart = source.indexOf('    private val labelThenCodeRegex = Regex(\n');
const blockEnd = source.indexOf('    private val japaneseActionCodeRegex = Regex(', blockStart);

if (blockStart < 0 || blockEnd < 0 || blockEnd <= blockStart) {
  throw new Error('Expected broad OTP label extractor block was not found');
}

const currentLabelThenCode = String.raw`    private val labelThenCodeRegex = Regex(
        pattern = "(?i)(?:verification|security|authentication|auth|login|log[\\s-]?in|sign[\\s-]?in|" +
            "signin|one[\\s-]?time|temporary|authorization|approval|recovery|device|" +
            "認証|確認|ログイン|サインイン|ワンタイム|本人確認|验证码|驗證碼|인증)" +
            "(?:[^\\nA-Z0-9]{0,24}(?:code|number|passcode|pin|token|コード|番号))?" +
            "(?:\\s*(?:is|are|was|:|：|=|\\-|–|—|は|が|です|になります|laute?t|est)\\s*)?" +
            "([A-Z0-9][A-Z0-9 \\t\\-–—]{2,16}[A-Z0-9])",
    )
`;

source = source.slice(0, blockStart) + currentLabelThenCode + source.slice(blockEnd);

const structuredBefore = `    private fun looksLikeStructuredNonCode(raw: String, value: String): Boolean {
        if (datePatterns.any { it.matches(raw) }) return true
        if (timePattern.matches(raw)) return true
`;
const structuredAfter = `    private fun looksLikeStructuredNonCode(raw: String, value: String): Boolean {
        if (raw.contains('\\n') || raw.contains('\\r')) return true
        if (datePatterns.any { it.matches(raw) }) return true
        if (timePattern.matches(raw)) return true
`;
if (!source.includes(structuredBefore)) {
  throw new Error('Expected structured non-code function block was not found');
}
source = source.replace(structuredBefore, structuredAfter);

fs.writeFileSync(extractorPath, source, 'utf8');
console.log('Prepared broad OTP extraction without swallowing relation words or app-hash lines.');
