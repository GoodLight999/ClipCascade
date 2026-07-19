# Latest Green Artifacts — 2026-07-19

This file records the latest matching Android source, CI runs, and Android artifact prepared for isolated Priority 1 validation.

## Source and PR

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- implementation anchor: `1e3aae70e2052420bfbcf2e326e04638787dfc1c`
- PR: `#1`
- PR state: open and Draft; keep it Draft

The implementation anchor includes the language-neutral ordinary-copy design, ACK-safe queue retention, and bounded idle retry. Later commits may be documentation-only; compare against this anchor before attributing behavior changes.

## CI at implementation anchor

- Android standalone CI run: `29674843116` — success
- Desktop Windows CI run: `29674843145` — success

Android CI passed:

- every source transform in production order;
- rejection of transformed `CopyCueClassifier` / `looksLikeCopyConfirmation` remnants;
- rejection of ordinary clipboard `TTL_MS` expiry;
- `ClipboardCopySignalPolicy`, `ClipboardRelayRetryPolicy`, and OTP unit tests;
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
- versionName: `3.2.1-extended.15-standalone`
- versionCode: `320119`
- GitHub Actions artifact ID: `8438520128`
- artifact ZIP SHA-256: `e3f50c87ebea56fe0039e3e08a909d282dc10631bb2dc808d6a01e86a1792e2a`
- extracted APK SHA-256: `15ee61ad66e68f114b3a52c160773976ac705954a3a278b6b892559bae6b8ee2`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-17T05:26:07Z`

The GitHub artifact digest and a local `sha256sum` of the downloaded ZIP matched exactly. The APK hash was calculated after extracting `app-debug.apk`. The signer diagnostics artifact reported the same expected V2 signer digest.

## Local filenames used in this conversation

- APK: `ClipCascade-Extended-3.2.1-extended.15-vc320119-1e3aae7.apk`
- artifact ZIP: `ClipCascade-Extended-3.2.1-extended.15-1e3aae7.zip`

These local paths are not repository assets. Use artifact ID `8438520128` when recovering the build from GitHub Actions.

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
- exactly-once target-device behavior;
- real Gmail, Beeper, Perceptron, or SMS notification extraction;
- battery behavior and Windows tray ghost prevention.

Do not mark PR #1 ready or merge it until the target-device matrix passes.
