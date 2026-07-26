package com.clipcascade

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.clipcascade.shizuku.IShizukuClipboardService
import com.clipcascade.shizuku.ShizukuClipboardUserService
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-process owner for the official Shizuku binder and one read-only
 * UserService. It never owns transport or stores clipboard payloads.
 */
object ShizukuClipboardBridge {
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
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
            lastError = "Shizuku clipboard service disconnected"
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        lastError = null
        ensureBound()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        remoteService = null
        binding.set(false)
        lastError = "Shizuku stopped"
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_PERMISSION_CODE) return@OnRequestPermissionResultListener
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
    }

    fun requestPermission(): Boolean {
        if (!Shizuku.pingBinder()) {
            lastError = "Shizuku is not running"
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
                    lastError = "Shizuku permission was permanently denied"
                    false
                }
                else -> {
                    Shizuku.requestPermission(REQUEST_PERMISSION_CODE)
                    false
                }
            }
        } catch (error: Throwable) {
            lastError = error.javaClass.simpleName
            false
        }
    }

    fun ensureBound(): Boolean {
        val context = appContext ?: return false
        if (remoteService?.asBinder()?.pingBinder() == true) return true
        if (!Shizuku.pingBinder() || Shizuku.isPreV11()) return false

        val permissionGranted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
        if (!permissionGranted || !binding.compareAndSet(false, true)) return false

        return try {
            Shizuku.bindUserService(userServiceArgs(context), userServiceConnection)
            true
        } catch (error: Throwable) {
            binding.set(false)
            lastError = error.javaClass.simpleName
            false
        }
    }

    fun readClipboard(callback: (CaptureResult) -> Unit) {
        if (!ensureBound()) {
            mainHandler.post {
                callback(CaptureResult(false, status = "unavailable", error = lastError))
            }
            return
        }

        val service = remoteService
        if (service == null) {
            mainHandler.post {
                callback(CaptureResult(false, status = "binding", error = "Shizuku service is binding"))
            }
            return
        }

        executor.execute {
            val result = try {
                decodeResult(service.readClipboard())
            } catch (error: Throwable) {
                remoteService = null
                binding.set(false)
                lastError = error.javaClass.simpleName
                CaptureResult(false, status = "error", error = lastError)
            }
            mainHandler.post { callback(result) }
        }
    }

    fun status(): Status {
        val context = appContext
        val installed = context?.let(::isInstalled) ?: false
        val binderAlive = Shizuku.pingBinder()
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

        return Status(installed, binderAlive, permissionGranted, serviceBound, serviceUid, lastError)
    }

    fun openShizuku(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        return try {
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://shizuku.rikka.app/download/")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            true
        } catch (error: Throwable) {
            lastError = error.javaClass.simpleName
            false
        }
    }

    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuClipboardUserService::class.java.name)
        )
            .daemon(false)
            .tag("clipcascade-clipboard-read-v1")
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

    private fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
