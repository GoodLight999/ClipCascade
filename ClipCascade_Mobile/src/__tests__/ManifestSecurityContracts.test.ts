import fs from 'fs';
import path from 'path';

const androidMain = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
);
const manifest = fs.readFileSync(
  path.resolve(androidMain, 'AndroidManifest.xml'),
  'utf8',
);
const setupActivity = fs.readFileSync(
  path.resolve(
    androidMain,
    'java',
    'com',
    'clipcascade',
    'BackgroundSetupActivity.kt',
  ),
  'utf8',
);

describe('Android manifest security and system binding', () => {
  test('accessibility service is bindable by Android and protected from apps', () => {
    expect(manifest).toMatch(
      /<service\s+[\s\S]*?android:name="\.ClipCascadeAccessibilityService"[\s\S]*?android:exported="true"[\s\S]*?android:permission="android\.permission\.BIND_ACCESSIBILITY_SERVICE"/,
    );
    expect(manifest).toContain(
      '<action android:name="android.accessibilityservice.AccessibilityService" />',
    );
  });

  test('battery settings navigation does not request direct allowlisting permission', () => {
    expect(manifest).not.toContain(
      'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
    );
    expect(setupActivity).toContain(
      'Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS',
    );
    expect(setupActivity).not.toContain(
      'Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
    );
  });
});
