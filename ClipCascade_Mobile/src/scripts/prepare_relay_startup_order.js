const fs = require('fs');
const path = require('path');

const servicePath = path.resolve(__dirname, '..', 'StartForegroundService.js');
let source = fs.readFileSync(servicePath, 'utf8');

const eagerResume =
  `        const { NativeBridgeModule, RelaySettingsModule } = NativeModules;\n` +
  `        if (RelaySettingsModule?.resumeRelayQueues) {\n` +
  `          await RelaySettingsModule.resumeRelayQueues();\n` +
  `        }\n`;
const deferredDeclaration =
  `        const { NativeBridgeModule, RelaySettingsModule } = NativeModules;\n`;

if (!source.includes(eagerResume)) {
  throw new Error('Expected eager relay queue resume block was not found');
}
source = source.replace(eagerResume, deferredDeclaration);

const listenerBoundary =
  `        });\n\n` +
  `        // Event listener triggered when image is shared with the app.`;
const deferredResume =
  `        });\n\n` +
  `        // Register listeners before waking persistent native queues.\n` +
  `        if (RelaySettingsModule?.resumeRelayQueues) {\n` +
  `          await RelaySettingsModule.resumeRelayQueues();\n` +
  `        }\n\n` +
  `        // Event listener triggered when image is shared with the app.`;

if (!source.includes(listenerBoundary)) {
  throw new Error('Expected SHARED_TEXT listener boundary was not found');
}
source = source.replace(listenerBoundary, deferredResume);

fs.writeFileSync(servicePath, source, 'utf8');
console.log('Moved relay queue resume after SHARED_TEXT listener registration.');
