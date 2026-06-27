const fs = require('fs');
const path = require('path');

const servicePath = path.resolve(__dirname, '..', 'StartForegroundService.js');
let source = fs.readFileSync(servicePath, 'utf8');

const before =
  `              const changed = await newCB(hcb);\n` +
  `              let peerAckAllowed = !changed && type_ === 'text';\n` +
  `              if (changed) {\n` +
  `                previous_clipboard_content_hash = hcb;\n\n` +
  `                await resetReceivingFragments();\n` +
  `                // validate clipboard size\n` +
  `                if (await validateClipboardSize(cb, type_, 'Inbound')) {\n` +
  `                  // set clipboard content\n`;

const after =
  `              const changed = await newCB(hcb);\n` +
  `              let peerAckAllowed = !changed && type_ === 'text';\n` +
  `              if (changed) {\n` +
  `                await resetReceivingFragments();\n` +
  `                // validate clipboard size\n` +
  `                if (await validateClipboardSize(cb, type_, 'Inbound')) {\n` +
  `                  previous_clipboard_content_hash = hcb;\n` +
  `                  // set clipboard content\n`;

if (!source.includes(before)) {
  throw new Error(
    'Unable to apply P2P validation ordering: expected receive block was not found',
  );
}
source = source.replace(before, after);

fs.writeFileSync(servicePath, source, 'utf8');
console.log('Prepared validated P2P clipboard acknowledgement ordering.');
