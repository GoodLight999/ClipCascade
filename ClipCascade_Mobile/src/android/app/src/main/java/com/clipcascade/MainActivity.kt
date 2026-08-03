// android\app\src\main\java\com\clipcascade\MainActivity.kt
package com.clipcascade

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.ReactInstanceManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.facebook.react.devsupport.interfaces.DevSupportManager
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.concurrent.TimeUnit

class MainActivity : ReactActivity() {
    private data class PendingReactEvent(
        val eventName: String,
        val key: String,
        val value: String
    )

    private val pendingReactEvents = ArrayDeque<PendingReactEvent>()
    private var reactInstanceListener: ReactInstanceManager.ReactInstanceEventListener? = null

    companion object {
        const val TAG = "ClipCascade"
        const val WORK_NAME = "schedule_work"
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
        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                sendToReactNative("SHARED_TEXT", "text", it)
            }
        } else if (
            Intent.ACTION_PROCESS_TEXT == intent.action &&
            "text/plain" == intent.type
        ) {
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.let {
                sendToReactNative("SHARED_TEXT", "text", it.toString())
            }
        } else if (
            Intent.ACTION_SEND == intent.action &&
            intent.type?.startsWith("image/") == true
        ) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let {
                sendToReactNative("SHARED_IMAGE", "image", it.toString())
            }
        } else if (Intent.ACTION_SEND == intent.action && intent.type != null) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let {
                sendToReactNative("SHARED_FILES", "files", it.toString())
            }
        } else if (
            Intent.ACTION_SEND_MULTIPLE == intent.action &&
            intent.type != null
        ) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris ->
                sendToReactNative(
                    "SHARED_FILES",
                    "files",
                    uris.joinToString(",") { it.toString() }
                )
            }
        }

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

    private fun sendToReactNative(eventName: String, key: String, value: String) {
        val event = PendingReactEvent(eventName, key, value)
        val manager = reactNativeHost.reactInstanceManager
        val reactContext = currentReactContextOrNull(manager)
        if (reactContext != null) {
            emitToReactNative(reactContext, event)
            return
        }

        // Cold-start share intents arrive before the React context exists.
        // Retain them until ReactInstanceManager announces initialization.
        pendingReactEvents.addLast(event)
        ensureReactInstanceListener(manager)
    }

    private fun ensureReactInstanceListener(manager: ReactInstanceManager) {
        if (reactInstanceListener != null) return

        lateinit var listener: ReactInstanceManager.ReactInstanceEventListener
        listener = ReactInstanceManager.ReactInstanceEventListener { context ->
            runOnUiThread {
                flushPendingReactEvents(context)
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
            flushPendingReactEvents(context)
            manager.removeReactInstanceEventListener(listener)
            if (reactInstanceListener === listener) {
                reactInstanceListener = null
            }
        }

        if (!manager.hasStartedCreatingInitialContext()) {
            manager.createReactContextInBackground()
        }
    }

    private fun flushPendingReactEvents(context: ReactContext) {
        while (pendingReactEvents.isNotEmpty()) {
            emitToReactNative(context, pendingReactEvents.removeFirst())
        }
    }

    private fun emitToReactNative(context: ReactContext, event: PendingReactEvent) {
        val params = Arguments.createMap().apply {
            putString(event.key, event.value)
        }
        context
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(event.eventName, params)
    }

    @SuppressLint("VisibleForTests")
    private fun currentReactContextOrNull(
        manager: ReactInstanceManager
    ): ReactContext? = manager.currentReactContext

    override fun onDestroy() {
        reactInstanceListener?.let { listener ->
            reactNativeHost.reactInstanceManager.removeReactInstanceEventListener(listener)
        }
        reactInstanceListener = null
        super.onDestroy()
    }
}
