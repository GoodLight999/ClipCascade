import fs from 'fs';
import path from 'path';

const sourceRoot = path.resolve(__dirname, '..');
const androidMain = path.resolve(sourceRoot, 'android', 'app', 'src', 'main');
const kotlinRoot = path.resolve(androidMain, 'java', 'com', 'clipcascade');

const read = (...parts: string[]) =>
  fs.readFileSync(path.resolve(...parts), 'utf8');

const manifest = read(androidMain, 'AndroidManifest.xml');
const backgroundCapture = read(kotlinRoot, 'BackgroundClipboardCapture.kt');
const floatingActivity = read(kotlinRoot, 'ClipboardFloatingActivity.kt');
const mainActivity = read(kotlinRoot, 'MainActivity.kt');
const shizukuBridge = read(kotlinRoot, 'ShizukuClipboardBridge.kt');
const shizukuUserService = read(
  kotlinRoot,
  'shizuku',
  'ShizukuClipboardUserService.kt',
);
const backupRules = read(androidMain, 'res', 'xml', 'backup_rules.xml');
const extractionRules = read(
  androidMain,
  'res',
  'xml',
  'data_extraction_rules.xml',
);
const lightStyles = read(androidMain, 'res', 'values', 'styles.xml');
const darkStyles = read(androidMain, 'res', 'values-night', 'styles.xml');

describe('Android native reliability contracts', () => {
  test('capture coalescing follows exact read completion, not a time window', () => {
    expect(backgroundCapture).toContain('requestedAtElapsedNanos');
    expect(backgroundCapture).toContain('SystemClock.elapsedRealtimeNanos()');
    expect(backgroundCapture).toContain('coveredThroughElapsedNanos');
    expect(backgroundCapture).toContain(
      'queued.requestedAtElapsedNanos <= coveredThroughElapsedNanos',
    );
    expect(backgroundCapture).toContain(
      'covered_by_completed_clipboard_read',
    );
    expect(backgroundCapture).not.toContain('postDelayed');
    expect(backgroundCapture).not.toContain('duplicateWindowMs');
    expect(backgroundCapture).not.toMatch(/delay\(|debounce/i);
  });

  test('overlay owns capture through its exact platform read and teardown', () => {
    expect(backgroundCapture).toContain('var overlayOwnsCompletion = false');
    expect(backgroundCapture).toContain('ClipboardFloatingActivity.getIntent(');
    expect(backgroundCapture).toContain('overlayOwnsCompletion = true');
    expect(backgroundCapture).toContain('if (!overlayOwnsCompletion)');
    expect(backgroundCapture).toContain('readCompletedAtElapsedNanos: Long?');

    expect(floatingActivity).toContain('EXTRA_TRIGGER_SOURCE');
    expect(floatingActivity).toContain('readCompletedAtElapsedNanos');
    expect(floatingActivity).toContain(
      'readCompletedAtElapsedNanos = SystemClock.elapsedRealtimeNanos()',
    );
    expect(floatingActivity).toContain('completeCoordinatorOnce()');
    expect(floatingActivity).toContain(
      'BackgroundClipboardCapture.completeOverlay(',
    );
    expect(floatingActivity).toMatch(
      /override fun onDestroy\(\)[\s\S]*completeCoordinatorOnce\(\)/,
    );

    const listenerRegistration = floatingActivity.indexOf(
      'addOnGlobalLayoutListener',
    );
    const windowAttachment = floatingActivity.indexOf(
      'windowManager.addView(floatingView, params)',
    );
    expect(listenerRegistration).toBeGreaterThan(-1);
    expect(windowAttachment).toBeGreaterThan(listenerRegistration);
    expect(floatingActivity).toContain(
      'WindowManager.LayoutParams(\n            1,\n            1,',
    );
  });

  test('Shizuku reports binding death and timestamps successful reads', () => {
    expect(shizukuBridge).toContain('override fun onBindingDied');
    expect(shizukuBridge).toContain('override fun onNullBinding');
    expect(shizukuBridge).toContain('clearUserService(');
    expect(shizukuBridge).toContain('readCompletedAtElapsedNanos: Long?');
    expect(shizukuBridge).toContain(
      'readCompletedAtElapsedNanos = SystemClock.elapsedRealtimeNanos()',
    );
  });

  test('Shizuku delegates OEM clipboard Binder details to device framework', () => {
    expect(shizukuUserService).toContain(
      'context.createPackageContext(SHELL_PACKAGE, 0)',
    );
    expect(shizukuUserService).toContain(
      'getSystemService(ClipboardManager::class.java)',
    );
    expect(shizukuUserService).toContain('return clipboardManager.primaryClip');
    expect(shizukuUserService).toContain(
      'private const val SHELL_PACKAGE = "com.android.shell"',
    );
    expect(shizukuUserService).not.toContain('Class.forName');
    expect(shizukuUserService).not.toContain('IClipboard$Stub');
    expect(shizukuUserService).not.toContain('DEFAULT_DEVICE_ID');
    expect(shizukuUserService).not.toContain('findSupportedGetPrimaryClip');
    expect(shizukuUserService).not.toContain('argumentsFor(');
  });

  test('cold-start shares remain queued until the JS transport drains them', () => {
    expect(mainActivity).toContain('PendingShareStore.enqueue(');
    expect(mainActivity).toContain('PendingShareStore.EVENT_AVAILABLE');
    expect(mainActivity).toContain('ReactInstanceEventListener');
    expect(mainActivity).toContain('createReactContextInBackground()');
    expect(mainActivity).toContain('EXTRA_SHARE_CONSUMED');
    expect(mainActivity).not.toContain('pendingReactEvents');
    expect(mainActivity).not.toContain('flushPendingReactEvents');
    expect(mainActivity).toContain('ExistingPeriodicWorkPolicy.KEEP');
    expect(mainActivity).toContain('cancelUniqueWork(WORK_NAME)');
    expect(mainActivity).not.toContain('getWorkInfosByTag(WORK_NAME).get()');
  });

  test('backup policy excludes state and legacy storage permissions are absent', () => {
    expect(manifest).not.toContain('READ_EXTERNAL_STORAGE');
    expect(manifest).not.toContain('WRITE_EXTERNAL_STORAGE');
    expect(manifest).toContain('android:fullBackupContent="@xml/backup_rules"');
    expect(manifest).toContain(
      'android:dataExtractionRules="@xml/data_extraction_rules"',
    );
    for (const domain of [
      'root',
      'file',
      'database',
      'sharedpref',
      'external',
      'device_root',
      'device_file',
      'device_database',
      'device_sharedpref',
    ]) {
      expect(backupRules).toContain(`domain="${domain}" path="."`);
      expect(extractionRules).toContain(`domain="${domain}" path="."`);
    }
    expect(extractionRules).toContain('<cloud-backup>');
    expect(extractionRules).toContain('<device-transfer>');
  });

  test('themes no longer reference private AppCompat edit resources', () => {
    expect(lightStyles).toContain(
      '<item name="android:editTextBackground">@android:color/transparent</item>',
    );
    expect(darkStyles).toContain(
      '<item name="android:editTextBackground">@android:color/transparent</item>',
    );
    expect(lightStyles).not.toContain('rn_edit_text_material');
    expect(darkStyles).not.toContain('rn_edit_text_material');
    expect(
      fs.existsSync(
        path.resolve(
          androidMain,
          'res',
          'drawable',
          'rn_edit_text_material.xml',
        ),
      ),
    ).toBe(false);
  });
});
