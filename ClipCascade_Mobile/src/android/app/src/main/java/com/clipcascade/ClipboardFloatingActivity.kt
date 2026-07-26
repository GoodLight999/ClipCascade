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
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.ReactInstanceManager
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap

class ClipboardFloatingActivity : AppCompatActivity() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var clipboardManager: ClipboardManager
    private var reactContext: ReactContext? = null
    private var isViewAttached = false
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission is not available; skipping background clipboard read")
            finishWithoutAnimation()
            return
        }

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        reactContext = getReactContext()

        if (reactContext == null || !ClipboardListenerModule.isRuntimeActive()) {
            Log.w(TAG, "React Native clipboard runtime is inactive; skipping background clipboard read")
            finishWithoutAnimation()
            return
        }

        try {
            createFloatingView()
            makeFloatingViewInFocus()

            globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                try {
                    globalLayoutListener?.let {
                        floatingView.viewTreeObserver.removeOnGlobalLayoutListener(it)
                    }
                    getClipboardContent()
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to read clipboard from overlay activity", e)
                } finally {
                    makeFloatingViewOutOfFocus()
                    removeFloatingView(finishActivity = true)
                }
            }

            floatingView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to create clipboard overlay", e)
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
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
        if (clip != null && clip.itemCount > 0) {
            val description = clip.description ?: return
            val mimeType = description.getMimeType(0) ?: return
            val item = clip.getItemAt(0)
            val params: WritableMap = Arguments.createMap()

            if (mimeType.startsWith("text/") && item.text != null) {
                params.putString("content", item.text.toString())
                params.putString("type", "text")
            }
            else if (mimeType.startsWith("image/") && item.uri != null) {
                params.putString("content", item.uri.toString())
                params.putString("type", "image")
            }
            else if (item.uri != null) {
                params.putString("content", item.uri.toString())
                params.putString("type", "files")
            }
            else {
                return
            }

            reactContext
                ?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                ?.emit("onClipboardChange", params)
        }
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

    private fun getReactContext(): ReactContext? {
        val reactInstanceManager: ReactInstanceManager =
            (applicationContext as MainApplication).reactNativeHost.reactInstanceManager
        return reactInstanceManager.currentReactContext
    }

    private fun removeFloatingView(finishActivity: Boolean) {
        if (isViewAttached) {
            try {
                globalLayoutListener?.let {
                    floatingView.viewTreeObserver.removeOnGlobalLayoutListener(it)
                }
            } catch (_: Exception) {}

            try {
                windowManager.removeViewImmediate(floatingView)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to remove clipboard overlay", e)
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
