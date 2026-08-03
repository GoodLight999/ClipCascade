// android\app\src\main\java\com\clipcascade\NativeBridgeModule.kt
package com.clipcascade

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PersistableBundle
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.work.WorkManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class NativeBridgeModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val asyncBridge = AsyncStorageBridge(reactContext)

    override fun getName(): String = "NativeBridgeModule"

    @ReactMethod
    fun getSharedText(promise: Promise) {
        promise.resolve(null)
    }

    @ReactMethod
    fun clearCookies(promise: Promise) {
        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            promise.resolve("Cookies cleared successfully!")
        } catch (e: Exception) {
            promise.reject("COOKIE_ERROR", "Failed to clear cookies", e)
        }
    }

    @ReactMethod
    fun stopWorkManager() {
        WorkManager.getInstance(reactApplicationContext).cancelAllWorkByTag(MainActivity.WORK_NAME)
    }

    @ReactMethod
    fun getFileSize(contentUri: String, promise: Promise) {
        try {
            val trimmedUri = contentUri.trim()
            val uri = Uri.parse(trimmedUri)
            var size: Long = -1
            val projection = arrayOf(OpenableColumns.SIZE)

            reactApplicationContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    size = cursor.getLong(sizeIndex)
                }
            }
            if (size == -1L) {
                promise.reject("ERROR", "Failed to get file size: -1 for URI: $trimmedUri")
                return
            }
            promise.resolve(size.toString())
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to get file size: ${e.message}")
        }
    }

    @ReactMethod
    fun getFileName(contentUri: String, promise: Promise) {
        try {
            val trimmedUri = contentUri.trim()
            val uri = Uri.parse(trimmedUri)
            var fileName: String? = null
            val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
            reactApplicationContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            if (fileName == null) {
                promise.reject("ERROR", "Failed to get file name: null for URI: $trimmedUri")
                return
            }
            promise.resolve(fileName)
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to get file name: ${e.message}")
        }
    }

    @ReactMethod
    fun getFileAsBase64(contentUri: String, promise: Promise) {
        try {
            val trimmedUri = contentUri.trim()
            val uri = Uri.parse(trimmedUri)
            reactApplicationContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                val base64String = Base64.encodeToString(bytes, Base64.DEFAULT)
                promise.resolve(base64String)
            } ?: run {
                promise.reject(
                    "ERROR",
                    "Failed to open file stream. Invalid URI or file access denied. $trimmedUri"
                )
            }
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to get file bytes: ${e.message}")
        }
    }

    fun convertAnySupportedImageToPng(imageBytes: ByteArray): ByteArray? {
        val bitmap: Bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return null
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return outputStream.toByteArray()
    }

    @ReactMethod
    fun drainPendingShareEvents(promise: Promise) {
        try {
            val result = Arguments.createArray()
            PendingShareStore.drain().forEach { event ->
                result.pushMap(
                    Arguments.createMap().apply {
                        putString("eventName", event.eventName)
                        putString("key", event.key)
                        putString("value", event.value)
                    }
                )
            }
            promise.resolve(result)
        } catch (error: Exception) {
            promise.reject(
                "PENDING_SHARE_DRAIN_ERROR",
                "Failed to drain pending share events",
                error
            )
        }
    }

    @ReactMethod
    fun setAppOwnedTextClipboard(content: String, promise: Promise) {
        try {
            val clipData = ClipData.newPlainText("ClipCascade text", content).apply {
                description.extras = appOwnedClipboardExtras()
            }
            val clipboard = reactApplicationContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clipData)
            promise.resolve(null)
        } catch (error: Exception) {
            promise.reject(
                "CLIPBOARD_WRITE_ERROR",
                "Failed to write app-owned text to the clipboard",
                error
            )
        }
    }

    private fun appOwnedClipboardExtras(): PersistableBundle =
        PersistableBundle().apply {
            putBoolean(ClipboardListenerModule.APP_OWNED_CLIP_MARKER, true)
        }

    @ReactMethod
    fun copyBase64ImageToClipboardUsingCache(base64String: String, promise: Promise) {
        try {
            clearImageCacheInternal()

            val imageBytes = convertAnySupportedImageToPng(
                Base64.decode(base64String, Base64.DEFAULT)
            )
            if (imageBytes == null) {
                promise.reject(
                    "INVALID_IMAGE",
                    "The provided bytes do not represent a valid image."
                )
                return
            }

            val fileName = "clipboard_image_${System.currentTimeMillis()}.png"
            val cacheFile = File(reactApplicationContext.cacheDir, fileName)
            FileOutputStream(cacheFile).use { output ->
                output.write(imageBytes)
            }

            val authority = "${reactApplicationContext.packageName}.fileprovider"
            val imageUri: Uri = FileProvider.getUriForFile(
                reactApplicationContext,
                authority,
                cacheFile
            )

            val clipData = ClipData.newUri(
                reactApplicationContext.contentResolver,
                "Image",
                imageUri
            ).apply {
                description.extras = appOwnedClipboardExtras()
            }
            val clipboard = reactApplicationContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clipData)

            promise.resolve("Image copied to clipboard.")
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to copy image to clipboard: ${e.message}")
        }
    }

    @ReactMethod
    fun clearImageCache(promise: Promise) {
        try {
            clearImageCacheInternal()
            promise.resolve("Cache cleared successfully.")
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to clear cache: ${e.message}")
        }
    }

    fun clearImageCacheInternal() {
        val cacheDir = reactApplicationContext.cacheDir
        val files = cacheDir.listFiles { file ->
            file.name.startsWith("clipboard_image_") && file.name.endsWith(".png")
        }

        files?.forEach { file -> file.delete() }
    }

    @ReactMethod
    fun saveBase64Files(contentUri: String, content: String, promise: Promise) {
        try {
            val trimmedUri = contentUri.trim()
            val uri = Uri.parse(trimmedUri)
            val fileMap = Json.decodeFromString<Map<String, String>>(content)
            val docFile = DocumentFile.fromTreeUri(reactApplicationContext, uri)
                ?: throw IllegalArgumentException("Invalid directory URI: $uri")

            for ((fileName, base64Data) in fileMap) {
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val uniqueName = getUniqueName(docFile, fileName)
                val newFile = docFile.createFile(
                    "application/octet-stream",
                    uniqueName
                ) ?: throw IOException("Failed to create file: $uniqueName")

                reactApplicationContext.contentResolver
                    .openOutputStream(newFile.uri)
                    .use { outputStream ->
                        if (outputStream == null) {
                            throw IOException("Failed to open output stream for: $uniqueName")
                        }
                        outputStream.write(decodedBytes)
                        outputStream.flush()
                    }
            }

            promise.resolve("Files saved successfully.")
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to save files: ${e.message}")
        }
    }

    private fun getUniqueName(dir: DocumentFile, originalName: String): String {
        val extensionIndex = originalName.lastIndexOf('.')
        val nameWithoutExt =
            if (extensionIndex > 0) originalName.substring(0, extensionIndex) else originalName
        val extension = if (extensionIndex > 0) originalName.substring(extensionIndex) else ""

        var candidate = originalName
        var counter = 1
        while (dir.findFile(candidate) != null) {
            candidate = "$nameWithoutExt($counter)$extension"
            counter++
        }
        return candidate
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun getFlagsSync(keys: ReadableArray): String {
        val list = mutableListOf<String>()
        for (i in 0 until keys.size()) {
            keys.getString(i)?.let { list.add(it) }
        }
        return asyncBridge.getValuesForKeys(list)
    }

    override fun invalidate() {
        asyncBridge.disconnect()
        super.invalidate()
    }
}
