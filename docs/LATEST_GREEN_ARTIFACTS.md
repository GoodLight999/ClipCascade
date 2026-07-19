# Latest Green Artifacts — 2026-07-19

This file records the latest matching Android source, CI runs, and Android artifact prepared for isolated Priority 1 validation.

## Source and PR

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- implementation anchor: `a010d7f0fb3871252580666df3264980b32c93cb`
- PR: `#1`
- PR state: open and Draft; keep it Draft

The implementation anchor includes language-neutral Copy confirmation, ACK-safe no-TTL/no-overflow retention, bounded retry, Gmail notification-listener recovery/diagnostics, active-notification rescan, and the default-OFF outbound transport debug notification. Later commits may be documentation-only; compare against this anchor before attributing behavior changes.

## CI at implementation anchor

- Android standalone CI run: `29681462233` — success
- Desktop Windows CI run: `29681462236` — success

Android CI passed:

- every source transform in production order;
- language-neutral Copy invariants;
- no ordinary clipboard TTL or overflow eviction;
- queue capacity/retry tests;
- broad OTP and Gmail-shaped extractor tests;
- listener rebind/rescan and stage-diagnostic source assertions;
- outbound debug notification source and ACK-isolation assertions;
- JavaScript bundle generation;
- Android resource and Kotlin compilation;
- APK assembly;
- embedded-bundle verification;
- deterministic signer verification;
- artifact upload.

Windows CI passed retained authenticated HTTP, Extended P2P peer-applied ACK, validation-before-ACK, shutdown/tray, and packaging tests. `.17` made no Windows implementation change.

## Android artifact

- application: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.17-standalone`
- versionCode: `320121`
- GitHub Actions artifact ID: `8440717410`
- artifact ZIP SHA-256: `5e545d9210a97819cfae79bde5a278e69631b395f55aa6cad0f260e4cd38134e`
- extracted APK SHA-256: `f3bba473b78d1f44f73fe529cd6c0187881269615aeb709651a6f8cd675ffb86`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-17T09:22:32Z`

The GitHub artifact digest and local `sha256sum` of the downloaded ZIP matched exactly. The APK hash was calculated after extracting `app-debug.apk`. The signer diagnostics artifact reported the expected V2 signer digest.

## Local filenames used in this conversation

- APK: `ClipCascade-Extended-3.2.1-extended.17-vc320121-a010d7f.apk`
- artifact ZIP: `ClipCascade-Extended-3.2.1-extended.17-a010d7f.zip`

These local paths are not repository assets. Use artifact ID `8440717410` when recovering the build from GitHub Actions.

## Test isolation

Before every Android outbound validation:

1. disable Microsoft Phone Link clipboard synchronization;
2. stop every competing clipboard synchronization tool;
3. do not use ADB, root, or Shizuku;
4. use unique synthetic values;
5. distinguish listener receipt, extras visibility, extraction, queueing, transport acceptance, peer application, ACK, and deletion;
6. never persist real notification text or real authentication values.

## Not proven by CI

The following remain unproven until isolated target-device evidence exists:

- real Gmail notification delivery to the listener;
- Gmail extras containing the code on the HONOR target;
- real Gmail extraction/queue/transport/ACK;
- listener rebind/rescan behavior after OEM process reclamation;
- outbound debug notification ON/OFF behavior on the target;
- selection-only suppression and multilingual Copy behavior;
- background, removed-from-recents, locked, and screen-off ordinary-copy delivery;
- long-disconnect retention and queue-full behavior;
- exactly-once target-device behavior;
- real Beeper, Perceptron, or SMS extraction;
- battery behavior and Windows tray ghost prevention.

Do not mark PR #1 ready or merge it until the target-device matrix passes.
