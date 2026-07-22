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

let manifest = fs.readFileSync(manifestPath, 'utf8');
manifest = replaceRequired(
  manifest,
  '    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />\n',
  '    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />\n' +
    '    <!-- Explicit user-authorized fallback for Android 10+ background clipboard reads. -->\n' +
    '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />\n',
  'overlay permission declaration',
);
fs.writeFileSync(manifestPath, manifest, 'utf8');

const policySource = String.raw`package com.clipcascade

/** Pure gating policy for the user-authorized overlay clipboard fallback. */
object OverlayClipboardPolicy {
    fun shouldAttempt(
        enabled: Boolean,
        permissionGranted: Boolean,
        directClipboardValuePresent: Boolean,
    ): Boolean = enabled && permissionGranted && !directClipboardValuePresent
}
`;
fs.writeFileSync(path.join(javaDir, 'OverlayClipboardPolicy.kt'), policySource, 'utf8');

const policyTestSource = String.raw`package com.clipcascade

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayClipboardPolicyTest {
    @Test
    fun overlayRunsOnlyWhenEnabledAuthorizedAndStillNeeded() {
        assertTrue(
            OverlayClipboardPolicy.shouldAttempt(
                enabled = true,
                permissionGranted = true,
                directClipboardValuePresent = false,
            ),
        )
        assertFalse(OverlayClipboardPolicy.shouldAttempt(false, true, false))
        assertFalse(OverlayClipboardPolicy.shouldAttempt(true, false, false))
        assertFalse(OverlayClipboardPolicy.shouldAttempt(true, true, true))
    }
}
`;
fs.mkdirSync(testDir, {recursive: true});
fs.writeFileSync(path.join(testDir, 'OverlayClipboardPolicyTest.kt'), policyTestSource, 'utf8');

const settingsStorePath = path.join(javaDir, 'RelaySettingsStore.kt');
let settingsStore = fs.readFileSync(settingsStorePath, 'utf8');
settingsStore = replaceRequired(
  settingsStore,
  '    fun codeRelayEnabled(context: Context): Boolean =\n',
  '    fun overlayClipboardEnabled(context: Context): Boolean =\n' +
    '        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)\n' +
    '            .getBoolean("overlay_clipboard_enabled", true)\n\n' +
    '    fun setOverlayClipboardEnabled(context: Context, enabled: Boolean) {\n' +
    '        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)\n' +
    '            .edit()\n' +
    '            .putBoolean("overlay_clipboard_enabled", enabled)\n' +
    '            .commit()\n' +
    '    }\n\n' +
    '    fun codeRelayEnabled(context: Context): Boolean =\n',
  'overlay clipboard preference',
);
fs.writeFileSync(settingsStorePath, settingsStore, 'utf8');

const permissionPath = path.join(javaDir, 'SetupPermissionHelper.kt');
let permission = fs.readFileSync(permissionPath, 'utf8');
permission = replaceRequired(
  permission,
  '    fun notificationAccessEnabled(context: Context): Boolean =\n',
  '    fun overlayPermissionGranted(context: Context): Boolean =\n' +
    '        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)\n\n' +
    '    fun notificationAccessEnabled(context: Context): Boolean =\n',
  'overlay permission status helper',
);
permission = replaceRequired(
  permission,
  '    fun openNotificationAccess(activity: Activity) {\n',
  '    fun openOverlayPermission(activity: Activity) {\n' +
    '        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return\n' +
    '        val intent = Intent(\n' +
    '            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,\n' +
    '            Uri.parse("package:${activity.packageName}"),\n' +
    '        )\n' +
    '        try {\n' +
    '            activity.startActivity(intent)\n' +
    '        } catch (_: Exception) {\n' +
    '            activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))\n' +
    '        }\n' +
    '    }\n\n' +
    '    fun openNotificationAccess(activity: Activity) {\n',
  'overlay permission settings launcher',
);
fs.writeFileSync(permissionPath, permission, 'utf8');

const accessibilityPath = path.join(javaDir, 'ClipboardAccessibilityService.kt');
let accessibility = fs.readFileSync(accessibilityPath, 'utf8');
accessibility = replaceRequired(
  accessibility,
  'import android.content.Context\nimport android.os.Handler\nimport android.os.Looper\n',
  'import android.content.Context\n' +
    'import android.graphics.PixelFormat\n' +
    'import android.os.Build\n' +
    'import android.os.Handler\n' +
    'import android.os.Looper\n' +
    'import android.view.Gravity\n' +
    'import android.view.View\n' +
    'import android.view.WindowManager\n',
  'Accessibility overlay imports',
);

const captureAnchor = '    private fun captureClipboard(sourcePackage: String, trigger: String): Boolean {\n';
const helperSource = String.raw`    private data class ClipboardReadResult(
        val text: String?,
        val path: String,
    )

    private fun inspectClipboard(
        manager: ClipboardManager,
        successPath: String,
        deniedPath: String,
        emptyPath: String,
    ): ClipboardReadResult = try {
        val clip = manager.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString()
        } else {
            null
        }
        ClipboardReadResult(
            text = text,
            path = if (text.isNullOrBlank()) emptyPath else successPath,
        )
    } catch (error: SecurityException) {
        Log.d(TAG, "ClipboardManager access was denied", error)
        ClipboardReadResult(null, deniedPath)
    } catch (error: Exception) {
        Log.w(TAG, "Unable to inspect clipboard", error)
        ClipboardReadResult(null, emptyPath)
    }

    private fun readClipboardWithOverlayFallback(): ClipboardReadResult {
        val manager = clipboardManager
            ?: (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).also {
                clipboardManager = it
            }
        val direct = inspectClipboard(
            manager = manager,
            successPath = "clipboard_manager",
            deniedPath = "clipboard_manager_denied",
            emptyPath = "clipboard_manager_empty",
        )
        if (!direct.text.isNullOrBlank()) return direct

        val overlayEnabled = RelaySettingsStore.overlayClipboardEnabled(this)
        val permissionGranted = SetupPermissionHelper.overlayPermissionGranted(this)
        if (
            !OverlayClipboardPolicy.shouldAttempt(
                enabled = overlayEnabled,
                permissionGranted = permissionGranted,
                directClipboardValuePresent = false,
            )
        ) {
            return when {
                !overlayEnabled -> ClipboardReadResult(null, "overlay_disabled")
                !permissionGranted -> ClipboardReadResult(null, "overlay_permission_missing")
                else -> direct
            }
        }

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val overlay = View(this).apply {
            alpha = 0f
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        var added = false
        return try {
            windowManager.addView(overlay, params)
            added = true
            inspectClipboard(
                manager = manager,
                successPath = "overlay_clipboard_manager",
                deniedPath = "overlay_clipboard_denied",
                emptyPath = "overlay_clipboard_empty",
            )
        } catch (error: SecurityException) {
            Log.w(TAG, "Overlay clipboard permission was denied", error)
            ClipboardReadResult(null, "overlay_permission_missing")
        } catch (error: Exception) {
            Log.w(TAG, "Unable to create temporary clipboard overlay", error)
            ClipboardReadResult(null, "overlay_add_failed")
        } finally {
            if (added) {
                try {
                    windowManager.removeViewImmediate(overlay)
                } catch (error: Exception) {
                    Log.w(TAG, "Unable to remove temporary clipboard overlay", error)
                }
            }
        }
    }

`;
accessibility = replaceRequired(
  accessibility,
  captureAnchor,
  helperSource + captureAnchor,
  'overlay clipboard helper insertion',
);

const oldReadBlock = `        var managerDenied = false
        val fromClipboard = try {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = manager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this)?.toString()
            } else {
                null
            }
        } catch (error: SecurityException) {
            managerDenied = true
            Log.d(TAG, "ClipboardManager access was denied; using Accessibility selection")
            null
        } catch (error: Exception) {
            Log.w(TAG, "Unable to inspect clipboard", error)
            null
        }
`;
const newReadBlock = `        val clipboardRead = readClipboardWithOverlayFallback()
        val fromClipboard = clipboardRead.text
`;
accessibility = replaceRequired(
  accessibility,
  oldReadBlock,
  newReadBlock,
  'clipboard read block',
);

const oldCapturePath = `        val capturePath = when {
            managerValue != null -> "clipboard_manager"
            rememberedSelection != null -> "selected_text_fallback"
            activeWindowSelection != null -> "active_window_selection"
            managerDenied -> "clipboard_denied_no_fallback"
            else -> "no_text_available"
        }
`;
const newCapturePath = `        val capturePath = when {
            managerValue != null -> clipboardRead.path
            rememberedSelection != null -> "selected_text_fallback"
            activeWindowSelection != null -> "active_window_selection"
            clipboardRead.path.isNotBlank() -> clipboardRead.path
            else -> "no_text_available"
        }
`;
accessibility = replaceRequired(
  accessibility,
  oldCapturePath,
  newCapturePath,
  'overlay-aware capture path',
);
fs.writeFileSync(accessibilityPath, accessibility, 'utf8');

const activityPath = path.join(javaDir, 'RelaySettingsActivity.kt');
let activity = fs.readFileSync(activityPath, 'utf8');
activity = replaceRequired(
  activity,
  '    private lateinit var accessibilityStatus: TextView\n',
  '    private lateinit var accessibilityStatus: TextView\n' +
    '    private lateinit var overlayStatus: TextView\n',
  'overlay status field',
);
activity = replaceRequired(
  activity,
  '        content.addView(bodyText(getString(R.string.accessibility_explanation)))\n\n        content.addView(sectionTitle(getString(R.string.otp_section)))\n',
  '        content.addView(bodyText(getString(R.string.accessibility_explanation)))\n' +
    '        val overlaySwitch = Switch(this).apply {\n' +
    '            text = getString(R.string.overlay_clipboard_enable)\n' +
    '            isChecked = RelaySettingsStore.overlayClipboardEnabled(this@RelaySettingsActivity)\n' +
    '            setOnCheckedChangeListener { _, enabled ->\n' +
    '                RelaySettingsStore.setOverlayClipboardEnabled(\n' +
    '                    this@RelaySettingsActivity,\n' +
    '                    enabled,\n' +
    '                )\n' +
    '                updateStatus()\n' +
    '            }\n' +
    '        }\n' +
    '        content.addView(overlaySwitch)\n' +
    '        overlayStatus = bodyText("")\n' +
    '        content.addView(overlayStatus)\n' +
    '        content.addView(Button(this).apply {\n' +
    '            text = getString(R.string.overlay_clipboard_open)\n' +
    '            setOnClickListener {\n' +
    '                SetupPermissionHelper.openOverlayPermission(this@RelaySettingsActivity)\n' +
    '            }\n' +
    '        })\n' +
    '        content.addView(bodyText(getString(R.string.overlay_clipboard_explanation)))\n\n' +
    '        content.addView(sectionTitle(getString(R.string.otp_section)))\n',
  'overlay settings controls',
);
activity = replaceRequired(
  activity,
  '            SetupPermissionHelper.accessibilityEnabled(this) to\n' +
    '                getString(R.string.setup_step_accessibility),\n' +
    '            SetupPermissionHelper.notificationAccessEnabled(this) to\n',
  '            SetupPermissionHelper.accessibilityEnabled(this) to\n' +
    '                getString(R.string.setup_step_accessibility),\n' +
    '            (!RelaySettingsStore.overlayClipboardEnabled(this) ||\n' +
    '                SetupPermissionHelper.overlayPermissionGranted(this)) to\n' +
    '                getString(R.string.setup_step_overlay),\n' +
    '            SetupPermissionHelper.notificationAccessEnabled(this) to\n',
  'overlay guided setup step',
);
activity = replaceRequired(
  activity,
  '            !SetupPermissionHelper.accessibilityEnabled(this) ->\n' +
    '                SetupPermissionHelper.openAccessibility(this)\n' +
    '            !SetupPermissionHelper.notificationAccessEnabled(this) ->\n',
  '            !SetupPermissionHelper.accessibilityEnabled(this) ->\n' +
    '                SetupPermissionHelper.openAccessibility(this)\n' +
    '            RelaySettingsStore.overlayClipboardEnabled(this) &&\n' +
    '                !SetupPermissionHelper.overlayPermissionGranted(this) ->\n' +
    '                SetupPermissionHelper.openOverlayPermission(this)\n' +
    '            !SetupPermissionHelper.notificationAccessEnabled(this) ->\n',
  'overlay guided setup action',
);
activity = replaceRequired(
  activity,
  '        notificationStatus.text = getString(\n',
  '        overlayStatus.text = getString(\n' +
    '            when {\n' +
    '                !RelaySettingsStore.overlayClipboardEnabled(this) ->\n' +
    '                    R.string.overlay_clipboard_status_disabled\n' +
    '                SetupPermissionHelper.overlayPermissionGranted(this) ->\n' +
    '                    R.string.overlay_clipboard_status_enabled\n' +
    '                else -> R.string.overlay_clipboard_status_missing\n' +
    '            },\n' +
    '        )\n' +
    '        notificationStatus.text = getString(\n',
  'overlay status rendering',
);
activity = replaceRequired(
  activity,
  '            "clipboard_manager" -> R.string.health_clipboard_manager\n',
  '            "clipboard_manager" -> R.string.health_clipboard_manager\n' +
    '            "clipboard_manager_denied" -> R.string.health_clipboard_manager_denied\n' +
    '            "clipboard_manager_empty" -> R.string.health_clipboard_manager_empty\n' +
    '            "overlay_clipboard_manager" -> R.string.health_overlay_clipboard_manager\n' +
    '            "overlay_clipboard_denied" -> R.string.health_overlay_clipboard_denied\n' +
    '            "overlay_clipboard_empty" -> R.string.health_overlay_clipboard_empty\n' +
    '            "overlay_permission_missing" -> R.string.health_overlay_permission_missing\n' +
    '            "overlay_disabled" -> R.string.health_overlay_disabled\n' +
    '            "overlay_add_failed" -> R.string.health_overlay_add_failed\n',
  'overlay diagnostic labels',
);
fs.writeFileSync(activityPath, activity, 'utf8');

const stringFiles = [
  {
    file: path.join(root, 'android', 'app', 'src', 'main', 'res', 'values', 'strings.xml'),
    setupAnchor: '    <string name="setup_step_accessibility">Enable Clipboard Sharing in Accessibility</string>',
    setupReplacement:
      '    <string name="setup_step_accessibility">Enable Clipboard Sharing in Accessibility</string>\n' +
      '    <string name="setup_step_overlay">Allow the temporary 1×1 clipboard access overlay</string>',
    clipboardAnchor: '    <string name="accessibility_status_disabled">Status: Disabled. Enable it for automatic clipboard sharing</string>',
    clipboardReplacement:
      '    <string name="accessibility_status_disabled">Status: Disabled. Enable it for automatic clipboard sharing</string>\n' +
      '    <string name="overlay_clipboard_enable">Use the reliable background clipboard access fallback</string>\n' +
      '    <string name="overlay_clipboard_open">Allow display over other apps</string>\n' +
      '    <string name="overlay_clipboard_explanation">Android 10+ blocks generic background clipboard reads. After an explicit Copy cue, ClipCascade can briefly add a fully transparent 1×1 non-touchable overlay, read the actual clipboard, and immediately remove the overlay. Disable this switch to keep the selection-text-only fallback.</string>\n' +
      '    <string name="overlay_clipboard_status_enabled">Status: Overlay fallback is enabled and authorized</string>\n' +
      '    <string name="overlay_clipboard_status_missing">Status: Overlay fallback is enabled, but Display over other apps is not allowed</string>\n' +
      '    <string name="overlay_clipboard_status_disabled">Status: Overlay fallback is disabled</string>',
    healthAnchor: '    <string name="health_clipboard_manager">clipboard read</string>',
    healthReplacement:
      '    <string name="health_clipboard_manager">clipboard read</string>\n' +
      '    <string name="health_clipboard_manager_denied">direct clipboard read denied</string>\n' +
      '    <string name="health_clipboard_manager_empty">direct clipboard read empty</string>\n' +
      '    <string name="health_overlay_clipboard_manager">temporary overlay clipboard read</string>\n' +
      '    <string name="health_overlay_clipboard_denied">overlay clipboard read denied</string>\n' +
      '    <string name="health_overlay_clipboard_empty">overlay clipboard read empty</string>\n' +
      '    <string name="health_overlay_permission_missing">overlay permission missing</string>\n' +
      '    <string name="health_overlay_disabled">overlay fallback disabled</string>\n' +
      '    <string name="health_overlay_add_failed">temporary overlay creation failed</string>',
  },
  {
    file: path.join(root, 'android', 'app', 'src', 'main', 'res', 'values-ja', 'strings.xml'),
    setupAnchor: '    <string name="setup_step_accessibility">ユーザー補助で「クリップボード共有」を有効化</string>',
    setupReplacement:
      '    <string name="setup_step_accessibility">ユーザー補助で「クリップボード共有」を有効化</string>\n' +
      '    <string name="setup_step_overlay">一時的な1×1クリップボード取得オーバーレイを許可</string>',
    clipboardAnchor: '    <string name="accessibility_status_disabled">状態: 無効です。自動クリップボード共有には有効化が必要です</string>',
    clipboardReplacement:
      '    <string name="accessibility_status_disabled">状態: 無効です。自動クリップボード共有には有効化が必要です</string>\n' +
      '    <string name="overlay_clipboard_enable">確実なバックグラウンド取得の補助経路を使う</string>\n' +
      '    <string name="overlay_clipboard_open">「他のアプリの上に表示」を許可</string>\n' +
      '    <string name="overlay_clipboard_explanation">Android 10以降は一般的なバックグラウンド読取を制限します。明示的なコピー操作を検出した後だけ、完全透明・1×1・タッチ不可のオーバーレイを一瞬追加し、実際のクリップボードを読んで直ちに削除します。OFFにすると選択文字列だけの代替経路へ戻ります。</string>\n' +
      '    <string name="overlay_clipboard_status_enabled">状態: オーバーレイ補助は有効・許可済みです</string>\n' +
      '    <string name="overlay_clipboard_status_missing">状態: オーバーレイ補助はONですが「他のアプリの上に表示」が未許可です</string>\n' +
      '    <string name="overlay_clipboard_status_disabled">状態: オーバーレイ補助はOFFです</string>',
    healthAnchor: '    <string name="health_clipboard_manager">クリップボード読取</string>',
    healthReplacement:
      '    <string name="health_clipboard_manager">クリップボード読取</string>\n' +
      '    <string name="health_clipboard_manager_denied">直接読取を拒否</string>\n' +
      '    <string name="health_clipboard_manager_empty">直接読取は空</string>\n' +
      '    <string name="health_overlay_clipboard_manager">一時オーバーレイ経由読取</string>\n' +
      '    <string name="health_overlay_clipboard_denied">オーバーレイ経由読取を拒否</string>\n' +
      '    <string name="health_overlay_clipboard_empty">オーバーレイ経由読取は空</string>\n' +
      '    <string name="health_overlay_permission_missing">オーバーレイ権限なし</string>\n' +
      '    <string name="health_overlay_disabled">オーバーレイ補助OFF</string>\n' +
      '    <string name="health_overlay_add_failed">一時オーバーレイ作成失敗</string>',
  },
];
for (const entry of stringFiles) {
  let strings = fs.readFileSync(entry.file, 'utf8');
  strings = replaceRequired(strings, entry.setupAnchor, entry.setupReplacement, `${entry.file} overlay setup strings`);
  strings = replaceRequired(strings, entry.clipboardAnchor, entry.clipboardReplacement, `${entry.file} overlay clipboard strings`);
  strings = replaceRequired(strings, entry.healthAnchor, entry.healthReplacement, `${entry.file} overlay health strings`);
  fs.writeFileSync(entry.file, strings, 'utf8');
}

console.log('Prepared optional Go-proven overlay clipboard acquisition while preserving the Extended queue and ACK path.');
