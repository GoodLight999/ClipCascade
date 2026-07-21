# Next ChatGPT Handoff

This is the canonical handoff. Read the listed documents before changing code.

## Repository and hard constraints

- repository: `GoodLight999/ClipCascade`
- branch: `stability-mobile-otp`
- PR: `#1`
- PR state: open and Draft
- never mark Ready, merge, or enable auto-merge
- preserve Extended P2P Windows-applied ACK before native deletion
- run Android and Windows CI for every `stability-mobile-otp` branch change
- record hypotheses, failures, artifacts, target evidence, and unproven claims

## Read in this exact order

1. `docs/progress.md`
2. `docs/REQUIREMENTS.md`
3. `docs/CURRENT_STATUS.md`
4. `docs/NEXT_CHATGPT_HANDOFF.md`
5. `docs/LATEST_FOREGROUND_QUEUE_DRAIN_HANDOFF.md`
6. `docs/LATEST_GREEN_ARTIFACTS.md`
7. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
8. `docs/LATEST_NOTIFICATION_LISTENER_ALPHA_HANDOFF.md`
9. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`
10. `docs/TEST_MATRIX.md`
11. `docs/LATEST_ACK_SAFE_QUEUE_OVERFLOW_HANDOFF.md`
12. `docs/LATEST_ACK_SAFE_CLIPBOARD_QUEUE_HANDOFF.md`
13. `docs/LATEST_LANGUAGE_NEUTRAL_COPY_HANDOFF.md`
14. `docs/LATEST_SELECTION_ONLY_COPY_FALSE_POSITIVE_HANDOFF.md`
15. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
16. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
17. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
18. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
19. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
20. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
21. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
22. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`

## Current implementation candidate

- implementation SHA: `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`
- implementation head message: `[alpha-release] Stage foreground transport queue drain alpha.2`
- intended tag: `v3.2.1-extended.19-alpha.2`
- versionName: `3.2.1-extended.19-alpha.2-standalone`
- versionCode: `320124`
- package: `com.clipcascade.extended`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

Implementation CI:

- Android `29837847117` — success
- Windows `29837846863` — success

Artifact:

- Actions artifact ID: `8498121042`
- Actions artifact ZIP SHA-256: `8c20eadce450c57fadb9eab39e465332fe34f8f25f7e28d44a072162dc480db3`
- extracted APK SHA-256: `f9f7b5fe6653beb8d0b08436657ddf719fd155e3ac9b1216b0307b7ea1cf63a7`
- APK size: `147933819` bytes
- Actions artifact expiry: `2026-10-19T14:10:20Z`

The connector did not independently enumerate the alpha-release workflow or public release asset list. Use the verified Actions artifact above if release-asset recovery is uncertain.

## Latest target-device truth

The latest report supersedes the earlier broad statement that ordinary synchronization had recovered:

- inbound Android synchronization works when the app UI is not open;
- Android outbound works while the app UI is open;
- Android outbound fails when the app UI is not open;
- ordinary Copy made while the UI is closed does not reach Windows;
- a real Gmail DAWN notification containing `Your code is 713642` was not relayed;
- the user reports previous ordinary success was intermittent and may have been misread.

Do not claim background outbound or real Gmail is fixed until alpha.2 passes isolated target-device tests.

Earlier Android-to-Windows success contaminated by Microsoft Phone Link remains invalid. Disable Phone Link and every competing clipboard synchronizer for every outbound test.

## Reference repository finding

The user found `wuxinkami/ClipCascade_go_fork`.

Its Android design keeps the Go synchronization engine inside a native `START_STICKY` foreground service. Accessibility binds to that service and requests clipboard work directly. It also uses a transparent overlay for clipboard eligibility.

Extended adopts only the transport-ownership principle. It does not add the fork's overlay or `SYSTEM_ALERT_WINDOW` permission.

## Implemented root-cause hypothesis

Before alpha.2, native clipboard and OTP dispatchers queued data but attempted delivery by emitting `SHARED_TEXT` through `MainApplication.currentReactContext`.

The Notifee foreground service can remain alive in a Headless React runtime and receive inbound traffic even while that UI/MainApplication React context is absent. This is consistent with:

`INBOUND WORKS + UI-OPEN OUTBOUND WORKS + UI-CLOSED OUTBOUND FAILS`

Alpha.2 lets the live foreground-service transport pull pending native queue items instead of waiting for a UI React event.

This is a strong hypothesis, not target-device proof.

## Alpha.2 implementation

### Foreground native claims

- `ClipboardRelayDispatcher.claimForForegroundService()` claims the oldest ordinary item.
- `OtpRelayDispatcher.claimForForegroundService()` claims the oldest verification item.
- both reuse existing native `inFlightId` / `inFlightSince` state;
- the existing 15-second ACK timeout releases a stuck claim for bounded retry;
- verification items are offered before ordinary text because they expire quickly.

### Foreground transport drain

`RelaySettingsModule.claimPendingForegroundRelay()` exposes one pending native item to the live Notifee foreground-service runtime.

The existing 3-second service poll invokes `drainNativeRelayQueue()` and reuses final `sendClipBoard(...)` plus existing ACK helpers:

- P2S local publish accepted -> native ACK/delete;
- Extended P2P send -> Windows validation/application -> peer ACK -> native ACK/delete;
- old/non-Extended P2P -> retained five-second compatibility fallback;
- failed send -> no delete; native in-flight timeout permits retry;
- outbound debug notification remains after local transport acceptance only.

No Windows implementation file changed.

### Copy detection retained from `.19-alpha.1`

- direct OS clipboard mutation remains the strongest Copy proof;
- Android framework-localized Copy/Copy URL click is a conservative fallback;
- exact framework label matching only;
- selection events only remember text;
- selection alone never reaches the fallback;
- no hard-coded Japanese/English dictionary;
- no `READ_LOGS`, overlay, ADB, root, or Shizuku.

## DAWN interpretation

The exact reported body shape is covered by `extractsDawnStandaloneNumericLoginCode`, and Android unit tests pass. The supplied text is therefore extractor-compatible.

A real DAWN notification can still fail because Gmail did not expose the same content to `NotificationListenerService`, or because the listener/queue/background transport failed. Use stage counters before changing extraction rules.

## Final Android transform order

`prepare_relay_claim.js` applies:

1. `prepare_internal_clipboard_guard.js`
2. `prepare_language_neutral_clipboard_copy.js`
3. `prepare_ack_safe_queue_overflow.js`
4. `prepare_gmail_ja_anchor_compat.js`
5. `prepare_gmail_notification_reliability.js`
6. `prepare_debug_notification_icon_compat.js`
7. `prepare_notification_listener_alpha_hardening.js`
8. `prepare_system_localized_copy_recovery.js`
9. `prepare_foreground_queue_drain.js`

Do not move the final transforms earlier without proving every source anchor and regenerated invariant.

## Copy/queue/ACK invariants — preserve

- selection alone never sends;
- OS clipboard mutation is primary Copy proof;
- framework-localized click is explicit-Copy fallback only;
- internal writes are suppressed;
- ACTION_COPY/Ctrl+C fallback remains bounded and serial-cancelled;
- no ordinary clipboard TTL;
- no overflow eviction of accepted items;
- capacity 16 with explicit `queue_full` rejection;
- retry cap 15 seconds;
- relay ID and native in-flight claim remain;
- Extended peer application precedes ACK;
- peer ACK precedes native deletion;
- validation-before-ACK remains;
- old-peer compatibility fallback remains generation-scoped and five seconds.

The transport debug notification proves local transport acceptance only. It is not peer application or ACK.

## Mandatory next target-device sequence

1. Install alpha.2 over the current build without uninstalling.
2. Confirm settings, notification access, Accessibility, and sync configuration survive.
3. Disable Phone Link and every competing clipboard synchronizer.
4. Confirm inbound still works while the Android UI is closed.
5. Enable outbound debug notification temporarily.
6. Open ClipCascade once and confirm connection, then leave the UI.
7. Copy one unique ordinary value.
8. Record Copy detection health and clipboard/delivery health.
9. Record whether `foreground_poll / claimed` appears.
10. Record whether the debug notification appears.
11. Record whether Windows applies the value exactly once.
12. Record whether the native queue deletes only after peer ACK.
13. Repeat after removing the app from recents.
14. Repeat locked/screen-off where Android permits.
15. Run deterministic component/transport test.
16. Run true listener-path self-test and record the first failed stage.
17. Test a real Gmail/DAWN notification when one naturally arrives.

## Failure-boundary interpretation

- no Copy detection row -> Accessibility/copy-cue delivery failure;
- Copy detection requested, no queue/capture result -> selected text/clipboard capture failure;
- queue accepted, no `foreground_poll claimed` -> foreground service/module poll failure;
- foreground claim, no debug notification -> transport acceptance failure;
- debug notification, no Windows application -> P2P transport/application failure;
- Windows application, queue remains -> peer ACK/native deletion failure;
- Gmail listener connected, seen zero -> Gmail callback/delivery failure;
- seen positive, text chars zero -> Gmail exposed no usable extras;
- text positive, auth hint zero -> preview omitted authentication context;
- auth hint positive, no-match positive -> extractor boundary;
- queued positive, no foreground claim/debug -> background transport boundary.

## Trial and error retained

- direct local `git clone` failed because the execution environment could not resolve GitHub; connector-backed staging was used instead;
- an initial alpha.2 staging tree based on `a984ace...` was correctly rejected as non-fast-forward after concurrent `.19-alpha.1` work advanced the branch;
- no force push was used;
- the concurrent system-localized Copy recovery was preserved and independently verified green before foreground drain was layered on top;
- final implementation Android and Windows CI are green;
- temporary `chatgpt-staging-*` branches remain because the available connector could create/update but not delete refs; they are not release or validation branches.

## Do not claim

CI does not prove:

- HONOR system-localized Copy callback behavior;
- background native queue insertion;
- foreground queue drain on target;
- real Gmail visibility/extraction;
- OEM listener survival;
- removed-from-recents, locked, or screen-off outbound;
- exactly-once target behavior;
- battery efficiency;
- Windows tray ghost prevention on the real machine.

Keep PR #1 open and Draft.
