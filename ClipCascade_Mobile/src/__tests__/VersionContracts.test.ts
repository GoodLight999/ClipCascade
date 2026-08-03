import fs from 'fs';
import path from 'path';

const sourceRoot = path.resolve(__dirname, '..');
const repositoryRoot = path.resolve(sourceRoot, '..', '..');
const appSource = fs.readFileSync(path.resolve(sourceRoot, 'App.js'), 'utf8');
const gradleSource = fs.readFileSync(
  path.resolve(sourceRoot, 'android', 'app', 'build.gradle'),
  'utf8',
);
const versionMetadata = JSON.parse(
  fs.readFileSync(path.resolve(repositoryRoot, 'version.json'), 'utf8'),
);

describe('product version contract', () => {
  test('JavaScript, repository metadata, and APK manifest use one version', () => {
    const androidVersion = String(versionMetadata.android);
    const parts = androidVersion.split('.').map(Number);
    expect(parts).toHaveLength(3);
    expect(parts.every(Number.isInteger)).toBe(true);

    const expectedVersionCode = parts[0] * 10000 + parts[1] * 100 + parts[2];

    expect(appSource).toContain(`const APP_VERSION = '${androidVersion}';`);
    expect(gradleSource).toContain(`versionName "${androidVersion}"`);
    expect(gradleSource).toContain(`versionCode ${expectedVersionCode}`);
    expect(gradleSource).not.toContain('versionName "1.0"');
    expect(gradleSource).not.toContain('versionCode 1\n');
  });
});
