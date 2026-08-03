from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, found {count}')
    return text.replace(old, new, 1)


bridge_path = root / 'ClipCascade_Mobile/src/android/app/src/main/java/com/clipcascade/NativeBridgeModule.kt'
bridge = bridge_path.read_text(encoding='utf-8')
bridge = once(
    bridge,
    'import android.graphics.BitmapFactory\n',
    'import android.graphics.BitmapFactory\nimport android.os.PersistableBundle\n',
    'PersistableBundle import',
)
bridge = once(
    bridge,
    '''    @ReactMethod
    fun copyBase64ImageToClipboardUsingCache(base64String: String, promise: Promise) {
''',
    '''    @ReactMethod
    fun setAppOwnedTextClipboard(content: String, promise: Promise) {
        try {
            val clipData = ClipData.newPlainText("ClipCascade text", content).apply {
                description.extras = appOwnedClipboardExtras()
            }
            val clipboard = reactApplicationContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clipData)
            promise.resolve(null)
        } catch (error: Exception) {
            promise.reject(
                "CLIPBOARD_WRITE_ERROR",
                "Failed to write app-owned text to the clipboard",
                error
            )
        }
    }

    private fun appOwnedClipboardExtras(): PersistableBundle =
        PersistableBundle().apply {
            putBoolean(ClipboardListenerModule.APP_OWNED_CLIP_MARKER, true)
        }

    @ReactMethod
    fun copyBase64ImageToClipboardUsingCache(base64String: String, promise: Promise) {
''',
    'app-owned text writer',
)
bridge = once(
    bridge,
    '''            val clipData = ClipData.newUri(reactApplicationContext.contentResolver, "Image", imageUri)
''',
    '''            val clipData = ClipData.newUri(
                reactApplicationContext.contentResolver,
                "Image",
                imageUri
            ).apply {
                description.extras = appOwnedClipboardExtras()
            }
''',
    'app-owned image marker',
)
bridge_path.write_text(bridge, encoding='utf-8')

service_path = root / 'ClipCascade_Mobile/src/StartForegroundService.js'
service = service_path.read_text(encoding='utf-8')
service = once(
    service,
    "import Clipboard from '@react-native-clipboard/clipboard';\n\n",
    '',
    'Clipboard import',
)
service = once(
    service,
    '''const {
  getP2SRetryDelayMs,
  DEFAULT_MAX_DELAY_MS,
} = require('./P2SRetryPolicy');

''',
    '''const {
  getP2SRetryDelayMs,
  DEFAULT_MAX_DELAY_MS,
} = require('./P2SRetryPolicy');

let clipboardListenerGeneration = 0;
let activeClipboardOnChangeSubscription = null;

function cleanupActiveClipboardListeners() {
  DeviceEventEmitter.removeAllListeners('SHARED_TEXT');
  DeviceEventEmitter.removeAllListeners('SHARED_IMAGE');
  DeviceEventEmitter.removeAllListeners('SHARED_FILES');
  activeClipboardOnChangeSubscription?.remove();
  activeClipboardOnChangeSubscription = null;
  NativeModules.ClipboardListener?.stopListening?.();
}

''',
    'generation globals',
)
service = once(
    service,
    '''function cleanupClipboardListeners() {
  DeviceEventEmitter.removeAllListeners('SHARED_TEXT');
  DeviceEventEmitter.removeAllListeners('SHARED_IMAGE');
  DeviceEventEmitter.removeAllListeners('SHARED_FILES');
  DeviceEventEmitter.removeAllListeners('onClipboardChange');
}

''',
    '',
    'obsolete global clipboard cleanup',
)
service = once(
    service,
    '''        const textEncoder = new TextEncoder();
        const textDecoder = new TextDecoder();

''',
    '''        const textEncoder = new TextEncoder();
        const textDecoder = new TextDecoder();

        cleanupActiveClipboardListeners();
        const listenerGeneration = ++clipboardListenerGeneration;
        let instanceClipboardOnChangeSubscription = null;
        const cleanupClipboardListeners = () => {
          const subscription = instanceClipboardOnChangeSubscription;
          subscription?.remove();
          instanceClipboardOnChangeSubscription = null;

          // A superseded service instance may clean up its own subscription,
          // but must not stop or remove listeners owned by the current instance.
          if (listenerGeneration !== clipboardListenerGeneration) return;

          DeviceEventEmitter.removeAllListeners('SHARED_TEXT');
          DeviceEventEmitter.removeAllListeners('SHARED_IMAGE');
          DeviceEventEmitter.removeAllListeners('SHARED_FILES');
          if (activeClipboardOnChangeSubscription === subscription) {
            activeClipboardOnChangeSubscription = null;
          }
          NativeModules.ClipboardListener?.stopListening?.();
        };

''',
    'generation-local cleanup',
)
service = once(
    service,
    '        let block_image_once = false;\n',
    '',
    'block_image_once declaration',
)
service = once(
    service,
    '''              /**
               * Sometimes `Clipboard.setString` is invoked before the app is fully opened, leading to an unauthorized state.
               * To handle this, implement a fail-safe mechanism that retries sending clipboard content only when it hasn't been successfully sent yet.
               * If both events are triggered successfully, the content won't be sent twice because the same content is hashed, ensuring that identical data is only processed once.
               */
''',
    '''              // Write a marked local copy for user visibility while sending the
              // shared payload directly through the existing transport. The native
              // marker prevents automatic recapture of this app-owned write.
''',
    'obsolete shared-text timing comment',
)
service = once(
    service,
    '''              Clipboard.setString(clipContent);
              await sendClipBoard(clipContent, 'text');
''',
    '''              await NativeBridgeModule.setAppOwnedTextClipboard(clipContent);
              await sendClipBoard(clipContent, 'text');
''',
    'shared text writer',
)
service = once(
    service,
    '        const clipboardOnChange = clipboardListener.addListener(\n',
    '        instanceClipboardOnChangeSubscription = clipboardListener.addListener(\n',
    'native clipboard subscription',
)
service = once(
    service,
    '''        );

        const clearFiles = async (expensiveCall = false) => {
''',
    '''        );
        activeClipboardOnChangeSubscription =
          instanceClipboardOnChangeSubscription;

        const clearFiles = async (expensiveCall = false) => {
''',
    'active subscription assignment',
)
if service.count('Clipboard.setString(cb);') != 2:
    raise SystemExit(
        'inbound text writes: expected 2 occurrences, found '
        + str(service.count('Clipboard.setString(cb);'))
    )
service = service.replace(
    'Clipboard.setString(cb);',
    'await NativeBridgeModule.setAppOwnedTextClipboard(cb);',
)
true_count = service.count('block_image_once = true;')
if true_count != 2:
    raise SystemExit(
        'image marker assignments: expected 2 occurrences, found '
        + str(true_count)
    )
service_lines = [
    line
    for line in service.splitlines(True)
    if 'let block_image_once = false;' not in line
    and 'block_image_once = true;' not in line
    and 'block_image_once = false;' not in line
]


def unwrap_one_shot_guard(lines):
    output = []
    index = 0
    replacements = 0
    while index < len(lines):
        line = lines[index]
        if line.strip() != 'if (block_image_once) {':
            output.append(line)
            index += 1
            continue

        indent = line[: len(line) - len(line.lstrip())]
        if index + 1 >= len(lines) or lines[index + 1].strip() != '} else {':
            raise SystemExit('unexpected one-shot image guard shape')

        body_start = index + 2
        depth = 1
        cursor = body_start
        while cursor < len(lines):
            current = lines[cursor]
            depth += current.count('{') - current.count('}')
            if depth == 0:
                break
            cursor += 1
        if cursor >= len(lines):
            raise SystemExit('unterminated one-shot image guard')

        for body_line in lines[body_start:cursor]:
            if body_line.startswith(indent + '  '):
                output.append(body_line[2:])
            else:
                output.append(body_line)
        replacements += 1
        index = cursor + 1

    return output, replacements


service_lines, guard_count = unwrap_one_shot_guard(service_lines)
if guard_count != 2:
    raise SystemExit(f'one-shot image guards: expected 2, found {guard_count}')
service = ''.join(service_lines)
if 'block_image_once' in service:
    raise SystemExit('block_image_once remains')
service = service.replace(
    '''              if (clipboardOnChange) {
                clipboardOnChange.remove();
              }
''',
    '''              instanceClipboardOnChangeSubscription?.remove();
              instanceClipboardOnChangeSubscription = null;
''',
)
service = once(
    service,
    '''      } catch (error) {
        await setDataInAsyncStorage('wsStatusMessage', '❌ Error:' + error);
        cleanupClipboardListeners();
        await notifee.stopForegroundService();
      }
''',
    '''      } catch (error) {
        await setDataInAsyncStorage('wsStatusMessage', '❌ Error:' + error);
        cleanupActiveClipboardListeners();
        await notifee.stopForegroundService();
      }
''',
    'outer cleanup scope',
)
service_path.write_text(service, encoding='utf-8')

print('Applied generation-safe app-owned clipboard transformation.')
