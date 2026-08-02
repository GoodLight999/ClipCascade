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

const appSource = fs.readFileSync(path.resolve(__dirname, '..', 'App.js'), 'utf8');
const shizukuBridgeSource = fs.readFileSync(
  path.resolve(
    __dirname,
    '..',
    'android',
    'app',
    'src',
    'main',
    'java',
    'com',
    'clipcascade',
    'ShizukuClipboardBridge.kt',
  ),
  'utf8',
);
const clipboardListenerSource = fs.readFileSync(
  path.resolve(
    __dirname,
    '..',
    'android',
    'app',
    'src',
    'main',
    'java',
    'com',
    'clipcascade',
    'ClipboardListenerModule.kt',
  ),
  'utf8',
);
const shizukuUserServiceSource = fs.readFileSync(
  path.resolve(
    __dirname,
    '..',
    'android',
    'app',
    'src',
    'main',
    'java',
    'com',
    'clipcascade',
    'shizuku',
    'ShizukuClipboardUserService.kt',
  ),
  'utf8',
);
const androidManifestSource = fs.readFileSync(
  path.resolve(__dirname, '..', 'android', 'app', 'src', 'main', 'AndroidManifest.xml'),
  'utf8',
);
const setupStylesSource = fs.readFileSync(
  path.resolve(
    __dirname,
    '..',
    'android',
    'app',
    'src',
    'main',
    'res',
    'values',
    'styles.xml',
  ),
  'utf8',
);
const runtimeMetadataSource = fs.readFileSync(
  path.resolve(__dirname, '..', '..', '..', 'metadata.json'),
  'utf8',
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
    expect(appSource).toContain(
      'https://raw.githubusercontent.com/GoodLight999/Trial-and-Error-ClipCascade/stability-recovery/metadata.json',
    );
  });

  test('does not send product UI links back to the upstream repository', () => {
    expect(appSource).not.toContain(
      'https://github.com/Sathvik-Rao/ClipCascade',
    );
    expect(appSource).not.toContain(
      'https://raw.githubusercontent.com/Sathvik-Rao/ClipCascade',
    );
    expect(runtimeMetadataSource).not.toContain('Sathvik-Rao');
    expect(runtimeMetadataSource).not.toContain(
      'https://github.com/Sathvik-Rao/ClipCascade',
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

  test('limits hidden clipboard calls to explicit AOSP IClipboard signatures', () => {
    expect(shizukuUserServiceSource).toContain(
      'private fun isAndroid14PlusSignature',
    );
    expect(shizukuUserServiceSource).toContain(
      'arrayOf(SHELL_PACKAGE, null, userId, DEFAULT_DEVICE_ID)',
    );
    expect(shizukuUserServiceSource).not.toContain('maxByOrNull');
    expect(shizukuUserServiceSource).not.toContain(
      'Unsupported getPrimaryClip parameter',
    );
  });

  test('preserves the upstream ordinary listener independently of background capture', () => {
    expect(clipboardListenerSource).toContain(
      'emitOrdinaryClipboard(clipboardManager.primaryClip)',
    );
    expect(clipboardListenerSource).toContain(
      '.emit("onClipboardChange", params)',
    );
    expect(clipboardListenerSource).not.toContain(
      'private val emissionGate = ClipboardEmissionGate()',
    );
    expect(clipboardListenerSource).toContain('emitExternalClipboard');
  });

  test('assigns explicit high-contrast defaults to the app and setup screen', () => {
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
