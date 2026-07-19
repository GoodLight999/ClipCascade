# Latest Green Artifacts — 2026-07-19

This file records the latest matching Android source, CI runs, and Android artifact prepared for isolated Priority 1 validation.

## Source and PR

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- implementation anchor: `20ef493a3b322ec2d95f76cee8902426b7623559`
- PR: `#1`
- PR state: open and Draft; keep it Draft

The implementation anchor includes the language-neutral ordinary-copy redesign and the updated validation matrix. Later commits may be documentation-only; compare against this anchor before attributing behavior changes.

## CI at implementation anchor

- Android standalone CI run: `29669730768` — success
- Desktop Windows CI run: `29669730745` — success

Android CI passed:

- all source transforms;
- rejection of transformed `CopyCueClassifier` / `looksLikeCopyConfirmation` remnants;
- JavaScript bundle generation;
- `ClipboardCopySignalPolicy` and OTP unit tests;
- Android resource and Kotlin compilation;
- APK assembly;
- embedded-bundle verification;
- deterministic signer verification;
- artifact upload.

Windows CI passed the retained desktop tests and packaging path, including the Extended P2P peer-applied acknowledgement implementation.

## Android artifact

- application: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.14-standalone`
- versionCode: `320118`
- GitHub Actions artifact ID: `8436928714`
- artifact ZIP SHA-256: `a569ff44a9b998754fc6190c742993508801b030c3237ed9b67b556d8e66154a`
- extracted APK SHA-256: `29c8e4a88b556aa9d94a07643b894d15d746740d5207e543671d8875196d63ba`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-17T02:08:43Z`

## Local filenames used in the producing conversation

- APK: `ClipCascade-Extended-3.2.1-extended.14-vc320118-20ef493.apk`
- artifact ZIP: `ClipCascade-Extended-3.2.1-extended.14-20ef493.zip`

These local paths are not repository assets. Use artifact ID `8436928714` when recovering the build from GitHub Actions.

## Test isolation

Before every Android outbound validation:

1. disable Microsoft Phone Link clipboard synchronization;
2. stop every competing clipboard synchronization tool;
3. do not use ADB, root, or Shizuku;
4. use a unique synthetic value for each copy action;
5. attribute success through native queue state, exactly one Windows application, peer ACK, and ACK-based deletion—not clipboard appearance alone.

## Not proven by CI

The following remain unproven until isolated target-device evidence exists:

- selection-only suppression on the HONOR target;
- multilingual behavior;
- background, removed-from-recents, locked, and screen-off ordinary-copy delivery;
- exactly-once target-device behavior;
- real Gmail, Beeper, Perceptron, or SMS notification extraction;
- battery behavior and Windows tray ghost prevention.

Do not mark PR #1 ready or merge it until the target-device matrix passes.