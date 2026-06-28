package com.clipcascade

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date

class RelaySettingsActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 4106
        private const val STATUS_REFRESH_MS = 1_000L
    }

    private lateinit var setupProgress: TextView
    private lateinit var setupDetails: TextView
    private lateinit var setupNextButton: Button
    private lateinit var backgroundConfirmationSwitch: Switch
    private lateinit var accessibilityStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var selectedAppsSummary: TextView
    private lateinit var otpTestStatus: TextView
    private lateinit var queueStatus: TextView
    private lateinit var healthStatus: TextView

    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRefresh = object : Runnable {
        override fun run() {
            updateStatus()
            statusHandler.postDelayed(this, STATUS_REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.relay_settings_title)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        content.addView(titleText(getString(R.string.background_sharing_title)))
        content.addView(bodyText(getString(R.string.background_sharing_body)))

        content.addView(sectionTitle(getString(R.string.setup_title)))
        content.addView(bodyText(getString(R.string.setup_body)))
        setupProgress = bodyText("")
        content.addView(setupProgress)
        setupDetails = bodyText("")
        content.addView(setupDetails)
        setupNextButton = Button(this).apply {
            setOnClickListener { continueSetup() }
        }
        content.addView(setupNextButton)
        content.addView(Button(this).apply {
            text = getString(R.string.setup_background_open)
            setOnClickListener { showBackgroundSettingsDialog() }
        })
        backgroundConfirmationSwitch = Switch(this).apply {
            text = getString(R.string.setup_background_confirm)
            isChecked = RelaySettingsStore.backgroundOperationConfirmed(
                this@RelaySettingsActivity,
            )
            setOnCheckedChangeListener { _, confirmed ->
                RelaySettingsStore.setBackgroundOperationConfirmed(
                    this@RelaySettingsActivity,
                    confirmed,
                )
                updateStatus()
            }
        }
        content.addView(backgroundConfirmationSwitch)
        content.addView(bodyText(getString(R.string.setup_manual_honesty)))

        content.addView(sectionTitle(getString(R.string.clipboard_section)))
        val clipboardSwitch = Switch(this).apply {
            text = getString(R.string.clipboard_enable)
            isChecked = RelaySettingsStore.clipboardEnabled(this@RelaySettingsActivity)
            setOnCheckedChangeListener { _, enabled ->
                RelaySettingsStore.setClipboardEnabled(this@RelaySettingsActivity, enabled)
                updateStatus()
            }
        }
        content.addView(clipboardSwitch)

        accessibilityStatus = bodyText("")
        content.addView(accessibilityStatus)
        content.addView(Button(this).apply {
            text = getString(R.string.accessibility_open)
            setOnClickListener {
                SetupPermissionHelper.openAccessibility(this@RelaySettingsActivity)
            }
        })
        content.addView(bodyText(getString(R.string.accessibility_explanation)))

        content.addView(sectionTitle(getString(R.string.otp_section)))
        val codeSwitch = Switch(this).apply {
            text = getString(R.string.otp_enable)
            isChecked = RelaySettingsStore.codeRelayEnabled(this@RelaySettingsActivity)
            setOnCheckedChangeListener { _, enabled ->
                RelaySettingsStore.setCodeRelayEnabled(this@RelaySettingsActivity, enabled)
                updateStatus()
            }
        }
        content.addView(codeSwitch)

        notificationStatus = bodyText("")
        content.addView(notificationStatus)
        content.addView(Button(this).apply {
            text = getString(R.string.notification_access_open)
            setOnClickListener {
                SetupPermissionHelper.openNotificationAccess(this@RelaySettingsActivity)
            }
        })
        content.addView(bodyText(getString(R.string.notification_explanation)))

        content.addView(sectionTitle(getString(R.string.otp_apps_section)))
        selectedAppsSummary = bodyText("")
        content.addView(selectedAppsSummary)
        content.addView(Button(this).apply {
            text = getString(R.string.otp_apps_choose)
            setOnClickListener { showAppPicker() }
        })
        content.addView(Button(this).apply {
            text = getString(R.string.otp_apps_reset)
            setOnClickListener {
                RelaySettingsStore.setSelectedApps(this@RelaySettingsActivity, emptySet())
                updateStatus()
            }
        })

        content.addView(sectionTitle(getString(R.string.otp_test_section)))
        content.addView(bodyText(getString(R.string.otp_test_body)))
        otpTestStatus = bodyText("")
        content.addView(otpTestStatus)
        content.addView(Button(this).apply {
            text = getString(R.string.otp_test_send)
            setOnClickListener { postOtpTestNotification() }
        })

        content.addView(sectionTitle(getString(R.string.clipboard_test_section)))
        content.addView(Button(this).apply {
            text = getString(R.string.clipboard_test_send)
            setOnClickListener { enqueueClipboardTest() }
        })

        content.addView(sectionTitle(getString(R.string.queue_section)))
        queueStatus = bodyText("")
        content.addView(queueStatus)
        content.addView(bodyText(getString(R.string.queue_body)))
        content.addView(Button(this).apply {
            text = getString(R.string.queue_clear)
            setOnClickListener {
                ClipboardRelayStore.clear(applicationContext)
                OtpRelayStore.clear(applicationContext)
                updateStatus()
                Toast.makeText(
                    this@RelaySettingsActivity,
                    getString(R.string.queue_cleared),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        })

        content.addView(sectionTitle(getString(R.string.health_section)))
        healthStatus = bodyText("")
        content.addView(healthStatus)
        content.addView(bodyText(getString(R.string.health_body)))
        content.addView(Button(this).apply {
            text = getString(R.string.health_clear)
            setOnClickListener {
                RelayHealthStore.clear(applicationContext)
                updateStatus()
                Toast.makeText(
                    this@RelaySettingsActivity,
                    getString(R.string.health_cleared),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        })

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        statusHandler.removeCallbacks(statusRefresh)
        statusHandler.post(statusRefresh)
    }

    override fun onPause() {
        statusHandler.removeCallbacks(statusRefresh)
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_POST_NOTIFICATIONS) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Toast.makeText(
                this,
                getString(R.string.setup_notification_permission_denied),
                Toast.LENGTH_LONG,
            ).show()
        }
        updateStatus()
    }

    private fun continueSetup() {
        when {
            !SetupPermissionHelper.notificationPermissionGranted(this) -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_POST_NOTIFICATIONS,
                    )
                }
            }
            !SetupPermissionHelper.accessibilityEnabled(this) ->
                SetupPermissionHelper.openAccessibility(this)
            !SetupPermissionHelper.notificationAccessEnabled(this) ->
                SetupPermissionHelper.openNotificationAccess(this)
            !SetupPermissionHelper.unrestrictedBattery(this) ->
                SetupPermissionHelper.openBatterySettings(this)
            !RelaySettingsStore.backgroundOperationConfirmed(this) ->
                showBackgroundSettingsDialog()
            else -> AlertDialog.Builder(this)
                .setTitle(R.string.setup_complete)
                .setMessage(R.string.setup_complete_message)
                .setPositiveButton(R.string.generic_ok, null)
                .show()
        }
    }

    private fun showBackgroundSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.setup_background_dialog_title)
            .setMessage(R.string.setup_background_dialog_message)
            .setPositiveButton(R.string.setup_open_settings) { _, _ ->
                SetupPermissionHelper.openAppDetails(this)
            }
            .setNegativeButton(R.string.setup_not_yet, null)
            .show()
    }

    private fun postOtpTestNotification() {
        val setupReady = RelaySettingsStore.codeRelayEnabled(this) &&
            SetupPermissionHelper.notificationPermissionGranted(this) &&
            SetupPermissionHelper.notificationAccessEnabled(this)
        if (!setupReady) {
            AlertDialog.Builder(this)
                .setTitle(R.string.otp_test_missing_setup_title)
                .setMessage(R.string.otp_test_missing_setup_message)
                .setPositiveButton(R.string.setup_continue) { _, _ -> continueSetup() }
                .setNegativeButton(R.string.generic_cancel, null)
                .show()
            return
        }

        try {
            val value = OtpTestNotificationManager.post(applicationContext)
            Toast.makeText(
                this,
                getString(R.string.otp_test_posted_toast, value),
                Toast.LENGTH_LONG,
            ).show()
        } catch (error: Exception) {
            val detail = error.javaClass.simpleName.ifBlank { "error" }
            OtpTestStatusStore.postFailed(applicationContext, detail)
            Toast.makeText(
                this,
                getString(R.string.otp_test_status_post_failed, detail),
                Toast.LENGTH_LONG,
            ).show()
        }
        updateStatus()
    }

    private fun enqueueClipboardTest() {
        if (!RelaySettingsStore.clipboardEnabled(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.clipboard_test_disabled_title)
                .setMessage(R.string.clipboard_test_disabled_message)
                .setPositiveButton(R.string.generic_ok, null)
                .show()
            return
        }

        val now = System.currentTimeMillis()
        val queued = ClipboardRelayStore.enqueue(
            applicationContext,
            ClipboardRelayStore.Item(
                id = "settings-test:$now",
                text = getString(R.string.clipboard_test_value),
                sourcePackage = packageName,
                createdAt = now,
            ),
        )
        if (queued) {
            ClipboardRelayDispatcher.schedule(applicationContext)
            RelayHealthStore.record(
                applicationContext,
                category = "clipboard",
                trigger = "settings_test",
                path = "manual_test",
                result = "queued",
            )
        }
        Toast.makeText(
            this,
            getString(
                if (queued) R.string.clipboard_test_queued
                else R.string.clipboard_test_duplicate,
            ),
            Toast.LENGTH_SHORT,
        ).show()
        updateStatus()
    }

    private fun updateStatus() {
        val setupSteps = listOf(
            SetupPermissionHelper.notificationPermissionGranted(this) to
                getString(R.string.setup_step_notifications),
            SetupPermissionHelper.accessibilityEnabled(this) to
                getString(R.string.setup_step_accessibility),
            SetupPermissionHelper.notificationAccessEnabled(this) to
                getString(R.string.setup_step_notification_access),
            SetupPermissionHelper.unrestrictedBattery(this) to
                getString(R.string.setup_step_battery),
            RelaySettingsStore.backgroundOperationConfirmed(this) to
                getString(R.string.setup_step_background),
        )
        val completed = setupSteps.count { it.first }
        setupProgress.text = getString(R.string.setup_progress, completed, setupSteps.size)
        setupDetails.text = setupSteps.joinToString("\n") { (ready, label) ->
            getString(
                if (ready) R.string.setup_line_ready else R.string.setup_line_missing,
                label,
            )
        }
        setupNextButton.text = getString(
            if (completed == setupSteps.size) R.string.setup_complete
            else R.string.setup_continue,
        )

        accessibilityStatus.text = getString(
            if (SetupPermissionHelper.accessibilityEnabled(this)) {
                R.string.accessibility_status_enabled
            } else {
                R.string.accessibility_status_disabled
            },
        )
        notificationStatus.text = getString(
            if (SetupPermissionHelper.notificationAccessEnabled(this)) {
                R.string.notification_status_enabled
            } else {
                R.string.notification_status_disabled
            },
        )

        val selected = RelaySettingsStore.selectedApps(this)
        selectedAppsSummary.text = if (selected.isEmpty()) {
            getString(R.string.otp_apps_all)
        } else {
            getString(R.string.otp_apps_selected, selected.size)
        }

        otpTestStatus.text = formatOtpTestStatus(OtpTestStatusStore.read(this))
        queueStatus.text = getString(
            R.string.queue_summary,
            ClipboardRelayStore.count(this),
            OtpRelayStore.pendingCount(this),
        )
        healthStatus.text = listOf(
            formatHealth(
                getString(R.string.health_clipboard),
                RelayHealthStore.read(this, "clipboard"),
            ),
            formatHealth(
                getString(R.string.health_verification),
                RelayHealthStore.read(this, "verification"),
            ),
            formatHealth(
                getString(R.string.health_recovery),
                RelayHealthStore.read(this, "recovery"),
            ),
        ).joinToString("\n")
    }

    private fun formatOtpTestStatus(snapshot: OtpTestStatusStore.Snapshot?): String {
        if (snapshot == null) return getString(R.string.otp_test_status_none)
        return when (snapshot.state) {
            OtpTestStatusStore.POSTED ->
                getString(R.string.otp_test_status_posted, snapshot.value)
            OtpTestStatusStore.DETECTED ->
                getString(R.string.otp_test_status_detected, snapshot.value)
            OtpTestStatusStore.QUEUED ->
                getString(R.string.otp_test_status_queued, snapshot.value)
            OtpTestStatusStore.ACKNOWLEDGED ->
                getString(R.string.otp_test_status_acknowledged, snapshot.value)
            OtpTestStatusStore.EXTRACT_FAILED ->
                getString(R.string.otp_test_status_extract_failed)
            OtpTestStatusStore.DEDUPLICATED ->
                getString(R.string.otp_test_status_deduplicated)
            OtpTestStatusStore.POST_FAILED ->
                getString(R.string.otp_test_status_post_failed, snapshot.value)
            else -> getString(R.string.otp_test_status_none)
        }
    }

    private fun formatHealth(label: String, snapshot: RelayHealthStore.Snapshot?): String {
        if (snapshot == null) return getString(R.string.health_none, label)
        val time = DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.MEDIUM,
        ).format(Date(snapshot.timestamp))
        return getString(
            R.string.health_format,
            label,
            time,
            healthLabel(snapshot.trigger),
            healthLabel(snapshot.path),
            healthLabel(snapshot.result),
        )
    }

    private fun healthLabel(value: String): String {
        val resource = when (value) {
            "service_connected" -> R.string.health_service_connected
            "service_interrupted" -> R.string.health_service_interrupted
            "selection" -> R.string.health_selection
            "click" -> R.string.health_click
            "announcement" -> R.string.health_announcement
            "copy_notice" -> R.string.health_copy_notice
            "ctrl_c" -> R.string.health_ctrl_c
            "settings_test" -> R.string.health_settings_test
            "listener_connected" -> R.string.health_listener_connected
            "listener_disconnected" -> R.string.health_listener_disconnected
            "context_match" -> R.string.health_context_match
            "accessibility" -> R.string.health_accessibility
            "accessibility_selection" -> R.string.health_accessibility_selection
            "clipboard_manager" -> R.string.health_clipboard_manager
            "selected_text_fallback" -> R.string.health_selected_text_fallback
            "clipboard_denied_no_fallback" -> R.string.health_clipboard_denied_no_fallback
            "no_text_available" -> R.string.health_no_text_available
            "notification_access" -> R.string.health_notification_access
            "local_extractor" -> R.string.health_local_extractor
            "coordinator" -> R.string.health_coordinator
            "active_react_context" -> R.string.health_active_react_context
            "headless_js" -> R.string.health_headless_js
            "manual_test" -> R.string.health_manual_test
            "ready" -> R.string.health_ready
            "remembered" -> R.string.health_remembered
            "queued" -> R.string.health_queued
            "deduplicated" -> R.string.health_deduplicated
            "retrying" -> R.string.health_retrying
            "interrupted" -> R.string.health_interrupted
            "rebind_requested" -> R.string.health_rebind_requested
            "requested" -> R.string.health_requested
            "declined" -> R.string.health_declined
            "blocked" -> R.string.health_blocked
            "cooldown" -> R.string.health_cooldown
            "sync_disabled" -> R.string.health_sync_disabled
            "heartbeat_timeout" -> R.string.health_heartbeat_timeout
            "network_available_but_offline" ->
                R.string.health_network_available_but_offline
            "test_notification" -> R.string.health_test_notification
            "test_extracted" -> R.string.health_test_extracted
            "test_acknowledged" -> R.string.health_test_acknowledged
            else -> null
        }
        return resource?.let(::getString) ?: value.ifBlank { "—" }
    }

    @Suppress("DEPRECATION")
    private fun showAppPicker() {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries = packageManager.queryIntentActivities(launchIntent, 0)
            .map {
                Pair(
                    it.activityInfo.packageName,
                    it.loadLabel(packageManager).toString(),
                )
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }

        val packages = entries.map { it.first }
        val labels = entries.map { "${it.second}\n${it.first}" }.toTypedArray()
        val selected = RelaySettingsStore.selectedApps(this).toMutableSet()
        val checked = BooleanArray(packages.size) { selected.contains(packages[it]) }

        AlertDialog.Builder(this)
            .setTitle(R.string.otp_apps_picker_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (isChecked) selected += packages[which] else selected -= packages[which]
            }
            .setPositiveButton(R.string.generic_save) { _, _ ->
                RelaySettingsStore.setSelectedApps(this, selected)
                updateStatus()
            }
            .setNegativeButton(R.string.generic_cancel, null)
            .show()
    }

    private fun titleText(value: String) = TextView(this).apply {
        text = value
        textSize = 24f
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, 0, 0, dp(12))
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 19f
        setPadding(0, dp(24), 0, dp(8))
    }

    private fun bodyText(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
