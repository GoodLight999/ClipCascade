package com.clipcascade.shizuku

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Process
import androidx.annotation.Keep
import org.json.JSONObject

/**
 * Runs with Shizuku's shell/root identity and performs one clipboard read on
 * demand. It owns no network connection, queue, or clipboard polling.
 *
 * Important: do not call android.content.IClipboard directly here. OEM Android
 * builds are free to extend that hidden Binder interface. The framework
 * ClipboardManager installed on the device is compiled against the device's
 * own IClipboard ABI, so it is the authoritative adapter for vendor-specific
 * signatures.
 */
class ShizukuClipboardUserService : IShizukuClipboardService.Stub {
    private var userServiceContext: Context? = null

    constructor() : super()

    @Keep
    constructor(context: Context) : super() {
        userServiceContext = context
    }

    override fun getServiceUid(): Int = Process.myUid()

    override fun readClipboard(userId: Int): String {
        return try {
            require(userId >= 0) { "Invalid Android user id: $userId" }
            val context = userServiceContext
                ?: error("Shizuku UserService Context is unavailable; Shizuku API v13+ is required")
            encodeClip(FrameworkClipboardReader.readPrimaryClip(context, userId))
        } catch (error: Throwable) {
            JSONObject()
                .put("status", "error")
                .put("error", error.javaClass.simpleName)
                .put("message", error.message ?: "Clipboard read failed")
                .toString()
        }
    }

    override fun destroy() {
        System.exit(0)
    }

    private fun encodeClip(clip: ClipData?): String {
        if (clip == null || clip.itemCount == 0) {
            return JSONObject().put("status", "empty").toString()
        }

        val description = clip.description
        val mimeType = if (description.mimeTypeCount > 0) description.getMimeType(0) else ""
        val item = clip.getItemAt(0)
        val text = item.text?.toString()

        if (text != null) {
            return JSONObject()
                .put("status", "ok")
                .put("type", "text")
                .put("content", text)
                .put("mimeType", mimeType)
                .toString()
        }

        return JSONObject()
            .put("status", "needs_fallback")
            .put("mimeType", mimeType)
            .toString()
    }

    private object FrameworkClipboardReader {
        private const val ROOT_UID = 0
        private const val SHELL_UID = 2_000
        private const val SHELL_PACKAGE = "com.android.shell"
        private const val PER_USER_RANGE = 100_000

        fun readPrimaryClip(context: Context, userId: Int): ClipData? {
            val serviceUid = Process.myUid()
            require(serviceUid == ROOT_UID || serviceUid == SHELL_UID) {
                "Unsupported Shizuku UserService UID: $serviceUid"
            }

            // Shizuku v13 constructs the supplied UserService Context for the
            // calling application's Android user. Keep the requested user and
            // that Context aligned before creating the shell package Context.
            val contextUserId = context.applicationInfo.uid / PER_USER_RANGE
            require(contextUserId == userId) {
                "UserService Context user $contextUserId does not match requested user $userId"
            }

            // ClipboardService validates the calling package against the Binder
            // identity. A Shizuku shell process therefore needs the platform's
            // com.android.shell package identity. Using that package Context also
            // lets the device's own ClipboardManager supply every OEM-specific
            // Binder argument instead of ClipCascade guessing hidden signatures.
            val shellContext = context.createPackageContext(SHELL_PACKAGE, 0)
            val shellContextUserId = shellContext.applicationInfo.uid / PER_USER_RANGE
            require(shellContextUserId == userId) {
                "Shell Context user $shellContextUserId does not match requested user $userId"
            }

            val clipboardManager = shellContext.getSystemService(ClipboardManager::class.java)
                ?: error("Framework ClipboardManager is unavailable")
            return clipboardManager.primaryClip
        }
    }
}
