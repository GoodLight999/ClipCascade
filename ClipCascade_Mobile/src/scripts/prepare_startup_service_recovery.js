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
console.log('Prepared automatic foreground-service restart after process replacement.');
