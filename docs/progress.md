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

Current phase: `.21-alpha.1` failed both decisive HONOR tests. `.22-alpha.1 / 320127` is exact-SHA Android/Windows CI-green with final main/helper artifacts and now requires isolated HONOR/MagicOS testing.

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

The user tested `.21-alpha.1` and established:

- the true notification-listener-path self-test failed;
- background Android-to-Windows clipboard sending failed.

`.21-alpha.1` is a failed target build despite Android and Windows CI success.

## Corrected Go-reference analysis

Reference: `wuxinkami/ClipCascade_go_fork` at `0ff3ba4b28daccc1a51e7c09907792bc0f8e53a8`.

The known-working Android path is not merely an overlay:

1. Accessibility starts and binds a native foreground service.
2. The service returns `START_STICKY`.
3. The service owns overlay creation and clipboard reading independently of the Activity.
4. Accessibility accepts broad candidate events, including selection changes and generic clicks.
5. Weak probes wait about 1.2 seconds before the native read.
6. The service creates a transparent 1×1 `TYPE_APPLICATION_OVERLAY`, reads `ClipboardManager`, and removes the view.

`.21` copied only item 6 and still required an exact semantic/framework Copy cue before entering it. MagicOS could suppress that cue, so the overlay could compile and never execute. Calling `.21` Go-equivalent was incorrect.

## Android 15+ OTP constraint

The target is Android 16. Android 15+ redacts detected OTP content from notifications delivered to untrusted NotificationListenerService apps. CompanionDeviceManager-associated apps are exempt from that specific restriction.

`.21` had ordinary notification access but no companion association. Extractor changes cannot recover text already redacted by Android.

The old listener-path self-test also posted from the same package, so it was not a faithful external-notification test.

## `.22-alpha.1` implementation

- implementation/release SHA: `29febc3e7a83575564145d470c4143b5b92e42f4`
- version: `3.2.1-extended.22-alpha.1-standalone`
- versionCode: `320127`
- intended tag: `v3.2.1-extended.22-alpha.1`
- Android CI `29917141620`: success
- Windows CI `29917141540`: success

### Clipboard acquisition

- native `ClipboardAcquisitionService` foreground service;
- Accessibility starts and binds it;
- service returns `START_STICKY`;
- service owns overlay, ClipboardManager read, mutation proof, and durable queue insertion;
- broad selection/click/notification/announcement probes matching the Go event entry;
- exact Copy labels and semantic ACTION_COPY remain strong fast paths, not the sole entry;
- selection records the old clipboard fingerprint immediately;
- delayed read queues only when the SHA-256 fingerprint changes;
- selection without Copy remains inert;
- Accessibility never inserts directly into `ClipboardRelayStore`;
- overlay-disabled mode retains direct ClipboardManager control behavior without claiming reliable background access.

### Notification trust and test

- Android 15+ setup requires a user-confirmed self-managed CompanionDeviceManager association;
- notification access is requested through `CompanionDeviceManager.requestNotificationAccess` after association;
- NotificationListenerService is non-exported;
- external listener self-test uses a separately installed same-signed helper APK;
- helper package: `com.clipcascade.extended.testnotifier`;
- helper Activity is protected by a signature permission;
- helper posts a normal external message notification containing a fake code;
- the main app must receive, extract, queue, transport, ACK, and delete it without direct insertion.

### ACK boundary preserved

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

No ordinary queue TTL was added. Capacity remains 16 with `queue_full`; accepted items are not evicted.

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

## Trial and error retained

- Android `29842404023`: old NativeModules transform anchor failed; no APK.
- Android `29842757548`: localized resource anchor failed; no APK.
- Android `29843090432`, Windows `29843090421`: `.20` implementation green.
- Android `29843413287`, Windows `29843413149`: `.20` release SHA green.
- Android `29888733469`, Windows `29888733458`: `.21` implementation green, but target later failed.
- Android `29889193284`, Windows `29889193301`: `.21` handoff HEAD green, but target later failed.
- non-default-branch diagnostic workflow did not report a run and is not counted.
- Draft PR #2 Android `29915789910`: initial `.22` validation green.
- Draft PR #2 Android `29916941772`: latest staging HEAD green after direct-read control fixup.
- final Android `29917141620`: success.
- final Windows `29917141540`: success.
- no force push, Ready conversion, merge, or auto-merge occurred.

## Next actions

1. Fast-forward the finalized handoff documents without force.
2. Trigger Android and Windows CI on the final documentation HEAD.
3. Close Draft PR #2 without merge.
4. Update PR #1 body and verify Open/Draft/unmerged state.
5. Install both `.22` APKs.
6. Run component/transport, external listener, and background clipboard tests in that order.
7. Do not claim target recovery until those rows pass.

CI is not HONOR/MagicOS proof.
