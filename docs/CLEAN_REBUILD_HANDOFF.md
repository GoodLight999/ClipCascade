# Clean Rebuild Handoff

Last updated: 2026-07-26 (Asia/Tokyo)

## Canonical repository and branches

- Working repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Upstream source of truth: `Sathvik-Rao/ClipCascade`
- Upstream/default branch: `main`
- Verified upstream baseline SHA: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Mirror branch: `main`
- Active development branch: `clean-rebuild`
- Active draft PR: `#3`

`main` was compared directly against the verified upstream SHA and was identical:

- ahead: 0
- behind: 0
- changed files: 0

The old patchwork draft PR was closed. Do not use its branch, files, assumptions, or architecture as a baseline.

## Read-first documents

1. `docs/CLEAN_REBUILD_HANDOFF.md`
2. `docs/EXPERIMENT_LOG.md`
3. `docs/BASELINE_ARCHITECTURE_INVENTORY.md`
4. Draft PR #3 description and latest comments
5. Latest commit diff

## Non-negotiable operating rules

1. Keep `main` as an upstream mirror. Product changes belong on focused branches and enter through reviewed pull requests.
2. Update this handoff in every meaningful work session.
3. Append every experiment, failure, build result, and device observation to `docs/EXPERIMENT_LOG.md`.
4. Do not stack speculative fixes. Every behavior change must have:
   - a stated hypothesis;
   - a bounded implementation;
   - a reproducible verification method;
   - an explicit outcome.
5. Preserve the upstream server protocol and compatibility with the public ClipCascade server.
6. Do not require root access.
7. Produce installable artifacts as release gates:
   - Android APK;
   - Windows executable/package;
   - Linux executable/package.
8. Never report a feature as working merely because compilation or CI passed. Background Android outbound sync requires a real-device acceptance test.
9. Do not inspect or copy the archived patchwork implementation unless a narrowly scoped experiment explicitly justifies one isolated comparison.

## Product requirements

### Android outbound reliability

Make Android-to-server clipboard transmission reliable while the application is backgrounded. Permitted mechanisms include:

- ordinary Android APIs;
- foreground services;
- AccessibilityService;
- Shizuku-mediated privileged operations;
- ADB-assisted setup or operation;
- combinations of the above.

Root-only designs are prohibited.

The implementation should prefer the least intrusive available mechanism, automatically detect capabilities, expose clear health state, and fall back deliberately rather than silently.

### Power consumption

Reduce unnecessary polling, wakeups, reconnect loops, and duplicated clipboard processing without sacrificing reliability. Power work is subordinate to correct delivery and must be measured rather than guessed.

### Setup experience

Provide a guided setup that a non-technical user can complete. Shizuku integration, when present, must include capability detection, step-by-step instructions, deep links where possible, and post-setup verification.

### Automatic diagnostics

Provide a one-action diagnostic mode that:

- verifies authentication and server reachability;
- verifies WebSocket/session state;
- verifies Android clipboard acquisition paths;
- verifies background execution capability;
- verifies outbound queueing and acknowledgement;
- records reconnect behavior;
- exports a sanitized support bundle.

### Desktop reliability and visibility

Windows and Linux clients must provide a persistent status UI after login, including:

- connected/disconnected/reconnecting state;
- last successful send and receive;
- last error with actionable explanation;
- manual reconnect;
- automatic reconnect with bounded exponential backoff and jitter;
- diagnostics export;
- tray/status integration where supported.

## Architecture guardrails

- One authoritative connection state machine per client.
- One authoritative outbound queue per client.
- Idempotent message handling and duplicate suppression.
- Explicit capability model for Android acquisition methods.
- No hidden infinite retry loops.
- No feature-specific reconnect implementations.
- No UI-only state that disagrees with transport state.
- Structured logs with stable event names.
- Sensitive clipboard contents must not appear in normal logs or exported diagnostics.

## Verified upstream architecture findings

Detailed evidence is recorded in `docs/BASELINE_ARCHITECTURE_INVENTORY.md`.

### Android

- React Native `0.80.2` with Kotlin native modules.
- The large `StartForegroundService.js` module currently owns transport, encryption, clipboard event listeners, duplicate suppression, foreground-service behavior, and media/file handling.
- `ClipboardListenerModule.kt` uses both a normal clipboard listener and a protected `READ_LOGS` logcat reader.
- The logcat path watches `ClipboardService:E`; when it sees a matching denial/event line, it launches `ClipboardFloatingActivity`.
- `ClipboardFloatingActivity` creates an overlay, temporarily takes focus, reads the clipboard, emits an event to React Native, and closes.
- This path is timing-sensitive and does not separate capture health from transport health.

### Desktop

- Python, Tkinter/pystray, STOMP/WebSocket, and PyInstaller.
- `STOMPManager.get_stats()` returns `None`.
- reconnect uses fixed-delay sleep and recursive `connect()` from the close callback.
- tray state initializes as connected independently of transport truth.
- the tray polls stats every second, but the STOMP implementation supplies none.

### External behavioral references

- Octoclip documents no-root Accessibility and Shizuku setup flows.
- Accessibility is acknowledged to miss copy operations that produce no recognizable accessibility event.
- Shizuku modes demonstrate capability detection, guided authorization, verification, and reboot-recovery UX patterns.
- No Octoclip source code is used.

## Planned delivery sequence

1. Baseline inventory and reproducible upstream builds.
2. Protocol and transport characterization without server changes.
3. Desktop connection-state refactor and visible status UI.
4. Android acquisition capability matrix and instrumentation.
5. Android foreground/background delivery state machine.
6. Accessibility path prototype and real-device evaluation.
7. Shizuku path prototype and guided setup.
8. Adaptive fallback policy and power measurements.
9. One-action diagnostic mode and support bundle.
10. Release workflows producing APK, Windows, and Linux artifacts.

## Current state

Completed in this session:

- verified that `main` exactly matches upstream SHA `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`;
- closed the previous patchwork draft PR as archived;
- created `clean-rebuild` directly from the verified upstream SHA;
- created draft PR #3;
- established canonical handoff and append-only experiment records;
- inventoried the mobile and desktop architecture and identified the primary Android capture and desktop reconnect failure surfaces;
- recorded the clean architecture direction without changing product code.

No product code has been changed yet.

The current execution environment could not clone GitHub directly because outbound DNS resolution to `github.com` is blocked. GitHub connector reads and writes remain functional. Therefore no local APK/EXE build has yet been claimed or fabricated.

## Next actions

1. Inventory `.github/workflows` and determine whether existing GitHub Actions can produce unsigned Android, Windows, and Linux artifacts from PR #3.
2. Add a baseline build workflow on `clean-rebuild` only if upstream automation cannot be reused safely.
3. Run pristine lint/tests/builds and record exact toolchain versions, commands, logs, artifact names, and hashes.
4. Characterize the STOMP delivery semantics before defining durable queue deletion rules.
5. Write the interface-level Android acquisition and connection-state proposal.
6. Implement the desktop state machine first because it is lower risk and provides reusable diagnostics patterns.
7. Begin Android backend work only after baseline APK production is reproducible.

## Definition of a valid handoff

A new thread must be able to continue by reading only the read-first documents listed above.

If those sources disagree, this file and the experiment log must be corrected before further implementation.