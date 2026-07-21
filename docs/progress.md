# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/LATEST_FOREGROUND_QUEUE_DRAIN_HANDOFF.md`
5. `docs/LATEST_GREEN_ARTIFACTS.md`
6. `docs/TEST_MATRIX_BACKGROUND_OUTBOUND.md`
7. `docs/TEST_MATRIX.md`
8. `docs/LATEST_NOTIFICATION_LISTENER_ALPHA_HANDOFF.md`
9. `docs/LATEST_GMAIL_NOTIFICATION_RELIABILITY_HANDOFF.md`
10. `docs/LATEST_ACK_SAFE_QUEUE_OVERFLOW_HANDOFF.md`
11. `docs/LATEST_ACK_SAFE_CLIPBOARD_QUEUE_HANDOFF.md`
12. `docs/LATEST_LANGUAGE_NEUTRAL_COPY_HANDOFF.md`
13. `docs/LATEST_SELECTION_ONLY_COPY_FALSE_POSITIVE_HANDOFF.md`
14. `docs/LATEST_BACKGROUND_CLIPBOARD_INTERMITTENT_HANDOFF.md`
15. `docs/LATEST_OTP_SELF_TEST_HANDOFF.md`
16. `docs/LATEST_BROAD_OTP_EXTRACTION_HANDOFF.md`
17. `docs/LATEST_OTP_EMAIL_EXTRACTION_HANDOFF.md`
18. `docs/LATEST_ANDROID_IDLE_POWER_HANDOFF.md`
19. `docs/LATEST_RUNTIME_CONTROL_STATE_HANDOFF.md`
20. `docs/LATEST_ANDROID_INIT_DUPLICATE_HANDOFF.md`
21. `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md`
22. `docs/LATEST_WINDOWS_TRAY_GHOST_HANDOFF.md`

Current phase: validate `3.2.1-extended.19-alpha.2 / 320124` on HONOR while preserving Extended P2P peer-applied ACK and PR #1 Draft state.

## 2026-07-21 — latest device report corrected the earlier recovery claim

The user's latest observation is:

- inbound synchronization works while the Android UI is not open;
- outbound works while the Android UI is open;
- outbound fails while the Android UI is not open;
- a background ordinary Copy does not reach Windows;
- a genuine Gmail DAWN notification containing a six-digit login code did not relay;
- previous apparent ordinary success was intermittent and may have been misinterpreted.

The prior statement “ordinary synchronization recovered on `.17`” is no longer a stable target-device conclusion. Treat background outbound and real Gmail as non-functional on the last tested build.

## 2026-07-21 — reference ClipCascade Go fork audit

The user found `wuxinkami/ClipCascade_go_fork`.

Its Android architecture keeps the Go synchronization engine inside a sticky native foreground service. Accessibility binds directly and asks that service to perform clipboard work. It also uses a transparent overlay for clipboard eligibility.

The useful architectural principle is transport ownership: the runtime that owns the live background connection performs outbound work without depending on an activity/UI runtime.

Extended intentionally did not copy the overlay or add `SYSTEM_ALERT_WINDOW`, `READ_LOGS`, root, ADB, or Shizuku.

## 2026-07-21 — concurrent `.19-alpha.1` Copy recovery

During the foreground-drain work, the branch advanced concurrently with a system-localized Copy recovery:

- implementation SHA: `dfff235dc293d75e28ca56787d1926a9cac33192`;
- version: `3.2.1-extended.19-alpha.1 / 320123`;
- Android CI: `29837095676`, success;
- Windows CI: `29837095565`, success.

It adds exact Android framework-localized Copy/Copy URL click detection as a conservative Accessibility fallback while preserving OS clipboard mutation as the strongest signal. Selection alone still cannot send.

The foreground-drain change was rebased on top. No force push or overwrite was used.

## 2026-07-21 — `.19-alpha.2` foreground transport queue drain

Root-cause hypothesis:

- native clipboard and OTP paths could queue successfully;
- delivery depended on emitting `SHARED_TEXT` through `MainApplication.currentReactContext`;
- the Notifee Headless React foreground runtime could remain alive for inbound while that UI/native event target was absent;
- this explains inbound success with UI-closed outbound failure.

Implemented:

- `ClipboardRelayDispatcher.claimForForegroundService()`;
- `OtpRelayDispatcher.claimForForegroundService()`;
- `RelaySettingsModule.claimPendingForegroundRelay()`;
- `drainNativeRelayQueue()` in the existing 3-second foreground-service poll;
- OTP-before-ordinary claim ordering;
- reuse of final `sendClipBoard`, P2P relay ID, peer ACK, native ACK deletion, P2S acceptance, and old-peer fallback;
- send failures retain the item and reuse the existing 15-second in-flight timeout;
- no Windows implementation change.

Implementation:

- SHA: `ed9c009af0fcfc238cfc6264dd4c7b85a8fe82a3`;
- versionName: `3.2.1-extended.19-alpha.2-standalone`;
- versionCode: `320124`;
- intended tag: `v3.2.1-extended.19-alpha.2`;
- Android CI: `29837847117`, success;
- Windows CI: `29837846863`, success;
- Actions artifact ID: `8498121042`;
- Actions ZIP SHA-256: `8c20eadce450c57fadb9eab39e465332fe34f8f25f7e28d44a072162dc480db3`;
- APK SHA-256: `f9f7b5fe6653beb8d0b08436657ddf719fd155e3ac9b1216b0307b7ea1cf63a7`;
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`;
- artifact expiry: `2026-10-19T14:10:20Z`.

Android CI proved final transform order, Copy recovery coexistence, foreground queue-drain source assertions, DAWN/Gmail/Beeper/Perceptron extractor tests, Kotlin/unit tests, bundle, APK, and signer.

Windows CI proved retained authenticated HTTP, peer-applied ACK, validation-before-ACK, shutdown/tray, and packaging tests.

These are build facts only, not HONOR proof.

## DAWN conclusion

The exact reported DAWN body shape is already covered by `extractsDawnStandaloneNumericLoginCode`, and unit tests pass. Therefore the text itself is compatible with the extractor.

The real failure may still occur at listener binding, callback, Gmail extras exposure, authentication-context visibility, queueing, foreground claim, transport, Windows application, or ACK. Do not loosen extraction until the first failed content-free stage is identified.

## Trial and error retained

- direct `git clone` failed because the execution environment could not resolve GitHub; connector-backed staging was used;
- the first staging attempt was based on an obsolete HEAD and correctly failed non-fast-forward;
- no force push was used;
- concurrent Copy recovery was audited, verified green, and preserved;
- multiple connector staging branches remain because the available connector had no delete-ref action;
- the implementation and documentation were separated so the implementation artifact could be tied to a green source SHA.

## Preserved invariants

- selection alone never sends;
- OS clipboard mutation remains primary Copy proof;
- framework-localized Copy click is explicit fallback only;
- internal writes remain suppressed;
- accepted ordinary clipboard items have no wall-clock expiry;
- accepted items are never evicted before ACK;
- queue capacity remains 16 with explicit `queue_full` rejection;
- Extended P2P deletion still requires Windows application followed by peer ACK;
- validation-before-ACK, relay IDs, native claims, and five-second old-peer fallback remain;
- transport debug notification defaults OFF and cannot alter ACK or queue state.

## Mandatory next proof

1. Install alpha.2 in place without uninstalling.
2. Confirm settings and permissions survive.
3. Disable Phone Link and all competitors.
4. Confirm inbound while UI is closed.
5. Enable outbound debug notification.
6. Open once, confirm connection, leave the UI, and copy one unique value.
7. Record Copy detection, capture/queue, `foreground_poll claimed`, debug notification, Windows application, peer ACK, and deletion.
8. Repeat removed from recents and locked/screen-off.
9. Run deterministic component/transport and true listener-path tests.
10. Test genuine Gmail/DAWN only when available and record the first failed stage without storing the code.

No background outbound or real Gmail success is claimed.
