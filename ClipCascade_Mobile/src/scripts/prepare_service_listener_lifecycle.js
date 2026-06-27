const fs = require('fs');
const path = require('path');

const servicePath = path.resolve(__dirname, '..', 'StartForegroundService.js');
let source = fs.readFileSync(servicePath, 'utf8');

function replaceExact(before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Unable to apply ${label}: expected source text was not found`);
  }
  source = source.replace(before, after);
}

replaceExact(
  `function cleanupClipboardListeners() {\n` +
    `  DeviceEventEmitter.removeAllListeners('SHARED_TEXT');\n` +
    `  DeviceEventEmitter.removeAllListeners('SHARED_IMAGE');\n` +
    `  DeviceEventEmitter.removeAllListeners('SHARED_FILES');\n` +
    `  DeviceEventEmitter.removeAllListeners('onClipboardChange');\n` +
    `}\n`,
  `function cleanupClipboardListeners(subscriptions) {\n` +
    `  for (const subscription of subscriptions) {\n` +
    `    try {\n` +
    `      subscription?.remove?.();\n` +
    `    } catch (error) {\n` +
    `      // A stale subscription is already detached.\n` +
    `    }\n` +
    `  }\n` +
    `  subscriptions.length = 0;\n` +
    `}\n`,
  'scoped listener cleanup function',
);

replaceExact(
  `        let isP2PStatusMsgChanged = false;\n`,
  `        let isP2PStatusMsgChanged = false;\n` +
    `        const serviceEventSubscriptions = [];\n`,
  'service subscription collection',
);

replaceExact(
  `        DeviceEventEmitter.addListener('SHARED_TEXT', async event => {\n`,
  `        serviceEventSubscriptions.push(\n` +
    `          DeviceEventEmitter.addListener('SHARED_TEXT', async event => {\n`,
  'shared text subscription opening',
);
replaceExact(
  `        });\n\n` +
    `        // Register listeners before waking persistent native queues.\n`,
  `          }),\n` +
    `        );\n\n` +
    `        // Register listeners before waking persistent native queues.\n`,
  'shared text subscription closing',
);

replaceExact(
  `        DeviceEventEmitter.addListener('SHARED_IMAGE', async event => {\n`,
  `        serviceEventSubscriptions.push(\n` +
    `          DeviceEventEmitter.addListener('SHARED_IMAGE', async event => {\n`,
  'shared image subscription opening',
);
replaceExact(
  `        });\n\n` +
    `        // Event listener triggered when files are shared with the app.\n`,
  `          }),\n` +
    `        );\n\n` +
    `        // Event listener triggered when files are shared with the app.\n`,
  'shared image subscription closing',
);

replaceExact(
  `        DeviceEventEmitter.addListener('SHARED_FILES', async event => {\n`,
  `        serviceEventSubscriptions.push(\n` +
    `          DeviceEventEmitter.addListener('SHARED_FILES', async event => {\n`,
  'shared files subscription opening',
);
replaceExact(
  `        });\n\n` +
    `        //clipboard monitor\n`,
  `          }),\n` +
    `        );\n\n` +
    `        //clipboard monitor\n`,
  'shared files subscription closing',
);

const cleanupCalls = source.match(/cleanupClipboardListeners\(\);/g) || [];
if (cleanupCalls.length === 0) {
  throw new Error('Unable to apply scoped cleanup calls: no call sites found');
}
source = source.replace(
  /cleanupClipboardListeners\(\);/g,
  'cleanupClipboardListeners(serviceEventSubscriptions);',
);

fs.writeFileSync(servicePath, source, 'utf8');
console.log(
  `Scoped ${cleanupCalls.length} listener cleanup call(s) to their service generation.`,
);
