package com.clipcascade

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
import com.facebook.react.devsupport.interfaces.DevSupportManager
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import java.util.concurrent.TimeUnit

class MainActivity : ReactActivity() {
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
        window.decorView.post { NotificationAccessPrompt.showIfNeeded(this) }

        val bridge = AsyncStorageBridge(applicationContext)
        try {
            val enablePeriodicChecks =
                bridge.getValue("enable_periodic_checks")?.toBoolean() ?: true
            if (enablePeriodicChecks) {
                scheduleJob()
                if (ScheduleService.hasNotificationPermission(applicationContext)) {
                    ScheduleService.removeNotificationIfPresent(applicationContext)
                }
            } else {
                WorkManager.getInstance(applicationContext)
                    .cancelUniqueWork(WORK_NAME)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Error scheduling health checks", error)
        } finally {
            bridge.disconnect()
        }
    }

    private fun scheduleJob() {
        val periodicWorkRequest =
            PeriodicWorkRequestBuilder<ScheduleService>(15, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWorkRequest,
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
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let {
                val uriList = it.map(Uri::toString).joinToString(",")
                sendToReactNative("SHARED_FILES", "files", uriList)
            }
        }

        if ("com.clipcascade.NOTIFICATION_ACTION" == intent.action) {
            val action = intent.getStringExtra("action")
            if (action == "foreground_service_stopped_running") {
                val bridge = AsyncStorageBridge(applicationContext)
                try {
                    bridge.setValue("foreground_service_stopped_running", "true")
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to save notification recovery action", error)
                } finally {
                    bridge.disconnect()
                }
            }
        }
    }

    private fun sendToReactNative(eventName: String, key: String, value: String) {
        reactInstanceManager.currentReactContext?.let { reactContext ->
            val params = Arguments.createMap().apply { putString(key, value) }
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
        }
    }
}
