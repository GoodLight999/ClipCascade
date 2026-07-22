const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

const servicePath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
  'ClipboardAcquisitionService.kt',
);
let service = fs.readFileSync(servicePath, 'utf8');
service = replaceRequired(
  service,
  `        if (!RelaySettingsStore.overlayClipboardEnabled(this)) {
            return ClipboardReadResult(null, "overlay_disabled")
        }
`,
  `        if (!RelaySettingsStore.overlayClipboardEnabled(this)) {
            // Overlay-free mode is not expected to be reliable while backgrounded,
            // but retain the platform clipboard read for foreground/control tests.
            return inspectClipboard("clipboard_manager", "clipboard_empty")
        }
`,
  'overlay-disabled direct clipboard fallback',
);
fs.writeFileSync(servicePath, service, 'utf8');
console.log('Prepared alpha.22 direct-read control path when overlay mode is disabled.');
