from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
source_root = root / 'ClipCascade_Mobile/src'
app_path = source_root / 'App.js'
test_path = source_root / '__tests__/NotificationPermissionContracts.test.ts'


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, found {count}')
    return text.replace(old, new, 1)


app = app_path.read_text(encoding='utf-8')
app = once(
    app,
    '  PermissionsAndroid,\n  StyleSheet,\n',
    '  PermissionsAndroid,\n  Platform,\n  StyleSheet,\n',
    'Platform import',
)
app = once(
    app,
    '''        await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
        );
''',
    '''        if (Platform.Version >= 33) {
          await PermissionsAndroid.request(
            PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
          );
        }
''',
    'Android 13 notification permission guard',
)
if app.count('PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS') != 1:
    raise SystemExit('unexpected POST_NOTIFICATIONS request count')
app_path.write_text(app, encoding='utf-8')

test_path.write_text(
    '''import fs from 'fs';
import path from 'path';

const appSource = fs.readFileSync(
  path.resolve(__dirname, '..', 'App.js'),
  'utf8',
);

describe('notification permission contract', () => {
  test('POST_NOTIFICATIONS is requested only on Android 13 or newer', () => {
    expect(appSource).toContain('Platform,');
    expect(appSource).toContain('if (Platform.Version >= 33) {');
    expect(appSource).toMatch(
      /if \(Platform\.Version >= 33\) \{[\s\S]*PermissionsAndroid\.request\([\s\S]*POST_NOTIFICATIONS/,
    );
    expect(
      appSource.match(/PermissionsAndroid\.PERMISSIONS\.POST_NOTIFICATIONS/g),
    ).toHaveLength(1);
  });
});
''',
    encoding='utf-8',
)

print('Applied API-33 notification permission guard.')
