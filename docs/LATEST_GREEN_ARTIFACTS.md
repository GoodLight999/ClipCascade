# Latest Green Artifacts — 2026-07-21

This file records the latest matching Priority 1 implementation, CI runs, and Android artifact prepared for isolated HONOR/MagicOS validation.

## Source and PR

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- implementation anchor: `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`
- intended alpha tag: `v3.2.1-extended.19-alpha.2`
- PR: `#1`
- PR state: open and Draft; keep it Draft

The implementation combines the `.19-alpha.1` Android-framework-localized Copy fallback with foreground-service native queue draining for ordinary clipboard and verification-code outbound.

## Implementation CI

- Android standalone CI: `29837847117` — success
- Desktop Windows CI: `29837846863` — success

Android CI passed:

- all source transforms in production order;
- `.19-alpha.2` / versionCode `320124` identity checks;
- exact Android framework-localized Copy/Copy URL matching;
- selection-only suppression and internal-write filtering;
- no ordinary clipboard TTL or overflow eviction;
- bounded queue and retry tests;
- foreground-service native queue claim and poll-drain source assertions;
- existing P2S/P2P send function reuse and debug-notification path;
- DAWN, Gmail-shaped, Perceptron, Beeper, multilingual, and negative OTP extractor tests;
- NotificationListener rebind/rescan, listener-path self-test, and duplicate receipt tests;
- JavaScript bundle generation;
- Android Kotlin/resources and unit tests;
- APK assembly and embedded-bundle verification;
- deterministic V2 signer verification;
- artifact upload.

Windows CI passed authenticated HTTP, Extended P2P peer-applied ACK, validation-before-ACK, shutdown/tray, standalone packaging, and executable upload. No Windows implementation file changed in alpha.2.

## Android artifact

- application: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.19-alpha.2-standalone`
- versionCode: `320124`
- GitHub Actions artifact ID: `8498121042`
- Actions artifact ZIP SHA-256: `8c20eadce450c57fadb9eab39e465332fe34f8f25f7e28d44a072162dc480db3`
- extracted APK SHA-256: `f9f7b5fe6653beb8d0b08436657ddf719fd155e3ac9b1216b0307b7ea1cf63a7`
- APK size: `147933819` bytes
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- Actions artifact expiry: `2026-10-19T14:10:20Z`

The downloaded Actions ZIP digest matched GitHub's recorded digest exactly. The APK hash was calculated after extracting `app-debug.apk`. The signer diagnostics artifact reported the expected public V2 test signer.

## Local conversation files

- APK: `ClipCascade-Extended-3.2.1-extended.19-alpha.2-vc320124-ed9c009.apk`
- Actions artifact ZIP: `ClipCascade-Extended-3.2.1-extended.19-alpha.2-actions.zip`

These are local conversation artifacts, not repository files.

## Alpha prerelease

- intended tag: `v3.2.1-extended.19-alpha.2`
- intended target: `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`
- release notes: `docs/RELEASE_NOTES_3.2.1_EXTENDED_19_ALPHA_2.md`

The release workflow is configured to wait for matching-sha Android and Windows success before creating assets. The connector used in this conversation did not independently enumerate the alpha-release workflow or public release asset list, so the Actions artifact and hashes above are the verified recovery source.

## Latest target-device evidence

This supersedes the earlier broad statement that ordinary synchronization had recovered:

- inbound Android synchronization works while the app UI is not open;
- Android outbound works while the app UI is open;
- Android outbound fails when the app UI is not open, including ordinary Copy;
- a real Gmail notification for DAWN (`Your code is 713642`) was not relayed;
- therefore background outbound and real Gmail remain non-functional on the last tested build.

`.19-alpha.2` is a candidate fix. CI is not target-device proof.

## Not proven by CI

- in-place alpha.2 update and settings retention;
- system-localized Copy fallback firing on HONOR/MagicOS;
- native queue insertion while backgrounded;
- foreground-service queue drain on the target;
- debug notification ON/OFF behavior;
- one Windows application followed by peer ACK and native deletion;
- real Gmail notification visibility, extraction, queueing, and delivery;
- removed-from-recents, locked, and screen-off outbound;
- long-disconnect durability and queue-full behavior;
- exactly-once target behavior;
- battery and Windows tray behavior on the real devices.

Do not mark PR #1 ready or merge it.
