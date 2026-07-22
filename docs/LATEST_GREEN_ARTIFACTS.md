# Latest Green Artifacts and Pending Candidate — 2026-07-22

## Latest confirmed green artifact

The latest fully recorded green artifact remains `.20-alpha.1` until `.21-alpha.1` completes exact-SHA Android and Windows CI.

### Source

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- implementation/release anchor: `290d6690e749fe34367b2383676faf27c0a4ba76`
- intended alpha tag: `v3.2.1-extended.20-alpha.1`
- PR: `#1`, Open and Draft

### CI

- Android standalone CI `29843413287` — success
- Desktop Windows CI `29843413149` — success

### Android artifact

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

## Pending `.21-alpha.1` candidate

- implementation/release candidate: `2ede4caf7b59b0cb9d56f06aa976e4d61c63be18`
- first handoff tree advanced by fast-forward to: `75d1d5ddca6c7197a13668e0a3883034f5ea0092`
- versionName: `3.2.1-extended.21-alpha.1-standalone`
- versionCode: `320126`
- intended tag: `v3.2.1-extended.21-alpha.1`
- Android CI: pending for the exact current handoff SHA
- Windows CI: pending for the exact current handoff SHA
- Actions artifact: pending
- ZIP/APK hash, size, signer diagnostics, and expiry: pending

`.21-alpha.1` adds the explicit user-authorized Go-proven 1×1 transparent overlay clipboard-acquisition fallback. It preserves the existing Extended queue and Windows-applied peer ACK.

Do not replace the confirmed `.20` artifact metadata above until the `.21` exact-SHA Android and Windows runs are both successful and the downloaded artifact is independently hashed.

## Target truth

`.19-alpha.2` failed the true listener-path self-test and could not send while the main app UI was closed.

`.20-alpha.1` repaired foreground-runner registration but did not reproduce the known-working Go overlay acquisition condition.

`.21-alpha.1` restores that condition in source, but CI cannot prove HONOR/MagicOS success.

## Not proven by CI

- in-place update/settings retention;
- overlay permission and WindowManager behavior on HONOR;
- fresh runner heartbeat after UI closure;
- component/transport self-test on target;
- true listener-path self-test on target;
- background explicit Copy producing `overlay_clipboard_manager`;
- selection-only no-overlay/no-send behavior on target apps;
- removed-from-recents, locked, or screen-off outbound;
- real Gmail/DAWN/Perceptron listener delivery/extras;
- exactly-once target behavior;
- battery and tray behavior.

Keep PR #1 Open and Draft.
