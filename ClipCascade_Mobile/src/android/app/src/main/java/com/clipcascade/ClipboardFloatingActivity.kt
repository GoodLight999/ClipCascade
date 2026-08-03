// android\app\src\main\java\com\clipcascade\ClipboardFloatingActivity.kt
package com.clipcascade

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * Existing overlay reader used when the unified coordinator cannot complete a
 * Shizuku read. It returns content to the same React Native event and existing
 * sender as a successful Shizuku read.
 */
class ClipboardFloatingActivity : AppCompatActivity() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var clipboardManager: ClipboardManager
    private var isViewAttached = false
    private var coordinatorCompleted = false
    private var triggerSource = "unknown"
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        triggerSource = intent.getStringExtra(EXTRA_TRIGGER_SOURCE) ?: "unknown"

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission is not available; skipping clipboard read")
            CaptureDiagnostics.recordIgnored(diagnosticSource(), "overlay_permission_missing")
            finishWithoutAnimation()
            return
        }

        if (!ClipboardListenerModule.isRuntimeActive()) {
            Log.w(TAG, "React Native clipboard runtime is inactive; skipping clipboard read")
            CaptureDiagnostics.recordIgnored(diagnosticSource(), "runtime_inactive")
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
                    CaptureDiagnostics.recordFailure(
                        diagnosticSource(),
                        "overlay_read_failed",
                        error
                    )
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
            CaptureDiagnostics.recordFailure(
                diagnosticSource(),
                "overlay_create_failed",
                error
            )
            removeFloatingView(finishActivity = false)
            finishWithoutAnimation()
        }
    }

    private fun createFloatingView() {
        floatingView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
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
            CaptureDiagnostics.recordIgnored(diagnosticSource(), "clipboard_empty")
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
                CaptureDiagnostics.recordIgnored(
                    diagnosticSource(),
                    "clipboard_type_unsupported"
                )
                return
            }
        }

        ClipboardListenerModule.emitExternalClipboard(
            content,
            type,
            diagnosticSource()
        )
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
                CaptureDiagnostics.recordFailure(
                    diagnosticSource(),
                    "overlay_remove_failed",
                    error
                )
            }
            isViewAttached = false
        }

        if (finishActivity && !isFinishing) {
            finishWithoutAnimation()
        }
    }

    private fun diagnosticSource(): String = "overlay:$triggerSource"

    private fun completeCoordinatorOnce() {
        if (coordinatorCompleted) return
        coordinatorCompleted = true
        BackgroundClipboardCapture.completeOverlay(this)
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        removeFloatingView(finishActivity = false)
        completeCoordinatorOnce()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClipboardFloating"
        private const val EXTRA_TRIGGER_SOURCE =
            "com.clipcascade.extra.OVERLAY_TRIGGER_SOURCE"

        fun getIntent(context: Context, triggerSource: String): Intent {
            return Intent(context.applicationContext, ClipboardFloatingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra(EXTRA_TRIGGER_SOURCE, triggerSource)
            }
        }
    }
}
