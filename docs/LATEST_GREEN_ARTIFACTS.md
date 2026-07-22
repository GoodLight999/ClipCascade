# Latest Green Artifacts — 2026-07-22

## Source

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- implementation/release SHA: `2f08e03b325eeff18ec63b1be8cbe1b08cb4f85d`
- intended alpha tag: `v3.2.1-extended.21-alpha.1`
- PR: `#1`, Open and Draft

## Exact implementation CI

- Android standalone CI `29888733469` — success
- Desktop Windows CI `29888733458` — success

Android CI passed:

- every production transform in final order;
- version `3.2.1-extended.21-alpha.1 / 320126`;
- index-level foreground-runner registration before `AppRegistry.registerComponent`;
- runner heartbeat and stale-state diagnostics;
- exact framework-localized Copy candidates and semantic Copy handling;
- selection-only negative behavior;
- explicit `SYSTEM_ALERT_WINDOW` declaration;
- transparent 1×1 `TYPE_APPLICATION_OVERLAY` acquisition path;
- non-touchable but intentionally focusable overlay flags;
- immediate `removeViewImmediate` cleanup in `finally`;
- overlay enable/permission gating policy and unit test;
- ordinary queue no-TTL, capacity 16, and `queue_full` invariants;
- foreground native queue claim/drain and existing ACK plumbing assertions;
- DAWN/Gmail/Perceptron/Beeper/multilingual/negative extractor tests;
- JavaScript bundle;
- Kotlin/resources and unit tests;
- APK assembly and embedded bundle;
- deterministic signer verification;
- artifact upload.

Windows CI passed authenticated HTTP handling, Extended P2P peer-applied ACK, validation-before-ACK, shutdown/tray behavior, tests, and EXE packaging. No Windows implementation source changed for `.21-alpha.1`.

## Android artifact

- application: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.21-alpha.1-standalone`
- versionCode: `320126`
- Actions artifact ID: `8517492289`
- Actions artifact ZIP SHA-256: `9bae0a27c80ddd6b16d9e8d7df95153ad080cd4cd0b864ec5eebc5d914370c87`
- APK SHA-256: `93b85d2bd8474c874d8937e76c09ec97dde90006c4b1e8d97f448557a959c7a9`
- APK size: `147945851` bytes
- signer diagnostics artifact ID: `8517490898`
- signer diagnostics ZIP SHA-256: `51c29398a4bfed2699ef79268d51d73be30b762785d2257551f127c0a52f4f62`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-20T03:33:01Z`

The downloaded Actions ZIP digest matched GitHub's artifact digest. The extracted APK hash/size and signer diagnostics were independently verified.

## Local filenames

- `/mnt/data/ClipCascade-Extended-3.2.1-extended.21-alpha.1-actions.zip`
- `/mnt/data/ClipCascade-Extended-3.2.1-extended.21-alpha.1-vc320126-2f08e03.apk`
- `/mnt/data/ClipCascade-Android-signer-diagnostics-21-alpha1.zip`

## Go-reference scope

The known-working Go implementation used a sticky native foreground service and a temporary transparent 1×1 application overlay to obtain the actual clipboard in the background.

`.21-alpha.1` imports that clipboard-acquisition condition while retaining Extended's durable queue and this acknowledgement order:

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

It does not import the Go mobile transport, does not disable Extended P2P, and does not treat a socket write as Windows clipboard application.

## Target truth

`.19-alpha.2` failed the true listener-path self-test and could not send while the main app UI was closed.

`.20-alpha.1` repaired foreground-runner registration but did not reproduce the known-working Go overlay acquisition condition.

`.21-alpha.1` restores that condition and is Android/Windows CI-green. CI still cannot prove HONOR/MagicOS success.

## Not proven by CI

- in-place update/settings retention;
- overlay permission and WindowManager behavior on HONOR;
- fresh runner heartbeat after UI closure;
- deterministic component/transport self-test on target;
- true listener-path self-test on target;
- background explicit Copy producing `overlay_clipboard_manager`;
- selection-only no-overlay/no-send behavior on target apps;
- removed-from-recents, locked, or screen-off outbound;
- real Gmail/DAWN/Perceptron listener delivery/extras;
- queue-full and long-disconnect durability;
- exactly-once target behavior;
- battery and tray behavior.

This documentation commit is the final handoff-head CI trigger. Record its Android and Windows run IDs in the PR handoff after both complete; do not create another documentation commit merely to self-reference those run IDs.

Keep PR #1 Open and Draft.
