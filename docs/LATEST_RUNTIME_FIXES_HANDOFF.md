# Latest Runtime Fixes Handoff — 2026-06-28

Read this after the canonical handoff files. It records the runtime issues reported after the first successful real SMS use.

## Real-world validation added

The user deliberately selected Yahoo! JAPAN SMS authentication instead of a passkey. The real SMS verification value reached the Windows clipboard successfully.

This proves one real Yahoo! JAPAN SMS notification/extraction/transport/Windows-clipboard path on the user's HONOR 400 Pro. The exact screen/lock duration was not specified, so it does not complete the locked or 1/15/30+ minute screen-off matrix.

## Android update conflict

Observed behavior: every CI APK reported a package conflict and required uninstalling the previous Android app.

Root cause: the standalone debug build used the Android SDK's default debug signer. GitHub-hosted runners create an ephemeral signing identity, so successive APKs had the same package name but different signing certificates. Android correctly rejected them as incompatible updates.

Repair:

- added `scripts/generate_stable_test_signing.py`;
- it deterministically regenerates one public 2048-bit RSA test identity during each build;
- CI converts the generated key/certificate to a temporary PKCS#12 store in the ignored build directory;
- Gradle signs `com.clipcascade.extended` with that identity;
- CI verifies the finished APK certificate digest with `apksigner`;
- expected certificate SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`;
- Android version is `3.2.1-extended.4-standalone`, versionCode `320107`.

Migration rule: the currently installed ephemeral-signed APK must be uninstalled once before installing the first stable-signed build. Every later APK carrying this same signer and a higher versionCode should update in place and retain app data/settings.

Security boundary: this deterministic identity is intentionally public and exists only to make development/test artifacts updateable. It is not a private production release identity and must not be represented as proof of publisher authenticity.

Trial record:

1. An attempted direct key-file commit was blocked. The final implementation commits no binary or encoded private-key blob; it deterministically generates the documented public test identity inside the build directory.
2. Android run `28312102175` built and tested the APK, but the initial certificate assertion failed.
3. A diagnostic artifact proved the APK was correctly signed. Expected, generated-certificate, and APK-certificate SHA-256 values were all `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`.
4. The failure was only the parser: current `apksigner` printed `V2 Signer: certificate SHA-256 digest: <value>`, while the workflow read field 2 (`certificate SHA-256 digest`) instead of the final field.
5. Commit `50808d40973c63a35141d860899efca5f65a411d` changed the parser to use the final colon-delimited field. Android run `28312431226` then passed source transforms, ACK transforms, unit tests, APK assembly, embedded bundle verification, stable certificate verification, diagnostics upload, and APK upload.
6. The tagged-release workflow was aligned in `386921f648f639bb6da61ff159eff465e418c27d` so it runs the same Android version transform, stable signer, final-field certificate parser, and Windows shutdown tests.

## Windows Quit left the EXE alive

Observed behavior: selecting tray `Quit` removed visible UI/tray state but left the PyInstaller process running until Task Manager killed it.

Confirmed lifecycle gaps:

- the tray stopped only `pystray.Icon` and called `root.quit()`;
- the separate status dialog ran its own Tk main loop;
- P2P used a dedicated asyncio event-loop thread;
- final disconnect requested asynchronous teardown without waiting for completion.

Repair:

- expose and close the active status dialog;
- stop accepting status/reconnect work after shutdown begins;
- stop the tray icon;
- close and destroy the hidden Tk root on the Tk thread;
- stop and join the recovery watchdog;
- await P2P transport teardown;
- stop, join, and close the P2P asyncio loop;
- flush logging;
- after graceful teardown, explicit tray Quit uses a final process-exit guarantee so a third-party GUI/network worker cannot leave a frozen EXE behind.

`tests/test_windows_shutdown.py` verifies idempotent Quit signaling, status/root/icon closure, and the revised recovery wording. Existing authenticated-HTTP and P2P Windows-applied ACK tests remain mandatory. Windows run `28312431225` passed all three test groups, PyInstaller packaging, and artifact upload.

## `Automatic reconnect: False`

The old Boolean meant only that the manager was not currently inside its reconnect-delay/retry phase. It did not mean the automatic-recovery feature was disabled. While normally connected, `False` was the expected value.

The status UI now displays an explicit state instead:

- `Automatic recovery: Standing by`
- `Automatic recovery: Retrying now`
- `Automatic recovery: Paused by user`

This is display-only. It does not alter transport recovery or ACK semantics.

## Preserved behavior

The transport acknowledgement implementation was not replaced or bypassed. Extended P2P still removes Android relay items only after validated Windows text clipboard application and peer ACK, subject to the documented old-peer compatibility fallback.

## Required validation

1. Kill the old lingering Windows process once, start the new EXE, choose Quit, and confirm no ClipCascade process remains in Task Manager.
2. Relaunch immediately after Quit and confirm no stale single-instance mutex blocks startup.
3. Uninstall the currently installed Android APK once and install the first stable-signed `extended.4` APK.
4. On the next development build, install over `extended.4` without uninstalling and confirm settings/data are retained.
5. Continue the reboot, process-kill, locked, and 1/15/30+ minute SMS/email matrix.

PR #1 must remain Draft until the mandatory target-device and regression matrix is complete.
