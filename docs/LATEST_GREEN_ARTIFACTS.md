# Latest Green Artifacts — 2026-07-21

This file records the latest matching alpha implementation, CI, tag, and Android artifact prepared for isolated Priority 1 validation.

## Source and PR

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- implementation anchor: `87e138380a139671168effd24a64df844f1bb879`
- alpha tag: `v3.2.1-extended.18-alpha.1`
- PR: `#1`
- PR state: open and Draft; keep it Draft

The tag resolves exactly to the implementation anchor.

## Implementation CI

- Android standalone CI: `29827698937` — success
- Desktop Windows CI: `29827698930` — success

Android CI passed:

- every source transform in production order;
- alpha version and versionCode checks;
- language-neutral Copy invariants;
- no ordinary clipboard TTL or overflow eviction;
- bounded queue and retry tests;
- broad OTP and Gmail-shaped extractor tests;
- own-package synthetic-marker boundary;
- listener-path self-test source assertions;
- persistent notification receipt source assertions;
- receipt TTL/capacity unit tests;
- existing listener rebind/rescan and diagnostics assertions;
- outbound debug notification ACK-isolation assertion;
- JavaScript bundle generation;
- Android Kotlin/resources and unit tests;
- APK assembly;
- embedded bundle verification;
- deterministic signer verification;
- artifact upload.

Windows CI passed retained authenticated HTTP, Extended P2P peer-applied ACK, validation-before-ACK, shutdown/tray, and packaging tests. The alpha directly changes no Windows implementation file.

## Android artifact

- application: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.18-alpha.1-standalone`
- versionCode: `320122`
- GitHub Actions artifact ID: `8494023518`
- Actions artifact ZIP SHA-256: `ae84ba8adda4b0c33ac8dbd39f835fdf7be9142dea8788e579361f6b0917cbeb`
- extracted APK SHA-256: `53da5cae4b5e2c7dd5cad0e6064aec47945b9d9620fbd30d9edaf391d37ac88d`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- Actions artifact expiry: `2026-10-19T11:50:45Z`

The downloaded Actions artifact ZIP digest matched GitHub's reported digest exactly. The APK hash was calculated after extracting `app-debug.apk`. Signer diagnostics reported the expected V2 signer digest.

## Local filenames used in the alpha conversation

- APK: `ClipCascade-Extended-3.2.1-extended.18-alpha.1-vc320122-87e1383.apk`
- Actions artifact ZIP: `ClipCascade-Extended-3.2.1-extended.18-alpha.1-actions.zip`

These are local conversation artifacts, not repository files.

## Alpha prerelease

- tag: `v3.2.1-extended.18-alpha.1`
- target: `87e138380a139671168effd24a64df844f1bb879`
- release notes: `docs/RELEASE_NOTES_3.2.1_EXTENDED_18_ALPHA_1.md`

The release workflow is designed to wait for matching-sha push Android and Windows CI, then create APK/ZIP/SHA256SUMS assets. The connector verified the tag and target commit. It could not independently enumerate the public release asset list, so do not substitute unverified release-asset hashes for the Actions artifact and APK hashes recorded above.

## Target-device evidence inherited from `.17`

- ordinary synchronization recovered and was working;
- Gmail was not testable because no real OTP arrived.

The alpha must first prove that ordinary synchronization remains working after in-place installation.

## Not proven by CI

- in-place alpha update and settings retention;
- no regression of recovered ordinary synchronization;
- listener-path self-test on HONOR;
- persistent receipt duplicate suppression on HONOR;
- real Gmail notification delivery/extras/extraction;
- OEM listener recovery;
- outbound debug switch ON/OFF on target;
- multilingual Copy behavior;
- background/screen-off ordinary or Gmail delivery;
- long-disconnect durability and queue-full behavior;
- exactly-once target behavior;
- battery and Windows tray behavior.

Do not mark PR #1 ready or merge it.
