import fs from 'fs';
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
