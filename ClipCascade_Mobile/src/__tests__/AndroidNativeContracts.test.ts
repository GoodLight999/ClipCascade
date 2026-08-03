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
  test('overlay owns the unified request until teardown', () => {
    expect(backgroundCapture).toContain('var overlayOwnsCompletion = false');
    expect(backgroundCapture).toContain(
      'ClipboardFloatingActivity.getIntent(appContext, source)',
    );
    expect(backgroundCapture).toContain('overlayOwnsCompletion = true');
    expect(backgroundCapture).toContain('if (!overlayOwnsCompletion)');
    expect(backgroundCapture).toContain('fun completeOverlay(context: Context)');

    expect(floatingActivity).toContain('EXTRA_TRIGGER_SOURCE');
    expect(floatingActivity).toContain('completeCoordinatorOnce()');
    expect(floatingActivity).toContain(
      'BackgroundClipboardCapture.completeOverlay(this)',
    );
    expect(floatingActivity).toMatch(
      /override fun onDestroy\(\)[\s\S]*completeCoordinatorOnce\(\)/,
    );
  });

  test('cold-start shares are queued until React initialization', () => {
    expect(mainActivity).toContain('pendingReactEvents.addLast(event)');
    expect(mainActivity).toContain('ReactInstanceEventListener');
    expect(mainActivity).toContain('flushPendingReactEvents(context)');
    expect(mainActivity).toContain('createReactContextInBackground()');
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

  test('hidden clipboard API use is explicit and API-26-safe', () => {
    expect(shizukuUserService).toContain('@SuppressLint("PrivateApi")');
    expect(shizukuUserService).toContain(
      'private const val DEFAULT_DEVICE_ID = 0',
    );
    expect(shizukuUserService).not.toContain('Context.DEVICE_ID_DEFAULT');
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
