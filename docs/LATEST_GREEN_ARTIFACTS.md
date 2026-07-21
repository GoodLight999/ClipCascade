# Latest Green Artifacts — 2026-07-21

## Source

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- implementation anchor: `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`
- alpha tag: `v3.2.1-extended.19-alpha.2`
- PR: `#1`, open and Draft

The tag resolves exactly to the implementation anchor.

## CI

- Android standalone CI `29837847117` — success
- Desktop Windows CI `29837846863` — success

Android CI passed:

- all transforms in production order;
- version `3.2.1-extended.19-alpha.2 / 320124`;
- Android-framework-localized Copy/copy-URL matching assertions;
- locale-positive and approximate-label/selection-negative tests;
- selection-alone negative tests;
- native clipboard and OTP foreground-service claim source assertions;
- React native `claimPendingForegroundRelay` assertion;
- Notifee foreground queue-drain and failure-retention assertions;
- DAWN-shaped, Gmail-shaped, Perceptron, Beeper, multilingual, and negative OTP extractor tests;
- explicit rejection of READ_LOGS and SYSTEM_ALERT_WINDOW;
- internal-write, queue-capacity, no-TTL, no-eviction, retry, notification receipt, debug, claim, and ACK invariants;
- JavaScript bundle;
- Kotlin/resources and unit tests;
- APK assembly and embedded bundle;
- deterministic signer verification;
- artifact upload.

Windows CI passed existing authenticated HTTP, Extended peer-applied ACK, validation-before-ACK, shutdown/tray, tests, and EXE packaging. No Windows implementation file changed in `.19`.

## Android artifact

- application: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.19-alpha.2-standalone`
- versionCode: `320124`
- Actions artifact ID: `8498121042`
- Actions artifact ZIP SHA-256: `8c20eadce450c57fadb9eab39e465332fe34f8f25f7e28d44a072162dc480db3`
- APK SHA-256: `f9f7b5fe6653beb8d0b08436657ddf719fd155e3ac9b1216b0307b7ea1cf63a7`
- APK size: `147933819` bytes
- signer diagnostics artifact ID: `8498117842`
- signer diagnostics ZIP SHA-256: `afe0ea50871ebc21412bae2f219f009cc0bc385eec5f85584468caea2c80206a`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-19T14:10:20Z`

The downloaded Actions ZIP digest matched GitHub's reported artifact digest. The extracted APK was independently hashed. Signer diagnostics reported the expected V2 certificate digest.

## Local filenames

- `/mnt/data/ClipCascade-Extended-3.2.1-extended.19-alpha.2-actions.zip`
- `/mnt/data/ClipCascade-Extended-3.2.1-extended.19-alpha.2-vc320124-ed9c009.apk`
- `/mnt/data/ClipCascade-Android-signer-diagnostics-ed9c009.zip`

## Release

- tag: `v3.2.1-extended.19-alpha.2`
- target: `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`
- notes: `docs/RELEASE_NOTES_3.2.1_EXTENDED_19_ALPHA_2.md`

## Corrected device truth

The current build responds to the observation that receive works and foreground outbound works, while background outbound Copy does not. `.19-alpha.2` is an unproven recovery attempt, not a confirmed fix.

A real Gmail verification notification failed. The structurally equivalent synthetic extractor test passes, which narrows—but does not prove—the likely failure toward listener delivery, filtering, extras exposure, lifecycle, or later queue drain.

## Not proven by CI

- in-place update/settings retention;
- foreground target regression safety;
- system Copy toolbar Accessibility event exposure;
- native queue capture and foreground-service drain on target;
- background, removed-from-recents, locked, or screen-off outbound;
- real Gmail listener delivery and extras;
- exactly-once target behavior;
- battery and tray behavior.

Keep PR #1 Draft.
