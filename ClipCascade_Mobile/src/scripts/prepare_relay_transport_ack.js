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

function replaceRegex(pattern, replacement, label) {
  if (!pattern.test(source)) {
    throw new Error(`Unable to apply ${label}: expected source block was not found`);
  }
  source = source.replace(pattern, replacement);
}

replaceExact(
  '        const { NativeBridgeModule } = NativeModules;\n',
  `        const { NativeBridgeModule, RelaySettingsModule } = NativeModules;\n` +
    `        if (RelaySettingsModule?.resumeRelayQueues) {\n` +
    `          await RelaySettingsModule.resumeRelayQueues();\n` +
    `        }\n`,
  'relay queue startup',
);

replaceRegex(
  /        \/\/ Event triggered when text content is shared with the app\. \(or\) when text selection popup menu action is invoked\n        DeviceEventEmitter\.addListener\('SHARED_TEXT', async event => \{[\s\S]*?\n        \}\);\n\n        \/\/ Event listener triggered when image is shared with the app\./,
  `        // Event triggered when text content is shared with the app.\n` +
    `        DeviceEventEmitter.addListener('SHARED_TEXT', async event => {\n` +
    `          try {\n` +
    `            const clipContent = event?.text;\n` +
    `            const relayId = event?.relayId;\n` +
    `            const relaySource = event?.source;\n` +
    `            if (clipContent) {\n` +
    `              // Manual share actions still update the local clipboard. Native\n` +
    `              // background relay items already originate from that clipboard,\n` +
    `              // so writing them again would trigger a duplicate listener event.\n` +
    `              if (!relayId) {\n` +
    `                Clipboard.setString(clipContent);\n` +
    `              }\n` +
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
    `              }\n` +
    `            }\n` +
    `          } catch (e) {\n` +
    `            await setDataInAsyncStorage(\n` +
    `              'wsStatusMessage',\n` +
    `              '❌ Outbound Error: ' + e,\n` +
    `            );\n` +
    `          }\n` +
    `        });\n\n` +
    `        // Event listener triggered when image is shared with the app.`,
  'shared text acknowledgement listener',
);

replaceRegex(
  /          \/\/ send clipboard content P2S\n          sendClipBoardP2S = async \(clipContent, type_ = 'text'\) => \{[\s\S]*?\n          \};\n\n          \/\/ stop events and connection P2S/,
  `          // Send clipboard content P2S. Returns true only when STOMP\n` +
    `          // accepted a publish call for this item.\n` +
    `          sendClipBoardP2S = async (\n` +
    `            clipContent,\n` +
    `            type_ = 'text',\n` +
    `            forceSend = false,\n` +
    `          ) => {\n` +
    `            try {\n` +
    `              await clearFiles();\n` +
    `              if (!stompClient || !stompClient.connected || toggle) {\n` +
    `                return false;\n` +
    `              }\n` +
    `              if (\n` +
    `                (type_ === 'image' && enable_image_sharing === 'false') ||\n` +
    `                (type_ === 'files' && enable_file_sharing === 'false')\n` +
    `              ) {\n` +
    `                return false;\n` +
    `              }\n` +
    `              if (!(await validateClipboardSize(clipContent, type_, 'Outbound'))) {\n` +
    `                return false;\n` +
    `              }\n` +
    `              if (type_ === 'image') {\n` +
    `                clipContent = await NativeBridgeModule.getFileAsBase64(clipContent);\n` +
    `              } else if (type_ === 'files') {\n` +
    `                const temp = {};\n` +
    `                const filePaths = clipContent\n` +
    `                  .split(',')\n` +
    `                  .filter(item => item.trim() !== '');\n` +
    `                for (const filePath of filePaths) {\n` +
    `                  temp[await NativeBridgeModule.getFileName(filePath)] =\n` +
    `                    await NativeBridgeModule.getFileAsBase64(filePath);\n` +
    `                }\n` +
    `                clipContent = JSON.stringify(temp);\n` +
    `              }\n` +
    `              const hcb = await hashCB(clipContent);\n` +
    `              if (!forceSend && !(await newCB(hcb))) {\n` +
    `                return false;\n` +
    `              }\n` +
    `              previous_clipboard_content_hash = hcb;\n` +
    `              if (block_image_once) {\n` +
    `                block_image_once = false;\n` +
    `                return false;\n` +
    `              }\n` +
    `              toggle = true;\n` +
    `              if (cipher_enabled === 'true') {\n` +
    `                clipContent = await encrypt(clipContent);\n` +
    `              }\n` +
    `              await setDataInAsyncStorage(\n` +
    `                'wsStatusMessage',\n` +
    `                '✅ Connected - Broadcasting',\n` +
    `              );\n` +
    `              stompClient.publish({\n` +
    `                destination: SEND_DESTINATION,\n` +
    `                body: JSON.stringify({\n` +
    `                  payload: String(clipContent),\n` +
    `                  type: type_,\n` +
    `                }),\n` +
    `              });\n` +
    `              return true;\n` +
    `            } catch (e) {\n` +
    `              toggle = false;\n` +
    `              block_image_once = false;\n` +
    `              throw e;\n` +
    `            }\n` +
    `          };\n\n` +
    `          // stop events and connection P2S`,
  'P2S accepted-send result',
);

replaceRegex(
  /          \/\/ send clipboard content P2P\n          sendClipBoardP2P = async \(clipContent, type_ = 'text'\) => \{[\s\S]*?\n          \};\n\n          \/\/ stop events and connection P2P/,
  `          // Send clipboard content P2P. Returns true only when at least one\n` +
    `          // open DataChannel accepted every fragment.\n` +
    `          sendClipBoardP2P = async (\n` +
    `            clipContent,\n` +
    `            type_ = 'text',\n` +
    `            forceSend = false,\n` +
    `          ) => {\n` +
    `            try {\n` +
    `              await clearFiles();\n` +
    `              if (\n` +
    `                (type_ === 'image' && enable_image_sharing === 'false') ||\n` +
    `                (type_ === 'files' && enable_file_sharing === 'false')\n` +
    `              ) {\n` +
    `                return false;\n` +
    `              }\n` +
    `              if (!(await validateClipboardSize(clipContent, type_, 'Outbound'))) {\n` +
    `                return false;\n` +
    `              }\n` +
    `              if (type_ === 'image') {\n` +
    `                clipContent = await NativeBridgeModule.getFileAsBase64(clipContent);\n` +
    `              } else if (type_ === 'files') {\n` +
    `                const temp = {};\n` +
    `                const filePaths = clipContent\n` +
    `                  .split(',')\n` +
    `                  .filter(item => item.trim() !== '');\n` +
    `                for (const filePath of filePaths) {\n` +
    `                  temp[await NativeBridgeModule.getFileName(filePath)] =\n` +
    `                    await NativeBridgeModule.getFileAsBase64(filePath);\n` +
    `                }\n` +
    `                clipContent = JSON.stringify(temp);\n` +
    `              }\n` +
    `              const hcb = await hashCB(clipContent);\n` +
    `              if (!forceSend && !(await newCB(hcb))) {\n` +
    `                return false;\n` +
    `              }\n` +
    `              previous_clipboard_content_hash = hcb;\n` +
    `              if (block_image_once) {\n` +
    `                block_image_once = false;\n` +
    `                return false;\n` +
    `              }\n` +
    `              await resetSendingFragmentId();\n` +
    `              await resetReceivingFragments();\n` +
    `              const openChannels = Object.values(dataChannels).filter(\n` +
    `                channel => channel && channel.readyState === 'open',\n` +
    `              );\n` +
    `              if (openChannels.length === 0) {\n` +
    `                return false;\n` +
    `              }\n` +
    `              const rawPayloadSizeInBytes = textEncoder.encode(clipContent).length;\n` +
    `              if (cipher_enabled === 'true') {\n` +
    `                clipContent = await encrypt(clipContent);\n` +
    `              }\n` +
    `              const fragments = await fragmentString(clipContent, FRAGMENT_SIZE);\n` +
    `              const messageId = await generateUuid();\n` +
    `              sendingFragmentId = messageId;\n` +
    `              const messages = fragments.map((fragment, index) =>\n` +
    `                JSON.stringify({\n` +
    `                  payload: fragment,\n` +
    `                  type: type_,\n` +
    `                  metadata: {\n` +
    `                    id: messageId,\n` +
    `                    isFragmented: fragments.length > 1,\n` +
    `                    index,\n` +
    `                    totalFragments: fragments.length,\n` +
    `                    combinedRawPayloadSizeInBytes: rawPayloadSizeInBytes,\n` +
    `                  },\n` +
    `                }),\n` +
    `              );\n` +
    `              let acceptedChannels = 0;\n` +
    `              for (const channel of openChannels) {\n` +
    `                try {\n` +
    `                  for (let index = 0; index < messages.length; index++) {\n` +
    `                    if (sendingFragmentId !== messageId) {\n` +
    `                      return false;\n` +
    `                    }\n` +
    `                    channel.send(messages[index]);\n` +
    `                    if (messages.length > 1) {\n` +
    `                      sendingFragmentStats = \`${index + 1}/${messages.length}\`;\n` +
    `                      await p2pStatusMessageChanged();\n` +
    `                    }\n` +
    `                  }\n` +
    `                  acceptedChannels += 1;\n` +
    `                } catch (channelError) {\n` +
    `                  p2pMsg = '⚠️ One P2P channel rejected an outbound item';\n` +
    `                  await p2pStatusMessageChanged();\n` +
    `                }\n` +
    `              }\n` +
    `              await resetSendingFragmentId();\n` +
    `              return acceptedChannels > 0;\n` +
    `            } catch (e) {\n` +
    `              block_image_once = false;\n` +
    `              p2pMsg = '❌ P2P Outbound Error: ' + JSON.stringify(e, null, 2);\n` +
    `              await p2pStatusMessageChanged();\n` +
    `              return false;\n` +
    `            }\n` +
    `          };\n\n` +
    `          // stop events and connection P2P`,
  'P2P accepted-send result',
);

replaceExact(
  `        const sendClipBoard = async (clipContent, type_ = 'text') => {\n` +
    `          if (server_mode === 'P2S') {\n` +
    `            await sendClipBoardP2S(clipContent, type_);\n` +
    `          } else if (server_mode === 'P2P') {\n` +
    `            await sendClipBoardP2P(clipContent, type_);\n` +
    `          }\n` +
    `        };`,
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
  'generic accepted-send result',
);

fs.writeFileSync(servicePath, source, 'utf8');
console.log('Prepared relay transport acknowledgement path.');
