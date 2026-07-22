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

// Extend the internal-write guard without consuming the mark from another listener.
const guardPath = path.join(javaDir, 'ClipboardWriteGuard.kt');
let guard = fs.readFileSync(guardPath, 'utf8');
guard = replaceRequired(
  guard,
  '    @Synchronized\n    fun consumeIfMarked(): Boolean {\n',
  '    @Synchronized\n    fun isMarked(): Boolean = SystemClock.elapsedRealtime() <= markedUntilElapsedMs\n\n' +
    '    @Synchronized\n    fun consumeIfMarked(): Boolean {\n',
  'non-consuming internal-write guard',
);
fs.writeFileSync(guardPath, guard, 'utf8');

// Manifest: companion trust, correct non-exported listener, and sticky native service.
let manifest = fs.readFileSync(manifestPath, 'utf8');
manifest = replaceRequired(
  manifest,
  '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />\n',
  '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />\n' +
    '    <uses-permission android:name="android.permission.REQUEST_COMPANION_SELF_MANAGED" />\n' +
    '    <uses-permission android:name="android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND" />\n' +
    '    <uses-permission android:name="android.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND" />\n' +
    '    <uses-feature android:name="android.software.companion_device_setup" android:required="false" />\n' +
    '    <permission android:name="com.clipcascade.extended.permission.POST_TEST_NOTIFICATION" android:protectionLevel="signature" />\n' +
    '    <uses-permission android:name="com.clipcascade.extended.permission.POST_TEST_NOTIFICATION" />\n',
  'companion permissions and helper signature permission',
);
manifest = replaceRequired(
  manifest,
  '      <service\n        android:name=".NotificationCodeListenerService"',
  '      <service\n        android:name=".ClipboardAcquisitionService"\n        android:foregroundServiceType="remoteMessaging"\n        android:exported="false" />\n\n      <service\n        android:name=".NotificationCodeListenerService"',
  'native acquisition service declaration',
);
manifest = replaceRequired(
  manifest,
  '        android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"\n        android:exported="true">',
  '        android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"\n        android:exported="false">',
  'non-exported notification listener',
);
fs.writeFileSync(manifestPath, manifest, 'utf8');

// Settings UI: companion association is an explicit prerequisite on Android 15+.
const activityPath = path.join(javaDir, 'RelaySettingsActivity.kt');
let activity = fs.readFileSync(activityPath, 'utf8');
activity = replaceRequired(
  activity,
  '    private lateinit var notificationStatus: TextView\n',
  '    private lateinit var notificationStatus: TextView\n' +
    '    private lateinit var companionStatus: TextView\n',
  'companion status field',
);
activity = replaceRequired(
  activity,
  '        content.addView(sectionTitle(getString(R.string.otp_section)))\n',
  '        content.addView(sectionTitle(getString(R.string.companion_section)))\n' +
    '        content.addView(bodyText(getString(R.string.companion_explanation)))\n' +
    '        companionStatus = bodyText("")\n' +
    '        content.addView(companionStatus)\n' +
    '        content.addView(Button(this).apply {\n' +
    '            text = getString(R.string.companion_associate)\n' +
    '            setOnClickListener {\n' +
    '                CompanionAssociationHelper.requestAssociation(this@RelaySettingsActivity) {\n' +
    '                    updateStatus()\n' +
    '                }\n' +
    '            }\n' +
    '        })\n\n' +
    '        content.addView(sectionTitle(getString(R.string.otp_section)))\n',
  'companion association controls',
);
activity = replaceRequired(
  activity,
  '            SetupPermissionHelper.notificationAccessEnabled(this) to\n',
  '            CompanionAssociationHelper.isAssociated(this) to\n' +
    '                getString(R.string.setup_step_companion),\n' +
    '            SetupPermissionHelper.notificationAccessEnabled(this) to\n',
  'companion setup step',
);
activity = replaceRequired(
  activity,
  '            !SetupPermissionHelper.notificationAccessEnabled(this) ->\n                SetupPermissionHelper.openNotificationAccess(this)\n',
  '            !CompanionAssociationHelper.isAssociated(this) ->\n' +
    '                CompanionAssociationHelper.requestAssociation(this) { updateStatus() }\n' +
    '            !SetupPermissionHelper.notificationAccessEnabled(this) ->\n' +
    '                CompanionAssociationHelper.requestNotificationAccess(this)\n',
  'companion setup progression',
);
activity = replaceRequired(
  activity,
  '        notificationStatus.text = getString(\n',
  '        companionStatus.text = getString(\n' +
    '            if (CompanionAssociationHelper.isAssociated(this)) {\n' +
    '                R.string.companion_status_associated\n' +
    '            } else {\n' +
    '                R.string.companion_status_missing\n' +
    '            },\n' +
    '        ) + CompanionAssociationHelper.lastError(this).takeIf { it.isNotBlank() }\n' +
    '            ?.let { "\\n$it" }.orEmpty()\n' +
    '        notificationStatus.text = getString(\n',
  'companion status display',
);
activity = replaceRequired(
  activity,
  '    override fun onRequestPermissionsResult(\n',
  '    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {\n' +
    '        super.onActivityResult(requestCode, resultCode, data)\n' +
    '        if (requestCode == CompanionAssociationHelper.REQUEST_ASSOCIATION) {\n' +
    '            updateStatus()\n' +
    '            if (CompanionAssociationHelper.isAssociated(this)) {\n' +
    '                CompanionAssociationHelper.requestNotificationAccess(this)\n' +
    '            }\n' +
    '        }\n' +
    '    }\n\n' +
    '    override fun onRequestPermissionsResult(\n',
  'companion activity result',
);
fs.writeFileSync(activityPath, activity, 'utf8');

const stringFiles = [
  {
    file: path.join(root, 'android', 'app', 'src', 'main', 'res', 'values', 'strings.xml'),
    anchor: '    <string name="otp_section">Verification codes from notifications</string>',
    insertion:
      '    <string name="companion_section">Trusted Windows companion</string>\n' +
      '    <string name="companion_explanation">Android 15 and later redact detected one-time codes from ordinary notification listeners. Associate ClipCascade with its Windows peer through Android Companion Device Manager before testing real notifications.</string>\n' +
      '    <string name="companion_associate">Associate trusted Windows companion</string>\n' +
      '    <string name="companion_status_associated">Status: companion association is active</string>\n' +
      '    <string name="companion_status_missing">Status: association missing; OTP content may be redacted</string>\n' +
      '    <string name="companion_device_display_name">ClipCascade Windows peer</string>\n' +
      '    <string name="setup_step_companion">Associate the Windows companion for unredacted notification access</string>\n' +
      '    <string name="clipboard_acquisition_channel">Clipboard acquisition</string>\n' +
      '    <string name="clipboard_acquisition_channel_description">Keeps the native clipboard acquisition owner available in the background.</string>\n' +
      '    <string name="clipboard_acquisition_notification_title">ClipCascade clipboard acquisition</string>\n' +
      '    <string name="clipboard_acquisition_notification_text">Watching explicit copy candidates through the native background service</string>\n' +
      '    <string name="otp_section">Verification codes from notifications</string>',
  },
  {
    file: path.join(root, 'android', 'app', 'src', 'main', 'res', 'values-ja', 'strings.xml'),
    anchor: '    <string name="otp_section">通知から認証コードを取得</string>',
    insertion:
      '    <string name="companion_section">信頼済みWindowsコンパニオン</string>\n' +
      '    <string name="companion_explanation">Android 15以降は、通常の通知リスナーに対して検出済みワンタイムコードを伏字にします。実通知テストの前に、Androidのコンパニオンデバイス管理からClipCascadeとWindows端末を関連付けます。</string>\n' +
      '    <string name="companion_associate">Windowsコンパニオンを信頼済みとして関連付け</string>\n' +
      '    <string name="companion_status_associated">状態: コンパニオン関連付け済み</string>\n' +
      '    <string name="companion_status_missing">状態: 未関連付け・OTP本文が伏字になる可能性あり</string>\n' +
      '    <string name="companion_device_display_name">ClipCascade Windows端末</string>\n' +
      '    <string name="setup_step_companion">通知本文取得のためWindowsコンパニオンを関連付け</string>\n' +
      '    <string name="clipboard_acquisition_channel">クリップボード取得</string>\n' +
      '    <string name="clipboard_acquisition_channel_description">ネイティブのクリップボード取得処理をバックグラウンドで維持します。</string>\n' +
      '    <string name="clipboard_acquisition_notification_title">ClipCascade クリップボード取得</string>\n' +
      '    <string name="clipboard_acquisition_notification_text">ネイティブ常駐サービスでコピー候補を監視中</string>\n' +
      '    <string name="otp_section">通知から認証コードを取得</string>',
  },
];
for (const entry of stringFiles) {
  let strings = fs.readFileSync(entry.file, 'utf8');
  strings = replaceRequired(strings, entry.anchor, entry.insertion, `${entry.file} alpha22 strings`);
  strings = strings.replace(
    /This separate test does not queue the value directly\.[^<]*/,
    'This test asks the separately signed test-sender APK to post an external notification. The value must pass through Android NotificationListenerService, extraction, the durable queue, transport, and peer ACK.',
  );
  strings = strings.replace(
    /この別テストは値を直接キューへ入れません。[^<]*/,
    'このテストは別パッケージのテスト送信APKへ実通知を依頼します。AndroidのNotificationListenerService、抽出、永続キュー、通信、peer ACKを通る必要があります。',
  );
  fs.writeFileSync(entry.file, strings, 'utf8');
}

console.log('Prepared alpha.22 sticky native clipboard owner, mutation-proof broad Accessibility probes, companion trust setup, and external-package listener self-test.');
