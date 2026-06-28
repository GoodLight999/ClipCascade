# Latest UI and Boot-Resume Handoff — 2026-06-28

This addendum records the changes after the guided bilingual OTP build. Read it together with `docs/progress.md`.

## Code baseline

- UI cleanup and boot-resume implementation: `1744ceb79eae126f04ed540b26ba3c61afbfabfe`
- Android version: `3.2.1-extended.3-standalone`
- PR #1 must remain Draft.

## Removed upstream behavior

The Extended Android build no longer:

- requests the upstream version file;
- requests upstream funding metadata;
- shows `New version available` for the original ClipCascade project;
- shows the original `GITHUB`, `HELP`, `DONATE`, or `HOMEPAGE` footer controls.

CI rejects those URLs, labels, and banner text if they reappear.

## Bottom bar

`Sharing setup` is no longer an absolute overlay. It is in a dedicated bottom bar outside the app's scrollable content, so it cannot cover bottom-page controls.

The same bar contains a localized boot-resume switch:

- `起動時に同期を再開`
- `Resume sync at startup`

## Exact boot behavior

The switch writes the existing `relaunch_on_boot` value.

After `BOOT_COMPLETED`, ClipCascade requests background synchronization recovery only when:

1. the switch is enabled; and
2. synchronization was active before reboot (`wsIsRunning == true`).

The app does not intentionally open its visible activity at boot. It first attempts Headless JS recovery and also schedules a one-time WorkManager heartbeat after 30 seconds. If synchronization was stopped before reboot, it remains stopped.

The duplicate advanced-login checkbox is removed from the transformed build. Login refreshes the persisted boot value before saving the complete settings object, so stale React state cannot revert the bottom-bar switch.

## Trial and correction

- Android run `28310040428`: upstream cleanup and non-overlapping bottom bar succeeded.
- Android run `28310252416`: failed because an overly broad regex removed login-form rows before the old boot checkbox.
- Commit `1744ceb79eae126f04ed540b26ba3c61afbfabfe` isolates only the row containing the unique `relaunch_on_boot` marker.
- Android run `28310323332`: succeeded, including transforms, bilingual/upstream assertions, ACK transforms, bundle, unit tests, APK assembly, embedded-bundle check, and artifact upload.
- Windows run `28310323338`: succeeded.

## Required real-device tests

- Confirm no update banner or upstream footer exists.
- Confirm the bottom bar never covers app content.
- Change the boot switch, close/reopen the app, and confirm persistence.
- Log in after changing it and confirm login does not revert it.
- Reboot while synchronization is active and confirm background recovery.
- Reboot while synchronization is stopped and confirm it remains stopped.
- Exercise the delayed fallback when immediate boot recovery is unavailable.
- Continue the synthetic OTP and real screen-off SMS/email matrix.

Boot recovery is CI validated but not yet proven on HONOR 400 Pro / MagicOS.
