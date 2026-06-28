# Android Initialization and Duplicate-Send Handoff — 2026-06-28

Read this after `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`.

## User report

On Android, the first launch displayed:

`Initialization error: Cannot perform this operation because the connection pool has been closed.`

Closing and reopening the application made the error disappear. A single copied value could then be sent twice.

The supplied Windows log contains WebSocket and ICE recovery activity but no content-bearing clipboard transmission record, so it cannot by itself identify which sender invocation produced the duplicate. It does show repeated connection generations and successful ICE completion.

## Confirmed initialization defect

`AsyncStorageBridge` obtains its database from React Native AsyncStorage's singleton `ReactDatabaseSupplier`. Its `disconnect()` method called `close()` on that shared database.

This bridge does not own the shared connection lifecycle. Closing it can invalidate React Native AsyncStorage while application or foreground-service initialization is still reading settings, producing the reported closed connection-pool error. Reopening the application creates/reopens the supplier connection, which explains the temporary recovery.

Repair:

- build transform `prepare_storage_lifecycle.js` removes the shared database `close()` call;
- `disconnect()` now releases only the bridge's local reference;
- Android CI rejects a transformed source that still contains the unsafe close call.

## Duplicate-send defect

The persistent native queue emits one `SHARED_TEXT` event containing an opaque relay ID. After an initialization failure and reopen, more than one JavaScript service listener can temporarily observe the same event.

Text-hash suppression is not sufficient for this case because native queue delivery intentionally uses forced sending and because each service generation owns separate JavaScript transport state. Two listeners can therefore attempt to send the same relay ID.

Repair:

- `RelaySettingsModule` now owns a process-wide synchronized relay-claim map;
- a JavaScript listener must atomically claim the relay ID before sending;
- only the first listener receives a successful claim;
- an unaccepted send releases the claim immediately;
- native acknowledgement releases the claim;
- stale claims expire after five seconds so native retry remains possible;
- existing persistent queue and P2P Windows-applied acknowledgement semantics remain unchanged.

The existing queue text deduplication remains in place for multiple Accessibility events that create separate items. The relay claim specifically protects one native queue item from duplicate JavaScript listeners.

## Build identity

Repair build:

- versionName: `3.2.1-extended.6-standalone`
- versionCode: `320110`
- package: `com.clipcascade.extended`
- signer unchanged
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

It is intended to update stable-signed versionCode `320107`, `320108`, or `320109` without uninstalling.

## Code-head CI and artifacts

Code and release-workflow HEAD:

- commit: `fb31ebca8fc99a7ff9504163cf584f35e52127ba`
- Android CI run: `28317382119` — success
- Windows CI run: `28317382112` — success

Android artifact:

- artifact ID: `7933086597`
- ZIP SHA-256: `5c2820acef2cdf8969a070a6462e728a2c2ca215a39d333240a989cc306c76b4`
- extracted APK SHA-256: `572f39b7e524a83b3d6e2819295b5aba111eed16d76ac3a9e3c10fc512fe3135`

Windows artifact:

- artifact ID: `7933081818`
- ZIP SHA-256: `cc316828d637b358fc93435c79bac4f8c4ef99b4d63759baaab93abec012e28b`
- extracted EXE SHA-256: `0953676d8eec3a69084b7cd17fd4e273906be8fece1d2e764a93ceaae573dd5d`

Android CI passed all source transforms, version checks, storage-lifecycle rejection check, relay-claim checks, JavaScript bundling, unit tests, Kotlin/resource compilation, APK assembly, embedded-bundle verification, and signer verification. Windows CI retained authenticated HTTP, P2P ACK, shutdown/status, compilation, and packaging tests.

## Mandatory real-device verification

With Phone Link clipboard synchronization and every competing clipboard utility disabled:

1. Install versionCode `320110` over the current stable-signed build without uninstalling.
2. Cold-launch Android at least five times. Confirm no closed connection-pool initialization error.
3. Start synchronization, background ClipCascade, and copy one unique synthetic string once.
4. Confirm exactly one peer clipboard application.
5. Repeat after closing and reopening the Android UI while synchronization is active.
6. Repeat after backgrounding for 30 seconds and after removing the UI from recents.
7. Confirm the native clipboard queue returns to zero only after the defined acknowledgement.
8. Repeat identical text deliberately after more than five seconds; it must be treated as a new user copy rather than permanently suppressed.

If duplication remains, record the Android recent diagnostics and pending queue count. Distinguish:

- two queue items: Accessibility/capture-level duplicate;
- one queue item with two peer applications: service-listener or transport-level duplicate;
- one peer application but two Windows UI/history observations: receiving UI or another clipboard observer.

## Current truth

The reported initialization error and a concrete duplicate-send race existed in ClipCascade. Both are repaired in code and CI, but real-device confirmation is still required. Keep PR #1 Draft.
