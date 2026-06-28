const fs = require('fs');
const path = require('path');

const appPath = path.resolve(__dirname, '..', 'App.js');
let source = fs.readFileSync(appPath, 'utf8');

function replaceRequired(before, after) {
  if (!source.includes(before)) {
    throw new Error(`Expected App.js text was not found: ${before.slice(0, 100)}`);
  }
  source = source.replace(before, after);
}

const bootMarker = "data.relaunch_on_boot === 'true'";
const markerIndex = source.indexOf(bootMarker);
if (markerIndex < 0 || source.indexOf(bootMarker, markerIndex + 1) >= 0) {
  throw new Error('Expected exactly one legacy boot-resume checkbox marker');
}

const rowStart = source.lastIndexOf('<View style={styles.row}>', markerIndex);
const rowEndStart = source.indexOf('</View>', markerIndex);
if (rowStart < 0 || rowEndStart < 0) {
  throw new Error('Unable to isolate the legacy boot-resume row');
}
const rowEnd = rowEndStart + '</View>'.length;
source = source.slice(0, rowStart) + source.slice(rowEnd);

replaceRequired(
  `        // Save data in async storage
        await setAsyncStorage(data_s);`,
  `        // The always-visible native switch is authoritative for boot resume.
        // Refresh it immediately before the login form persists its full state,
        // so a stale React state object cannot overwrite the native setting.
        const persistedRelaunchOnBoot = await getDataFromAsyncStorage(
          'relaunch_on_boot',
        );
        if (persistedRelaunchOnBoot !== null) {
          data_s.relaunch_on_boot = persistedRelaunchOnBoot;
        }

        // Save data in async storage
        await setAsyncStorage(data_s);`,
);

fs.writeFileSync(appPath, source, 'utf8');
console.log('Prepared canonical boot-resume control without duplicate settings.');
