package com.clipcascade

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native setup screen available even when React Native is not active.
 * It configures capture paths and exposes payload-free diagnostics, but never
 * owns network transport.
 */
class BackgroundSetupActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.background_setup_title)
        ShizukuClipboardBridge.initialize(this)

        val scrollView = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        scrollView.addView(content)

        content.addView(TextView(this).apply {
            text = getString(R.string.background_setup_intro)
            textSize = 16f
        }, blockLayoutParams())

        statusView = TextView(this).apply {
            textSize = 16f
            setTextIsSelectable(true)
        }
        content.addView(statusView, blockLayoutParams())

        content.addView(actionButton(R.string.open_shizuku) {
            if (!ShizukuClipboardBridge.openShizuku(this)) {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
            }
        }, blockLayoutParams())

        content.addView(actionButton(R.string.request_shizuku_permission) {
            ShizukuClipboardBridge.requestPermission()
            handler.postDelayed(::refreshStatus, 750)
        }, blockLayoutParams())

        content.addView(actionButton(R.string.test_shizuku_read) {
            testShizukuRead()
        }, blockLayoutParams())

        content.addView(actionButton(R.string.open_accessibility_settings) {
            openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, blockLayoutParams())

        content.addView(actionButton(R.string.open_overlay_settings) {
            openSettings(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }, blockLayoutParams())

        content.addView(actionButton(R.string.open_battery_settings) {
            openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, blockLayoutParams())

        content.addView(actionButton(R.string.copy_adb_fallback) {
            copyAdbFallbackCommands()
        }, blockLayoutParams())

        content.addView(actionButton(R.string.refresh_status) {
            refreshStatus()
        }, blockLayoutParams())

        content.addView(actionButton(R.string.reset_capture_diagnostics) {
            CaptureDiagnostics.reset()
            refreshStatus()
        }, blockLayoutParams())

        content.addView(actionButton(R.string.open_clipcascade) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }, blockLayoutParams())

        content.addView(TextView(this).apply {
            text = getString(R.string.background_setup_safety_note)
            textSize = 14f
        }, blockLayoutParams())

        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        ShizukuClipboardBridge.ensureBound()
        refreshStatus()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun refreshStatus() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)
        val readLogsEnabled = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_LOGS
        ) == PackageManager.PERMISSION_GRANTED
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryExempt = powerManager.isIgnoringBatteryOptimizations(packageName)
        val runtimeActive = ClipboardListenerModule.isRuntimeActive()
        val shizuku = ShizukuClipboardBridge.status()
        val diagnostics = CaptureDiagnostics.snapshot()

        val capabilityStatus = getString(
            R.string.background_setup_status,
            enabledLabel(shizuku.installed),
            enabledLabel(shizuku.binderAlive),
            enabledLabel(shizuku.permissionGranted),
            if (shizuku.serviceBound) {
                getString(R.string.status_bound_uid, shizuku.serviceUid ?: -1)
            } else {
                getString(R.string.status_not_bound)
            },
            enabledLabel(accessibilityEnabled),
            enabledLabel(overlayEnabled),
            enabledLabel(readLogsEnabled),
            enabledLabel(batteryExempt),
            enabledLabel(runtimeActive),
            shizuku.lastError ?: getString(R.string.status_none)
        )

        val lastEvent = diagnostics.lastEventAt?.let { timestamp ->
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(Date(timestamp))
        } ?: getString(R.string.status_none)
        val diagnosticsStatus = getString(
            R.string.capture_diagnostics_status,
            diagnostics.triggerCount,
            diagnostics.coalescedTriggerCount,
            diagnostics.shizukuAttemptCount,
            diagnostics.shizukuSuccessCount,
            diagnostics.overlayFallbackCount,
            diagnostics.emittedCount,
            diagnostics.duplicateSuppressedCount,
            diagnostics.ignoredCount,
            diagnostics.lastSource ?: getString(R.string.status_none),
            diagnostics.lastStage ?: getString(R.string.status_none),
            diagnostics.lastError ?: getString(R.string.status_none),
            lastEvent
        )

        statusView.text = "$capabilityStatus\n\n$diagnosticsStatus"
    }

    private fun testShizukuRead() {
        val source = "manual_shizuku_test"
        CaptureDiagnostics.recordTrigger(source)
        CaptureDiagnostics.recordShizukuAttempt(source)
        Toast.makeText(this, R.string.shizuku_test_started, Toast.LENGTH_SHORT).show()
        ShizukuClipboardBridge.readClipboard { result ->
            val message = if (result.success && result.content != null) {
                CaptureDiagnostics.recordShizukuSuccess(source)
                getString(
                    R.string.shizuku_test_success,
                    result.type ?: "unknown",
                    result.content.length
                )
            } else {
                CaptureDiagnostics.recordIgnored(
                    source,
                    result.error ?: result.status
                )
                getString(
                    R.string.shizuku_test_failure,
                    result.error ?: result.status
                )
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            refreshStatus()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, ClipCascadeAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        return enabledServices
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }
    }

    private fun enabledLabel(enabled: Boolean): String =
        getString(if (enabled) R.string.status_enabled else R.string.status_disabled)

    private fun copyAdbFallbackCommands() {
        val commands = """adb -d shell pm grant $packageName android.permission.READ_LOGS
adb -d shell appops set $packageName SYSTEM_ALERT_WINDOW allow
adb -d shell am force-stop $packageName"""
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ClipCascade ADB fallback", commands))
        Toast.makeText(this, R.string.adb_commands_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun actionButton(labelRes: Int, action: () -> Unit): Button =
        Button(this).apply {
            setText(labelRes)
            setOnClickListener { action() }
            isAllCaps = false
        }

    private fun blockLayoutParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
