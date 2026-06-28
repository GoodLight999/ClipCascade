const fs = require('fs');
const path = require('path');

const appPath = path.resolve(__dirname, '..', 'App.js');
let source = fs.readFileSync(appPath, 'utf8');

const previousVersion = "const APP_VERSION = '3.2.1-extended.3';";
const currentVersion = "const APP_VERSION = '3.2.1-extended.7';";

if (!source.includes(previousVersion)) {
  throw new Error('Expected transformed Extended version was not found');
}
source = source.replace(previousVersion, currentVersion);
fs.writeFileSync(appPath, source, 'utf8');
console.log('Prepared ClipCascade Extended 3.2.1-extended.7 identity.');
