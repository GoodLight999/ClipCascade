package com.clipcascade

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import com.clipcascade.shizuku.IShizukuClipboardService
import com.clipcascade.shizuku.ShizukuClipboardUserService
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the official Shizuku client lifecycle and one read-only UserService.
 *
 * Binder acquisition is handled by rikka.shizuku.ShizukuProvider, registered
 * in AndroidManifest.xml as required by the official Shizuku API guide. This
 * class does not discover manager packages or send private manager broadcasts.
 */
object ShizukuClipboardBridge {
    private const val REQUEST_PERMISSION_CODE = 5107

    private val initialized = AtomicBoolean(false)
    private val binding = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "clipcascade-shizuku-read").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var remoteService: IShizukuClipboardService? = null

    @Volatile
    private var lastError: String? = null

    data class Status(
        val installed: Boolean,
        val binderAlive: Boolean,
        val permissionGranted: Boolean,
        val serviceBound: Boolean,
        val serviceUid: Int?,
        val lastError: String?
    )

    data class CaptureResult(
        val success: Boolean,
        val content: String? = null,
        val type: String? = null,
        val status: String,
        val error: String? = null
    )

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            remoteService = IShizukuClipboardService.Stub.asInterface(binder)
            binding.set(false)
            lastError = null
        }

        override fun onServiceDisconnected(name: ComponentName) {
            remoteService = null
            binding.set(false)
            lastError = "Shizuku UserService disconnected"
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        lastError = null
        ensureBound()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        remoteService = null
        binding.set(false)
        lastError = "Shizuku Binder stopped"
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_PERMISSION_CODE) {
                return@OnRequestPermissionResultListener
            }
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                lastError = null
                ensureBound()
            } else {
                lastError = "Shizuku permission denied"
            }
        }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (!initialized.compareAndSet(false, true)) return

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        if (binderAlive()) {
            ensureBound()
        }
    }

    fun requestPermission(): Boolean {
        if (!binderAlive()) {
            lastError = binderUnavailableMessage()
            return false
        }
        if (Shizuku.isPreV11()) {
            lastError = "Shizuku API 11 or newer is required"
            return false
        }

        return try {
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    ensureBound()
                    true
                }
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    lastError = "Shizuku permission was denied with 'don't ask again'"
                    false
                }
                else -> {
                    Shizuku.requestPermission(REQUEST_PERMISSION_CODE)
                    false
                }
            }
        } catch (error: Throwable) {
            lastError = "${error.javaClass.simpleName}: ${error.message ?: "permission request failed"}"
            false
        }
    }

    fun ensureBound(): Boolean {
        val context = appContext ?: return false
        if (remoteService?.asBinder()?.pingBinder() == true) return true
        if (!binderAlive()) {
            lastError = binderUnavailableMessage()
            return false
        }
        if (Shizuku.isPreV11()) {
            lastError = "Shizuku API 11 or newer is required"
            return false
        }

        val permissionGranted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (error: Throwable) {
            lastError = "${error.javaClass.simpleName}: ${error.message ?: "permission check failed"}"
            false
        }
        if (!permissionGranted) {
            if (lastError == null) lastError = "Shizuku permission is not granted"
            return false
        }
        if (!binding.compareAndSet(false, true)) return false

        return try {
            Shizuku.bindUserService(userServiceArgs(context), userServiceConnection)
            true
        } catch (error: Throwable) {
            binding.set(false)
            lastError = "${error.javaClass.simpleName}: ${error.message ?: "UserService bind failed"}"
            false
        }
    }

    fun readClipboard(callback: (CaptureResult) -> Unit) {
        if (!ensureBound()) {
            mainHandler.post {
                callback(
                    CaptureResult(
                        success = false,
                        status = "unavailable",
                        error = status().lastError
                    )
                )
            }
            return
        }

        val service = remoteService
        if (service == null) {
            mainHandler.post {
                callback(
                    CaptureResult(
                        success = false,
                        status = "binding",
                        error = "Shizuku UserService is binding"
                    )
                )
            }
            return
        }

        executor.execute {
            val result = try {
                decodeResult(service.readClipboard())
            } catch (error: Throwable) {
                remoteService = null
                binding.set(false)
                lastError = "${error.javaClass.simpleName}: ${error.message ?: "clipboard read failed"}"
                CaptureResult(false, status = "error", error = lastError)
            }
            mainHandler.post { callback(result) }
        }
    }

    fun openShizuku(context: Context): Boolean {
        return try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            lastError = "Open the installed Shizuku-compatible manager and start its server"
            true
        } catch (error: Throwable) {
            lastError = "${error.javaClass.simpleName}: ${error.message ?: "application settings unavailable"}"
            false
        }
    }

    fun status(): Status {
        val binderAlive = binderAlive()
        val permissionGranted = if (binderAlive) {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                false
            }
        } else {
            false
        }
        val service = remoteService
        val serviceBound = service?.asBinder()?.pingBinder() == true
        val serviceUid = if (serviceBound) {
            try {
                service?.serviceUid
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        val effectiveError = when {
            !binderAlive -> lastError ?: binderUnavailableMessage()
            !permissionGranted -> lastError ?: "Shizuku permission is not granted"
            binding.get() && !serviceBound -> lastError ?: "Shizuku UserService is binding"
            else -> lastError
        }

        return Status(
            // There is no official package-discovery API. Binder availability is
            // the only manager-independent capability signal, including forks.
            installed = binderAlive,
            binderAlive = binderAlive,
            permissionGranted = permissionGranted,
            serviceBound = serviceBound,
            serviceUid = serviceUid,
            lastError = effectiveError
        )
    }

    private fun binderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    private fun binderUnavailableMessage(): String =
        "Shizuku Binder is unavailable. Start the Shizuku-compatible server, then reopen or refresh ClipCascade."

    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuClipboardUserService::class.java.name)
        )
            .daemon(false)
            .tag("clipcascade-clipboard-read-v2")
            .processNameSuffix("shizuku_clipboard")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

    private fun decodeResult(json: String): CaptureResult {
        val data = JSONObject(json)
        val status = data.optString("status", "error")
        return if (status == "ok") {
            CaptureResult(
                success = true,
                content = data.getString("content"),
                type = data.getString("type"),
                status = status
            )
        } else {
            CaptureResult(
                success = false,
                status = status,
                error = data.optString("message").takeIf { it.isNotBlank() }
                    ?: data.optString("error").takeIf { it.isNotBlank() }
            )
        }
    }
}
