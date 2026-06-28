# Latest Priority 1 Validation Handoff — 2026-06-28

Read this after `docs/LATEST_RUNTIME_FIXES_HANDOFF.md`.

This document records the preparation state for the three mandatory Priority 1 real-device checks:

1. one-time migration to the deterministic stable-test-signed Android APK;
2. in-place installation of the next versionCode with settings/data retention;
3. real Windows tray Quit process termination and immediate relaunch.

No real-device result is claimed here. The APK installation/update checks and Task Manager check still require execution on the user's HONOR 400 Pro and Windows 11 machine.

## Pull request state before this handoff commit

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- PR: `#1`
- PR state: open and Draft
- inspected previous HEAD: `6a97d5c43ca8cf1e9b39d54f6c3535538409fe75`
- previous Android CI: `28313162710` — success
- previous Windows CI: `28313162732` — success

## Stable-signer migration APK

Use this APK only as the first deterministic-signer installation after the one-time uninstall of the old ephemeral-signed build.

- source commit: `6a97d5c43ca8cf1e9b39d54f6c3535538409fe75`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.4-standalone`
- versionCode: `320107`
- Android workflow run: `28313162710`
- artifact ID: `7931606134`
- artifact ZIP SHA-256: `9d3ae8a96d29a587673b3b7671cd36ad709899709358fd8e45e239421b9c3ee1`
- extracted APK SHA-256: `453e23c9e74220c7291b76e5f9b422e0074576d6e1db0da03088a8670fb7895e`
- expected certificate SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

Migration procedure:

1. Record any non-secret settings needed for restoration.
2. Uninstall the currently installed old ephemeral-signed ClipCascade Extended APK once.
3. Install the `320107` APK.
4. Restore login and the Android-granted/user-confirmed settings: Accessibility, notification access, notification runtime permission, battery/background permissions, and boot-resume preference.
5. Launch synchronization and confirm ordinary copied-text relay still reaches Windows before applying the update-probe APK.

The uninstall is required only for the final transition away from the runner-local ephemeral certificate. Do not uninstall between `320107` and `320108`.

## Update-probe implementation

Commit `96bab2db604b227f2c18724c7843f523c539062a` changes exactly one runtime build field:

- `versionCode 320107` -> `versionCode 320108`

The following were intentionally unchanged:

- versionName remains `3.2.1-extended.4-standalone`;
- package remains `com.clipcascade.extended`;
- deterministic stable-test signer and certificate remain unchanged;
- Android relay/extractor/recovery code remains unchanged;
- Windows code remains unchanged;
- P2P `relayId` / `ackRequested`, Windows-applied ACK, ACK-envelope ordering, fallback timer, queue deletion semantics, receive-hash timing, and listener startup ordering remain unchanged.

Reason for leaving versionName unchanged: the proof requires only a higher Android versionCode. A versionCode-only probe minimizes the change surface and isolates signer/update/data-retention behavior from feature changes.

CI for `96bab2db604b227f2c18724c7843f523c539062a`:

- Android workflow run `28313420849` — success
- Windows workflow run `28313420850` — success
- Android CI passed the existing transport ACK transformations, startup/listener ordering checks, unit tests, APK assembly, embedded bundle verification, deterministic certificate verification, and artifact upload.
- Windows CI passed authenticated HTTP tests, existing P2P Windows-applied ACK tests, shutdown/status tests, executable packaging, and artifact upload.

## In-place update-probe APK

Install this APK over the installed `320107` APK without uninstalling or clearing app storage.

- source commit: `96bab2db604b227f2c18724c7843f523c539062a`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.4-standalone`
- versionCode: `320108`
- Android workflow run: `28313420849`
- artifact ID: `7931707239`
- artifact ZIP SHA-256: `27b85c92eef611368c08c6c5b9e6164b65b8dc5121b0a1ab2555573a747c400d`
- extracted APK SHA-256: `328b4b3e0b5caaf1c8fa7c3181da4572a84b3765a3c430e37175a5ec6e6a52b6`
- expected certificate SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

Required update proof:

1. Confirm the `320107` app is installed, configured, and synchronizing.
2. Install `320108` directly over it. Do not uninstall first.
3. Confirm Android accepts the update without a package/signature conflict.
4. Confirm login/app data remain present.
5. Confirm the app-level switches and source-app selection remain present.
6. Confirm Android-managed grants remain present where Android normally retains them: Accessibility, notification access, notification runtime permission, and battery exemption.
7. Confirm the manual HONOR/MagicOS background confirmation and boot-resume preference remain present.
8. Confirm ordinary copied-text relay still reaches Windows.
9. Record each retained/reset item independently rather than reporting only a general success/failure.

A reset caused by the mandatory uninstall before `320107` is expected and is not an update-retention failure. Only the transition from installed/configured `320107` to `320108` is the in-place retention proof.

## Current Windows Quit validation executable

- source commit: `96bab2db604b227f2c18724c7843f523c539062a`
- Windows workflow run: `28313420850`
- artifact ID: `7931680395`
- artifact ZIP SHA-256: `1f062553ebae0a56006e5a4ca31090d784066b02296a1611f0f4ad5c895e5bcd`
- extracted EXE SHA-256: `c8a57326b78c62564351b62adcbb12c11111af769160dd18fa7ea185d9987fb1`

Required Windows real-process proof:

1. Use Task Manager to terminate any old lingering ClipCascade process once before starting the test.
2. Launch the current EXE and reach its normal status window/tray state.
3. Select the tray `Quit` command.
4. Confirm the status window closes and the tray icon disappears.
5. Confirm no `ClipCascade.exe` process remains in Task Manager.
6. Relaunch the EXE immediately.
7. Confirm the normal window opens and no stale single-instance mutex blocks startup.
8. Quit once more and confirm the process again disappears.

Do not infer process termination solely from the tray icon disappearing. Task Manager confirmation is mandatory.

## Trial-and-error / hypothesis record

- No new transport or signing defect was found while preparing this probe.
- The initial question was whether a new visible versionName was necessary. It is not necessary for Android update eligibility; only a greater versionCode and the same package signer are required for this controlled proof. The implementation therefore changes only versionCode.
- Matching prior/current artifacts were fetched and their ZIP digests matched the GitHub Actions artifact records.
- Extracted APK/EXE hashes were recorded above so the exact files used in the real tests can be identified later.
- Real-device installation, retained-setting inspection, Task Manager process disappearance, and immediate relaunch have not yet been executed and must not be marked complete.

## Next documentation update

After the real tests, record the outcome in:

- `docs/progress.md` — chronological result and any failed attempt/root cause;
- `docs/CURRENT_STATUS.md` — current proven facts and remaining limitations;
- `docs/TEST_MATRIX.md` — individual checked/unchecked items;
- `docs/NEXT_CHATGPT_HANDOFF.md` — exact next action and artifact identities.

Keep PR #1 Draft. Do not publish or tag a release from these CI results alone.
