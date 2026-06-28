const fs = require('fs');
const path = require('path');

const appPath = path.resolve(__dirname, '..', 'App.js');
let source = fs.readFileSync(appPath, 'utf8');

const before = `      const latest = JSON.parse(json);

      if (latest.wsIsRunning === 'true') {`;
const after = `      const latest = JSON.parse(json);

      // The foreground service may resume before or independently of this UI.
      // Keep the Start/Stop control synchronized with the persisted runtime state.
      if (
        latest.wsIsRunning === 'true' ||
        latest.wsIsRunning === 'false'
      ) {
        setWsIsRunning(latest.wsIsRunning);
      }

      if (latest.wsIsRunning === 'true') {`;

if (!source.includes(before)) {
  throw new Error('Expected UI polling block was not found');
}
source = source.replace(before, after);
fs.writeFileSync(appPath, source, 'utf8');
console.log('Prepared runtime-synchronized Start/Stop control state.');
