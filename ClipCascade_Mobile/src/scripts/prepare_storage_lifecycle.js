const fs = require('fs');
const path = require('path');

const target = path.resolve(__dirname, '..', 'android', 'app', 'src', 'main', 'java', 'com', 'clipcascade', 'AsyncStorageBridge.kt');
let source = fs.readFileSync(target, 'utf8');
source = source.replace('            db?.close()\n            db = null', '            db = null');
fs.writeFileSync(target, source, 'utf8');
