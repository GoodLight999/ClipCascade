package com.clipcascade

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * One background-read decision point shared by Accessibility and READ_LOGS.
 * Preferred path: Shizuku shell read. Fallback: the existing overlay activity.
 */
object BackgroundClipboardCapture {
    private const val TAG = "BackgroundCapture"

    fun request(context: Context, source: String) {
        if (!ClipboardListenerModule.isRuntimeActive()) {
            Log.d(TAG, "Ignoring $source trigger because the ClipCascade runtime is inactive")
            return
        }

        ShizukuClipboardBridge.readClipboard { result ->
            if (result.success && result.content != null && result.type != null) {
                if (!ClipboardListenerModule.emitExternalClipboard(result.content, result.type)) {
                    Log.w(TAG, "Shizuku read succeeded but React Native runtime was unavailable")
                }
                return@readClipboard
            }

            // Shizuku may be absent, stopped, denied, binding, or unsupported on
            // a vendor build. Preserve upstream's existing overlay path as the
            // non-root fallback instead of failing the copy silently.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                try {
                    context.startActivity(ClipboardFloatingActivity.getIntent(context))
                } catch (error: Throwable) {
                    Log.e(TAG, "Overlay fallback failed for $source", error)
                }
            } else {
                Log.w(TAG, "No usable background clipboard read path for $source: ${result.status}")
            }
        }
    }
}
