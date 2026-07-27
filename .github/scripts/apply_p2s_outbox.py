from pathlib import Path

path = Path("ClipCascade_Mobile/src/StartForegroundService.js")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count == 1:
        text = text.replace(old, new, 1)
        return
    if count == 0 and new in text:
        return
    raise SystemExit(
        f"{label}: expected one old block or an already-applied new block; old count={count}"
    )


def replace_between(start: str, end: str, replacement: str, label: str) -> None:
    global text
    if replacement in text:
        return
    start_count = text.count(start)
    if start_count != 1:
        raise SystemExit(f"{label}: expected one start marker, found {start_count}")
    start_index = text.index(start)
    end_index = text.index(end, start_index)
    text = text[:start_index] + replacement + text[end_index:]


replace_once(
    """} from './AsyncStorageManagement';

function cleanupClipboardListeners() {""",
    """} from './AsyncStorageManagement';
const { P2STextOutbox } = require('./P2STextOutbox');

function cleanupClipboardListeners() {""",
    "outbox import",
)

replace_once(
    """          websocket_url,
          cipher_enabled,
          maxsize: maxsizeStr,""",
    """          websocket_url,
          username,
          hashed_password,
          cipher_enabled,
          maxsize: maxsizeStr,""",
    "outbox scope destructuring",
)

replace_once(
    """          'websocket_url',
          'cipher_enabled',
          'maxsize',""",
    """          'websocket_url',
          'username',
          'hashed_password',
          'cipher_enabled',
          'maxsize',""",
    "outbox scope storage keys",
)

replace_once(
    """        if (server_mode === 'P2S') {
          // websocket stomp client""",
    """        if (server_mode === 'P2S') {
          const outboxScopeFingerprint = await hashCB(
            [websocket_url, username || '', cipher_enabled, hashed_password || ''].join('\\u0000'),
          );
          const p2sTextOutbox = new P2STextOutbox({
            storage: {
              read: getDataFromAsyncStorage,
              write: setDataInAsyncStorage,
            },
            storageKey: `p2s_text_outbox_v1_${outboxScopeFingerprint}`,
            maxItems: 20,
            maxBytes: Math.max(
              256 * 1024,
              Math.min(
                Number.isFinite(maxsize) && maxsize > 0 ? maxsize * 4 : 4 * 1024 * 1024,
                8 * 1024 * 1024,
              ),
            ),
          });
          await p2sTextOutbox.load();

          let p2sTextDrainPromise = null;
          let p2sTextEchoTimer = null;

          const updateP2STextOutboxStatus = async () => {
            await setDataInAsyncStorage(
              'p2sTextOutboxStatus',
              await p2sTextOutbox.snapshot(),
            );
          };

          const clearP2STextEchoTimer = () => {
            if (p2sTextEchoTimer != null) {
              clearTimeout(p2sTextEchoTimer);
              p2sTextEchoTimer = null;
            }
          };

          const releaseP2STextInFlight = async () => {
            clearP2STextEchoTimer();
            const head = await p2sTextOutbox.peek();
            if (head && head.state === 'inflight') {
              await p2sTextOutbox.releaseInFlight(head.id);
            }
            await updateP2STextOutboxStatus();
          };

          const drainP2STextOutbox = async () => {
            if (p2sTextDrainPromise != null) {
              return p2sTextDrainPromise;
            }

            p2sTextDrainPromise = (async () => {
              if (!stompClient || !stompClient.connected) return;

              const head = await p2sTextOutbox.peek();
              if (!head || head.state === 'inflight') return;
              if (!(await p2sTextOutbox.markAttempt(head.id))) return;

              try {
                stompClient.publish({
                  destination: SEND_DESTINATION,
                  body: JSON.stringify({
                    payload: head.wirePayload,
                    type: 'text',
                  }),
                });

                clearP2STextEchoTimer();
                p2sTextEchoTimer = setTimeout(() => {
                  p2sTextEchoTimer = null;
                  p2sTextOutbox
                    .releaseInFlight(head.id)
                    .then(updateP2STextOutboxStatus)
                    .then(drainP2STextOutbox)
                    .catch(async error => {
                      await setDataInAsyncStorage(
                        'wsStatusMessage',
                        '❌ Text outbox retry error: ' + error,
                      );
                    });
                }, 30000);

                await setDataInAsyncStorage(
                  'wsStatusMessage',
                  `📤 Sending queued text (attempt ${head.attempts + 1})`,
                );
              } catch (error) {
                await p2sTextOutbox.releaseInFlight(head.id);
                throw error;
              } finally {
                await updateP2STextOutboxStatus();
              }
            })();

            try {
              return await p2sTextDrainPromise;
            } finally {
              p2sTextDrainPromise = null;
            }
          };

          await updateP2STextOutboxStatus();

          // websocket stomp client""",
    "P2S outbox setup",
)

replace_between(
    "            onConnect: async () => {",
    "            onDisconnect: async () => {",
    """            onConnect: async () => {
              toggle = false;

              const subscription = stompClient.subscribe(
                SUBSCRIPTION_DESTINATION,
                async message => {
                  try {
                    await clearFiles();
                    toggle = false;

                    if (message && message.body) {
                      const body = JSON.parse(message.body);
                      let cb = String(body.payload);
                      const type_ = body.type ?? 'text';

                      if (cipher_enabled === 'true') {
                        try {
                          cb = await decrypt(JSON.parse(cb));
                        } catch (error) {
                          throw new Error(
                            `Encryption must be enabled on all devices if enabled. JSON parsing failed: ${error.message}`,
                          );
                        }
                      }

                      const hcb = await hashCB(cb);
                      const acknowledgedQueuedText =
                        type_ === 'text' &&
                        (await p2sTextOutbox.acknowledgeEcho(hcb));

                      if (acknowledgedQueuedText) {
                        clearP2STextEchoTimer();
                        await updateP2STextOutboxStatus();
                      }

                      if (await newCB(hcb)) {
                        previous_clipboard_content_hash = hcb;

                        if (await validateClipboardSize(cb, type_, 'Inbound')) {
                          if (type_ === 'text') {
                            Clipboard.setString(cb);
                          } else if (type_ === 'image') {
                            await NativeBridgeModule.copyBase64ImageToClipboardUsingCache(
                              cb,
                            );
                            block_image_once = true;
                          } else if (type_ === 'files') {
                            await showFilesDownloadNotification('📥 Download File(s)');
                            files_in_memory = cb;
                            await setDataInAsyncStorage(
                              'filesAvailableToDownload',
                              'true',
                            );
                          }
                        }
                      }

                      if (acknowledgedQueuedText) {
                        await drainP2STextOutbox();
                      }
                    }
                  } catch (e) {
                    await setDataInAsyncStorage(
                      'wsStatusMessage',
                      '❌ Inbound Error: ' + e,
                    );
                  }
                },
              );

              if (!subscription) {
                throw new Error('STOMP subscription was not created');
              }

              await setDataInAsyncStorage(
                'wsStatusMessage',
                '✅ Connected - Subscribed',
              );
              await drainP2STextOutbox();

              if (enable_websocket_status_notification === 'true') {
                if (websocket_status_notification_toggle == true) {
                  websocket_status_notification_toggle = false;
                  await showWebSocketStatusNotification(
                    'WebSocket Connection Restored 🔗',
                  );
                } else {
                  await notifee.cancelNotification(
                    'ClipCascade_WebSocket_Status_Notification_Id',
                  );
                }
              }
            },
""",
    "P2S onConnect and echo acknowledgement",
)

replace_between(
    "            onDisconnect: async () => {",
    "          // start websocket stomp connection",
    """            onDisconnect: async () => {
              block_image_once = false;
              await releaseP2STextInFlight();
              await setDataInAsyncStorage('wsStatusMessage', 'Disconnected');
            },
            onStompError: async frame => {
              block_image_once = false;
              await releaseP2STextInFlight();
              await setDataInAsyncStorage(
                'wsStatusMessage',
                '❌ STOMP Error: ' + JSON.stringify(frame, null, 2),
              );
            },
            onWebSocketError: async event => {
              block_image_once = false;
              await releaseP2STextInFlight();
              await setDataInAsyncStorage(
                'wsStatusMessage',
                '❌ WebSocket Error: ' + JSON.stringify(event, null, 2),
              );
            },
            onWebSocketClose: async event => {
              block_image_once = false;
              await releaseP2STextInFlight();
              const reason = event?.reason || 'closed by client';
              await setDataInAsyncStorage(
                'wsStatusMessage',
                `⚠️ WebSocket Close: ${reason}`,
              );
              if (
                enable_websocket_status_notification === 'true' &&
                websocket_status_notification_toggle == false &&
                (await getDataFromAsyncStorage('wsIsRunning')) === 'true'
              ) {
                websocket_status_notification_toggle = true;
                await showWebSocketStatusNotification(
                  'WebSocket Connection Lost ⛓️‍💥',
                  -1,
                );
              }
            },
          });

          // start websocket stomp connection""",
    "P2S disconnect release",
)

replace_between(
    "          // send clipboard content P2S",
    "          // stop events and connection P2S",
    """          // send clipboard content P2S
          sendClipBoardP2S = async (clipContent, type_ = 'text') => {
            try {
              await clearFiles();

              if (
                (type_ === 'image' && enable_image_sharing === 'false') ||
                (type_ === 'files' && enable_file_sharing === 'false')
              ) {
                return;
              }

              if (!(await validateClipboardSize(clipContent, type_, 'Outbound'))) {
                return;
              }

              if (type_ === 'text') {
                const hcb = await hashCB(clipContent);
                if (!(await newCB(hcb))) return;

                let wirePayload = clipContent;
                if (cipher_enabled === 'true') {
                  wirePayload = await encrypt(wirePayload);
                }
                wirePayload = String(wirePayload);

                const enqueueResult = await p2sTextOutbox.enqueue({
                  wirePayload,
                  contentHash: hcb,
                  wireBytes: Buffer.byteLength(wirePayload, 'utf8'),
                });

                if (enqueueResult.accepted || enqueueResult.duplicate) {
                  previous_clipboard_content_hash = hcb;
                }

                await setDataInAsyncStorage(
                  'p2sTextOutboxStatus',
                  enqueueResult.snapshot,
                );

                if (enqueueResult.tooLarge || enqueueResult.overflow) {
                  await setDataInAsyncStorage(
                    'wsStatusMessage',
                    '⚠️ Text outbox is full; clipboard was not queued',
                  );
                  return;
                }

                if (
                  enqueueResult.accepted &&
                  (!stompClient || !stompClient.connected)
                ) {
                  await setDataInAsyncStorage(
                    'wsStatusMessage',
                    `📥 Text queued while offline (${enqueueResult.snapshot.count})`,
                  );
                }

                await drainP2STextOutbox();
                return;
              }

              if (!(stompClient && stompClient.connected && !toggle)) {
                return;
              }

              if (type_ === 'image') {
                clipContent = await NativeBridgeModule.getFileAsBase64(
                  clipContent,
                );
              } else if (type_ === 'files') {
                const temp = {};
                const file_paths = clipContent
                  .split(',')
                  .filter(item => item.trim() !== '');

                for (const file_path of file_paths) {
                  temp[await NativeBridgeModule.getFileName(file_path)] =
                    await NativeBridgeModule.getFileAsBase64(file_path);
                }
                clipContent = JSON.stringify(temp);
              }

              const hcb = await hashCB(clipContent);
              if (await newCB(hcb)) {
                previous_clipboard_content_hash = hcb;

                if (block_image_once) {
                  block_image_once = false;
                } else {
                  toggle = true;

                  if (cipher_enabled === 'true') {
                    clipContent = await encrypt(clipContent);
                  }

                  await setDataInAsyncStorage(
                    'wsStatusMessage',
                    '✅ Connected - Broadcasting',
                  );

                  stompClient.publish({
                    destination: SEND_DESTINATION,
                    body: JSON.stringify({
                      payload: String(clipContent),
                      type: type_,
                    }),
                  });
                }
              }
            } catch (e) {
              toggle = false;
              block_image_once = false;
              throw e;
            }
          };

""",
    "P2S send path",
)

replace_once(
    """          stopServicesP2S = async () => {
            // 1) Stop clipboard listening""",
    """          stopServicesP2S = async () => {
            clearP2STextEchoTimer();
            await releaseP2STextInFlight();

            // 1) Stop clipboard listening""",
    "P2S stop persistence",
)

path.write_text(text, encoding="utf-8")
