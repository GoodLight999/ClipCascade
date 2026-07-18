const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

const servicePath = path.resolve(__dirname, '..', 'StartForegroundService.js');
let service = fs.readFileSync(servicePath, 'utf8');
service = replaceRequired(
  service,
  `  const HEARTBEAT_INTERVAL = 20000; // 20 seconds\n  const FRAGMENT_SIZE = 15360; // 15 KiB`,
  `  const HEARTBEAT_INTERVAL = 20000; // 20 seconds\n  // AsyncStorage polling crosses the JS/native boundary and opens SQLite. Three\n  // seconds keeps stop/heartbeat handling responsive without waking once a second.\n  const SERVICE_FLAG_POLL_MS = 3000;\n  const FRAGMENT_SIZE = 15360; // 15 KiB`,
  'foreground-service poll constant',
);
service = replaceRequired(
  service,
  `            await sleep(1000);\n          }\n        }\n\n        pollFlagsLoop();`,
  `            await sleep(SERVICE_FLAG_POLL_MS);\n          }\n        }\n\n        pollFlagsLoop();`,
  'foreground-service poll delay',
);
fs.writeFileSync(servicePath, service, 'utf8');

const appPath = path.resolve(__dirname, '..', 'App.js');
let app = fs.readFileSync(appPath, 'utf8');
app = replaceRequired(
  app,
  `      await sleep(300);\n    }\n  }`,
  `      // UI-only status refresh does not need sub-second SQLite polling.\n      await sleep(1000);\n    }\n  }`,
  'UI poll delay',
);
fs.writeFileSync(appPath, app, 'utf8');

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
  '            notificationTimeout = 25',
  '            notificationTimeout = 100',
  'Accessibility notification timeout',
);
accessibility = replaceRequired(
  accessibility,
  `    private fun looksLikeCopyConfirmation(event: AccessibilityEvent): Boolean {\n        if (event.action == AccessibilityNodeInfo.ACTION_COPY) return true\n        val node = event.source\n        val text = buildString {\n            append(event.text.joinToString(" "))\n            append(' ')\n            append(event.contentDescription?.toString().orEmpty())\n            append(' ')\n            append(node?.text?.toString().orEmpty())\n            append(' ')\n            append(node?.contentDescription?.toString().orEmpty())\n            append(' ')\n            append(node?.viewIdResourceName.orEmpty())\n        }\n        return COPY_MARKER_REGEX.containsMatchIn(text)\n    }`,
  `    private fun looksLikeCopyConfirmation(event: AccessibilityEvent): Boolean {\n        if (event.action == AccessibilityNodeInfo.ACTION_COPY) return true\n\n        // Most high-frequency window-content events already carry the changed text.\n        // Avoid an AccessibilityNodeInfo binder traversal unless the event is a\n        // direct interaction/window transition where the Copy control itself may be\n        // present only on the source node.\n        val eventText = buildString {\n            append(event.text.joinToString(" "))\n            append(' ')\n            append(event.contentDescription?.toString().orEmpty())\n        }\n        if (COPY_MARKER_REGEX.containsMatchIn(eventText)) return true\n\n        val inspectSource = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||\n            event.eventType == AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED ||\n            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED\n        if (!inspectSource) return false\n\n        val node = event.source\n        val nodeText = buildString {\n            append(node?.text?.toString().orEmpty())\n            append(' ')\n            append(node?.contentDescription?.toString().orEmpty())\n            append(' ')\n            append(node?.viewIdResourceName.orEmpty())\n        }\n        return COPY_MARKER_REGEX.containsMatchIn(nodeText)\n    }`,
  'Accessibility copy-cue inspection',
);
fs.writeFileSync(accessibilityPath, accessibility, 'utf8');

const accessibilityXmlPath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'res',
  'xml',
  'clipboard_accessibility_service.xml',
);
let accessibilityXml = fs.readFileSync(accessibilityXmlPath, 'utf8');
accessibilityXml = replaceRequired(
  accessibilityXml,
  '    android:notificationTimeout="25"',
  '    android:notificationTimeout="100"',
  'Accessibility XML notification timeout',
);
fs.writeFileSync(accessibilityXmlPath, accessibilityXml, 'utf8');

const schedulePath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
  'ScheduleService.kt',
);
let schedule = fs.readFileSync(schedulePath, 'utf8');
schedule = replaceRequired(
  schedule,
  `        repeat(35) {\n            delay(100)\n            if (bridge.getValue("echo") == "pong") return true\n        }`,
  `        // The foreground service now polls at a lower idle frequency. This worker\n        // runs only every 15 minutes, so a longer bounded wait is far cheaper than\n        // forcing the service to wake once per second around the clock.\n        repeat(8) {\n            delay(500)\n            if (bridge.getValue("echo") == "pong") return true\n        }`,
  'health-check heartbeat wait',
);
fs.writeFileSync(schedulePath, schedule, 'utf8');

// Reliability follow-up: restore copy-cue source inspection for OEM/custom
// toolbars and bootstrap React in-process when a queued relay outlives the
// Notifee/React service generation.
require('./prepare_background_clipboard_reliability.js');
require('./prepare_recovery_cooldown_bootstrap.js');

console.log('Prepared lower-power idle polling with reliable background clipboard recovery.');
