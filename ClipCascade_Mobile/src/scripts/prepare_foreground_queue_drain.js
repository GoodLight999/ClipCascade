const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

const javaDir = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'main',
  'java',
  'com',
  'clipcascade',
);

const clipboardDispatcherPath = path.join(javaDir, 'ClipboardRelayDispatcher.kt');
let clipboardDispatcher = fs.readFileSync(clipboardDispatcherPath, 'utf8');
clipboardDispatcher = replaceRequired(
  clipboardDispatcher,
  `    private val retryTask = object : Runnable {\n`,
  `    data class ForegroundRelay(\n        val relayId: String,\n        val text: String,\n        val sourcePackage: String,\n    )\n\n    private val retryTask = object : Runnable {\n`,
  'clipboard foreground relay type',
);
clipboardDispatcher = replaceRequired(
  clipboardDispatcher,
  `    @Synchronized\n    private fun tryDispatch(context: Context): Boolean {\n`,
  `    @Synchronized\n    fun claimForForegroundService(context: Context): ForegroundRelay? {\n        val applicationContext = context.applicationContext\n        if (!RelaySettingsStore.clipboardEnabled(applicationContext)) {\n            ClipboardRelayStore.clear(applicationContext)\n            inFlightId = null\n            inFlightSince = 0L\n            record(applicationContext, "foreground_poll", "sync_disabled")\n            return null\n        }\n\n        val now = System.currentTimeMillis()\n        val currentInFlight = inFlightId\n        if (currentInFlight != null) {\n            if (now - inFlightSince < ACK_TIMEOUT_MS) return null\n            Log.w(TAG, "Foreground relay acknowledgement timed out; releasing claim")\n            record(applicationContext, "peer_ack", "retrying")\n            inFlightId = null\n            inFlightSince = 0L\n        }\n\n        val item = ClipboardRelayStore.pending(applicationContext) ?: return null\n        inFlightId = item.id\n        inFlightSince = now\n        record(applicationContext, "foreground_poll", "claimed")\n        return ForegroundRelay(\n            relayId = item.id,\n            text = item.text,\n            sourcePackage = item.sourcePackage,\n        )\n    }\n\n    @Synchronized\n    private fun tryDispatch(context: Context): Boolean {\n`,
  'clipboard foreground-service claim',
);
fs.writeFileSync(clipboardDispatcherPath, clipboardDispatcher, 'utf8');

const otpDispatcherPath = path.join(javaDir, 'OtpRelayDispatcher.kt');
let otpDispatcher = fs.readFileSync(otpDispatcherPath, 'utf8');
otpDispatcher = replaceRequired(
  otpDispatcher,
  `    private val retryTask = object : Runnable {\n`,
  `    data class ForegroundRelay(\n        val relayId: String,\n        val text: String,\n    )\n\n    private val retryTask = object : Runnable {\n`,
  'OTP foreground relay type',
);
otpDispatcher = replaceRequired(
  otpDispatcher,
  `    @Synchronized\n    fun tryDispatch(context: Context): Int {\n`,
  `    @Synchronized\n    fun claimForForegroundService(context: Context): ForegroundRelay? {\n        val applicationContext = context.applicationContext\n        if (!RelaySettingsStore.codeRelayEnabled(applicationContext)) {\n            OtpRelayStore.clear(applicationContext)\n            inFlightId = null\n            inFlightSince = 0L\n            return null\n        }\n\n        val now = System.currentTimeMillis()\n        val currentInFlight = inFlightId\n        if (currentInFlight != null) {\n            if (now - inFlightSince < ACK_TIMEOUT_MS) return null\n            Log.w(TAG, "Foreground OTP acknowledgement timed out; releasing claim")\n            inFlightId = null\n            inFlightSince = 0L\n        }\n\n        val item = OtpRelayStore.pending(applicationContext, MAX_BATCH_SIZE).firstOrNull()\n            ?: return null\n        inFlightId = item.id\n        inFlightSince = now\n        RelayHealthStore.record(\n            applicationContext,\n            category = "verification",\n            trigger = "delivery",\n            path = "foreground_poll",\n            result = "claimed",\n        )\n        return ForegroundRelay(\n            relayId = item.id,\n            text = item.code,\n        )\n    }\n\n    @Synchronized\n    fun tryDispatch(context: Context): Int {\n`,
  'OTP foreground-service claim',
);
fs.writeFileSync(otpDispatcherPath, otpDispatcher, 'utf8');

const relayModulePath = path.join(javaDir, 'RelaySettingsModule.kt');
let relayModule = fs.readFileSync(relayModulePath, 'utf8');
relayModule = replaceRequired(
  relayModule,
  `    @ReactMethod\n    fun acknowledgeRelay(relayId: String, source: String, promise: Promise) {\n`,
  `    @ReactMethod\n    fun claimPendingForegroundRelay(promise: Promise) {\n        try {\n            // Verification values expire quickly, so drain them before ordinary\n            // clipboard text. Both paths retain their existing native in-flight\n            // timeout and acknowledgement deletion rules.\n            val otp = OtpRelayDispatcher.claimForForegroundService(reactApplicationContext)\n            if (otp != null) {\n                promise.resolve(Arguments.createMap().apply {\n                    putString("relayId", otp.relayId)\n                    putString("text", otp.text)\n                    putString("source", "notification_code")\n                    putString("sourcePackage", "")\n                })\n                return\n            }\n\n            val clipboard = ClipboardRelayDispatcher.claimForForegroundService(\n                reactApplicationContext,\n            )\n            if (clipboard != null) {\n                promise.resolve(Arguments.createMap().apply {\n                    putString("relayId", clipboard.relayId)\n                    putString("text", clipboard.text)\n                    putString("source", "accessibility_clipboard")\n                    putString("sourcePackage", clipboard.sourcePackage)\n                })\n                return\n            }\n\n            promise.resolve(null)\n        } catch (error: Exception) {\n            promise.reject(\n                "RELAY_FOREGROUND_CLAIM_ERROR",\n                "Unable to claim a pending native relay item",\n                error,\n            )\n        }\n    }\n\n    @ReactMethod\n    fun acknowledgeRelay(relayId: String, source: String, promise: Promise) {\n`,
  'React Native foreground relay claim method',
);
fs.writeFileSync(relayModulePath, relayModule, 'utf8');

const servicePath = path.resolve(__dirname, '..', 'StartForegroundService.js');
let service = fs.readFileSync(servicePath, 'utf8');
service = replaceRequired(
  service,
  `        async function pollFlagsLoop() {\n`,
  `        let nativeRelayDrainInProgress = false;\n\n        async function drainNativeRelayQueue() {\n          if (\n            nativeRelayDrainInProgress ||\n            !RelaySettingsModule?.claimPendingForegroundRelay\n          ) {\n            return;\n          }\n\n          nativeRelayDrainInProgress = true;\n          let claimedRelay = null;\n          let peerAckStaged = false;\n          try {\n            claimedRelay = await RelaySettingsModule.claimPendingForegroundRelay();\n            const relayId = claimedRelay?.relayId;\n            const relayText = claimedRelay?.text;\n            const relaySource = claimedRelay?.source;\n            if (!relayId || !relayText || !relaySource) {\n              return;\n            }\n\n            // The Notifee foreground-service runtime owns the live transport even\n            // when MainApplication.currentReactContext is absent. Pulling the native\n            // durable queue here avoids routing background outbound through a UI\n            // React context while retaining the existing send and ACK machinery.\n            peerAckStaged = server_mode === 'P2P';\n            if (peerAckStaged) {\n              stageP2PAck(relayId, relaySource);\n            }\n\n            const accepted = await sendClipBoard(\n              relayText,\n              'text',\n              true,\n              relayId,\n              relaySource,\n            );\n            if (!accepted && peerAckStaged) {\n              cancelP2PAck(relayId);\n              peerAckStaged = false;\n            } else if (accepted && peerAckStaged) {\n              armP2PAckFallback(relayId);\n            } else if (accepted) {\n              await acknowledgeNativeRelay(relayId, relaySource);\n            }\n          } catch (error) {\n            if (peerAckStaged && claimedRelay?.relayId) {\n              cancelP2PAck(claimedRelay.relayId);\n            }\n            // Keep the native item queued. Its existing 15-second in-flight timeout\n            // releases the claim for a bounded retry without deleting sensitive data.\n            console.warn('Native foreground relay drain failed:', error);\n          } finally {\n            nativeRelayDrainInProgress = false;\n          }\n        }\n\n        async function pollFlagsLoop() {\n`,
  'foreground queue drain function',
);
service = replaceRequired(
  service,
  `            if (isP2PStatusMsgChanged) {\n`,
  `            await drainNativeRelayQueue();\n\n            if (isP2PStatusMsgChanged) {\n`,
  'foreground queue drain poll hook',
);
fs.writeFileSync(servicePath, service, 'utf8');

const testPath = path.resolve(
  __dirname,
  '..',
  'android',
  'app',
  'src',
  'test',
  'java',
  'com',
  'clipcascade',
  'OtpCodeExtractorTest.kt',
);
let tests = fs.readFileSync(testPath, 'utf8');
const finalBrace = tests.lastIndexOf('\n}');
if (finalBrace < 0) {
  throw new Error('Expected final OtpCodeExtractorTest class brace was not found');
}
const dawnTest = `\n    @Test\n    fun extractsDawnLoginCode() {\n        assertEquals(\n            "713642",\n            OtpCodeExtractor.extract(\n                """\n                DAWN\n                Log in to DAWN\n\n                Your code is\n\n                713642\n\n                This code expires in 10 minutes. Do not share this code with anyone.\n                """.trimIndent(),\n            ),\n        )\n    }\n`;
tests = tests.slice(0, finalBrace) + dawnTest + tests.slice(finalBrace);
fs.writeFileSync(testPath, tests, 'utf8');

console.log(
  'Prepared foreground-service native queue drain with unchanged transport and ACK paths.',
);
