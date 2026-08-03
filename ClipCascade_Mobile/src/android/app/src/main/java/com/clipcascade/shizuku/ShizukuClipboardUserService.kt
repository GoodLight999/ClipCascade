package com.clipcascade.shizuku

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.os.IBinder
import android.os.Process
import androidx.annotation.Keep
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Runs with Shizuku's shell/root identity and performs one clipboard read on
 * demand. It owns no network connection, queue, or clipboard polling.
 *
 * Shizuku's official UserService documentation states that non-SDK APIs are
 * available in this process. The Binder call signatures below are limited to
 * explicit AOSP IClipboard#getPrimaryClip signatures; unknown signatures fail
 * closed and report their actual parameter list.
 */
class ShizukuClipboardUserService : IShizukuClipboardService.Stub {
    constructor() : super()

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : super()

    override fun getServiceUid(): Int = Process.myUid()

    override fun readClipboard(userId: Int): String {
        return try {
            require(userId >= 0) { "Invalid Android user id: $userId" }
            encodeClip(HiddenClipboardReader.readPrimaryClip(userId))
        } catch (error: Throwable) {
            val cause = rootCause(error)
            JSONObject()
                .put("status", "error")
                .put("error", cause.javaClass.simpleName)
                .put("message", cause.message ?: "Clipboard read failed")
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

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        while (current is InvocationTargetException && current.targetException != null) {
            current = current.targetException
        }
        return current
    }

    private object HiddenClipboardReader {
        private const val SHELL_PACKAGE = "com.android.shell"
        private const val ROOT_PACKAGE = "root"
        // AOSP Context.DEVICE_ID_DEFAULT is the inlined integer value 0.
        private const val DEFAULT_DEVICE_ID = 0

        /**
         * This method intentionally uses Android's hidden ServiceManager and
         * IClipboard interfaces inside a Shizuku UserService. The selected
         * signatures are explicit and unknown signatures fail closed.
         */
        @SuppressLint("PrivateApi")
        fun readPrimaryClip(userId: Int): ClipData? {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val binder = serviceManager
                .getMethod("getService", String::class.java)
                .invoke(null, Context.CLIPBOARD_SERVICE) as? IBinder
                ?: error("Clipboard service binder is unavailable")

            val stubClass = Class.forName("android.content.IClipboard\$Stub")
            val service = stubClass
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
                ?: error("Clipboard service interface is unavailable")

            val interfaceClass = Class.forName("android.content.IClipboard")
            val method = findSupportedGetPrimaryClip(interfaceClass.methods)
            method.isAccessible = true
            return method.invoke(service, *argumentsFor(method, userId)) as? ClipData
        }

        private fun findSupportedGetPrimaryClip(methods: Array<Method>): Method {
            val candidates = methods.filter { it.name == "getPrimaryClip" }
            return candidates.firstOrNull(::isAndroid14PlusSignature)
                ?: candidates.firstOrNull(::isAndroid12Signature)
                ?: candidates.firstOrNull(::isAndroid10Signature)
                ?: candidates.firstOrNull(::isLegacySignature)
                ?: error(
                    "Unsupported IClipboard#getPrimaryClip signature: " +
                        candidates.joinToString { method ->
                            method.parameterTypes.joinToString(
                                prefix = "(",
                                postfix = ")"
                            ) { it.name }
                        }
                )
        }

        private fun isAndroid14PlusSignature(method: Method): Boolean =
            method.parameterTypes.contentEquals(
                arrayOf(
                    String::class.java,
                    String::class.java,
                    Integer.TYPE,
                    Integer.TYPE
                )
            )

        private fun isAndroid12Signature(method: Method): Boolean =
            method.parameterTypes.contentEquals(
                arrayOf(
                    String::class.java,
                    String::class.java,
                    Integer.TYPE
                )
            )

        private fun isAndroid10Signature(method: Method): Boolean =
            method.parameterTypes.contentEquals(
                arrayOf(
                    String::class.java,
                    Integer.TYPE
                )
            )

        private fun isLegacySignature(method: Method): Boolean =
            method.parameterTypes.contentEquals(arrayOf(String::class.java))

        private fun argumentsFor(method: Method, userId: Int): Array<Any?> {
            val packageName = when (Process.myUid()) {
                0 -> ROOT_PACKAGE
                2_000 -> SHELL_PACKAGE
                else -> error("Unsupported Shizuku UserService UID: ${Process.myUid()}")
            }
            return when {
                isAndroid14PlusSignature(method) ->
                    arrayOf(packageName, null, userId, DEFAULT_DEVICE_ID)
                isAndroid12Signature(method) ->
                    arrayOf(packageName, null, userId)
                isAndroid10Signature(method) ->
                    arrayOf(packageName, userId)
                isLegacySignature(method) ->
                    arrayOf(packageName)
                else -> error("Unsupported IClipboard#getPrimaryClip signature")
            }
        }
    }
}
