package com.clipcascade.testnotifier

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class TestNotificationActivity : Activity() {
    companion object {
        private const val CHANNEL_ID = "clipcascade_external_listener_test"
        private const val NOTIFICATION_TAG = "clipcascade-external-listener-test"
        private const val NOTIFICATION_ID = 4114
        private const val REQUEST_NOTIFICATIONS = 4115
        private const val EXTRA_CODE = "verification_code"
        private val CODE_PATTERN = Regex("^[0-9]{6}$")
    }

    private var pendingCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCode = intent?.getStringExtra(EXTRA_CODE).orEmpty()
        if (!CODE_PATTERN.matches(pendingCode)) {
            finish()
            return
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS,
            )
            return
        }
        postExternalNotification()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (
            requestCode == REQUEST_NOTIFICATIONS &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            postExternalNotification()
        } else {
            Toast.makeText(
                this,
                "Notification permission is required for the listener-path test.",
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }

    private fun postExternalNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "ClipCascade external listener tests",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "External notifications used to validate ClipCascade NotificationListenerService."
                    setShowBadge(false)
                },
            )
        }
        val text = "Your verification code is $pendingCode. This code expires in 10 minutes."
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("ClipCascade external listener test")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setTimeoutAfter(5 * 60_000L)
            .build()
        manager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
        finish()
    }
}
