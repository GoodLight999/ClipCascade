# Latest Green Artifacts — 2026-07-22

## Source

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- implementation/release SHA: `29febc3e7a83575564145d470c4143b5b92e42f4`
- intended alpha tag: `v3.2.1-extended.22-alpha.1`
- PR: `#1`, Open and Draft

## Exact implementation CI

- Android standalone CI `29917141620` — success
- Desktop Windows CI `29917141540` — success

Android CI passed:

- all production transforms in final order;
- version `3.2.1-extended.22-alpha.1 / 320127`;
- early Notifee transport-runner registration and heartbeat;
- native `ClipboardAcquisitionService` ownership;
- `START_STICKY` and Accessibility bind path;
- broad selection/click/announcement/notification probes;
- pre-selection clipboard fingerprint baseline and mutation policy tests;
- transparent 1×1 `TYPE_APPLICATION_OVERLAY` read path;
- no direct Accessibility queue insertion;
- ordinary queue no-TTL, capacity 16, and `queue_full` invariants;
- foreground native queue claim/drain and existing ACK assertions;
- Android 15+ CompanionDeviceManager setup sources;
- non-exported NotificationListenerService;
- external same-signed notification-test helper module;
- JavaScript bundle;
- Kotlin/resources and unit tests;
- main APK and helper APK assembly;
- embedded main-app bundle;
- matching deterministic signer verification for both APKs;
- artifact upload.

Windows CI passed authenticated HTTP handling, Extended P2P peer-applied ACK, validation-before-ACK, shutdown/tray behavior, tests, and EXE packaging. No Windows implementation source changed for `.22-alpha.1`.

## Android artifact

- application: `ClipCascade Extended`
- main package: `com.clipcascade.extended`
- helper package: `com.clipcascade.extended.testnotifier`
- versionName: `3.2.1-extended.22-alpha.1-standalone`
- versionCode: `320127`
- Actions artifact ID: `8528455362`
- Actions artifact ZIP SHA-256: `61cba5012ebc412d0075c165b29fb6a5d4ded79f1ad8a28a993218c722717539`
- main APK SHA-256: `ac6fe987eb3e4a469abcdc53bc552313f752c8780a27498a8abf7aa89c8a681a`
- main APK size: `147968683` bytes
- helper APK SHA-256: `61e9183d4eb93fedb80e1ea0b624663516f61fc2a2e5752df985aee9066b6e2b`
- helper APK size: `831357` bytes
- signer diagnostics artifact ID: `8528452879`
- signer diagnostics ZIP SHA-256: `b60b8d425ef486a87a7905de06b429415f50bb9e874b96ec29e73f840b6bc114`
- signer SHA-256 for both APKs: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-20T11:48:52Z`

The downloaded Actions ZIP digest matched GitHub's recorded digest. Both APK hashes and sizes matched the packaged `SHA256SUMS.txt`. Signer diagnostics contained the expected SHA-256 certificate digest twice, once for each APK.

## Local filenames

- `/mnt/data/ClipCascade-Extended-3.2.1-extended.22-alpha.1-actions.zip`
- `/mnt/data/ClipCascade-Extended-3.2.1-extended.22-alpha.1.apk`
- `/mnt/data/ClipCascade-Notification-Test-Sender-22-alpha.1.apk`
- `/mnt/data/ClipCascade-Android-signer-diagnostics-22-alpha1.zip`

## Target truth

`.21-alpha.1` was Android/Windows CI-green but failed both decisive target tests:

- background Android-to-Windows clipboard sending failed;
- true notification-listener-path self-test failed.

`.22-alpha.1` corrects the incomplete Go-reference integration by moving acquisition into a bound sticky native service and accepting broad probes with clipboard-fingerprint mutation proof. It also addresses Android 15+ OTP redaction through CompanionDeviceManager association and replaces the same-package listener test with a separately installed external helper APK.

These changes are CI-green but are not yet HONOR/MagicOS proof.

## ACK boundary retained

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

The native acquisition service inserts into the existing durable queue and does not directly send, ACK, or delete.

## Not proven by CI

- in-place main-APK update and settings retention;
- helper APK installation/launch on HONOR;
- native acquisition service survival after UI closure;
- target clipboard fingerprint timing and overlay access;
- selection-only no-queue behavior in representative apps;
- CompanionDeviceManager association flow on MagicOS;
- unredacted external helper notification fields;
- real Gmail/DAWN/Perceptron delivery/extras;
- removed-from-recents, locked, or screen-off outbound;
- queue-full and long-disconnect durability;
- exactly-once target behavior;
- battery and tray behavior.

Keep PR #1 Open and Draft. CI is not target proof.
