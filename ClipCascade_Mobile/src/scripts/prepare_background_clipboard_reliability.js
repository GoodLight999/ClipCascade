const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

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

const narrowedSourceInspection = String.raw`        val inspectSource = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!inspectSource) return false
`;
const reliableSourceInspection = String.raw`        // Some floating toolbars and custom app copy controls expose the Copy
        // marker only through the event source, including window-content,
        // announcement, and notification-state events. Inspect only that source
        // node (not the whole tree) so reliability is restored without reviving
        // the former unbounded traversal cost.
        val inspectSource = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED,
            AccessibilityEvent.TYPE_ANNOUNCEMENT,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> true
            else -> false
        }
        if (!inspectSource) return false
`;
accessibility = replaceRequired(
  accessibility,
  narrowedSourceInspection,
  reliableSourceInspection,
  'narrow Accessibility source inspection',
);
fs.writeFileSync(accessibilityPath, accessibility, 'utf8');

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
let recovery = fs.readFileSync(recoveryPath, 'utf8');

recovery = replaceRequired(
  recovery,
  'import android.content.Intent\nimport android.os.SystemClock\n',
  'import android.content.Intent\nimport android.os.Handler\nimport android.os.Looper\nimport android.os.SystemClock\n',
  'RecoveryCoordinator handler imports',
);

recovery = replaceRequired(
  recovery,
  '    private const val MIN_REQUEST_INTERVAL_MS = 60_000L\n\n    @Volatile\n    private var lastRequestElapsedMs = 0L\n',
  '    private const val MIN_REQUEST_INTERVAL_MS = 60_000L\n' +
    '    private const val REACT_BOOTSTRAP_POLL_MS = 250L\n' +
    '    private const val REACT_BOOTSTRAP_ATTEMPTS = 16\n\n' +
    '    private val mainHandler = Handler(Looper.getMainLooper())\n\n' +
    '    @Volatile\n' +
    '    private var lastRequestElapsedMs = 0L\n\n' +
    '    @Volatile\n' +
    '    private var reactBootstrapPending = false\n',
  'RecoveryCoordinator bootstrap state',
);

recovery = replaceRequired(
  recovery,
  '        return try {\n            val intent = Intent(applicationContext, HeadlessTaskService::class.java).apply {\n',
  '        // A queued clipboard item may keep the Accessibility process alive while\n' +
    '        // the React/Notifee generation has been reclaimed. Recreate the React\n' +
    '        // context in-process as a recovery path that does not depend on Android\n' +
    '        // accepting a background Service start. Keep the existing Headless JS\n' +
    '        // request as a parallel compatibility fallback.\n' +
    '        val bootstrapRequested = requestReactContextBootstrap(applicationContext, reason)\n\n' +
    '        return try {\n' +
    '            val intent = Intent(applicationContext, HeadlessTaskService::class.java).apply {\n',
  'RecoveryCoordinator bootstrap request',
);

recovery = replaceRequired(
  recovery,
  '                false\n            } else {\n                HeadlessJsTaskService.acquireWakeLockNow(applicationContext)\n',
  '                bootstrapRequested\n            } else {\n                HeadlessJsTaskService.acquireWakeLockNow(applicationContext)\n',
  'declined Headless JS fallback result',
);

recovery = replaceRequired(
  recovery,
  '            false\n        }\n    }\n\n    private fun emitToActiveReactContext(context: Context, reason: String): Boolean {\n',
  '            bootstrapRequested\n        }\n    }\n\n' +
    '    private fun requestReactContextBootstrap(context: Context, reason: String): Boolean {\n' +
    '        val application = context as? MainApplication ?: return false\n' +
    '        if (reactBootstrapPending) return true\n' +
    '        reactBootstrapPending = true\n\n' +
    '        return try {\n' +
    '            mainHandler.post {\n' +
    '                try {\n' +
    '                    val manager = application.reactNativeHost.reactInstanceManager\n' +
    '                    if (manager.currentReactContext == null) {\n' +
    '                        try {\n' +
    '                            manager.createReactContextInBackground()\n' +
    '                        } catch (error: Throwable) {\n' +
    '                            // React may already be creating its initial context.\n' +
    '                            // Polling below still observes that in-flight creation.\n' +
    '                            Log.i(TAG, "React context creation was already in progress", error)\n' +
    '                        }\n' +
    '                    }\n' +
    '                    pollForReactContext(application, reason, REACT_BOOTSTRAP_ATTEMPTS)\n' +
    '                } catch (error: Throwable) {\n' +
    '                    reactBootstrapPending = false\n' +
    '                    Log.w(TAG, "Unable to bootstrap React context for relay recovery", error)\n' +
    '                }\n' +
    '            }\n' +
    '            true\n' +
    '        } catch (error: Throwable) {\n' +
    '            reactBootstrapPending = false\n' +
    '            Log.w(TAG, "Unable to schedule React context bootstrap", error)\n' +
    '            false\n' +
    '        }\n' +
    '    }\n\n' +
    '    private fun pollForReactContext(\n' +
    '        application: MainApplication,\n' +
    '        reason: String,\n' +
    '        attemptsRemaining: Int,\n' +
    '    ) {\n' +
    '        if (emitToActiveReactContext(application, reason)) {\n' +
    '            reactBootstrapPending = false\n' +
    '            RelayHealthStore.record(\n' +
    '                application,\n' +
    '                category = "recovery",\n' +
    '                trigger = reason,\n' +
    '                path = "react_bootstrap",\n' +
    '                result = "requested",\n' +
    '            )\n' +
    '            return\n' +
    '        }\n\n' +
    '        if (attemptsRemaining <= 0) {\n' +
    '            reactBootstrapPending = false\n' +
    '            RelayHealthStore.record(\n' +
    '                application,\n' +
    '                category = "recovery",\n' +
    '                trigger = reason,\n' +
    '                path = "react_bootstrap",\n' +
    '                result = "interrupted",\n' +
    '            )\n' +
    '            return\n' +
    '        }\n\n' +
    '        mainHandler.postDelayed(\n' +
    '            { pollForReactContext(application, reason, attemptsRemaining - 1) },\n' +
    '            REACT_BOOTSTRAP_POLL_MS,\n' +
    '        )\n' +
    '    }\n\n' +
    '    private fun emitToActiveReactContext(context: Context, reason: String): Boolean {\n',
  'RecoveryCoordinator bootstrap implementation',
);

fs.writeFileSync(recoveryPath, recovery, 'utf8');
console.log('Prepared reliable Accessibility copy cues and in-process React recovery.');
