const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

const sourceDir = path.resolve(__dirname, '..');
const javaDir = path.join(
  sourceDir,
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
);

// Notifee requires the foreground runner to be registered outside React
// components, as early as possible from index.js. Previously it was registered
// only when the UI/recovery path called StartForegroundService(), leaving a
// recreated process able to retain a native foreground shell without its JS
// transport runner.
const servicePath = path.join(sourceDir, 'StartForegroundService.js');
let service = fs.readFileSync(servicePath, 'utf8');
service = replaceRequired(
  service,
  `module.exports = async (inputData = null) => {\n`,
  `let foregroundRunnerRegistered = false;\n\nmodule.exports = async (inputData = null) => {\n`,
  'foreground runner registration state',
);
service = replaceRequired(
  service,
  `  // forground service\n  notifee.registerForegroundService(notification => {\n`,
  `  // Register exactly once, before any foreground notification is started.\n  if (!foregroundRunnerRegistered) {\n    foregroundRunnerRegistered = true;\n    notifee.registerForegroundService(notification => {\n`,
  'foreground runner guarded registration',
);
service = replaceRequired(
  service,
  `    });\n  });\n\n  try {\n`,
  `    });\n    });\n  }\n\n  if (inputData?.registerOnly === true) {\n    try {\n      await NativeModules.RelaySettingsModule?.recordForegroundRunnerRegistered?.();\n    } catch (error) {\n      // Registration is still valid even if diagnostics are unavailable.\n    }\n    return [true, 'Foreground runner registered'];\n  }\n\n  try {\n`,
  'foreground runner register-only exit',
);
service = replaceRequired(
  service,
  `        if (RelaySettingsModule?.resumeRelayQueues) {\n`,
  `        if (RelaySettingsModule?.recordForegroundRunnerStarted) {\n          await RelaySettingsModule.recordForegroundRunnerStarted();\n        }\n        if (RelaySettingsModule?.resumeRelayQueues) {\n`,
  'foreground runner started marker',
);
service = replaceRequired(
  service,
  `        async function pollFlagsLoop() {\n          const POLL_KEYS = [\n`,
  `        let lastRunnerHeartbeatAt = 0;\n\n        async function pollFlagsLoop() {\n          const POLL_KEYS = [\n`,
  'foreground runner heartbeat state',
);
service = replaceRequired(
  service,
  `          while (true) {\n            const json = NativeBridgeModule.getFlagsSync(POLL_KEYS);\n`,
  `          while (true) {\n            const now = Date.now();\n            if (\n              now - lastRunnerHeartbeatAt >= 15_000 &&\n              RelaySettingsModule?.recordForegroundRunnerHeartbeat\n            ) {\n              lastRunnerHeartbeatAt = now;\n              await RelaySettingsModule.recordForegroundRunnerHeartbeat();\n            }\n            const json = NativeBridgeModule.getFlagsSync(POLL_KEYS);\n`,
  'foreground runner heartbeat poll',
);
service = replaceRequired(
  service,
  `      } catch (error) {\n        await setDataInAsyncStorage('wsStatusMessage', '❌ Error:' + error);\n`,
  `      } catch (error) {\n        try {\n          await NativeModules.RelaySettingsModule?.recordForegroundRunnerStopped?.(\n            'runner_error',\n          );\n        } catch (diagnosticError) {\n          // Never hide the real runner failure behind diagnostics.\n        }\n        await setDataInAsyncStorage('wsStatusMessage', '❌ Error:' + error);\n`,
  'foreground runner stopped marker',
);
fs.writeFileSync(servicePath, service, 'utf8');

const indexPath = path.join(sourceDir, 'index.js');
let index = fs.readFileSync(indexPath, 'utf8');
index = replaceRequired(
  index,
  `import {registerRecoveryListener} from './RecoveryListener';\n\nregisterRecoveryListener();\n`,
  `import {registerRecoveryListener} from './RecoveryListener';\nimport StartForegroundService from './StartForegroundService';\n\n// Notifee foreground tasks must be registered at bundle entry, not lazily from\n// a mounted React component. This call registers the task but does not display\n// or restart a notification.\nStartForegroundService({registerOnly: true}).catch(error => {\n  console.error('Unable to register ClipCascade foreground runner:', error);\n});\nregisterRecoveryListener();\n`,
  'index-level foreground registration',
);
fs.writeFileSync(indexPath, index, 'utf8');

const runtimeStoreSource = String.raw`package com.clipcascade

import android.content.Context

object ForegroundTransportRuntimeStore {
    private const val PREFS = "foreground_transport_runtime"
    private const val KEY_REGISTERED_AT = "registered_at"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_HEARTBEAT_AT = "heartbeat_at"
    private const val KEY_STOPPED_AT = "stopped_at"
    private const val KEY_STOP_REASON = "stop_reason"
    private const val FRESH_HEARTBEAT_MS = 45_000L

    data class Snapshot(
        val registeredAt: Long,
        val startedAt: Long,
        val heartbeatAt: Long,
        val stoppedAt: Long,
        val stopReason: String,
    ) {
        fun isFresh(now: Long = System.currentTimeMillis()): Boolean =
            heartbeatAt > 0L && now - heartbeatAt in 0..FRESH_HEARTBEAT_MS
    }

    @Synchronized
    fun markRegistered(context: Context) {
        prefs(context).edit()
            .putLong(KEY_REGISTERED_AT, System.currentTimeMillis())
            .commit()
    }

    @Synchronized
    fun markStarted(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putLong(KEY_STARTED_AT, now)
            .putLong(KEY_HEARTBEAT_AT, now)
            .remove(KEY_STOP_REASON)
            .commit()
    }

    @Synchronized
    fun markHeartbeat(context: Context) {
        prefs(context).edit()
            .putLong(KEY_HEARTBEAT_AT, System.currentTimeMillis())
            .commit()
    }

    @Synchronized
    fun markStopped(context: Context, reason: String) {
        prefs(context).edit()
            .putLong(KEY_STOPPED_AT, System.currentTimeMillis())
            .putString(KEY_STOP_REASON, reason.take(64))
            .commit()
    }

    fun read(context: Context): Snapshot {
        val prefs = prefs(context)
        return Snapshot(
            registeredAt = prefs.getLong(KEY_REGISTERED_AT, 0L),
            startedAt = prefs.getLong(KEY_STARTED_AT, 0L),
            heartbeatAt = prefs.getLong(KEY_HEARTBEAT_AT, 0L),
            stoppedAt = prefs.getLong(KEY_STOPPED_AT, 0L),
            stopReason = prefs.getString(KEY_STOP_REASON, "").orEmpty(),
        )
    }

    fun isFresh(context: Context): Boolean = read(context).isFresh()

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
`;
fs.writeFileSync(
  path.join(javaDir, 'ForegroundTransportRuntimeStore.kt'),
  runtimeStoreSource,
  'utf8',
);

const modulePath = path.join(javaDir, 'RelaySettingsModule.kt');
let moduleSource = fs.readFileSync(modulePath, 'utf8');
moduleSource = replaceRequired(
  moduleSource,
  `    @ReactMethod\n    fun acknowledgeRelay(relayId: String, source: String, promise: Promise) {\n`,
  `    @ReactMethod\n    fun recordForegroundRunnerRegistered(promise: Promise) {\n        ForegroundTransportRuntimeStore.markRegistered(reactApplicationContext)\n        promise.resolve(true)\n    }\n\n    @ReactMethod\n    fun recordForegroundRunnerStarted(promise: Promise) {\n        ForegroundTransportRuntimeStore.markStarted(reactApplicationContext)\n        RelayHealthStore.record(\n            reactApplicationContext,\n            category = "transport_runtime",\n            trigger = "runner_started",\n            path = "notifee_foreground",\n            result = "ready",\n        )\n        promise.resolve(true)\n    }\n\n    @ReactMethod\n    fun recordForegroundRunnerHeartbeat(promise: Promise) {\n        ForegroundTransportRuntimeStore.markHeartbeat(reactApplicationContext)\n        promise.resolve(true)\n    }\n\n    @ReactMethod\n    fun recordForegroundRunnerStopped(reason: String, promise: Promise) {\n        ForegroundTransportRuntimeStore.markStopped(reactApplicationContext, reason)\n        RelayHealthStore.record(\n            reactApplicationContext,\n            category = "transport_runtime",\n            trigger = "runner_stopped",\n            path = "notifee_foreground",\n            result = "interrupted",\n        )\n        promise.resolve(true)\n    }\n\n    @ReactMethod\n    fun acknowledgeRelay(relayId: String, source: String, promise: Promise) {\n`,
  'foreground runner native diagnostics methods',
);
fs.writeFileSync(modulePath, moduleSource, 'utf8');

const activityPath = path.join(javaDir, 'RelaySettingsActivity.kt');
let activity = fs.readFileSync(activityPath, 'utf8');
activity = replaceRequired(
  activity,
  `        try {\n            val value = OtpTestNotificationManager.post(applicationContext)\n`,
  `        try {\n            if (!ForegroundTransportRuntimeStore.isFresh(applicationContext)) {\n                RecoveryCoordinator.request(applicationContext, "component_test_runner_stale")\n            }\n            val value = OtpTestNotificationManager.post(applicationContext)\n`,
  'component test runner preflight',
);
activity = replaceRequired(
  activity,
  `            val value = OtpTestNotificationManager.postListenerPath(applicationContext)\n            Toast.makeText(\n`,
  `            if (!ForegroundTransportRuntimeStore.isFresh(applicationContext)) {\n                RecoveryCoordinator.request(applicationContext, "listener_test_runner_stale")\n            }\n            val value = OtpTestNotificationManager.postListenerPath(applicationContext)\n            listOf(500L, 2_000L, 5_000L).forEach { delayMs ->\n                statusHandler.postDelayed(\n                    {\n                        NotificationCodeListenerService.requestImmediateScan(\n                            applicationContext,\n                            "listener_path_self_test_delayed_scan",\n                        )\n                        updateStatus()\n                    },\n                    delayMs,\n                )\n            }\n            Toast.makeText(\n`,
  'listener test runner preflight and delayed scan',
);
activity = replaceRequired(
  activity,
  `        queueStatus.text = getString(\n            R.string.queue_summary,\n            ClipboardRelayStore.count(this),\n            OtpRelayStore.pendingCount(this),\n        )\n`,
  `        val runner = ForegroundTransportRuntimeStore.read(this)\n        queueStatus.text = getString(\n            R.string.queue_summary,\n            ClipboardRelayStore.count(this),\n            OtpRelayStore.pendingCount(this),\n        ) + "\\n" + getString(\n            if (runner.isFresh()) R.string.foreground_runner_active\n            else R.string.foreground_runner_inactive,\n        )\n`,
  'foreground runner settings status',
);
activity = replaceRequired(
  activity,
  `            formatHealth(\n                getString(R.string.health_recovery),\n                RelayHealthStore.read(this, "recovery"),\n            ),\n`,
  `            formatHealth(\n                getString(R.string.health_recovery),\n                RelayHealthStore.read(this, "recovery"),\n            ),\n            formatHealth(\n                getString(R.string.health_transport_runtime),\n                RelayHealthStore.read(this, "transport_runtime"),\n            ),\n`,
  'foreground runner health row',
);
activity = replaceRequired(
  activity,
  `            "service_connected" -> R.string.health_service_connected\n`,
  `            "runner_started" -> R.string.health_runner_started\n            "runner_stopped" -> R.string.health_runner_stopped\n            "notifee_foreground" -> R.string.health_notifee_foreground\n            "service_connected" -> R.string.health_service_connected\n`,
  'foreground runner health labels',
);
fs.writeFileSync(activityPath, activity, 'utf8');

const testManagerPath = path.join(javaDir, 'OtpTestNotificationManager.kt');
let testManager = fs.readFileSync(testManagerPath, 'utf8');
testManager = replaceRequired(
  testManager,
  `.setTimeoutAfter(60_000L)`,
  `.setTimeoutAfter(5 * 60_000L)`,
  'component synthetic notification lifetime',
);
fs.writeFileSync(testManagerPath, testManager, 'utf8');

// Expand the exact framework-label search to the clicked node's immediate
// hierarchy and AccessibilityAction labels. This remains explicit-Copy-only;
// selection events still cannot trigger a send.
const accessibilityPath = path.join(javaDir, 'ClipboardAccessibilityService.kt');
let accessibility = fs.readFileSync(accessibilityPath, 'utf8');
accessibility = replaceRequired(
  accessibility,
  `        val node = event.source\n        val candidates = buildList<CharSequence?> {\n            add(event.contentDescription)\n            addAll(event.text)\n            add(node?.text)\n            add(node?.contentDescription)\n        }\n        return SystemCopyCuePolicy.matchesAny(\n`,
  `        val node = event.source\n        val candidates = linkedSetOf<CharSequence?>().apply {\n            add(event.contentDescription)\n            addAll(event.text)\n            addNodeCopyCueCandidates(node, this)\n            val parent = node?.parent\n            addNodeCopyCueCandidates(parent, this)\n            if (node != null) {\n                for (index in 0 until node.childCount.coerceAtMost(12)) {\n                    addNodeCopyCueCandidates(node.getChild(index), this)\n                }\n            }\n            if (parent != null) {\n                for (index in 0 until parent.childCount.coerceAtMost(12)) {\n                    addNodeCopyCueCandidates(parent.getChild(index), this)\n                }\n            }\n        }\n        return SystemCopyCuePolicy.matchesAny(\n`,
  'hierarchical framework Copy cue candidates',
);
accessibility = replaceRequired(
  accessibility,
  `    override fun onKeyEvent(event: KeyEvent?): Boolean {\n`,
  `    private fun addNodeCopyCueCandidates(\n        node: AccessibilityNodeInfo?,\n        target: MutableSet<CharSequence?>,\n    ) {\n        if (node == null) return\n        target.add(node.text)\n        target.add(node.contentDescription)\n        node.actionList.forEach { action -> target.add(action.label) }\n    }\n\n    override fun onKeyEvent(event: KeyEvent?): Boolean {\n`,
  'hierarchical Copy cue helper',
);
fs.writeFileSync(accessibilityPath, accessibility, 'utf8');

const stringFiles = [
  {
    file: path.join(sourceDir, 'android', 'app', 'src', 'main', 'res', 'values', 'strings.xml'),
    anchor: '    <string name="health_recovery">Recovery</string>',
    replacement:
      '    <string name="health_recovery">Recovery</string>\n' +
      '    <string name="health_transport_runtime">Foreground transport runner</string>\n' +
      '    <string name="health_runner_started">runner started</string>\n' +
      '    <string name="health_runner_stopped">runner stopped</string>\n' +
      '    <string name="health_notifee_foreground">Notifee foreground task</string>\n' +
      '    <string name="foreground_runner_active">Foreground transport runner: active</string>\n' +
      '    <string name="foreground_runner_inactive">Foreground transport runner: no fresh heartbeat</string>',
  },
  {
    file: path.join(sourceDir, 'android', 'app', 'src', 'main', 'res', 'values-ja', 'strings.xml'),
    anchor: '    <string name="health_recovery">回復処理</string>',
    replacement:
      '    <string name="health_recovery">回復処理</string>\n' +
      '    <string name="health_transport_runtime">常駐通信ランナー</string>\n' +
      '    <string name="health_runner_started">ランナー開始</string>\n' +
      '    <string name="health_runner_stopped">ランナー停止</string>\n' +
      '    <string name="health_notifee_foreground">Notifee常駐タスク</string>\n' +
      '    <string name="foreground_runner_active">常駐通信ランナー: 動作中</string>\n' +
      '    <string name="foreground_runner_inactive">常駐通信ランナー: 新しいheartbeatなし</string>',
  },
];
for (const entry of stringFiles) {
  let strings = fs.readFileSync(entry.file, 'utf8');
  strings = replaceRequired(
    strings,
    entry.anchor,
    entry.replacement,
    `${entry.file} foreground runner strings`,
  );
  fs.writeFileSync(entry.file, strings, 'utf8');
}

console.log(
  'Prepared index-level Notifee runner registration, heartbeat diagnostics, self-test rescans, and hierarchical Copy cues.',
);
