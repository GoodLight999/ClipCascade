/**
 * @format
 *
 * Source-contract tests for the React Native entrypoint and Android product
 * integration. Rendering App in Jest without an Android runtime previously
 * produced a false smoke test because Notifee and the other native modules do
 * not exist in Node.
 */

import fs from 'fs';
import path from 'path';

const sourceRoot = path.resolve(__dirname, '..');
const androidMain = path.resolve(sourceRoot, 'android', 'app', 'src', 'main');
const kotlinRoot = path.resolve(androidMain, 'java', 'com', 'clipcascade');
const testKotlinRoot = path.resolve(
  sourceRoot,
  'android',
  'app',
  'src',
  'test',
  'java',
  'com',
  'clipcascade',
);

const read = (...parts: string[]) =>
  fs.readFileSync(path.resolve(...parts), 'utf8');

const appSource = read(sourceRoot, 'App.js');
const shizukuBridgeSource = read(kotlinRoot, 'ShizukuClipboardBridge.kt');
const backgroundCaptureSource = read(kotlinRoot, 'BackgroundClipboardCapture.kt');
const clipboardListenerSource = read(kotlinRoot, 'ClipboardListenerModule.kt');
const backgroundSetupSource = read(kotlinRoot, 'BackgroundSetupActivity.kt');
const mobileIndexSource = read(sourceRoot, 'index.js');
const desktopEntrySource = read(
  sourceRoot,
  '..',
  '..',
  'ClipCascade_Desktop',
  'src',
  'main.py',
);
const desktopPyprojectSource = read(
  sourceRoot,
  '..',
  '..',
  'ClipCascade_Desktop',
  'src',
  'pyproject.toml',
);
const accessibilityServiceSource = read(
  kotlinRoot,
  'ClipCascadeAccessibilityService.kt',
);
const copyClassifierSource = read(kotlinRoot, 'CopySignalClassifier.kt');
const shizukuUserServiceSource = read(
  kotlinRoot,
  'shizuku',
  'ShizukuClipboardUserService.kt',
);
const shizukuAidlSource = read(
  androidMain,
  'aidl',
  'com',
  'clipcascade',
  'shizuku',
  'IShizukuClipboardService.aidl',
);
const accessibilityConfigSource = read(
  androidMain,
  'res',
  'xml',
  'accessibility_service_config.xml',
);
const androidManifestSource = read(androidMain, 'AndroidManifest.xml');
const setupStylesSource = read(
  androidMain,
  'res',
  'values',
  'styles.xml',
);
const runtimeMetadataSource = read(
  sourceRoot,
  '..',
  '..',
  'metadata.json',
);

describe('App canonical product contract', () => {
  test('uses the recovery repository for product navigation and update metadata', () => {
    expect(appSource).toContain(
      'https://github.com/GoodLight999/Trial-and-Error-ClipCascade',
    );
    expect(appSource).toContain(
      'https://raw.githubusercontent.com/GoodLight999/Trial-and-Error-ClipCascade/stability-recovery/version.json',
    );
    expect(appSource).toContain(
      '${GITHUB_URL}/blob/stability-recovery/docs/ANDROID_SETUP.md',
    );
  });

  test('does not expose upstream or server-supplied donation links in product UI', () => {
    expect(appSource).not.toContain(
      'https://github.com/Sathvik-Rao/ClipCascade',
    );
    expect(appSource).not.toContain(
      'https://raw.githubusercontent.com/Sathvik-Rao/ClipCascade',
    );
    expect(appSource).not.toContain('METADATA_URL');
    expect(appSource).not.toContain('setDonateUrl');
    expect(appSource).not.toContain('Linking.openURL(donateUrl)');
    expect(appSource).not.toContain('>DONATE<');
    expect(appSource).toContain('>PROJECT<');
    expect(appSource).toContain('>SETUP<');
    expect(appSource).toContain('>SERVER<');
    expect(runtimeMetadataSource).not.toContain('Sathvik-Rao');
    expect(mobileIndexSource).not.toContain('github.com/Sathvik-Rao');
    expect(desktopEntrySource).not.toContain('github.com/Sathvik-Rao');
    expect(desktopPyprojectSource).not.toContain('github.com/Sathvik-Rao');
    expect(desktopPyprojectSource).not.toContain('clipcascade.sathvik.dev');
    expect(desktopPyprojectSource).toContain(
      'github.com/GoodLight999/Trial-and-Error-ClipCascade',
    );
    expect(shizukuBridgeSource).not.toContain('shizuku.rikka.app/download');
  });

  test('uses the official ShizukuProvider binder acquisition path', () => {
    expect(androidManifestSource).toContain(
      'android:name="rikka.shizuku.ShizukuProvider"',
    );
    expect(androidManifestSource).toContain(
      'android:authorities="${applicationId}.shizuku"',
    );
    expect(androidManifestSource).toContain(
      'android:permission="android.permission.INTERACT_ACROSS_USERS_FULL"',
    );
    expect(androidManifestSource).toContain(
      'android:name="app.notifee.core.ForegroundService"',
    );
    expect(androidManifestSource).toContain('tools:ignore="MissingClass"');
    expect(shizukuBridgeSource).toContain(
      'Shizuku.addBinderReceivedListenerSticky',
    );
    expect(shizukuBridgeSource).toContain('Shizuku.bindUserService');
    expect(shizukuBridgeSource).not.toContain(
      'rikka.shizuku.intent.action.REQUEST_BINDER',
    );
    expect(shizukuBridgeSource).not.toContain('sendBroadcast(');
    expect(shizukuBridgeSource).not.toContain('queryBroadcastReceivers');
  });

  test('passes the client Android user to explicit AOSP clipboard signatures', () => {
    expect(shizukuAidlSource).toContain('String readClipboard(int userId)');
    expect(shizukuBridgeSource).toContain(
      'clientUserId = Process.myUid() / PER_USER_RANGE',
    );
    expect(shizukuBridgeSource).toContain(
      'service.readClipboard(requestedUserId)',
    );
    expect(shizukuUserServiceSource).toContain(
      'private fun isAndroid14PlusSignature',
    );
    expect(shizukuUserServiceSource).toContain(
      'private const val DEFAULT_DEVICE_ID = 0',
    );
    expect(shizukuUserServiceSource).toContain(
      'arrayOf(packageName, null, userId, DEFAULT_DEVICE_ID)',
    );
    expect(shizukuUserServiceSource).not.toContain(
      'Context.DEVICE_ID_DEFAULT',
    );
    expect(shizukuUserServiceSource).toContain('0 -> ROOT_PACKAGE');
    expect(shizukuUserServiceSource).toContain('2_000 -> SHELL_PACKAGE');
    expect(shizukuUserServiceSource).not.toContain('maxByOrNull');
    expect(shizukuUserServiceSource).not.toContain(
      'val userId = Process.myUid() / PER_USER_RANGE',
    );
  });

  test('routes every automatic trigger through the same acquisition coordinator', () => {
    expect(clipboardListenerSource).toContain(
      'BackgroundClipboardCapture.request(',
    );
    expect(clipboardListenerSource).toContain('"clipboard_listener"');
    expect(clipboardListenerSource).toContain('"read_logs"');
    expect(clipboardListenerSource).not.toContain(
      'emitOrdinaryClipboard(clipboardManager.primaryClip)',
    );
    expect(clipboardListenerSource).not.toContain(
      'val clip = clipboardManager.primaryClip',
    );
    expect(clipboardListenerSource).toContain(
      'clipboardManager.primaryClipDescription',
    );
    expect(accessibilityServiceSource).toContain(
      'BackgroundClipboardCapture.request(this, "accessibility_action_copy")',
    );
    expect(backgroundCaptureSource).toContain(
      'ShizukuClipboardBridge.readClipboard',
    );
    expect(backgroundCaptureSource).toContain(
      'ClipboardFloatingActivity.getIntent',
    );
    expect(backgroundCaptureSource).toContain(
      'ClipboardListenerModule.emitExternalClipboard',
    );
  });

  test('uses only the documented Accessibility ACTION_COPY signal', () => {
    expect(copyClassifierSource).toContain(
      'action == AccessibilityNodeInfo.ACTION_COPY',
    );
    expect(accessibilityServiceSource).toContain(
      'CopySignalClassifier.isCopyAction(event.action)',
    );
    expect(accessibilityConfigSource).toContain(
      'android:accessibilityEventTypes="typeAllMask"',
    );
    expect(accessibilityConfigSource).toContain(
      'android:canRetrieveWindowContent="false"',
    );
    expect(accessibilityConfigSource).toContain(
      'android:notificationTimeout="0"',
    );
    expect(copyClassifierSource).not.toContain('startsWith');
    expect(copyClassifierSource).not.toContain('contains');
    expect(copyClassifierSource).not.toContain('コピー');
    expect(copyClassifierSource).not.toContain('copied');
    expect(accessibilityServiceSource).not.toContain('postDelayed');
    expect(accessibilityServiceSource).not.toContain('event.text');
    expect(accessibilityServiceSource).not.toContain('contentDescription');
  });

  test('contains no obsolete native duplicate gate', () => {
    expect(
      fs.existsSync(path.resolve(kotlinRoot, 'ClipboardEmissionGate.kt')),
    ).toBe(false);
    expect(
      fs.existsSync(path.resolve(testKotlinRoot, 'ClipboardEmissionGateTest.kt')),
    ).toBe(false);
    expect(clipboardListenerSource).not.toContain('ClipboardEmissionGate');
    expect(backgroundCaptureSource).not.toContain('duplicateWindowMs');
  });

  test('assigns explicit high-contrast colors to React Native and native setup UI', () => {
    expect(appSource).toContain('useColorScheme');
    expect(appSource).toContain("background: '#FFFFFF'");
    expect(appSource).toContain("background: '#111318'");
    expect(appSource).toContain("textPrimary: '#15161A'");
    expect(appSource).toContain("textPrimary: '#F2F3F7'");
    expect(appSource).toContain('const createStyles = palette =>');
    expect(appSource).toContain('backgroundColor: palette.background');
    expect(appSource).toContain('color: palette.textPrimary');
    expect(appSource).toContain('backgroundColor: palette.surface');
    expect(appSource).toContain('tintColors={checkboxTintColors}');
    expect(appSource).not.toContain('const styles = StyleSheet.create');

    expect(androidManifestSource).toContain(
      'android:theme="@style/Theme.ClipCascade.BackgroundSetup"',
    );
    expect(setupStylesSource).toContain('<style name="AppTheme"');
    expect(setupStylesSource).toContain(
      '<item name="android:textColorPrimary">@color/background_setup_text_primary</item>',
    );
    expect(setupStylesSource).toContain(
      '<item name="android:windowBackground">@color/background_setup_surface</item>',
    );
    expect(setupStylesSource).toContain(
      '<style name="Theme.ClipCascade.BackgroundSetup" parent="AppTheme" />',
    );
    expect(backgroundSetupSource).toContain(
      'setBackgroundColor(surfaceColor)',
    );
    expect(backgroundSetupSource).toContain(
      'setTextColor(primaryTextColor)',
    );
    expect(backgroundSetupSource).toContain('backgroundTintList');
  });

  test('retains the existing foreground-service transport entrypoint', () => {
    expect(appSource).toContain(
      "import StartForegroundService from './StartForegroundService';",
    );
  });

  test('projects persistent P2S outbox metadata through the existing UI poller', () => {
    expect(appSource).toContain(
      "const { formatP2SOutboxStatus } = require('./P2SOutboxStatus');",
    );
    expect(appSource).toContain("'p2sTextOutboxStatus'");
    expect(appSource).toContain(
      'formatP2SOutboxStatus(latest.p2sTextOutboxStatus)',
    );
    expect(appSource).toContain('{p2sOutboxMessage !== \'\' && (');
  });
});
