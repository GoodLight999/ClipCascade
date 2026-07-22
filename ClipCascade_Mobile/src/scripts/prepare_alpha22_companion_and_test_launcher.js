const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

const root = path.resolve(__dirname, '..');
const javaDir = path.join(root, 'android', 'app', 'src', 'main', 'java', 'com', 'clipcascade');
const testDir = path.join(root, 'android', 'app', 'src', 'test', 'java', 'com', 'clipcascade');
const manifestPath = path.join(root, 'android', 'app', 'src', 'main', 'AndroidManifest.xml');

const companionHelperSource = String.raw`package com.clipcascade

import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build

/** Establishes the CompanionDeviceManager trust boundary required for OTP content on Android 15+. */
object CompanionAssociationHelper {
    const val REQUEST_ASSOCIATION = 4113
    private const val PREFS = "companion_association"
    private const val KEY_LAST_ERROR = "last_error"

    fun requiredForUnredactedOtp(): Boolean = Build.VERSION.SDK_INT >= 35

    fun supported(context: Context): Boolean = Build.VERSION.SDK_INT >= 33 &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)

    fun isAssociated(context: Context): Boolean {
        if (!requiredForUnredactedOtp()) return true
        if (!supported(context)) return false
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return try {
            manager.myAssociations.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun lastError(context: Context): String = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_LAST_ERROR, "")
        .orEmpty()

    fun requestAssociation(activity: Activity, onChanged: () -> Unit) {
        if (!requiredForUnredactedOtp()) {
            onChanged()
            return
        }
        if (!supported(activity)) {
            saveError(activity, "companion_setup_unavailable")
            onChanged()
            return
        }
        val manager = activity.getSystemService(CompanionDeviceManager::class.java)
            ?: run {
                saveError(activity, "companion_manager_unavailable")
                onChanged()
                return
            }
        val request = AssociationRequest.Builder()
            .setSelfManaged(true)
            .setDisplayName(activity.getString(R.string.companion_device_display_name))
            .setForceConfirmation(true)
            .build()
        manager.associate(
            request,
            activity.mainExecutor,
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    activity.startIntentSenderForResult(
                        intentSender,
                        REQUEST_ASSOCIATION,
                        null,
                        0,
                        0,
                        0,
                    )
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    saveError(activity, "")
                    activity.runOnUiThread {
                        requestNotificationAccess(activity)
                        onChanged()
                    }
                }

                override fun onFailure(errorMessage: CharSequence?) {
                    saveError(activity, errorMessage?.toString().orEmpty().ifBlank { "association_failed" })
                    activity.runOnUiThread { onChanged() }
                }
            },
        )
    }

    fun requestNotificationAccess(activity: Activity) {
        val component = ComponentName(activity, NotificationCodeListenerService::class.java)
        val manager = activity.getSystemService(CompanionDeviceManager::class.java)
        if (manager != null && isAssociated(activity)) {
            try {
                manager.requestNotificationAccess(component)
                return
            } catch (_: Exception) {
            }
        }
        SetupPermissionHelper.openNotificationAccess(activity)
    }

    private fun saveError(context: Context, value: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ERROR, value.take(160))
            .commit()
    }
}
`;
fs.writeFileSync(path.join(javaDir, 'CompanionAssociationHelper.kt'), companionHelperSource, 'utf8');

const externalTestStoreSource = String.raw`package com.clipcascade

import android.content.Context

object ExternalNotificationTestStore {
    const val HELPER_PACKAGE = "com.clipcascade.extended.testnotifier"
    const val ACTION_POST_TEST = "com.clipcascade.extended.action.POST_LISTENER_TEST"
    const val EXTRA_CODE = "verification_code"
    private const val PREFS = "external_notification_test"
    private const val TTL_MS = 5 * 60_000L

    fun start(context: Context, expected: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("expected", expected)
            .putLong("started_at", System.currentTimeMillis())
            .commit()
    }

    fun expected(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val startedAt = prefs.getLong("started_at", 0L)
        val age = System.currentTimeMillis() - startedAt
        if (startedAt <= 0L || age !in 0..TTL_MS) {
            clear(context)
            return ""
        }
        return prefs.getString("expected", "").orEmpty()
    }

    fun isTrustedHelper(context: Context, packageName: String): Boolean =
        packageName == HELPER_PACKAGE &&
            context.packageManager.checkSignatures(context.packageName, HELPER_PACKAGE) ==
            android.content.pm.PackageManager.SIGNATURE_MATCH

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
`;
fs.writeFileSync(path.join(javaDir, 'ExternalNotificationTestStore.kt'), externalTestStoreSource, 'utf8');

const testManagerSource = String.raw`package com.clipcascade

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import java.security.SecureRandom
import java.util.UUID

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
        val text = applicationContext.getString(R.string.otp_test_notification_text, value)
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
            .setTimeoutAfter(5 * 60_000L)
            .addExtras(extras)
            .build()
        manager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
        queueSyntheticValue(applicationContext, text, value)
        return value
    }

    fun postListenerPath(context: Context): String {
        val applicationContext = context.applicationContext
        val value = random.nextInt(1_000_000).toString().padStart(6, '0')
        OtpTestStatusStore.start(applicationContext, value)
        ExternalNotificationTestStore.start(applicationContext, value)
        val intent = Intent(ExternalNotificationTestStore.ACTION_POST_TEST)
            .setPackage(ExternalNotificationTestStore.HELPER_PACKAGE)
            .putExtra(ExternalNotificationTestStore.EXTRA_CODE, value)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        applicationContext.startActivity(intent)
        return value
    }

    private fun queueSyntheticValue(context: Context, notificationText: String, expected: String) {
        val extracted = OtpCodeExtractor.extract(notificationText)
        if (extracted != expected) {
            OtpTestStatusStore.extractionFailed(context, expected)
            return
        }
        val relayId = OtpTestStatusStore.RELAY_ID_PREFIX + UUID.randomUUID().toString()
        val queued = OtpRelayStore.enqueue(
            context,
            OtpRelayStore.Item(relayId, expected, System.currentTimeMillis()),
        )
        if (queued) {
            OtpTestStatusStore.queued(context, expected, relayId)
            OtpRelayDispatcher.schedule(context)
        } else {
            OtpTestStatusStore.deduplicated(context, expected)
        }
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.otp_test_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.otp_test_channel_description)
                setShowBadge(false)
            },
        )
    }
}
`;
fs.writeFileSync(path.join(javaDir, 'OtpTestNotificationManager.kt'), testManagerSource, 'utf8');


console.log('Prepared alpha.22 companion association and external test launcher.');
