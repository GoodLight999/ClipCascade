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

/**
 * Native setup screen that remains usable even when the React Native UI or
 * foreground service is not running. It only reports capability state and
 * opens Android's own settings screens; it does not own clipboard transport.
 */
class BackgroundSetupActivity : AppCompatActivity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.background_setup_title)

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
        refreshStatus()
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

        statusView.text = getString(
            R.string.background_setup_status,
            enabledLabel(accessibilityEnabled),
            enabledLabel(overlayEnabled),
            enabledLabel(readLogsEnabled),
            enabledLabel(batteryExempt),
            enabledLabel(runtimeActive)
        )
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
