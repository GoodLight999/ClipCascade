const fs = require('fs');
const path = require('path');

const appPath = path.resolve(__dirname, '..', 'App.js');
let source = fs.readFileSync(appPath, 'utf8');

function replaceRequired(before, after) {
  if (!source.includes(before)) {
    throw new Error(`Expected App.js text was not found: ${before.slice(0, 120)}`);
  }
  source = source.replace(before, after);
}

replaceRequired(
  `        // get foreground service status from work manager
        let foregroundServiceStoppedRunning = await getDataFromAsyncStorage(
          'foreground_service_stopped_running',
        );`,
  `        // Track a persisted-running session whose foreground service was lost
        // during an app update, process replacement, or OS termination.
        let foregroundServiceNeedsRestart = false;
        let foregroundServiceStoppedRunning = await getDataFromAsyncStorage(
          'foreground_service_stopped_running',
        );`,
);

replaceRequired(
  `          if (!foregroundServiceIsActive) {
            wsIsRunning_s = 'false';
            await clearFiles();
            await setDataInAsyncStorage(
              'wsStatusMessage',
              '⚠️ Foreground service stopped running',
            );
          }`,
  `          if (!foregroundServiceIsActive) {
            foregroundServiceNeedsRestart = true;
            wsIsRunning_s = 'false';
            await clearFiles();
            await setDataInAsyncStorage(
              'wsStatusMessage',
              tr(
                '♻️ バックグラウンド同期を再開中...',
                '♻️ Restarting background synchronization...',
              ),
            );
          }`,
);

replaceRequired(
  `            if (
              foregroundServiceStoppedRunning &&
              foregroundServiceStoppedRunning === 'true'
            ) {
              foregroundService();
            }`,
  `            if (
              foregroundServiceNeedsRestart ||
              (foregroundServiceStoppedRunning &&
                foregroundServiceStoppedRunning === 'true')
            ) {
              await foregroundService();
            }`,
);

if (source.includes('Foreground service stopped running')) {
  throw new Error('The obsolete terminal foreground-service warning remains');
}

fs.writeFileSync(appPath, source, 'utf8');

function replaceResource(resourcePath, before, after) {
  let resource = fs.readFileSync(resourcePath, 'utf8');
  if (!resource.includes(before)) {
    throw new Error(`Expected Android resource text was not found: ${before}`);
  }
  resource = resource.replace(before, after);
  fs.writeFileSync(resourcePath, resource, 'utf8');
}

const defaultStrings = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'res',
  'values',
  'strings.xml',
);
const japaneseStrings = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'res',
  'values-ja',
  'strings.xml',
);

replaceResource(
  defaultStrings,
  '<string name="background_sharing_body">Set up Android permissions without ADB. Clipboard text is captured through Accessibility, while verification codes are read from notifications and relayed to Windows.</string>',
  '<string name="background_sharing_body">Set up Android permissions without ADB. Clipboard text is captured through Accessibility, while verification codes are read from notifications and relayed to connected devices.</string>',
);
replaceResource(
  defaultStrings,
  '<string name="clipboard_test_send">Send a test string to Windows</string>',
  '<string name="clipboard_test_send">Send a test string to connected devices</string>',
);
replaceResource(
  japaneseStrings,
  '<string name="background_sharing_body">ADBを使わずにAndroidの権限を設定します。通常のコピーはユーザー補助、認証コードは通知アクセスから取得してWindowsへ送ります。</string>',
  '<string name="background_sharing_body">ADBを使わずにAndroidの権限を設定します。通常のコピーはユーザー補助、認証コードは通知アクセスから取得して接続中の端末へ送ります。</string>',
);
replaceResource(
  japaneseStrings,
  '<string name="clipboard_test_send">テスト文字列をWindowsへ送る</string>',
  '<string name="clipboard_test_send">接続中の端末へテスト文字列を送る</string>',
);

console.log(
  'Prepared automatic foreground-service restart and target-neutral relay wording.',
);
