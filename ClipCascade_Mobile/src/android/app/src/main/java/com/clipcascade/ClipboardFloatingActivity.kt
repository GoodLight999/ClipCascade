// android\app\src\main\java\com\clipcascade\ClipboardFloatingActivity.kt
package com.clipcascade

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * Existing overlay fallback used only when direct Shizuku reading is unavailable.
 * All content now returns through ClipboardListenerModule so ordinary listener,
 * Shizuku, and overlay reads share one duplicate gate and diagnostics path.
 */
class ClipboardFloatingActivity : AppCompatActivity() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var clipboardManager: ClipboardManager
    private var isViewAttached = false
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission is not available; skipping background clipboard read")
            CaptureDiagnostics.recordIgnored("overlay", "overlay_permission_missing")
            finishWithoutAnimation()
            return
        }

        if (!ClipboardListenerModule.isRuntimeActive()) {
            Log.w(TAG, "React Native clipboard runtime is inactive; skipping background clipboard read")
            CaptureDiagnostics.recordIgnored("overlay", "runtime_inactive")
            finishWithoutAnimation()
            return
        }

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        try {
            createFloatingView()
            makeFloatingViewInFocus()

            globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                try {
                    globalLayoutListener?.let {
                        floatingView.viewTreeObserver.removeOnGlobalLayoutListener(it)
                    }
                    getClipboardContent()
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to read clipboard from overlay activity", error)
                    CaptureDiagnostics.recordFailure("overlay", "overlay_read_failed", error)
                } finally {
                    makeFloatingViewOutOfFocus()
                    removeFloatingView(finishActivity = true)
                }
            }

            globalLayoutListener?.let {
                floatingView.viewTreeObserver.addOnGlobalLayoutListener(it)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to create clipboard overlay", error)
            CaptureDiagnostics.recordFailure("overlay", "overlay_create_failed", error)
            removeFloatingView(finishActivity = false)
            finishWithoutAnimation()
        }
    }

    private fun createFloatingView() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_view_layout, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            x = 0
            y = 0
        }
        windowManager.addView(floatingView, params)
        isViewAttached = true
    }

    private fun getClipboardContent() {
        val clip = clipboardManager.primaryClip
        if (clip == null || clip.itemCount == 0) {
            CaptureDiagnostics.recordIgnored("overlay", "clipboard_empty")
            return
        }

        val description = clip.description
        val mimeType = if (description.mimeTypeCount > 0) description.getMimeType(0) else ""
        val item = clip.getItemAt(0)

        val content: String
        val type: String
        when {
            item.text != null && mimeType.startsWith("text/") -> {
                content = item.text.toString()
                type = "text"
            }
            item.uri != null && mimeType.startsWith("image/") -> {
                content = item.uri.toString()
                type = "image"
            }
            item.uri != null -> {
                content = item.uri.toString()
                type = "files"
            }
            item.text != null -> {
                content = item.text.toString()
                type = "text"
            }
            else -> {
                CaptureDiagnostics.recordIgnored("overlay", "clipboard_type_unsupported")
                return
            }
        }

        ClipboardListenerModule.emitExternalClipboard(content, type, "overlay")
    }

    private fun makeFloatingViewInFocus() {
        if (isViewAttached) {
            val params = floatingView.layoutParams as WindowManager.LayoutParams
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(floatingView, params)
        }
    }

    private fun makeFloatingViewOutOfFocus() {
        if (isViewAttached) {
            val params = floatingView.layoutParams as WindowManager.LayoutParams
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(floatingView, params)
        }
    }

    private fun removeFloatingView(finishActivity: Boolean) {
        if (isViewAttached) {
            try {
                globalLayoutListener?.let {
                    floatingView.viewTreeObserver.removeOnGlobalLayoutListener(it)
                }
            } catch (_: Exception) {
            }

            try {
                windowManager.removeViewImmediate(floatingView)
            } catch (error: Exception) {
                Log.w(TAG, "Unable to remove clipboard overlay", error)
                CaptureDiagnostics.recordFailure("overlay", "overlay_remove_failed", error)
            }
            isViewAttached = false
        }

        if (finishActivity && !isFinishing) {
            finishWithoutAnimation()
        }
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        removeFloatingView(finishActivity = false)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClipboardFloating"

        fun getIntent(context: Context): Intent {
            return Intent(context.applicationContext, ClipboardFloatingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
        }
    }
}
