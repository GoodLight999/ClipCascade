const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

const nativePath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
  'NativeBridgeModule.kt',
);
let nativeSource = fs.readFileSync(nativePath, 'utf8');
nativeSource = replaceRequired(
  nativeSource,
  '    @ReactMethod\n    fun clearCookies(promise: Promise) {\n',
  '    @ReactMethod\n' +
    '    fun markInternalClipboardWrite(promise: Promise) {\n' +
    '        ClipboardWriteGuard.markInternalWrite()\n' +
    '        promise.resolve(true)\n' +
    '    }\n\n' +
    '    @ReactMethod\n' +
    '    fun clearCookies(promise: Promise) {\n',
  'native clipboard-write marker method',
);
nativeSource = replaceRequired(
  nativeSource,
  '            clipboard.setPrimaryClip(clipData)\n',
  '            ClipboardWriteGuard.markInternalWrite()\n' +
    '            clipboard.setPrimaryClip(clipData)\n',
  'native image clipboard-write marker',
);
fs.writeFileSync(nativePath, nativeSource, 'utf8');

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
  '    private val clipboardChangedListener = ClipboardManager.OnPrimaryClipChangedListener {\n' +
    '        if (!RelaySettingsStore.clipboardEnabled(this)) {\n',
  '    private val clipboardChangedListener = ClipboardManager.OnPrimaryClipChangedListener {\n' +
    '        if (ClipboardWriteGuard.consumeIfMarked()) {\n' +
    '            return@OnPrimaryClipChangedListener\n' +
    '        }\n' +
    '        if (!RelaySettingsStore.clipboardEnabled(this)) {\n',
  'Accessibility internal-write suppression',
);
fs.writeFileSync(accessibilityPath, accessibility, 'utf8');

const servicePath = path.resolve(__dirname, '..', 'StartForegroundService.js');
let service = fs.readFileSync(servicePath, 'utf8');
let replacements = 0;
service = service.replace(
  /^(\s*)Clipboard\.setString\(([^;\n]+)\);/gm,
  (match, indent, argument) => {
    replacements += 1;
    return (
      `${indent}if (NativeBridgeModule?.markInternalClipboardWrite) {\n` +
      `${indent}  await NativeBridgeModule.markInternalClipboardWrite();\n` +
      `${indent}}\n` +
      `${indent}Clipboard.setString(${argument});`
    );
  },
);
if (replacements < 2) {
  throw new Error(`Expected at least two final text clipboard writes, found ${replacements}`);
}
fs.writeFileSync(servicePath, service, 'utf8');
console.log(`Guarded ${replacements} internal clipboard text write(s).`);
