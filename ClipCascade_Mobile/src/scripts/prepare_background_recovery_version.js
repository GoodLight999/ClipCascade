const fs = require('fs');
const path = require('path');

const gradlePath = path.resolve(__dirname, '..', 'android', 'app', 'build.gradle');
let gradle = fs.readFileSync(gradlePath, 'utf8');

const previousCode = '        versionCode 320108';
const currentCode = '        versionCode 320127';
const previousName = '        versionName "3.2.1-extended.4"';
const currentName = '        versionName "3.2.1-extended.22-alpha.1"';

if (!gradle.includes(previousCode) || !gradle.includes(previousName)) {
  throw new Error('Expected Android update-probe version was not found');
}

gradle = gradle.replace(previousCode, currentCode);
gradle = gradle.replace(previousName, currentName);
fs.writeFileSync(gradlePath, gradle, 'utf8');

console.log('Prepared Android Extended alpha.22 native service and companion notification recovery build.');
