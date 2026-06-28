package com.clipcascade

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import java.security.SecureRandom

object OtpTestNotificationManager {
    const val EXTRA_SYNTHETIC_TEST = "com.clipcascade.extra.SYNTHETIC_VERIFICATION_TEST"
    const val EXTRA_EXPECTED_VALUE = "com.clipcascade.extra.SYNTHETIC_EXPECTED_VALUE"

    private const val CHANNEL_ID = "clipcascade_verification_test"
    private const val NOTIFICATION_TAG = "clipcascade-verification-test"
    private const val NOTIFICATION_ID = 4107
    private val random = SecureRandom()

    fun isSyntheticTest(notification: Notification): Boolean =
        notification.extras?.getBoolean(EXTRA_SYNTHETIC_TEST, false) == true

    fun expectedValue(notification: Notification): String =
        notification.extras?.getString(EXTRA_EXPECTED_VALUE).orEmpty()

    fun post(context: Context): String {
        val applicationContext = context.applicationContext
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        ensureChannel(applicationContext, manager)

        val value = random.nextInt(1_000_000).toString().padStart(6, '0')
        OtpTestStatusStore.start(applicationContext, value)

        val text = applicationContext.getString(
            R.string.otp_test_notification_text,
            value,
        )
        val extras = Bundle().apply {
            putBoolean(EXTRA_SYNTHETIC_TEST, true)
            putString(EXTRA_EXPECTED_VALUE, value)
        }
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(applicationContext.getString(R.string.otp_test_notification_title))
            .setContentText(text)
            .setSubText(applicationContext.getString(R.string.otp_test_notification_subtext))
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .addExtras(extras)
            .build()

        manager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
        return value
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.otp_test_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.otp_test_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
