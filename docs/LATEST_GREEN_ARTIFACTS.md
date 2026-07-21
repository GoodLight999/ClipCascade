# Latest Green Artifacts — 2026-07-21

## Source

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- implementation/release anchor: `290d6690e749fe34367b2383676faf27c0a4ba76`
- intended alpha tag: `v3.2.1-extended.20-alpha.1`
- PR: `#1`, open and Draft

## CI

- Android standalone CI `29843413287` — success
- Desktop Windows CI `29843413149` — success

Android CI passed:

- all transforms in production order;
- version `3.2.1-extended.20-alpha.1 / 320125`;
- index-level runner registration before `AppRegistry.registerComponent`;
- one runner registration per process generation;
- heartbeat and stale-state diagnostics;
- listener delayed rescans and five-minute synthetic notifications;
- hierarchical exact localized Copy candidates;
- selection-only negative behavior;
- foreground native queue claim/drain source assertions;
- DAWN/Gmail/Perceptron/Beeper/multilingual/negative extractor tests;
- no READ_LOGS or SYSTEM_ALERT_WINDOW;
- queue/claim/retry/debug/ACK invariants;
- JavaScript bundle;
- Kotlin/resources and unit tests;
- APK assembly and embedded bundle;
- deterministic signer verification;
- artifact upload.

Windows CI passed authenticated HTTP, Extended peer-applied ACK, validation-before-ACK, shutdown/tray, tests, and EXE packaging. No Windows implementation file changed.

## Android artifact

- application: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.20-alpha.1-standalone`
- versionCode: `320125`
- Actions artifact ID: `8500372630`
- Actions artifact ZIP SHA-256: `ba2152241fdfe8c5bb99e3087b8781faa15915a281df3e10a9e8065fad177660`
- APK SHA-256: `1b48a7bb7e6d4ab757a3a044fda233e8363ce58d6d634b071e7f90a90ac35cbe`
- APK size: `147937563` bytes
- signer diagnostics artifact ID: `8500368947`
- signer diagnostics ZIP SHA-256: `74aa5e5b0e966abc73e1e0d68750564e051fb0f4e9f270f84c05a0dda0c92e57`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-19T15:19:19Z`

The downloaded Actions ZIP digest matched GitHub's artifact digest. The extracted APK and signer diagnostics were independently verified.

## Local filenames

- `/mnt/data/ClipCascade-Extended-3.2.1-extended.20-alpha.1-actions.zip`
- `/mnt/data/ClipCascade-Extended-3.2.1-extended.20-alpha.1-vc320125-290d669.apk`
- `/mnt/data/ClipCascade-Android-signer-diagnostics-20-alpha1.zip`

## Target truth

`.19-alpha.2` failed the true listener-path self-test and still could not send while the main app UI was closed. `.20-alpha.1` addresses a confirmed foreground-runner registration defect, but has no target success evidence yet.

## Not proven by CI

- in-place update/settings retention;
- fresh runner heartbeat on HONOR;
- component/transport self-test on target;
- true listener-path self-test on target;
- background Copy event exposure, capture, and drain;
- removed-from-recents, locked, or screen-off outbound;
- real Gmail listener delivery/extras;
- exactly-once target behavior;
- battery and tray behavior.

Keep PR #1 Draft.
