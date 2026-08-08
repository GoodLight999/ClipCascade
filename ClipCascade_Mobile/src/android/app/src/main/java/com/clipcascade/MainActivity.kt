// android\app\src\main\java\com\clipcascade\MainActivity.kt
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
