# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/LATEST_ALPHA21_FAILURE_ALPHA22_NATIVE_RECOVERY_HANDOFF.md`
5. `docs/LATEST_GREEN_ARTIFACTS.md`
6. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
7. `docs/LATEST_GO_OVERLAY_CLIPBOARD_RECOVERY_HANDOFF.md`
8. `docs/LATEST_FOREGROUND_RUNNER_LIFECYCLE_HANDOFF.md`
9. `docs/LATEST_NOTIFICATION_LISTENER_ALPHA_HANDOFF.md`
10. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`
11. older focused handoffs linked from those documents

Current phase: `.21-alpha.1` failed both decisive HONOR tests. `.22-alpha.1 / 320127` is exact-SHA Android/Windows CI-green, both final APKs are independently hashed and signer-matched, and the next decisive step is isolated HONOR/MagicOS installation and testing.

## Target truth

### `.19-alpha.2`

- true NotificationListener-path self-test failed;
- Android outbound failed whenever the main UI was not open;
- inbound continued to work.

### `.20-alpha.1`

- repaired a real Notifee foreground-runner registration defect;
- did not reproduce the Go clipboard-acquisition structure;
- remained target-unproven.

### `.21-alpha.1`

- true notification-listener-path self-test failed on HONOR;
- background Android-to-Windows clipboard sending failed on HONOR;
- CI was green but the target build failed.

## Corrected Go-reference analysis

Reference: `wuxinkami/ClipCascade_go_fork` at `0ff3ba4b28daccc1a51e7c09907792bc0f8e53a8`.

The known-working Android path is not merely an overlay:

1. Accessibility starts and binds a native foreground service.
2. The service returns `START_STICKY`.
3. The service owns overlay creation and clipboard reading independently of the Activity.
4. Accessibility accepts broad candidate events, including selection changes and generic clicks.
5. Weak probes wait about 1.2 seconds before the native read.
6. The service creates a transparent 1×1 `TYPE_APPLICATION_OVERLAY`, reads `ClipboardManager`, and removes the view.

`.21` copied only item 6 and still required an exact semantic/framework Copy cue. Calling `.21` Go-equivalent was incorrect.

## `.22-alpha.1` implementation

- implementation/release SHA: `29febc3e7a83575564145d470c4143b5b92e42f4`
- version: `3.2.1-extended.22-alpha.1-standalone`
- versionCode: `320127`
- intended tag: `v3.2.1-extended.22-alpha.1`
- implementation Android CI `29917141620`: success
- implementation Windows CI `29917141540`: success
- validated documentation head `a12621942d2a22b51fb94b9042509ab3845b1c3f`
- documentation-head Android `29917915931`: success
- documentation-head Windows `29917915917`: success

### Clipboard acquisition

- native `ClipboardAcquisitionService` foreground service;
- Accessibility starts and binds it;
- service returns `START_STICKY`;
- service owns overlay, `ClipboardManager` read, mutation proof, and durable queue insertion;
- broad selection/click/notification/announcement probes matching the Go event entry;
- exact Copy labels and semantic ACTION_COPY remain strong fast paths, not the sole entry;
- selection records the old clipboard fingerprint immediately;
- delayed read queues only when the SHA-256 fingerprint changes;
- selection without Copy remains inert;
- Accessibility never inserts directly into `ClipboardRelayStore`.

### Notification trust and test

- Android 15+ setup uses a user-confirmed self-managed CompanionDeviceManager association;
- notification access is requested through the companion flow;
- NotificationListenerService is non-exported;
- true listener self-test uses a separately installed, same-signed helper APK;
- helper package: `com.clipcascade.extended.testnotifier`;
- helper Activity is protected by signature permission;
- helper posts a normal external notification with a generated fake code;
- no direct queue insertion is permitted for this test.

### ACK boundary retained

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

No ordinary queue TTL was added. Capacity remains 16 with explicit `queue_full`; accepted items are not evicted.

## Final artifacts

- artifact ID `8528455362`;
- ZIP SHA-256 `61cba5012ebc412d0075c165b29fb6a5d4ded79f1ad8a28a993218c722717539`;
- main APK SHA-256 `ac6fe987eb3e4a469abcdc53bc552313f752c8780a27498a8abf7aa89c8a681a`;
- main size `147968683` bytes;
- helper APK SHA-256 `61e9183d4eb93fedb80e1ea0b624663516f61fc2a2e5752df985aee9066b6e2b`;
- helper size `831357` bytes;
- signer diagnostics artifact `8528452879`;
- signer SHA-256 for both `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`;
- expiry `2026-10-20T11:48:52Z`.

The downloaded ZIP and both APKs were independently verified.

## Temporary PR #2 record

Temporary Draft PR #2 was used only as a staging CI trigger. The validated tree was integrated by no-force fast-forward. GitHub reports PR #2 as closed/merged because its exact head is now an ancestor of the base branch; no separate merge commit, merge-button action, Ready conversion, auto-merge, or force push occurred.

Canonical PR #1 remains Open, Draft, and unmerged.

## Trial and error retained

- Android `29842404023`: old transform anchor failure; no APK.
- Android `29842757548`: localized resource anchor failure; no APK.
- Android `29843090432`, Windows `29843090421`: `.20` implementation green.
- Android `29843413287`, Windows `29843413149`: `.20` release SHA green.
- Android `29888733469`, Windows `29888733458`: `.21` implementation green, later target-failed.
- Android `29889193284`, Windows `29889193301`: `.21` handoff green, later target-failed.
- a non-default-branch diagnostic workflow did not report a run and is not counted.
- staging Android `29915789910`: success.
- staging Android `29916941772`: success after final direct-read control fixup.
- implementation Android `29917141620` and Windows `29917141540`: success.
- documentation-head Android `29917915931` and Windows `29917915917`: success.
- no force push, Ready conversion, auto-merge, or separate merge commit occurred.

## Next actions

1. Install the `.22` main APK over the existing app without uninstalling.
2. Install the helper APK separately.
3. Complete overlay, Accessibility, CompanionDeviceManager, notification-access, battery, and MagicOS background setup.
4. Run deterministic component/transport test.
5. Run external notification-listener test.
6. Run foreground Copy, then UI-closed background Copy.
7. Verify selection without Copy does not queue or change Windows.
8. Only after simple success, test recents removal, lock, screen off, reboot, disconnect, queue full, and exactly-once behavior.

PR #1 must remain Open and Draft. CI is not HONOR/MagicOS proof.