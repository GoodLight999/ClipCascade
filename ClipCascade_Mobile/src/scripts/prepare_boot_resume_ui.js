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

const bootRowPattern = /\n\s*<View style=\{styles\.row\}>[\s\S]*?data\.relaunch_on_boot === 'true'[\s\S]*?handleInputChange\('relaunch_on_boot',[\s\S]*?\n\s*<\/View>/g;
const bootRows = source.match(bootRowPattern) || [];
if (bootRows.length !== 1) {
  throw new Error(`Expected one legacy boot-resume row, found ${bootRows.length}`);
}
source = source.replace(bootRowPattern, '');

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
