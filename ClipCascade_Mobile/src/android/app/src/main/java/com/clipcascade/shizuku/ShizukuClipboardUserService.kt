package com.clipcascade.shizuku

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
 * the IClipboard#getPrimaryClip signatures present in AOSP releases supported
 * by this application; unknown signatures fail closed instead of guessing.
 */
class ShizukuClipboardUserService : IShizukuClipboardService.Stub {
    constructor() : super()

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : super()

    override fun getServiceUid(): Int = Process.myUid()

    override fun readClipboard(): String {
        return try {
            encodeClip(HiddenClipboardReader.readPrimaryClip())
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
        private const val DEFAULT_DEVICE_ID = 0
        private const val PER_USER_RANGE = 100_000

        fun readPrimaryClip(): ClipData? {
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
            return method.invoke(service, *argumentsFor(method)) as? ClipData
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
                            ) { it.simpleName }
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

        private fun argumentsFor(method: Method): Array<Any?> {
            // AOSP UserHandle.getUserId(uid) is uid / PER_USER_RANGE.
            val userId = Process.myUid() / PER_USER_RANGE
            return when {
                isAndroid14PlusSignature(method) ->
                    arrayOf(SHELL_PACKAGE, null, userId, DEFAULT_DEVICE_ID)
                isAndroid12Signature(method) ->
                    arrayOf(SHELL_PACKAGE, null, userId)
                isAndroid10Signature(method) ->
                    arrayOf(SHELL_PACKAGE, userId)
                isLegacySignature(method) ->
                    arrayOf(SHELL_PACKAGE)
                else -> error("Unsupported IClipboard#getPrimaryClip signature")
            }
        }
    }
}
