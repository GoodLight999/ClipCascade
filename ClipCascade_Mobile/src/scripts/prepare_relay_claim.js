const fs = require('fs');
const path = require('path');

const modulePath = path.resolve(__dirname, '..', 'android', 'app', 'src', 'main', 'java', 'com', 'clipcascade', 'RelaySettingsModule.kt');
let moduleSource = fs.readFileSync(modulePath, 'utf8');
moduleSource = moduleSource.replace('import java.util.Locale\n', 'import java.util.Locale\nimport android.os.SystemClock\n');
moduleSource = moduleSource.replace('    override fun getName(): String = "RelaySettingsModule"\n', `    companion object {
        private const val RELAY_CLAIM_TTL_MS = 5_000L
        private val relayClaims = mutableMapOf<String, Long>()

        @Synchronized
        private fun claimRelay(relayId: String): Boolean {
            val now = SystemClock.elapsedRealtime()
            relayClaims.entries.removeAll { now - it.value >= RELAY_CLAIM_TTL_MS }
            if (relayClaims.containsKey(relayId)) return false
            relayClaims[relayId] = now
            return true
        }

        @Synchronized
        private fun releaseRelay(relayId: String) {
            relayClaims.remove(relayId)
        }
    }

    override fun getName(): String = "RelaySettingsModule"
`);
moduleSource = moduleSource.replace('    @ReactMethod\n    fun acknowledgeRelay(relayId: String, source: String, promise: Promise) {', `    @ReactMethod
    fun claimRelayDispatch(relayId: String, promise: Promise) {
        promise.resolve(relayId.isNotBlank() && claimRelay(relayId))
    }

    @ReactMethod
    fun releaseRelayDispatch(relayId: String, promise: Promise) {
        releaseRelay(relayId)
        promise.resolve(true)
    }

    @ReactMethod
    fun acknowledgeRelay(relayId: String, source: String, promise: Promise) {`);
moduleSource = moduleSource.replace('            promise.resolve(acknowledged)\n', '            releaseRelay(relayId)\n            promise.resolve(acknowledged)\n');
fs.writeFileSync(modulePath, moduleSource, 'utf8');

const servicePath = path.resolve(__dirname, '..', 'StartForegroundService.js');
let serviceSource = fs.readFileSync(servicePath, 'utf8');
serviceSource = serviceSource.replace('            const relaySource = event?.source;\n', `            const relaySource = event?.source;
            if (relayId && RelaySettingsModule?.claimRelayDispatch) {
              const claimed = await RelaySettingsModule.claimRelayDispatch(relayId);
              if (!claimed) {
                return;
              }
            }
`);
serviceSource = serviceSource.replace('              if (!accepted && peerAckRequested) {\n', `              if (!accepted && relayId && RelaySettingsModule?.releaseRelayDispatch) {
                await RelaySettingsModule.releaseRelayDispatch(relayId);
              }
              if (!accepted && peerAckRequested) {
`);
fs.writeFileSync(servicePath, serviceSource, 'utf8');

// These must run after every transport/listener transform. First guard the final
// Clipboard.setString call sites, then replace localized UI-text heuristics with
// language-neutral clipboard-change and semantic ACTION_COPY confirmation.
require('./prepare_internal_clipboard_guard.js');
require('./prepare_language_neutral_clipboard_copy.js');
