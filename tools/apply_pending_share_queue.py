from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, found {count}')
    return text.replace(old, new, 1)


kotlin_root = root / 'ClipCascade_Mobile/src/android/app/src/main/java/com/clipcascade'
test_kotlin_root = root / 'ClipCascade_Mobile/src/android/app/src/test/java/com/clipcascade'
source_root = root / 'ClipCascade_Mobile/src'

pending_store = '''package com.clipcascade

import java.util.ArrayDeque

/**
 * Process-local, bounded handoff from Android share intents to the JavaScript
 * foreground-service listener. React Native events are wake signals only; the
 * payload remains here until JavaScript atomically drains it.
 */
object PendingShareStore {
    const val EVENT_AVAILABLE = "SHARED_EVENT_AVAILABLE"
    internal const val MAX_PENDING_EVENTS = 64

    data class PendingShareEvent(
        val eventName: String,
        val key: String,
        val value: String
    )

    private val lock = Any()
    private val events = ArrayDeque<PendingShareEvent>()

    /** Returns true when the oldest event had to be discarded at the bound. */
    fun enqueue(eventName: String, key: String, value: String): Boolean =
        synchronized(lock) {
            val droppedOldest = events.size >= MAX_PENDING_EVENTS
            if (droppedOldest) {
                events.removeFirst()
            }
            events.addLast(PendingShareEvent(eventName, key, value))
            droppedOldest
        }

    fun drain(): List<PendingShareEvent> = synchronized(lock) {
        if (events.isEmpty()) return@synchronized emptyList()
        val drained = events.toList()
        events.clear()
        drained
    }
}
'''
(kotlin_root / 'PendingShareStore.kt').write_text(pending_store, encoding='utf-8')

main_activity = '''// android\\app\\src\\main\\java\\com\\clipcascade\\MainActivity.kt
package com.clipcascade

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.ReactInstanceManager
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.facebook.react.devsupport.interfaces.DevSupportManager
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.concurrent.TimeUnit

class MainActivity : ReactActivity() {
    private var reactInstanceListener: ReactInstanceManager.ReactInstanceEventListener? = null

    companion object {
        const val TAG = "ClipCascade"
        const val WORK_NAME = "schedule_work"
        private const val EXTRA_SHARE_CONSUMED =
            "com.clipcascade.extra.SHARE_INTENT_CONSUMED"
    }

    override fun getMainComponentName(): String = "ClipCascade"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.let { handleIntent(it) }

        try {
            val bridgeData = AsyncStorageBridge(applicationContext)
            val enablePeriodicChecks =
                bridgeData.getValue("enable_periodic_checks")?.toBoolean() ?: true
            if (enablePeriodicChecks) {
                scheduleJob()
                if (ScheduleService.hasNotificationPermission(applicationContext)) {
                    ScheduleService.removeNotificationIfPresent(applicationContext)
                }
            } else {
                WorkManager.getInstance(applicationContext).cancelUniqueWork(WORK_NAME)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Error scheduling job", error)
        }
    }

    private fun scheduleJob() {
        val periodicWorkRequest =
            PeriodicWorkRequestBuilder<ScheduleService>(15, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

        // KEEP is the WorkManager-native equivalent of "create it only when no
        // existing unique periodic work is active" and avoids blocking the UI
        // thread on getWorkInfosByTag(...).get().
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    override fun onPause() {
        super.onPause()
        val manager: ReactInstanceManager = reactNativeHost.reactInstanceManager
        val devSupportManager: DevSupportManager = manager.devSupportManager
        if (devSupportManager.devSupportEnabled) {
            devSupportManager.hideRedboxDialog()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        handleShareIntent(intent)

        if ("com.clipcascade.NOTIFICATION_ACTION" == intent.action) {
            if (intent.getStringExtra("action") == "foreground_service_stopped_running") {
                try {
                    AsyncStorageBridge(applicationContext)
                        .setValue("foreground_service_stopped_running", "true")
                } catch (error: Exception) {
                    Log.e(
                        TAG,
                        "Error connecting/initializing values to AsyncStorageBridge",
                        error
                    )
                }
            }
        }
    }

    private fun handleShareIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_SHARE_CONSUMED, false)) return

        val event = when {
            Intent.ACTION_SEND == intent.action && intent.type == "text/plain" ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                    PendingShareStore.PendingShareEvent("SHARED_TEXT", "text", it)
                }
            Intent.ACTION_PROCESS_TEXT == intent.action && intent.type == "text/plain" ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.let {
                    PendingShareStore.PendingShareEvent("SHARED_TEXT", "text", it.toString())
                }
            Intent.ACTION_SEND == intent.action &&
                intent.type?.startsWith("image/") == true ->
                intent.streamUri()?.let {
                    PendingShareStore.PendingShareEvent("SHARED_IMAGE", "image", it.toString())
                }
            Intent.ACTION_SEND == intent.action && intent.type != null ->
                intent.streamUri()?.let {
                    PendingShareStore.PendingShareEvent("SHARED_FILES", "files", it.toString())
                }
            Intent.ACTION_SEND_MULTIPLE == intent.action && intent.type != null ->
                intent.streamUris()?.takeIf { it.isNotEmpty() }?.let { uris ->
                    PendingShareStore.PendingShareEvent(
                        "SHARED_FILES",
                        "files",
                        uris.joinToString(",") { it.toString() }
                    )
                }
            else -> null
        } ?: return

        intent.putExtra(EXTRA_SHARE_CONSUMED, true)
        val dropped = PendingShareStore.enqueue(event.eventName, event.key, event.value)
        if (dropped) {
            Log.w(TAG, "Pending share queue reached its bound; oldest event discarded")
        }
        signalPendingShares()
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun Intent.streamUris(): ArrayList<Uri>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }

    private fun signalPendingShares() {
        val manager = reactNativeHost.reactInstanceManager
        currentReactContextOrNull(manager)?.let { context ->
            emitPendingShareSignal(context)
            return
        }
        ensureReactInstanceListener(manager)
    }

    private fun ensureReactInstanceListener(manager: ReactInstanceManager) {
        if (reactInstanceListener != null) return

        lateinit var listener: ReactInstanceManager.ReactInstanceEventListener
        listener = ReactInstanceManager.ReactInstanceEventListener { context ->
            runOnUiThread {
                emitPendingShareSignal(context)
                manager.removeReactInstanceEventListener(listener)
                if (reactInstanceListener === listener) {
                    reactInstanceListener = null
                }
            }
        }
        reactInstanceListener = listener
        manager.addReactInstanceEventListener(listener)

        // Close the race where initialization completed between the first
        // context check and listener registration.
        currentReactContextOrNull(manager)?.let { context ->
            emitPendingShareSignal(context)
            manager.removeReactInstanceEventListener(listener)
            if (reactInstanceListener === listener) {
                reactInstanceListener = null
            }
        }

        if (!manager.hasStartedCreatingInitialContext()) {
            manager.createReactContextInBackground()
        }
    }

    private fun emitPendingShareSignal(context: com.facebook.react.bridge.ReactContext) {
        context
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(PendingShareStore.EVENT_AVAILABLE, null)
    }

    @SuppressLint("VisibleForTests")
    private fun currentReactContextOrNull(
        manager: ReactInstanceManager
    ): com.facebook.react.bridge.ReactContext? = manager.currentReactContext

    override fun onDestroy() {
        reactInstanceListener?.let { listener ->
            reactNativeHost.reactInstanceManager.removeReactInstanceEventListener(listener)
        }
        reactInstanceListener = null
        super.onDestroy()
    }
}
'''
(kotlin_root / 'MainActivity.kt').write_text(main_activity, encoding='utf-8')

native_path = kotlin_root / 'NativeBridgeModule.kt'
native = native_path.read_text(encoding='utf-8')
native = once(
    native,
    'import com.facebook.react.bridge.ReactMethod\n',
    'import com.facebook.react.bridge.ReactMethod\nimport com.facebook.react.bridge.Arguments\n',
    'NativeBridge Arguments import',
)
drain_method = '''    @ReactMethod
    fun drainPendingShareEvents(promise: Promise) {
        try {
            val result = Arguments.createArray()
            PendingShareStore.drain().forEach { event ->
                result.pushMap(
                    Arguments.createMap().apply {
                        putString("eventName", event.eventName)
                        putString("key", event.key)
                        putString("value", event.value)
                    }
                )
            }
            promise.resolve(result)
        } catch (error: Exception) {
            promise.reject(
                "PENDING_SHARE_DRAIN_ERROR",
                "Failed to drain pending share events",
                error
            )
        }
    }

'''
native = once(
    native,
    '    @ReactMethod\n    fun setAppOwnedTextClipboard(content: String, promise: Promise) {\n',
    drain_method + '    @ReactMethod\n    fun setAppOwnedTextClipboard(content: String, promise: Promise) {\n',
    'NativeBridge pending-share drain insertion',
)
native_path.write_text(native, encoding='utf-8')

service_path = source_root / 'StartForegroundService.js'
service = service_path.read_text(encoding='utf-8')
service = once(
    service,
    'let activeClipboardOnChangeSubscription = null;\n',
    'let activeClipboardOnChangeSubscription = null;\nlet activeShareAvailabilitySubscription = null;\n',
    'global share subscription',
)
service = once(
    service,
    "  activeClipboardOnChangeSubscription?.remove();\n  activeClipboardOnChangeSubscription = null;\n  NativeModules.ClipboardListener?.stopListening?.();\n",
    "  activeClipboardOnChangeSubscription?.remove();\n  activeClipboardOnChangeSubscription = null;\n  activeShareAvailabilitySubscription?.remove();\n  activeShareAvailabilitySubscription = null;\n  NativeModules.ClipboardListener?.stopListening?.();\n",
    'global cleanup share subscription',
)
service = once(
    service,
    '        let instanceClipboardOnChangeSubscription = null;\n',
    '        let instanceClipboardOnChangeSubscription = null;\n        let instanceShareAvailabilitySubscription = null;\n        let sharedTransportReady = false;\n        let sharedDrainRequested = false;\n        let sharedDrainPromise = null;\n',
    'instance share state',
)
service = once(
    service,
    "          const subscription = instanceClipboardOnChangeSubscription;\n          subscription?.remove();\n          instanceClipboardOnChangeSubscription = null;\n",
    "          const subscription = instanceClipboardOnChangeSubscription;\n          subscription?.remove();\n          instanceClipboardOnChangeSubscription = null;\n          const shareSubscription = instanceShareAvailabilitySubscription;\n          shareSubscription?.remove();\n          instanceShareAvailabilitySubscription = null;\n          sharedTransportReady = false;\n          sharedDrainRequested = false;\n",
    'instance cleanup share subscription',
)
service = once(
    service,
    "          DeviceEventEmitter.removeAllListeners('SHARED_TEXT');\n          DeviceEventEmitter.removeAllListeners('SHARED_IMAGE');\n          DeviceEventEmitter.removeAllListeners('SHARED_FILES');\n          if (activeClipboardOnChangeSubscription === subscription) {\n            activeClipboardOnChangeSubscription = null;\n          }\n          NativeModules.ClipboardListener?.stopListening?.();\n",
    "          if (activeClipboardOnChangeSubscription === subscription) {\n            activeClipboardOnChangeSubscription = null;\n          }\n          if (activeShareAvailabilitySubscription === shareSubscription) {\n            activeShareAvailabilitySubscription = null;\n          }\n          NativeModules.ClipboardListener?.stopListening?.();\n",
    'generation cleanup removeAll replacement',
)
start_marker = '        // Event triggered when text content is shared with the app. (or) when text selection popup menu action is invoked\n'
end_marker = '        //clipboard monitor\n'
start = service.find(start_marker)
end = service.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('shared-listener block markers not found')
shared_block = '''        const processPendingShareEvent = async event => {
          const clipContent = event?.value;
          if (!clipContent) return;

          if (event.eventName === 'SHARED_TEXT') {
            await NativeBridgeModule.setAppOwnedTextClipboard(clipContent);
            await sendClipBoard(clipContent, 'text');
          } else if (event.eventName === 'SHARED_IMAGE') {
            await sendClipBoard(clipContent, 'image');
          } else if (event.eventName === 'SHARED_FILES') {
            await sendClipBoard(clipContent, 'files');
          } else {
            throw new Error(`Unsupported pending share event: ${event.eventName}`);
          }
        };

        const requestPendingShareDrain = async () => {
          sharedDrainRequested = true;
          if (!sharedTransportReady) return;
          if (sharedDrainPromise !== null) return sharedDrainPromise;

          sharedDrainPromise = (async () => {
            while (sharedDrainRequested) {
              sharedDrainRequested = false;
              const pendingEvents =
                (await NativeBridgeModule.drainPendingShareEvents()) ?? [];
              for (const event of pendingEvents) {
                try {
                  await processPendingShareEvent(event);
                } catch (error) {
                  await setDataInAsyncStorage(
                    'wsStatusMessage',
                    '❌ Outbound shared-content error: ' + error,
                  );
                }
              }
            }
          })();

          try {
            return await sharedDrainPromise;
          } finally {
            sharedDrainPromise = null;
            if (sharedDrainRequested && sharedTransportReady) {
              void requestPendingShareDrain();
            }
          }
        };

        instanceShareAvailabilitySubscription = DeviceEventEmitter.addListener(
          'SHARED_EVENT_AVAILABLE',
          () => {
            void requestPendingShareDrain();
          },
        );
        activeShareAvailabilitySubscription =
          instanceShareAvailabilitySubscription;

'''
service = service[:start] + shared_block + service[end:]
service = once(
    service,
    "        const sendClipBoard = async (clipContent, type_ = 'text') => {\n          if (server_mode === 'P2S') {\n            await sendClipBoardP2S(clipContent, type_);\n          } else if (server_mode === 'P2P') {\n            await sendClipBoardP2P(clipContent, type_);\n          }\n        };\n",
    "        const sendClipBoard = async (clipContent, type_ = 'text') => {\n          if (server_mode === 'P2S') {\n            await sendClipBoardP2S(clipContent, type_);\n          } else if (server_mode === 'P2P') {\n            await sendClipBoardP2P(clipContent, type_);\n          }\n        };\n\n        sharedTransportReady = true;\n        await requestPendingShareDrain();\n",
    'initial pending-share drain after transport initialization',
)
for forbidden in [
    "DeviceEventEmitter.removeAllListeners('SHARED_TEXT')",
    "DeviceEventEmitter.removeAllListeners('SHARED_IMAGE')",
    "DeviceEventEmitter.removeAllListeners('SHARED_FILES')",
    "DeviceEventEmitter.addListener('SHARED_TEXT'",
    "DeviceEventEmitter.addListener('SHARED_IMAGE'",
    "DeviceEventEmitter.addListener('SHARED_FILES'",
]:
    if forbidden in service:
        raise SystemExit(f'forbidden legacy shared-event pattern remains: {forbidden}')
service_path.write_text(service, encoding='utf-8')

android_contract_path = source_root / '__tests__/AndroidNativeContracts.test.ts'
android_contract = android_contract_path.read_text(encoding='utf-8')
old_test = '''  test('cold-start shares are queued until React initialization', () => {
    expect(mainActivity).toContain('pendingReactEvents.addLast(event)');
    expect(mainActivity).toContain('ReactInstanceEventListener');
    expect(mainActivity).toContain('flushPendingReactEvents(context)');
    expect(mainActivity).toContain('createReactContextInBackground()');
    expect(mainActivity).toContain('ExistingPeriodicWorkPolicy.KEEP');
    expect(mainActivity).toContain('cancelUniqueWork(WORK_NAME)');
    expect(mainActivity).not.toContain('getWorkInfosByTag(WORK_NAME).get()');
  });
'''
new_test = '''  test('cold-start shares remain queued until the JS transport drains them', () => {
    expect(mainActivity).toContain('PendingShareStore.enqueue(');
    expect(mainActivity).toContain('PendingShareStore.EVENT_AVAILABLE');
    expect(mainActivity).toContain('ReactInstanceEventListener');
    expect(mainActivity).toContain('createReactContextInBackground()');
    expect(mainActivity).toContain('EXTRA_SHARE_CONSUMED');
    expect(mainActivity).not.toContain('pendingReactEvents');
    expect(mainActivity).not.toContain('flushPendingReactEvents');
    expect(mainActivity).toContain('ExistingPeriodicWorkPolicy.KEEP');
    expect(mainActivity).toContain('cancelUniqueWork(WORK_NAME)');
    expect(mainActivity).not.toContain('getWorkInfosByTag(WORK_NAME).get()');
  });
'''
android_contract = once(android_contract, old_test, new_test, 'Android cold-share contract')
android_contract_path.write_text(android_contract, encoding='utf-8')

owned_contract_path = source_root / '__tests__/OwnedClipboardContracts.test.ts'
owned_contract = owned_contract_path.read_text(encoding='utf-8')
insert_before = "  test('only the active service generation can stop native monitoring', () => {\n"
queue_contract = '''  test('shared intents use one generation-owned wake subscription and atomic drain', () => {
    expect(foregroundService).toContain(
      'let activeShareAvailabilitySubscription = null',
    );
    expect(foregroundService).toContain(
      "DeviceEventEmitter.addListener(\\n          'SHARED_EVENT_AVAILABLE'",
    );
    expect(foregroundService).toContain(
      'await NativeBridgeModule.drainPendingShareEvents()',
    );
    expect(foregroundService).toContain('sharedDrainRequested = true');
    expect(foregroundService).toContain('sharedTransportReady = true');
    expect(foregroundService).not.toContain(
      "DeviceEventEmitter.removeAllListeners('SHARED_TEXT')",
    );
    expect(foregroundService).not.toContain(
      "DeviceEventEmitter.addListener('SHARED_TEXT'",
    );
  });

'''
owned_contract = once(
    owned_contract,
    insert_before,
    queue_contract + insert_before,
    'Owned clipboard pending-share contract',
)
owned_contract_path.write_text(owned_contract, encoding='utf-8')

pending_test = '''package com.clipcascade

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingShareStoreTest {
    @After
    fun clearStore() {
        PendingShareStore.drain()
    }

    @Test
    fun drainIsAtomicAndEmptyAfterward() {
        PendingShareStore.enqueue("SHARED_TEXT", "text", "one")
        PendingShareStore.enqueue("SHARED_IMAGE", "image", "two")

        val drained = PendingShareStore.drain()

        assertEquals(listOf("one", "two"), drained.map { it.value })
        assertTrue(PendingShareStore.drain().isEmpty())
    }

    @Test
    fun queueIsBoundedAndDropsOnlyTheOldestEvent() {
        for (index in 0..PendingShareStore.MAX_PENDING_EVENTS) {
            PendingShareStore.enqueue("SHARED_TEXT", "text", index.toString())
        }

        val drained = PendingShareStore.drain()

        assertEquals(PendingShareStore.MAX_PENDING_EVENTS, drained.size)
        assertEquals("1", drained.first().value)
        assertEquals(PendingShareStore.MAX_PENDING_EVENTS.toString(), drained.last().value)
    }
}
'''
test_kotlin_root.mkdir(parents=True, exist_ok=True)
(test_kotlin_root / 'PendingShareStoreTest.kt').write_text(pending_test, encoding='utf-8')

print('Applied bounded pending-share queue transformation.')
