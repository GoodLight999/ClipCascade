from pathlib import Path

handoff_path = Path("docs/HANDOFF.md")
log_path = Path("docs/EXPERIMENT_LOG.md")
handoff = handoff_path.read_text(encoding="utf-8")


def replace_exact(old: str, new: str, label: str) -> None:
    global handoff
    count = handoff.count(old)
    if count == 1:
        handoff = handoff.replace(old, new, 1)
        return
    if count == 0 and new in handoff:
        return
    raise SystemExit(f"{label}: expected one old block, found {count}")


def replace_section(start: str, end: str, replacement: str, label: str) -> None:
    global handoff
    if replacement in handoff:
        return
    if handoff.count(start) != 1:
        raise SystemExit(f"{label}: invalid start marker count")
    start_index = handoff.index(start)
    end_index = handoff.index(end, start_index)
    handoff = handoff[:start_index] + replacement + handoff[end_index:]


replace_exact(
    """7. `docs/EXPERIMENT_LOG_2026-07-27_OUTBOX_STATUS_UI.md`
8. `docs/EXPERIMENT_LOG_2026-07-27_DESKTOP_RECOVERY.md`
9. Draft PR `#4`""",
    """7. `docs/EXPERIMENT_LOG_2026-07-27_OUTBOX_STATUS_UI.md`
8. `docs/EXPERIMENT_LOG_2026-07-27_DIAGNOSTIC_REPORT.md`
9. `docs/EXPERIMENT_LOG_2026-07-27_DESKTOP_RECOVERY.md`
10. Draft PR `#4`""",
    "read order",
)

replace_exact(
    """- Latest green branch head: `60c71a7d77e2980d2f2e35c8f325c4c22d37c4cf`
- Latest green Android workflow: `30249557589`
- Latest green desktop workflow on the same head: `30249557577`""",
    """- Latest green product-code head: `9277b67d8c0797014e17489a59d3c4aca64e97eb`
- Latest green Android workflow: `30250829837`
- Latest green desktop workflow on the same product head: `30250829830`""",
    "latest green state",
)

diagnostic_section = """## Android milestone DIAGNOSTIC-REPORT-001

Implemented and build-verified using existing real state owners:

- added `診断レポートを共有` to the existing native background setup screen;
- report combines device/build, Shizuku capability, Accessibility/overlay/READ_LOGS/battery/runtime, real capture counters, connection state, and bounded P2S outbox metadata;
- no second React Native runtime, transport, capture backend, timer, or diagnostic state owner;
- uses Android `ACTION_SEND` Sharesheet instead of clipboard copy, preventing the report from synchronizing itself;
- report input types exclude clipboard payloads, content hashes, server URLs, usernames, passwords, cookies, and encryption keys;
- free-form status/error text is one-line, bounded to 300 characters, and redacts HTTP/WebSocket URLs plus email addresses;
- malformed/missing outbox state fails closed to unavailable;
- report-builder JVM tests cover stage visibility, sensitive-data exclusion, URL/email redaction, control-character normalization, bounds, missing state, and negative counters.

This is a real-state passive diagnostic report, not the archived synthetic native-to-React-Native roundtrip. A guided active capture → queue → server echo → remote-apply test is still not implemented.

Verification:

- JavaScript reliability gate remained green: 4 suites / 26 tests;
- new JVM diagnostic tests and existing Android tests passed;
- AIDL/Shizuku, Kotlin/resources, standalone build, JS bundle, ZIP integrity, checksum, and upload passed;
- DEX/resource inspection found the diagnostic builder, report marker, URL redaction marker, Shizuku state label, and share resource.

"""
replace_section(
    "## Latest verified Android artifact",
    "## Android build failures retained as evidence",
    diagnostic_section + "## Latest verified Android artifact\n\n- Workflow: `30250829837`\n- Product-code head: `9277b67d8c0797014e17489a59d3c4aca64e97eb`\n- APK artifact ID: `8647003886`\n- Build-log artifact ID: `8647002070`\n- Artifact file: `ClipCascade-Android-stability-standalone.apk`\n- User-facing file: `ClipCascade-Android-stability-diagnostics.apk`\n- Size: `93,615,651` bytes\n- SHA-256: `2af9f94dff4f8447001378da780591b970d8adfe0698357f42ef6eb826fbd785`\n- Exact `assets/index.android.bundle`: present\n- APK ZIP integrity: passed\n- APK entry count: `538`\n- JavaScript tests: `26/26` passed across 4 suites\n- Gradle result: `BUILD SUCCESSFUL in 3m 55s`\n- Status: debug-signed engineering artifact, not a production release\n\n",
    "diagnostic milestone and Android artifact",
)

replace_section(
    "## Verified desktop artifacts",
    "## Explicitly not yet proven",
    """## Verified desktop artifacts

Latest same-product-head desktop workflow: `30250829830` at `9277b67d8c0797014e17489a59d3c4aca64e97eb`.

The workflow passed Ubuntu and Windows tests, Windows EXE generation, Linux packaging, checksum creation, and artifact upload.

### Windows

- Artifact ID: `8646940303`
- File: `ClipCascade-Windows-stability.exe`
- Size: `57,456,040` bytes
- SHA-256: `45eedcd94b3d6f953639716002e63218d2b2f5d6e304f1c0f9f4b72ca502b39f`
- PE32+ GUI x86-64
- Artifact ZIP integrity: passed
- Embedded checksum matched independent recalculation

### Linux

- Artifact ID: `8646901511`
- File: `ClipCascade-Linux-stability.tar.gz`
- Size: `60,409` bytes
- SHA-256: `7f61bcbfa4073ed7af20391fe2a3b0d3ff9ba65c52a3d578553c5f15efd39908`
- gzip-compressed Unix tar
- Archive integrity: passed
- Entry count: `59`
- Embedded checksum matched independent recalculation

""",
    "same-head desktop artifacts",
)

replace_exact(
    "- full end-to-end automatic diagnostic flow.",
    "- diagnostic Sharesheet behavior and report readability on the user's device;\n- active guided capture → queue → server echo → remote-apply diagnostic flow.",
    "diagnostic unproven state",
)

replace_section(
    "## Exact next actions",
    "## Continuation checklist",
    """## Exact next actions

1. Install `ClipCascade-Android-stability-diagnostics.apk`.
2. Start Shizuku using its normal wireless-debugging or computer-assisted setup.
3. Long-press ClipCascade and open `バックグラウンド設定`.
4. Confirm readable light/dark rendering and Shizuku installation/running/permission/UserService state.
5. Confirm the service UID is a shell UID and run the privacy-safe Shizuku read test.
6. Start the existing ClipCascade foreground service and verify one normal Android-to-Windows text copy.
7. Tap `診断レポートを共有`, share it into the working conversation, and verify payloads, URLs, accounts, credentials, cookies, and keys are absent.
8. Disable Android networking without stopping the foreground service; copy A, B, and C; reconnect; verify ordered A/B/C delivery without duplicates.
9. Confirm Android's clipboard does not roll back to A or B when queued self-echoes return.
10. Repeat with process termination/relaunch between enqueue and reconnect to test persisted recovery.
11. Stop Shizuku and repeat a copy to verify overlay fallback.
12. Test launcher drawer, Amazon, browser, selection toolbar, and search fields for focus/input regressions.
13. Verify the P2S queue line changes from empty to queued/sending and back to empty.
14. Record missed sends, duplicate sends, report stages, queue state, UI changes, wakeups, and battery behavior.
15. Run `ClipCascade-Windows-stability.exe` against the existing server and force network loss/restoration.
16. Replace the fixed 30-second missing-echo retry with a tested bounded exponential delay to reduce outage battery/network load without changing protocol semantics.
17. Add an active guided end-to-end diagnostic only by invoking the real existing capture/queue/echo stages, never a synthetic parallel runtime.

""",
    "next actions",
)

replace_exact(
    "- A11Y, Shizuku, P2S outbox, and desktop implementation boundaries;",
    "- A11Y, Shizuku, P2S outbox, passive diagnostic report, and desktop implementation boundaries;",
    "continuation checklist",
)

handoff_path.write_text(handoff, encoding="utf-8")

log = log_path.read_text(encoding="utf-8")
entry = """

---

## 2026-07-27 — DIAGNOSTIC-REPORT-001: shareable real-state report

Detailed record: `docs/EXPERIMENT_LOG_2026-07-27_DIAGNOSTIC_REPORT.md`.

Result:

- existing setup screen now shares a real-state diagnostic report through Android Sharesheet;
- report combines capability, capture, connection, and P2S outbox metadata without payload fields;
- URLs and email addresses in free-form errors are redacted;
- JVM diagnostic tests, existing Android tests, standalone APK, bundle, ZIP integrity, and artifact upload passed;
- Android workflow `30250829837` and desktop workflow `30250829830` passed on product head `9277b67d8c0797014e17489a59d3c4aca64e97eb`;
- latest APK SHA-256: `2af9f94dff4f8447001378da780591b970d8adfe0698357f42ef6eb826fbd785`.

Sharesheet/runtime readability and an active guided end-to-end test remain unproven.
"""
if "## 2026-07-27 — DIAGNOSTIC-REPORT-001" not in log:
    log_path.write_text(log.rstrip() + entry + "\n", encoding="utf-8")
