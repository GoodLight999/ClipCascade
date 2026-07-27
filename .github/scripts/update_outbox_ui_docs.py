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
    """6. `docs/EXPERIMENT_LOG_2026-07-27_P2S_OUTBOX.md`
7. `docs/EXPERIMENT_LOG_2026-07-27_DESKTOP_RECOVERY.md`
8. Draft PR `#4`""",
    """6. `docs/EXPERIMENT_LOG_2026-07-27_P2S_OUTBOX.md`
7. `docs/EXPERIMENT_LOG_2026-07-27_OUTBOX_STATUS_UI.md`
8. `docs/EXPERIMENT_LOG_2026-07-27_DESKTOP_RECOVERY.md`
9. Draft PR `#4`""",
    "read order",
)

replace_exact(
    """- Latest green branch head: `f35ebffba8b99f783b20b0bec2e4bc16a0421f1b`
- Latest green Android workflow: `30248168083`
- Latest green desktop workflow on the same head: `30248168084`""",
    """- Latest green branch head: `60c71a7d77e2980d2f2e35c8f325c4c22d37c4cf`
- Latest green Android workflow: `30249557589`
- Latest green desktop workflow on the same head: `30249557577`""",
    "latest green state",
)

ui_section = """## Android milestone OUTBOX-UI-001

Implemented and build-verified:

- reused the existing `App.js` 300 ms status poller; no additional loop or service;
- added `p2sTextOutboxStatus` to the existing polled key set;
- P2S connection page displays queue count, queued/sending state, total wire bytes, head attempts, and dropped count;
- P2P and unknown modes clear the P2S queue display;
- service toggles clear stale display state;
- formatter excludes payloads, hashes, storage scope, server/account identifiers, and password material;
- native synchronous polling returns AsyncStorage objects as JSON strings, so the formatter safely accepts either serialized JSON or a direct object;
- malformed, non-object, or unloaded values fail closed to an empty display.

A desk review caught the serialized-native-value issue before device testing. The first UI implementation would have built successfully but displayed nothing. This correction is covered by tests.

Verification:

- JavaScript reliability gate: 4 suites, 26 tests, all passed;
- Android JVM tests, AIDL/Shizuku compilation, Kotlin/resources, standalone build, JS bundle, ZIP integrity, checksum, and upload passed;
- independent bundle inspection found the outbox status key, formatter, persistent outbox, and recovery-repository markers.

"""
replace_section(
    "## Latest verified Android artifact",
    "## Android build failures retained as evidence",
    ui_section + "## Latest verified Android artifact\n\n- Workflow: `30249557589`\n- Branch head: `60c71a7d77e2980d2f2e35c8f325c4c22d37c4cf`\n- APK artifact ID: `8646500843`\n- Build-log artifact ID: `8646499050`\n- Artifact file: `ClipCascade-Android-stability-standalone.apk`\n- User-facing file: `ClipCascade-Android-stability-outbox-status.apk`\n- Size: `93,614,299` bytes\n- SHA-256: `614f6fd7d2bcecc96ceba331601ae9d84f6c475047d4301fa5f099286ad0893b`\n- Exact `assets/index.android.bundle`: present\n- APK ZIP integrity: passed\n- APK entry count: `538`\n- JavaScript tests: `26/26` passed across 4 suites\n- Status: debug-signed engineering artifact, not a production release\n\n",
    "UI milestone and Android artifact",
)

replace_section(
    "## Verified desktop artifacts",
    "## Explicitly not yet proven",
    """## Verified desktop artifacts

Latest same-head desktop workflow: `30249557577` at `60c71a7d77e2980d2f2e35c8f325c4c22d37c4cf`.

The workflow passed Ubuntu and Windows tests, Windows EXE generation, Linux packaging, checksum creation, and artifact upload.

### Windows

- Artifact ID: `8646440413`
- File: `ClipCascade-Windows-stability.exe`
- Size: `57,456,040` bytes
- SHA-256: `199f5ab18413255bbee4c2efac2b5694a0a69c68a6d1273292dfe16eff35926e`
- PE32+ GUI x86-64
- Artifact ZIP integrity: passed
- Embedded checksum matched independent recalculation

### Linux

- Artifact ID: `8646405080`
- File: `ClipCascade-Linux-stability.tar.gz`
- Size: `60,413` bytes
- SHA-256: `12e6cc5f2b9937d08801ea8a9a2d4e93682a74bdd8d6b87543f73f7f14900c5a`
- gzip-compressed Unix tar
- Archive integrity: passed
- Entry count: `59`
- Embedded checksum matched independent recalculation

""",
    "same-head desktop artifacts",
)

replace_exact(
    "- offline queue survival, ordering, retry, and self-echo suppression on the real device/public server;",
    "- queue-status rendering and live count/state changes on the user's device;\n- offline queue survival, ordering, retry, and self-echo suppression on the real device/public server;",
    "UI runtime proof",
)

replace_exact(
    "14. Connect `p2sTextOutboxStatus` to the existing mobile status page, then expand diagnostics only around stages proven useful by acceptance evidence.",
    "14. Verify the P2S queue status line changes from empty to queued/sending and back to empty during the A/B/C test.\n15. Add a payload-free diagnostic report export that combines capability state, capture counters, connection status, and outbox metadata without creating another diagnostics runtime.",
    "next actions",
)

handoff_path.write_text(handoff, encoding="utf-8")

log = log_path.read_text(encoding="utf-8")
entry = """

---

## 2026-07-27 — OUTBOX-UI-001: queue status on the existing connection page

Detailed record: `docs/EXPERIMENT_LOG_2026-07-27_OUTBOX_STATUS_UI.md`.

Result:

- existing 300 ms UI poller reused;
- payload-free count/state/bytes/attempt/drop display added for P2S;
- native bridge JSON-string shape identified and handled safely;
- 4 JavaScript suites / 26 tests passed;
- Android workflow `30249557589` and desktop workflow `30249557577` passed on head `60c71a7d77e2980d2f2e35c8f325c4c22d37c4cf`;
- latest APK SHA-256: `614f6fd7d2bcecc96ceba331601ae9d84f6c475047d4301fa5f099286ad0893b`.

Real-device rendering and live state changes remain unproven.
"""
if "## 2026-07-27 — OUTBOX-UI-001" not in log:
    log_path.write_text(log.rstrip() + entry + "\n", encoding="utf-8")
