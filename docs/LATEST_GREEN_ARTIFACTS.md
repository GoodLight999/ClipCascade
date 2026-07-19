# Latest Green Artifacts — 2026-07-19

This file records the latest matching Android source, CI runs, and Android artifact prepared for isolated Priority 1 validation.

## Source and PR

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- implementation anchor: `f86705c513c56a9fd24e218f8513dad9cead2ed8`
- PR: `#1`
- PR state: open and Draft; keep it Draft

The implementation anchor includes language-neutral Copy confirmation, ACK-safe no-TTL retention, bounded idle retry, and ACK-safe full-queue rejection. Later commits may be documentation-only; compare against this anchor before attributing behavior changes.

## CI at implementation anchor

- Android standalone CI run: `29675438972` — success
- Desktop Windows CI run: `29675438978` — success

Android CI passed:

- every source transform in production order;
- rejection of transformed `CopyCueClassifier` / `looksLikeCopyConfirmation` remnants;
- rejection of ordinary clipboard `TTL_MS` expiry;
- rejection of overflow eviction of accepted unacknowledged items;
- final `queue_full` handling in Accessibility and settings-test paths;
- queue-capacity, retry-policy, Copy-signal, and OTP unit tests;
- JavaScript bundle generation;
- Android resource and Kotlin compilation;
- APK assembly;
- embedded-bundle verification;
- deterministic signer verification;
- artifact upload.

Windows CI passed the retained desktop tests and packaging path, including Extended P2P peer-applied acknowledgement and validation-before-ACK ordering.

## Android artifact

- application: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.16-standalone`
- versionCode: `320120`
- GitHub Actions artifact ID: `8438725636`
- artifact ZIP SHA-256: `dda947ceb29452edc4defc94ee3c09852a87529b2db581a0f1d304b0182564f6`
- extracted APK SHA-256: `1bb1301e0a44a06f42cb04cbe55de03e9abc0baa6c224738d89f4686409a5def`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-17T05:49:26Z`

The GitHub artifact digest and a local `sha256sum` of the downloaded ZIP matched exactly. The APK hash was calculated after extracting `app-debug.apk`. The signer diagnostics artifact reported the same expected V2 signer digest.

## Local filenames used in this conversation

- APK: `ClipCascade-Extended-3.2.1-extended.16-vc320120-f86705c.apk`
- artifact ZIP: `ClipCascade-Extended-3.2.1-extended.16-f86705c.zip`

These local paths are not repository assets. Use artifact ID `8438725636` when recovering the build from GitHub Actions.

## Test isolation

Before every Android outbound validation:

1. disable Microsoft Phone Link clipboard synchronization;
2. stop every competing clipboard synchronization tool;
3. do not use ADB, root, or Shizuku;
4. use a unique synthetic value for each Copy action;
5. attribute success through native queue state, exactly one Windows application, peer ACK, and ACK-based deletion—not clipboard appearance alone.

## Not proven by CI

The following remain unproven until isolated target-device evidence exists:

- selection-only suppression on the HONOR target;
- multilingual behavior;
- background, removed-from-recents, locked, and screen-off ordinary-copy delivery;
- queue retention beyond ten/thirty minutes on the target device;
- preservation of 16 accepted items plus 17th `queue_full` rejection on the target device;
- exactly-once target-device behavior;
- real Gmail, Beeper, Perceptron, or SMS notification extraction;
- battery behavior and Windows tray ghost prevention.

Do not mark PR #1 ready or merge it until the target-device matrix passes.
