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
  `        const serviceEventSubscriptions = [];\n`,
  `        const serviceEventSubscriptions = [];\n` +
    `        const pendingP2PAcks = new Map();\n` +
    `        const P2P_PEER_ACK_FALLBACK_MS = 5000;\n\n` +
    `        const acknowledgeNativeRelay = async (relayId, relaySource) => {\n` +
    `          if (\n` +
    `            relayId &&\n` +
    `            relaySource &&\n` +
    `            RelaySettingsModule?.acknowledgeRelay\n` +
    `          ) {\n` +
    `            await RelaySettingsModule.acknowledgeRelay(relayId, relaySource);\n` +
    `          }\n` +
    `        };\n\n` +
    `        const stageP2PAck = (relayId, relaySource) => {\n` +
    `          const previous = pendingP2PAcks.get(relayId);\n` +
    `          if (previous?.timer) {\n` +
    `            clearTimeout(previous.timer);\n` +
    `          }\n` +
    `          pendingP2PAcks.set(relayId, {relaySource, timer: null});\n` +
    `        };\n\n` +
    `        const cancelP2PAck = relayId => {\n` +
    `          const pending = pendingP2PAcks.get(relayId);\n` +
    `          if (pending?.timer) {\n` +
    `            clearTimeout(pending.timer);\n` +
    `          }\n` +
    `          pendingP2PAcks.delete(relayId);\n` +
    `        };\n\n` +
    `        const completeP2PAck = async relayId => {\n` +
    `          const pending = pendingP2PAcks.get(relayId);\n` +
    `          if (!pending) {\n` +
    `            return false;\n` +
    `          }\n` +
    `          if (pending.timer) {\n` +
    `            clearTimeout(pending.timer);\n` +
    `          }\n` +
    `          pendingP2PAcks.delete(relayId);\n` +
    `          await acknowledgeNativeRelay(relayId, pending.relaySource);\n` +
    `          return true;\n` +
    `        };\n\n` +
    `        const armP2PAckFallback = relayId => {\n` +
    `          const pending = pendingP2PAcks.get(relayId);\n` +
    `          if (!pending) {\n` +
    `            return;\n` +
    `          }\n` +
    `          pending.timer = setTimeout(async () => {\n` +
    `            try {\n` +
    `              await completeP2PAck(relayId);\n` +
    `            } catch (error) {\n` +
    `              // Native timeout keeps the item queued if fallback ACK fails.\n` +
    `            }\n` +
    `          }, P2P_PEER_ACK_FALLBACK_MS);\n` +
    `        };\n\n` +
    `        const cleanupPendingP2PAcks = () => {\n` +
    `          for (const pending of pendingP2PAcks.values()) {\n` +
    `            if (pending?.timer) {\n` +
    `              clearTimeout(pending.timer);\n` +
    `            }\n` +
    `          }\n` +
    `          pendingP2PAcks.clear();\n` +
    `        };\n`,
  'P2P acknowledgement state',
);

replaceExact(
  `              const accepted = await sendClipBoard(\n` +
    `                clipContent,\n` +
    `                'text',\n` +
    `                Boolean(relayId),\n` +
    `              );\n` +
    `              if (\n` +
    `                accepted === true &&\n` +
    `                relayId &&\n` +
    `                relaySource &&\n` +
    `                RelaySettingsModule?.acknowledgeRelay\n` +
    `              ) {\n` +
    `                await RelaySettingsModule.acknowledgeRelay(\n` +
    `                  relayId,\n` +
    `                  relaySource,\n` +
    `                );\n` +
    `              }\n`,
  `              const peerAckRequested =\n` +
    `                Boolean(relayId) && server_mode === 'P2P';\n` +
    `              if (peerAckRequested) {\n` +
    `                stageP2PAck(relayId, relaySource);\n` +
    `              }\n` +
    `              const accepted = await sendClipBoard(\n` +
    `                clipContent,\n` +
    `                'text',\n` +
    `                Boolean(relayId),\n` +
    `                relayId || null,\n` +
    `              );\n` +
    `              if (!accepted && peerAckRequested) {\n` +
    `                cancelP2PAck(relayId);\n` +
    `              } else if (accepted && peerAckRequested) {\n` +
    `                armP2PAckFallback(relayId);\n` +
    `              } else if (accepted && relayId && relaySource) {\n` +
    `                await acknowledgeNativeRelay(relayId, relaySource);\n` +
    `              }\n`,
  'background relay acknowledgement selection',
);

replaceExact(
  `          sendClipBoardP2P = async (\n` +
    `            clipContent,\n` +
    `            type_ = 'text',\n` +
    `            forceSend = false,\n` +
    `          ) => {\n`,
  `          sendClipBoardP2P = async (\n` +
    `            clipContent,\n` +
    `            type_ = 'text',\n` +
    `            forceSend = false,\n` +
    `            relayId = null,\n` +
    `          ) => {\n`,
  'P2P send relay identifier',
);

replaceExact(
  `                    combinedRawPayloadSizeInBytes: rawPayloadSizeInBytes,\n` +
    `                  },\n`,
  `                    combinedRawPayloadSizeInBytes: rawPayloadSizeInBytes,\n` +
    `                    relayId: relayId || undefined,\n` +
    `                    ackRequested: Boolean(relayId),\n` +
    `                  },\n`,
  'P2P relay metadata',
);

replaceExact(
  `        const sendClipBoard = async (\n` +
    `          clipContent,\n` +
    `          type_ = 'text',\n` +
    `          forceSend = false,\n` +
    `        ) => {\n` +
    `          if (server_mode === 'P2S') {\n` +
    `            return await sendClipBoardP2S(clipContent, type_, forceSend);\n` +
    `          } else if (server_mode === 'P2P') {\n` +
    `            return await sendClipBoardP2P(clipContent, type_, forceSend);\n` +
    `          }\n` +
    `          return false;\n` +
    `        };`,
  `        const sendClipBoard = async (\n` +
    `          clipContent,\n` +
    `          type_ = 'text',\n` +
    `          forceSend = false,\n` +
    `          relayId = null,\n` +
    `        ) => {\n` +
    `          if (server_mode === 'P2S') {\n` +
    `            return await sendClipBoardP2S(clipContent, type_, forceSend);\n` +
    `          } else if (server_mode === 'P2P') {\n` +
    `            return await sendClipBoardP2P(\n` +
    `              clipContent,\n` +
    `              type_,\n` +
    `              forceSend,\n` +
    `              relayId,\n` +
    `            );\n` +
    `          }\n` +
    `          return false;\n` +
    `        };`,
  'generic P2P relay identifier',
);

replaceExact(
  `          const onDataChannelMessage = async messageJson => {\n`,
  `          const onDataChannelMessage = async (\n` +
    `            messageJson,\n` +
    `            replyChannel = null,\n` +
    `          ) => {\n`,
  'P2P receive reply channel',
);

replaceExact(
  `              if (message && message._cc_keepalive === true) {\n` +
    `                return;\n` +
    `              }\n`,
  `              if (message && message._cc_keepalive === true) {\n` +
    `                return;\n` +
    `              }\n` +
    `              if (message?._cc_ack?.relayId) {\n` +
    `                await completeP2PAck(String(message._cc_ack.relayId));\n` +
    `                return;\n` +
    `              }\n`,
  'P2P acknowledgement receive',
);

replaceExact(
  `              // hash clipboard content\n` +
    `              const hcb = await hashCB(cb);\n` +
    `              if (await newCB(hcb)) {\n` +
    `                previous_clipboard_content_hash = hcb;\n\n` +
    `                await resetReceivingFragments();\n` +
    `                // validate clipboard size\n` +
    `                if (await validateClipboardSize(cb, type_, 'Inbound')) {\n` +
    `                  // set clipboard content\n` +
    `                  if (type_ === 'text') {\n` +
    `                    Clipboard.setString(cb);\n` +
    `                  } else if (type_ === 'image') {\n` +
    `                    await NativeBridgeModule.copyBase64ImageToClipboardUsingCache(\n` +
    `                      cb,\n` +
    `                    );\n` +
    `                    block_image_once = true;\n` +
    `                  } else if (type_ === 'files') {\n` +
    `                    await showFilesDownloadNotification('📥 Download File(s)');\n\n` +
    `                    files_in_memory = cb;\n` +
    `                    await setDataInAsyncStorage(\n` +
    `                      'filesAvailableToDownload',\n` +
    `                      'true',\n` +
    `                    );\n` +
    `                  }\n` +
    `                }\n` +
    `              }\n`,
  `              // hash clipboard content\n` +
    `              const hcb = await hashCB(cb);\n` +
    `              const changed = await newCB(hcb);\n` +
    `              let peerAckAllowed = !changed && type_ === 'text';\n` +
    `              if (changed) {\n` +
    `                previous_clipboard_content_hash = hcb;\n\n` +
    `                await resetReceivingFragments();\n` +
    `                // validate clipboard size\n` +
    `                if (await validateClipboardSize(cb, type_, 'Inbound')) {\n` +
    `                  // set clipboard content\n` +
    `                  if (type_ === 'text') {\n` +
    `                    Clipboard.setString(cb);\n` +
    `                    peerAckAllowed = true;\n` +
    `                  } else if (type_ === 'image') {\n` +
    `                    await NativeBridgeModule.copyBase64ImageToClipboardUsingCache(\n` +
    `                      cb,\n` +
    `                    );\n` +
    `                    block_image_once = true;\n` +
    `                  } else if (type_ === 'files') {\n` +
    `                    await showFilesDownloadNotification('📥 Download File(s)');\n\n` +
    `                    files_in_memory = cb;\n` +
    `                    await setDataInAsyncStorage(\n` +
    `                      'filesAvailableToDownload',\n` +
    `                      'true',\n` +
    `                    );\n` +
    `                  }\n` +
    `                }\n` +
    `              }\n` +
    `              if (\n` +
    `                peerAckAllowed &&\n` +
    `                metadata?.ackRequested === true &&\n` +
    `                metadata?.relayId &&\n` +
    `                replyChannel?.readyState === 'open'\n` +
    `              ) {\n` +
    `                replyChannel.send(\n` +
    `                  JSON.stringify({\n` +
    `                    _cc_ack: {relayId: String(metadata.relayId)},\n` +
    `                  }),\n` +
    `                );\n` +
    `              }\n`,
  'P2P clipboard-applied acknowledgement',
);

replaceExact(
  `              await onDataChannelMessage(e.data);\n`,
  `              await onDataChannelMessage(e.data, channel);\n`,
  'P2P reply channel propagation',
);

const cleanupCalls = source.match(
  /cleanupClipboardListeners\(serviceEventSubscriptions\);/g,
) || [];
if (cleanupCalls.length === 0) {
  throw new Error('Unable to add P2P acknowledgement cleanup: no cleanup calls found');
}
source = source.replace(
  /cleanupClipboardListeners\(serviceEventSubscriptions\);/g,
  `cleanupPendingP2PAcks();\n            cleanupClipboardListeners(serviceEventSubscriptions);`,
);

fs.writeFileSync(servicePath, source, 'utf8');
console.log(
  `Prepared P2P peer ACK with ${cleanupCalls.length} scoped cleanup call(s).`,
);
