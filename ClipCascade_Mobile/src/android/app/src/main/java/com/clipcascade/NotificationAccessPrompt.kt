package com.clipcascade

import android.app.AlertDialog
import android.content.Context
import android.content.Intent

object NotificationAccessPrompt {
    private fun setupComplete(context: Context): Boolean =
        SetupPermissionHelper.notificationPermissionGranted(context) &&
            SetupPermissionHelper.accessibilityEnabled(context) &&
            SetupPermissionHelper.notificationAccessEnabled(context) &&
            SetupPermissionHelper.unrestrictedBattery(context) &&
            RelaySettingsStore.backgroundOperationConfirmed(context)

    fun showIfNeeded(context: Context) {
        if (setupComplete(context)) return

        AlertDialog.Builder(context)
            .setTitle(R.string.setup_title)
            .setMessage(R.string.background_sharing_body)
            .setPositiveButton(R.string.setup_continue) { _, _ -> openSettings(context) }
            .setNegativeButton(R.string.setup_not_yet, null)
            .show()
    }

    fun openSettings(context: Context) {
        val intent = Intent(context, RelaySettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
