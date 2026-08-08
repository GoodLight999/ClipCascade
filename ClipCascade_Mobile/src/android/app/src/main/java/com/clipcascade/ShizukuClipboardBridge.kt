package com.clipcascade

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.clipcascade.shizuku.IShizukuClipboardService
import com.clipcascade.shizuku.ShizukuClipboardUserService
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the official Shizuku client lifecycle and one read-only UserService.
 *
 * Binder acquisition is handled by rikka.shizuku.ShizukuProvider, registered
 * in AndroidManifest.xml as required by the official Shizuku API guide. This
 * class does not discover manager packages or send manager-specific broadcasts.
 */
object ShizukuClipboardBridge {
    private const val REQUEST_PERMISSION_CODE = 5107
    private const val PER_USER_RANGE = 100_000

    // Shizuku uses UserServiceArgs.version to replace an already-running
    // UserService when its implementation changes. This must be independent of
    // the app versionCode: engineering APKs can keep the same product version
    // while the privileged service implementation changes between tests.
    // Previous builds passed BuildConfig.VERSION_CODE (30200). Any different
    // value forces that stale service to be destroyed and recreated.
    private const val USER_SERVICE_IMPLEMENTATION_VERSION = 4
    private const val USER_SERVICE_TAG = "clipcascade-clipboard-read-v3"

    private val initialized = AtomicBoolean(false)
    private val binding = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "clipcascade-shizuku-read").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val statusListeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var clientUserId: Int = 0

    @Volatile
    private var remoteService: IShizukuClipboardService? = null

    @Volatile
    private var lastError: String? = null

    data class Status(
        val binderAvailable: Boolean,
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
        val error: String? = null,
        val readCompletedAtElapsedNanos: Long? = null
    )

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            remoteService = IShizukuClipboardService.Stub.asInterface(binder)
            binding.set(false)
            lastError = null
            notifyStatusChanged()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            clearUserService("Shizuku UserService disconnected")
        }

        override fun onBindingDied(name: ComponentName) {
            clearUserService("Shizuku UserService binding died")
        }

        override fun onNullBinding(name: ComponentName) {
            clearUserService("Shizuku UserService returned a null binding")
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        lastError = null
        ensureBound()
        notifyStatusChanged()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        remoteService = null
        binding.set(false)
        lastError = "Shizuku Binder stopped"
        notifyStatusChanged()
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
            notifyStatusChanged()
        }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        // AOSP UserHandle.getUserId(uid) is uid / PER_USER_RANGE. This must be
        // captured in the client process; the UserService itself runs as shell
        // or root and therefore has a different process UID.
        clientUserId = Process.myUid() / PER_USER_RANGE
        if (!initialized.compareAndSet(false, true)) return

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        if (binderAvailable()) {
            ensureBound()
        }
        notifyStatusChanged()
    }

    fun addStatusListener(listener: () -> Unit) {
        statusListeners.add(listener)
        mainHandler.post(listener)
    }

    fun removeStatusListener(listener: () -> Unit) {
        statusListeners.remove(listener)
    }

    fun requestPermission(): Boolean {
        if (!binderAvailable()) {
            lastError = binderUnavailableMessage()
            notifyStatusChanged()
            return false
        }
        if (Shizuku.isPreV11()) {
            lastError = "Shizuku API 11 or newer is required"
            notifyStatusChanged()
            return false
        }

        return try {
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    ensureBound()
                    notifyStatusChanged()
                    true
                }
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    lastError = "Shizuku permission was denied with 'don't ask again'"
                    notifyStatusChanged()
                    false
                }
                else -> {
                    Shizuku.requestPermission(REQUEST_PERMISSION_CODE)
                    false
                }
            }
        } catch (error: Throwable) {
            lastError = "${error.javaClass.simpleName}: ${error.message ?: "permission request failed"}"
            notifyStatusChanged()
            false
        }
    }

    fun ensureBound(): Boolean {
        val context = appContext
        if (context == null) {
            lastError = "Shizuku bridge is not initialized"
            notifyStatusChanged()
            return false
        }
        if (remoteService?.asBinder()?.pingBinder() == true) return true
        if (binding.get()) return true
        if (!binderAvailable()) {
            lastError = binderUnavailableMessage()
            notifyStatusChanged()
            return false
        }
        if (Shizuku.isPreV11()) {
            lastError = "Shizuku API 11 or newer is required"
            notifyStatusChanged()
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
            notifyStatusChanged()
            return false
        }
        if (!binding.compareAndSet(false, true)) return true

        return try {
            Shizuku.bindUserService(userServiceArgs(context), userServiceConnection)
            notifyStatusChanged()
            true
        } catch (error: Throwable) {
            binding.set(false)
            lastError = "${error.javaClass.simpleName}: ${error.message ?: "UserService bind failed"}"
            notifyStatusChanged()
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

        val requestedUserId = clientUserId
        executor.execute {
            val result = try {
                val decoded = decodeResult(service.readClipboard(requestedUserId))
                if (decoded.success) {
                    decoded.copy(
                        readCompletedAtElapsedNanos = SystemClock.elapsedRealtimeNanos()
                    )
                } else {
                    decoded
                }
            } catch (error: Throwable) {
                clearUserService(
                    "${error.javaClass.simpleName}: ${error.message ?: "clipboard read failed"}"
                )
                CaptureResult(false, status = "error", error = lastError)
            }
            mainHandler.post { callback(result) }
        }
    }

    fun status(): Status {
        val binderAvailable = binderAvailable()
        val permissionGranted = if (binderAvailable) {
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
            !binderAvailable -> lastError ?: binderUnavailableMessage()
            !permissionGranted -> lastError ?: "Shizuku permission is not granted"
            binding.get() && !serviceBound -> lastError ?: "Shizuku UserService is binding"
            else -> lastError
        }

        return Status(
            binderAvailable = binderAvailable,
            permissionGranted = permissionGranted,
            serviceBound = serviceBound,
            serviceUid = serviceUid,
            lastError = effectiveError
        )
    }

    private fun binderAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    private fun binderUnavailableMessage(): String =
        "Shizuku Binder is unavailable. Start the installed compatible Shizuku server, then reopen or refresh ClipCascade."

    private fun clearUserService(error: String) {
        remoteService = null
        binding.set(false)
        lastError = error
        notifyStatusChanged()
    }

    private fun notifyStatusChanged() {
        statusListeners.forEach { listener ->
            mainHandler.post {
                try {
                    listener()
                } catch (_: Throwable) {
                    // A UI observer must not break the Shizuku lifecycle.
                }
            }
        }
    }

    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuClipboardUserService::class.java.name)
        )
            .daemon(false)
            .tag(USER_SERVICE_TAG)
            .processNameSuffix("shizuku_clipboard")
            .debuggable(BuildConfig.DEBUG)
            .version(USER_SERVICE_IMPLEMENTATION_VERSION)

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
