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
    """3. `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU.md`
4. `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU_BUILD.md`
5. `docs/EXPERIMENT_LOG_2026-07-27_DESKTOP_RECOVERY.md`
6. Draft PR `#4`""",
    """3. `docs/EXPERIMENT_LOG_2026-07-27_CAPTURE_PIPELINE.md`
4. `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU.md`
5. `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU_BUILD.md`
6. `docs/EXPERIMENT_LOG_2026-07-27_P2S_OUTBOX.md`
7. `docs/EXPERIMENT_LOG_2026-07-27_DESKTOP_RECOVERY.md`
8. Draft PR `#4`""",
    "required read order",
)

replace_exact(
    """- Latest green product-code head: `6dc93e08b10db5f3f138cce6b6d867548f9f89c4`
- Latest green Android workflow: `30211592338`
- Latest green desktop workflow on the same product head: `30211592333`""",
    """- Latest green branch head: `f35ebffba8b99f783b20b0bec2e4bc16a0421f1b`
- Latest green Android workflow: `30248168083`
- Latest green desktop workflow on the same head: `30248168084`""",
    "canonical green state",
)

replace_exact(
    "- no new transport, network client, polling loop, outbound queue, or payload history;",
    "- no new transport, network client, or polling loop was introduced by SHIZUKU-001; the later OUTBOX-001 milestone extends the existing P2S send path separately;",
    "Shizuku/outbox boundary",
)

outbox_section = """## Android milestone OUTBOX-001

Implemented and build-verified for P2S text:

- persistent bounded `P2STextOutbox` using the existing AsyncStorage adapter;
- exact existing `/app/cliptext` publish and `/user/queue/cliptext` subscription destinations retained;
- no second network client and no server change;
- queue scoped by server URL, username, cipher mode, and hashed-password fingerprint;
- text prepared by the existing validation and encryption code before persistence;
- when encryption is enabled, persisted wire payload is ciphertext;
- FIFO, maximum 20 items, bounded bytes, 24-hour default expiry, and serialized storage mutations;
- one in-flight item at a time;
- STOMP connect plus successful subscription starts draining;
- matching plaintext server echo acknowledges and removes only the in-flight head;
- 30-second missing-echo timeout releases the head for retry;
- disconnect, STOMP error, WebSocket error/close, and service shutdown release in-flight text back to queued state;
- restart recovery converts persisted in-flight state back to queued;
- acknowledged own echoes are not written back to the Android clipboard, preventing A/B/C rollback during offline replay;
- queue count, bytes, oldest time, head state, attempts, and last-attempt time are stored as non-payload status;
- image/file sending remains unchanged and is not claimed durable.

Verification:

- JavaScript reliability gate: 3 suites, 19 tests, all passed;
- Android JVM tests, AIDL/Shizuku compilation, Kotlin/resources, standalone build, JS bundle, ZIP integrity, checksum, and upload passed;
- bundle inspection found outbox storage and echo-acknowledgement markers.

Runtime semantics are at-least-once relative to the unchanged server echo, not a new application-level delivery acknowledgement. Real-device disconnect/reconnect ordering still requires acceptance testing.

"""
replace_section(
    "## Latest verified Android artifact",
    "## Android build failures retained as evidence",
    outbox_section + "## Latest verified Android artifact\n\n- Workflow: `30248168083`\n- Branch head: `f35ebffba8b99f783b20b0bec2e4bc16a0421f1b`\n- APK artifact ID: `8645964331`\n- Build-log artifact ID: `8645962581`\n- Artifact file: `ClipCascade-Android-stability-standalone.apk`\n- User-facing file: `ClipCascade-Android-stability-outbox.apk`\n- Size: `93,613,211` bytes\n- SHA-256: `9810be35788fbcad32cf34986f0b19bcb324db1f40a9766024c298aec8e32b2d`\n- Exact `assets/index.android.bundle`: present\n- APK ZIP integrity: passed\n- APK entry count: `538`\n- JavaScript tests: `19/19` passed\n- Status: debug-signed engineering artifact, not a production release\n\n",
    "Android milestone and artifact",
)

replace_section(
    "## Verified desktop artifacts",
    "## Explicitly not yet proven",
    """## Verified desktop artifacts

Latest same-head desktop workflow: `30248168084` at `f35ebffba8b99f783b20b0bec2e4bc16a0421f1b`.

The workflow passed Ubuntu and Windows tests, Windows EXE generation, Linux packaging, checksum creation, and artifact upload.

### Windows

- Artifact ID: `8645900812`
- File: `ClipCascade-Windows-stability.exe`
- Size: `57,456,040` bytes
- SHA-256: `e83390cffca570224ae47d8144313062564e7886eace44dc04a28701c7855128`
- PE32+ GUI x86-64
- Artifact ZIP integrity: passed
- Embedded checksum matched independent recalculation

### Linux

- Artifact ID: `8645865478`
- File: `ClipCascade-Linux-stability.tar.gz`
- Size: `60,415` bytes
- SHA-256: `6bde0b175cf12ef3e26224efd04ee8449720d34f46735f39e1520fd7599cfc57`
- gzip-compressed Unix tar
- Archive integrity: passed
- Entry count: `59`
- Embedded checksum matched independent recalculation

""",
    "desktop artifacts",
)

replace_exact(
    """- duplicate-send behavior;
- battery/wakeup behavior;
- Shizuku-only clipboard-change monitoring;
- durable outbound queue or server-level delivery acknowledgement;
- full end-to-end automatic diagnostic flow.""",
    """- duplicate-send behavior on the real device;
- battery/wakeup behavior;
- Shizuku-only clipboard-change monitoring;
- offline queue survival, ordering, retry, and self-echo suppression on the real device/public server;
- image/file durability;
- true server or remote-application acknowledgement beyond the unchanged server echo;
- full end-to-end automatic diagnostic flow.""",
    "Android unproven list",
)

replace_section(
    "## Exact next actions",
    "## Continuation checklist",
    """## Exact next actions

1. Install `ClipCascade-Android-stability-outbox.apk`.
2. Start Shizuku using its normal wireless-debugging or computer-assisted setup.
3. Long-press ClipCascade and open `バックグラウンド設定`.
4. Confirm readable light/dark rendering and Shizuku installation/running/permission/UserService state.
5. Confirm the service UID is a shell UID and run the privacy-safe Shizuku read test.
6. Start the existing ClipCascade foreground service and verify one normal Android-to-Windows text copy.
7. Disable Android networking without stopping the foreground service; copy A, B, and C; reconnect; verify ordered A/B/C delivery without duplicates.
8. Confirm Android's clipboard does not roll back to A or B when queued self-echoes return.
9. Repeat with process termination/relaunch between enqueue and reconnect to test persisted recovery.
10. Stop Shizuku and repeat a copy to verify overlay fallback.
11. Test launcher drawer, Amazon, browser, selection toolbar, and search fields for focus/input regressions.
12. Record missed sends, duplicate sends, queue state, UI changes, wakeups, and battery behavior.
13. Run `ClipCascade-Windows-stability.exe` against the existing server and force network loss/restoration.
14. Connect `p2sTextOutboxStatus` to the existing mobile status page, then expand diagnostics only around stages proven useful by acceptance evidence.

""",
    "next actions",
)

replace_exact(
    "- A11Y, Shizuku, and desktop implementation boundaries;",
    "- A11Y, Shizuku, P2S outbox, and desktop implementation boundaries;",
    "continuation checklist",
)

handoff_path.write_text(handoff, encoding="utf-8")

log = log_path.read_text(encoding="utf-8")
summary = """

---

## 2026-07-27 — OUTBOX-001: persistent P2S text outbox

Detailed record: `docs/EXPERIMENT_LOG_2026-07-27_P2S_OUTBOX.md`.

Result:

- existing server protocol and STOMP destinations retained;
- offline P2S text is now persisted, bounded, replayed after subscription, and removed after matching server echo;
- connection loss and shutdown release in-flight text for retry;
- own queued echoes do not roll the Android clipboard backward;
- 3 JavaScript suites / 19 tests passed;
- Android workflow `30248168083` and desktop workflow `30248168084` passed on head `f35ebffba8b99f783b20b0bec2e4bc16a0421f1b`;
- latest APK SHA-256: `9810be35788fbcad32cf34986f0b19bcb324db1f40a9766024c298aec8e32b2d`.

Runtime/device acceptance remains required. Image/file durability is not implemented.
"""
if "## 2026-07-27 — OUTBOX-001: persistent P2S text outbox" not in log:
    log_path.write_text(log.rstrip() + summary + "\n", encoding="utf-8")
