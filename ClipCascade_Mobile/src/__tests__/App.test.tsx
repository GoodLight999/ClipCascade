/**
 * @format
 *
 * Source-contract tests for the React Native entrypoint. Rendering App in Jest
 * without an Android runtime previously produced a false smoke test because
 * Notifee and the other native modules do not exist in Node.
 */

import fs from 'fs';
import path from 'path';

const appSource = fs.readFileSync(path.resolve(__dirname, '..', 'App.js'), 'utf8');

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
