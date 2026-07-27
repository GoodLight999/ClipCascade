package com.clipcascade

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.clipcascade.shizuku.IShizukuClipboardService
import com.clipcascade.shizuku.ShizukuClipboardUserService
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * App-process owner for the Shizuku-compatible binder and one read-only
 * UserService. It never owns transport or stores clipboard payloads.
 *
 * Manager discovery is protocol-based. The official manager and compatible
 * forks expose rikka.shizuku.intent.action.REQUEST_BINDER; product behavior
 * must not depend on one manager package name or download site.
 */
object ShizukuClipboardBridge {
    private const val OFFICIAL_SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val ACTION_REQUEST_BINDER = "rikka.shizuku.intent.action.REQUEST_BINDER"
    private const val RECOVERY_SETUP_URL =
        "https://github.com/GoodLight999/Trial-and-Error-ClipCascade/blob/stability-recovery/docs/ANDROID_SETUP.md#shizuku"
    private const val REQUEST_PERMISSION_CODE = 5107
    private const val REPROBE_THROTTLE_MS = 750L

    private val initialized = AtomicBoolean(false)
    private val binding = AtomicBoolean(false)
    private val lastBinderRequestAt = AtomicLong(0L)
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

    private data class ManagerCandidate(
        val packageName: String,
        val label: String,
        val versionName: String?
    )

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
        lastError = "Shizuku binder stopped"
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

        // Do not wait forever for a passive process-start delivery. Official
        // Shizuku and compatible forks expose the same binder-request action.
        reprobeBinder(force = true)
    }

    /**
     * Explicitly asks every visible Shizuku-compatible manager to resend its
     * binder. This is safe when the server is stopped: no privileged action is
     * performed, and the existing overlay path remains the fallback.
     */
    fun reprobeBinder(force: Boolean = false): Int {
        val context = appContext ?: return 0
        if (Shizuku.pingBinder()) {
            lastError = null
            ensureBound()
            return 0
        }

        val now = System.currentTimeMillis()
        val previous = lastBinderRequestAt.get()
        if (!force && now - previous < REPROBE_THROTTLE_MS) return 0
        if (!lastBinderRequestAt.compareAndSet(previous, now) && !force) return 0
        if (force) lastBinderRequestAt.set(now)

        val managers = discoverManagers(context)
        var sent = 0
        managers.forEach { manager ->
            try {
                context.sendBroadcast(
                    Intent(ACTION_REQUEST_BINDER)
                        .setPackage(manager.packageName)
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                )
                sent += 1
            } catch (_: Throwable) {
                // Continue probing other compatible managers.
            }
        }

        lastError = when {
            sent > 0 -> {
                val target = managers.joinToString { managerDisplayName(it) }
                "Binder requested from $target; confirm its server is running and ClipCascade is allowed"
            }
            managers.isNotEmpty() ->
                "Compatible manager found, but its binder request receiver is unavailable"
            else ->
                "No Shizuku-compatible manager receiver was found"
        }

        mainHandler.postDelayed({
            if (Shizuku.pingBinder()) {
                lastError = null
                ensureBound()
            }
        }, 500L)

        return sent
    }

    fun requestPermission(): Boolean {
        if (!Shizuku.pingBinder()) {
            reprobeBinder(force = true)
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
        if (!Shizuku.pingBinder()) {
            reprobeBinder()
            return false
        }
        if (Shizuku.isPreV11()) {
            lastError = "Shizuku API 11 or newer is required"
            return false
        }

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
                callback(CaptureResult(false, status = "unavailable", error = status().lastError))
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
        val managers = context?.let(::discoverManagers).orEmpty()
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

        val effectiveError = when {
            binderAlive -> lastError
            lastError != null -> lastError
            managers.isNotEmpty() ->
                "Binder not received from ${managers.joinToString { managerDisplayName(it) }}"
            else -> "No Shizuku-compatible manager receiver was found"
        }

        return Status(
            installed = managers.isNotEmpty(),
            binderAlive = binderAlive,
            permissionGranted = permissionGranted,
            serviceBound = serviceBound,
            serviceUid = serviceUid,
            lastError = effectiveError
        )
    }

    fun openShizuku(context: Context): Boolean {
        val managers = discoverManagers(context)
        val candidate = managers.firstOrNull { manager ->
            context.packageManager.getLaunchIntentForPackage(manager.packageName) != null
        }
        val launchIntent = candidate?.let { manager ->
            context.packageManager.getLaunchIntentForPackage(manager.packageName)
        }

        return try {
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                // Keep the product UI on the recovery project's setup guide. It
                // explains official and forked managers without forcing one URL.
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(RECOVERY_SETUP_URL))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    private fun discoverManagers(context: Context): List<ManagerCandidate> {
        val packageManager = context.packageManager
        val packages = linkedSetOf<String>()

        queryBinderReceivers(packageManager).forEach { resolveInfo ->
            resolveInfo.activityInfo?.packageName?.let(packages::add)
        }

        // The package fallback preserves detection when a manager deliberately
        // hides its receiver from package queries, as some forks can do.
        if (packageExists(packageManager, OFFICIAL_SHIZUKU_PACKAGE)) {
            packages.add(OFFICIAL_SHIZUKU_PACKAGE)
        }

        queryLauncherActivities(packageManager).forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
            val label = resolveInfo.loadLabel(packageManager)?.toString().orEmpty()
            val identity = "$packageName $label".lowercase(Locale.ROOT)
            if (identity.contains("shizuku") || identity.contains("nightzuku")) {
                packages.add(packageName)
            }
        }

        return packages.mapNotNull { packageName ->
            managerCandidate(packageManager, packageName)
        }
    }

    @Suppress("DEPRECATION")
    private fun queryBinderReceivers(packageManager: PackageManager): List<ResolveInfo> {
        val intent = Intent(ACTION_REQUEST_BINDER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryBroadcastReceivers(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL)
        }
    }

    @Suppress("DEPRECATION")
    private fun queryLauncherActivities(packageManager: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
    }

    @Suppress("DEPRECATION")
    private fun managerCandidate(
        packageManager: PackageManager,
        packageName: String
    ): ManagerCandidate? = try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }
        val applicationInfo = packageInfo.applicationInfo ?: return null
        ManagerCandidate(
            packageName = packageName,
            label = packageManager.getApplicationLabel(applicationInfo).toString(),
            versionName = packageInfo.versionName
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun packageExists(packageManager: PackageManager, packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun managerDisplayName(candidate: ManagerCandidate): String = buildString {
        append(candidate.label.ifBlank { candidate.packageName })
        candidate.versionName?.takeIf { it.isNotBlank() }?.let { version ->
            append(" ")
            append(version)
        }
        append(" [")
        append(candidate.packageName)
        append("]")
    }
}
