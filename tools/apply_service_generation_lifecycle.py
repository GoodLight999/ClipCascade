from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
source_root = root / 'ClipCascade_Mobile/src'
service_path = source_root / 'StartForegroundService.js'
test_path = source_root / '__tests__/OwnedClipboardContracts.test.ts'


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, found {count}')
    return text.replace(old, new, 1)


service = service_path.read_text(encoding='utf-8')
service = once(
    service,
    '  notifee.registerForegroundService(notification => {\n'
    '    return new Promise(async () => {\n'
    '      try {\n',
    '  notifee.registerForegroundService(notification => {\n'
    '    return new Promise(async () => {\n'
    '      let serviceGeneration = 0;\n'
    '      let cleanupServiceInstance = async () => {};\n'
    '      try {\n',
    'foreground service outer lifecycle state',
)
service = once(
    service,
    '        const listenerGeneration = ++clipboardListenerGeneration;\n',
    '        const listenerGeneration = ++clipboardListenerGeneration;\n'
    '        serviceGeneration = listenerGeneration;\n',
    'service generation assignment',
)
old_stop = '''        const stopServices = async () => {
          if (server_mode === 'P2S') {
            await stopServicesP2S();
          } else if (server_mode === 'P2P') {
            await stopServicesP2P();
          }

          cleanupClipboardListeners();
        };
'''
new_stop = '''        cleanupServiceInstance = async () => {
          try {
            if (server_mode === 'P2S' && stopServicesP2S !== null) {
              await stopServicesP2S();
            } else if (server_mode === 'P2P' && stopServicesP2P !== null) {
              await stopServicesP2P();
            }
          } finally {
            cleanupClipboardListeners();
          }
        };
        const stopServices = cleanupServiceInstance;
'''
service = once(service, old_stop, new_stop, 'generation-owned stopServices')

poll_start = service.index('        async function pollFlagsLoop() {')
poll_end = service.index('        pollFlagsLoop();', poll_start)
poll_block = service[poll_start:poll_end]
if poll_block.count('          while (true) {') != 1:
    raise SystemExit(
        'poll loop: expected one while(true), found '
        + str(poll_block.count('          while (true) {'))
    )
poll_block = poll_block.replace(
    '          while (true) {',
    '          while (listenerGeneration === clipboardListenerGeneration) {',
    1,
)
poll_tail_old = '''            await sleep(1000);
          }
        }

'''
poll_tail_new = '''            await sleep(1000);
          }

          if (listenerGeneration !== clipboardListenerGeneration) {
            await stopServices();
          }
        }

'''
if poll_block.count(poll_tail_old) != 1:
    raise SystemExit('poll loop tail mismatch')
poll_block = poll_block.replace(poll_tail_old, poll_tail_new, 1)
service = service[:poll_start] + poll_block + service[poll_end:]

service = once(
    service,
    '''        pollFlagsLoop();
      } catch (error) {
        await setDataInAsyncStorage('wsStatusMessage', '❌ Error:' + error);
        cleanupActiveClipboardListeners();
        await notifee.stopForegroundService();
      }
''',
    '''        pollFlagsLoop().catch(async error => {
          try {
            await setDataInAsyncStorage(
              'wsStatusMessage',
              '❌ Foreground service poll error:' + error,
            );
            await cleanupServiceInstance();
          } finally {
            if (serviceGeneration === clipboardListenerGeneration) {
              await notifee.stopForegroundService();
            }
          }
        });
      } catch (error) {
        try {
          await setDataInAsyncStorage('wsStatusMessage', '❌ Error:' + error);
          await cleanupServiceInstance();
        } finally {
          if (serviceGeneration === clipboardListenerGeneration) {
            await notifee.stopForegroundService();
          }
        }
      }
''',
    'poll rejection and outer catch lifecycle',
)

for forbidden in [
    'while (true)',
    '\n        pollFlagsLoop();\n',
    "cleanupActiveClipboardListeners();\n        await notifee.stopForegroundService();",
]:
    if forbidden in service:
        raise SystemExit(f'forbidden legacy lifecycle pattern remains: {forbidden!r}')
service_path.write_text(service, encoding='utf-8')

test_source = test_path.read_text(encoding='utf-8')
insert_before = '  test(\'only the active service generation can stop native monitoring\', () => {\n'
new_contract = '''  test('polling and failure cleanup belong to their service generation', () => {
    expect(foregroundService).toContain('let serviceGeneration = 0');
    expect(foregroundService).toContain(
      'let cleanupServiceInstance = async () => {}',
    );
    expect(foregroundService).toContain(
      'serviceGeneration = listenerGeneration',
    );
    expect(foregroundService).toContain(
      'while (listenerGeneration === clipboardListenerGeneration)',
    );
    expect(foregroundService).toContain('pollFlagsLoop().catch(async error =>');
    expect(foregroundService).toContain('await cleanupServiceInstance()');
    expect(foregroundService).toContain(
      'if (serviceGeneration === clipboardListenerGeneration)',
    );
    expect(foregroundService).not.toContain('while (true)');
    expect(foregroundService).not.toContain('\\n        pollFlagsLoop();\\n');
  });

'''
test_source = once(
    test_source,
    insert_before,
    new_contract + insert_before,
    'foreground generation lifecycle contract',
)
test_path.write_text(test_source, encoding='utf-8')

print('Applied generation-owned foreground-service lifecycle transformation.')
