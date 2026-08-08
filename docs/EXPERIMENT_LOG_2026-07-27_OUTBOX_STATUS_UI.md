# Experiment Log — 2026-07-27 — P2S Outbox Status UI

## Goal

Expose the persistent P2S text outbox state through the existing mobile connection page without adding a new polling loop, a second state owner, or any clipboard payload display.

## Existing mechanism reused

`App.js` already polls a small set of AsyncStorage-backed status keys every 300 ms through `NativeBridgeModule.getFlagsSync`.

The implementation therefore:

- added `p2sTextOutboxStatus` to the existing poll key list;
- reused the existing WebSocket status page;
- displays the outbox only in P2S mode;
- clears it in P2P/unknown mode and on service toggle;
- did not add a timer, Activity, service, or transport.

## Payload-free formatter

Added `P2SOutboxStatus.js`.

Displayed fields are limited to:

- queue count;
- queued versus sending state;
- total queued wire bytes;
- head attempt count;
- dropped count.

The formatter ignores unknown fields and never emits payload text, content hashes, storage scope, server URL, username, or password material.

## Desk-review failure found before device testing

Initial implementation expected `p2sTextOutboxStatus` to reach JavaScript as an object.

Inspection of `AsyncStorageBridge.getValuesForKeys` showed that native synchronous polling wraps stored object JSON as a string value. Therefore:

- `JSON.parse(getFlagsSync(...))` returns `p2sTextOutboxStatus` as a serialized JSON string;
- the first formatter would reject it because `typeof status !== 'object'`;
- the UI would silently display nothing even though all builds passed.

Correction:

- formatter accepts either a direct object or the exact serialized object returned by the native bridge;
- one safe `JSON.parse` is attempted for strings;
- malformed/non-object/unloaded values produce an empty display rather than an exception;
- tests cover the native serialized shape.

This is a concrete example of why build success alone is not considered runtime proof.

## Tests

Latest JavaScript run:

- 4 suites passed;
- 26 tests passed;
- no snapshots;
- includes formatter privacy, serialized native bridge shape, App poll/display contract, outbox core, and outbox integration contracts.

Latest Android build also passed:

- JVM tests;
- Shizuku/AIDL and Kotlin compilation;
- standalone APK generation;
- exact JavaScript bundle presence;
- APK ZIP integrity;
- artifact upload.

## Latest green evidence

Branch head: `60c71a7d77e2980d2f2e35c8f325c4c22d37c4cf`

Android:

- workflow: `30249557589`
- APK artifact ID: `8646500843`
- build-log artifact ID: `8646499050`
- file: `ClipCascade-Android-stability-standalone.apk`
- user-facing file: `ClipCascade-Android-stability-outbox-status.apk`
- size: `93,614,299` bytes
- SHA-256: `614f6fd7d2bcecc96ceba331601ae9d84f6c475047d4301fa5f099286ad0893b`
- APK entries: `538`
- independent bundle inspection found `p2sTextOutboxStatus`, `P2SOutboxStatus`, persistent outbox storage, and recovery-repository markers

Desktop on the same head:

- workflow: `30249557577`
- Windows artifact ID: `8646440413`
- Windows file size: `57,456,040` bytes
- Windows SHA-256: `199f5ab18413255bbee4c2efac2b5694a0a69c68a6d1273292dfe16eff35926e`
- Linux artifact ID: `8646405080`
- Linux file size: `60,413` bytes
- Linux SHA-256: `12e6cc5f2b9937d08801ea8a9a2d4e93682a74bdd8d6b87543f73f7f14900c5a`

## Runtime acceptance still required

- queue status visibly renders on the user's device;
- count/state changes while offline and reconnecting;
- text remains readable in light and dark modes;
- long messages do not make the existing status page unusable;
- actual queue ordering and echo acknowledgement match the displayed metadata.
