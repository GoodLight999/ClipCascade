package com.clipcascade.shizuku

import android.content.ClipData
import android.content.Context
import android.os.IBinder
import android.os.Process
import androidx.annotation.Keep
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException

/**
 * Runs with Shizuku's shell identity and performs one clipboard read on demand.
 * It owns no network connection, queue, or clipboard-change polling.
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
        val uri = item.uri?.toString()

        val type: String
        val content: String
        when {
            text != null && mimeType.startsWith("text/") -> {
                type = "text"
                content = text
            }
            uri != null && mimeType.startsWith("image/") -> {
                type = "image"
                content = uri
            }
            uri != null -> {
                type = "files"
                content = uri
            }
            text != null -> {
                type = "text"
                content = text
            }
            else -> {
                return JSONObject()
                    .put("status", "unsupported")
                    .put("mimeType", mimeType)
                    .toString()
            }
        }

        return JSONObject()
            .put("status", "ok")
            .put("type", type)
            .put("content", content)
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

            val method = service.javaClass.methods
                .filter { it.name == "getPrimaryClip" }
                .maxByOrNull { it.parameterCount }
                ?: error("getPrimaryClip is unavailable on this Android build")

            method.isAccessible = true
            val arguments = buildArguments(method.parameterTypes)
            return method.invoke(service, *arguments) as? ClipData
        }

        private fun buildArguments(types: Array<Class<*>>): Array<Any?> {
            var stringIndex = 0
            var intIndex = 0
            val userId = Process.myUid() / 100000

            return Array(types.size) { index ->
                when (types[index]) {
                    String::class.java -> {
                        val value = if (stringIndex == 0) SHELL_PACKAGE else null
                        stringIndex += 1
                        value
                    }
                    Int::class.javaPrimitiveType, Int::class.javaObjectType -> {
                        val value = if (intIndex == 0) userId else 0
                        intIndex += 1
                        value
                    }
                    Long::class.javaPrimitiveType, Long::class.javaObjectType -> 0L
                    Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> false
                    else -> error("Unsupported getPrimaryClip parameter: ${types[index].name}")
                }
            }
        }
    }
}
