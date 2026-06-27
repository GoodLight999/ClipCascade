const fs = require('fs');
const path = require('path');

const appPath = path.resolve(__dirname, '..', 'App.js');
let source = fs.readFileSync(appPath, 'utf8');

const replacements = [
  ["const APP_VERSION = '3.2.0';", "const APP_VERSION = '3.2.1-extended.1';"],
  ["const APP_NAME = 'ClipCascade';", "const APP_NAME = 'ClipCascade Extended';"],
  [
    "There's also a workaround to enable clipboard sharing in the\n                  background. Scroll down for setup instructions.",
    "This build supports ADB-free clipboard sharing. Open the persistent\n                  sharing settings button to enable it.",
  ],
  ["Automatic Clipboard Monitoring Setup:", "ADB-free Clipboard Monitoring Setup:"],
  [
    "On rooted/non-rooted devices, to enable automatic clipboard\n                  monitoring you need to execute these 3 ADB commands:",
    "Open the sharing settings and complete these Android permissions:",
  ],
  ["1. Enable the READ_LOGS permission:", "1. Enable ClipCascade clipboard sharing under Accessibility."],
  [
    "{`> adb -d shell pm grant com.clipcascade android.permission.READ_LOGS`}",
    "No ADB command is required.",
  ],
  [
    "2. Allow \"Drawing over other apps\", also accessible from\n                    Settings:",
    "2. Enable Notification access to relay SMS and email verification codes.",
  ],
  [
    "{`> adb -d shell appops set com.clipcascade SYSTEM_ALERT_WINDOW allow`}",
    "Only extracted verification codes are relayed.",
  ],
  [
    "3. Kill the app for the new permissions to take effect:",
    "3. Return to ClipCascade and start synchronization.",
  ],
  [
    "{`> adb -d shell am force-stop com.clipcascade`}",
    "Use the test button in sharing settings to verify the connection.",
  ],
];

for (const [before, after] of replacements) {
  if (!source.includes(before)) {
    throw new Error(`Expected App.js text was not found: ${before.slice(0, 80)}`);
  }
  source = source.replace(before, after);
}

fs.writeFileSync(appPath, source, 'utf8');
console.log('Prepared ClipCascade Extended user interface for bundling.');
