package com.clipcascade

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object NotificationAccessPrompt {
    private fun notificationAccessEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    private fun accessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ClipboardAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun showIfNeeded(context: Context) {
        if (notificationAccessEnabled(context) && accessibilityEnabled(context)) return

        AlertDialog.Builder(context)
            .setTitle("バックグラウンド共有の設定")
            .setMessage(
                "ADB不要のクリップボード共有にはユーザー補助を、" +
                    "SMS・メール等の認証コード共有には通知アクセスを有効にしてください。",
            )
            .setPositiveButton("共有設定を開く") { _, _ -> openSettings(context) }
            .setNegativeButton("あとで", null)
            .show()
    }

    fun openSettings(context: Context) {
        val intent = Intent(context, RelaySettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
