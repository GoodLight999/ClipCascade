package com.clipcascade

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object NotificationAccessPrompt {
    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun showIfNeeded(context: Context) {
        if (isEnabled(context)) return

        AlertDialog.Builder(context)
            .setTitle("Enable notification code relay")
            .setMessage(
                "ClipCascade can detect verification codes from notifications while the screen is off. " +
                    "Android requires you to explicitly grant Notification access. " +
                    "Only the extracted short code is sent through your existing encrypted connection.",
            )
            .setPositiveButton("Open settings") { _, _ -> openSettings(context) }
            .setNegativeButton("Later", null)
            .show()
    }

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
