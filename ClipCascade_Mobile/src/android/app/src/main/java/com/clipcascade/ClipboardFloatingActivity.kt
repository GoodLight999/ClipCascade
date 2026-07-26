package com.clipcascade

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.clipcascade.acquisition.AcquisitionBackendId
import com.clipcascade.acquisition.ClipboardReadResultCode
import com.clipcascade.acquisition.ClipboardReadRuntime
import com.facebook.react.ReactInstanceManager
import com.facebook.react.bridge.ReactContext

class ClipboardFloatingActivity : AppCompatActivity() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var isViewAttached = false
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            createFloatingView()
            makeFloatingViewInFocus()

            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                globalLayoutListener?.let { current ->
                    try {
                        floatingView.viewTreeObserver.removeOnGlobalLayoutListener(current)
                    } catch (_: Exception) {
                    }
                }
                globalLayoutListener = null

                try {
                    ClipboardReadRuntime.readAndEmit(
                        context = this,
                        backendId = AcquisitionBackendId.LOGCAT_OVERLAY,
                        reactContext = getReactContext(),
                    )
                } catch (exception: Exception) {
                    ClipboardReadRuntime.recordExternalResult(
                        AcquisitionBackendId.LOGCAT_OVERLAY,
                        ClipboardReadResultCode.READ_FAILED,
                    )
                    Log.e(TAG, "Overlay clipboard read failed", exception)
                } finally {
                    makeFloatingViewOutOfFocus()
                    removeFloatingViewAndFinish()
                }
            }
            globalLayoutListener = listener
            floatingView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        } catch (_: SecurityException) {
            ClipboardReadRuntime.recordExternalResult(
                AcquisitionBackendId.LOGCAT_OVERLAY,
                ClipboardReadResultCode.FOCUS_REQUIRED,
            )
            Log.w(TAG, "Overlay permission was denied")
            removeFloatingViewAndFinish()
        } catch (exception: Exception) {
            ClipboardReadRuntime.recordExternalResult(
                AcquisitionBackendId.LOGCAT_OVERLAY,
                ClipboardReadResultCode.READ_FAILED,
            )
            Log.e(TAG, "Failed to create clipboard overlay", exception)
            removeFloatingViewAndFinish()
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
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            x = 0
            y = 0
        }
        windowManager.addView(floatingView, params)
        isViewAttached = true
    }

    private fun makeFloatingViewInFocus() {
        if (!isViewAttached) {
            return
        }
        val params = floatingView.layoutParams as WindowManager.LayoutParams
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        windowManager.updateViewLayout(floatingView, params)
    }

    private fun makeFloatingViewOutOfFocus() {
        if (!isViewAttached) {
            return
        }
        try {
            val params = floatingView.layoutParams as WindowManager.LayoutParams
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(floatingView, params)
        } catch (_: Exception) {
        }
    }

    private fun getReactContext(): ReactContext? {
        val reactInstanceManager: ReactInstanceManager =
            (applicationContext as MainApplication).reactNativeHost.reactInstanceManager
        return reactInstanceManager.currentReactContext
    }

    private fun detachFloatingView() {
        if (!isViewAttached) {
            return
        }

        globalLayoutListener?.let { listener ->
            try {
                floatingView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            } catch (_: Exception) {
            }
        }
        globalLayoutListener = null

        try {
            windowManager.removeViewImmediate(floatingView)
        } catch (_: Exception) {
        }
        isViewAttached = false
    }

    private fun removeFloatingViewAndFinish() {
        detachFloatingView()
        if (!isFinishing) {
            finish()
        }
    }

    override fun onDestroy() {
        detachFloatingView()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClipCascadeCapture"

        fun getIntent(context: Context): Intent = Intent(
            context.applicationContext,
            ClipboardFloatingActivity::class.java,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
    }
}
