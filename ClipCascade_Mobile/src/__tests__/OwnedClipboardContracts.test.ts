import fs from 'fs';
import path from 'path';

const sourceRoot = path.resolve(__dirname, '..');
const kotlinRoot = path.resolve(
  sourceRoot,
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
);

const foregroundService = fs.readFileSync(
  path.resolve(sourceRoot, 'StartForegroundService.js'),
  'utf8',
);
const nativeBridge = fs.readFileSync(
  path.resolve(kotlinRoot, 'NativeBridgeModule.kt'),
  'utf8',
);

describe('app-owned clipboard writes', () => {
  test('shared and inbound text use the marked native writer', () => {
    expect(nativeBridge).toContain('fun setAppOwnedTextClipboard');
    expect(nativeBridge).toContain('appOwnedClipboardExtras()');
    expect(nativeBridge).toContain('APP_OWNED_CLIP_MARKER');
    expect(nativeBridge).toContain('PersistableBundle');
    expect(foregroundService).toContain(
      'await NativeBridgeModule.setAppOwnedTextClipboard(clipContent)',
    );
    expect(foregroundService).toContain(
      'await NativeBridgeModule.setAppOwnedTextClipboard(cb)',
    );
    expect(foregroundService).not.toContain('Clipboard.setString');
    expect(foregroundService).not.toContain(
      "import Clipboard from '@react-native-clipboard/clipboard'",
    );
  });

  test('inbound images are marked instead of using a one-shot state flag', () => {
    expect(nativeBridge).toMatch(
      /ClipData\.newUri\([\s\S]*description\.extras = appOwnedClipboardExtras\(\)/,
    );
    expect(foregroundService).not.toContain('block_image_once');
  });

  test('only the active service generation can stop native monitoring', () => {
    expect(foregroundService).toContain('let clipboardListenerGeneration = 0');
    expect(foregroundService).toContain(
      'let activeClipboardOnChangeSubscription = null',
    );
    expect(foregroundService).toContain(
      'const listenerGeneration = ++clipboardListenerGeneration',
    );
    expect(foregroundService).toContain(
      'let instanceClipboardOnChangeSubscription = null',
    );
    expect(foregroundService).toContain(
      'if (listenerGeneration !== clipboardListenerGeneration) return',
    );
    expect(foregroundService).toContain(
      'instanceClipboardOnChangeSubscription = clipboardListener.addListener',
    );
    expect(foregroundService).toContain(
      'NativeModules.ClipboardListener?.stopListening?.()',
    );
    expect(foregroundService).not.toContain(
      "DeviceEventEmitter.removeAllListeners('onClipboardChange')",
    );
  });
});
