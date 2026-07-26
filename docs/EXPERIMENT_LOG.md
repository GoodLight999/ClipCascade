# Experiment Log

This document is append-only except for correcting factual errors. Every meaningful attempt must record its hypothesis, exact change or command, evidence, result, and next decision.

## Entry template

```markdown
## YYYY-MM-DD — Short title

- Baseline commit:
- Environment/device:
- Hypothesis:
- Change or command:
- Expected result:
- Observed result:
- Evidence/artifacts:
- Decision:
- Follow-up:
```

## 2026-07-26 — Verify clean upstream baseline

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: GitHub repository metadata and commit comparison
- Hypothesis: The current `main` branch may contain the broken patchwork implementation and require a destructive reset.
- Change or command: Compared `GoodLight999/Trial-and-Error-ClipCascade:main` against upstream SHA `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`.
- Expected result: Detect divergence and force-reset `main` if necessary.
- Observed result: The branches were identical: ahead 0, behind 0, changed files 0.
- Evidence/artifacts: GitHub compare result; upstream and fork both resolve the verified latest commit to `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`.
- Decision: Do not perform a meaningless force update. Treat `main` as the pristine upstream mirror.
- Follow-up: Isolate all new work on `clean-rebuild`.

## 2026-07-26 — Archive patchwork development line

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: GitHub pull request state
- Hypothesis: Leaving the old draft PR open would make its 529-commit patchwork branch appear canonical and invite accidental reuse.
- Change or command: Closed draft PR #1 and replaced its description with an archival notice and the verified clean baseline.
- Expected result: The previous implementation remains recoverable in Git history but is no longer presented as active work.
- Observed result: PR #1 is closed and unmerged.
- Evidence/artifacts: PR #1 state and updated description.
- Decision: Do not inspect or reuse the archived branch unless a future experiment names a specific isolated fact to verify.
- Follow-up: Start from a fresh branch created directly from upstream.

## 2026-07-26 — Create clean development branch and handoff discipline

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: GitHub branch and documentation
- Hypothesis: A pristine branch plus mandatory handoff and experiment records will prevent another untraceable patchwork collapse.
- Change or command: Created `clean-rebuild` from the exact upstream SHA and added `docs/CLEAN_REBUILD_HANDOFF.md` and this log.
- Expected result: Every subsequent thread can identify the authoritative baseline, constraints, current state, and next actions without reading obsolete implementation branches.
- Observed result: Branch and documentation created successfully.
- Evidence/artifacts: `clean-rebuild`; commit `01e811f737a5e06a838cca1eb0d704c046da8715` for the initial handoff document.
- Decision: No product-code change may precede baseline build reproduction and architecture inventory.
- Follow-up: Reproduce upstream builds and record exact commands, versions, and artifacts.

## 2026-07-26 — Attempt local pristine checkout

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: Current execution container
- Hypothesis: A direct clone of `clean-rebuild` would allow immediate local Android and desktop baseline builds.
- Change or command: Attempted `git clone --branch clean-rebuild --single-branch https://github.com/GoodLight999/Trial-and-Error-ClipCascade.git`.
- Expected result: Obtain a local checkout at the active branch head.
- Observed result: Clone failed before repository transfer: `Could not resolve host: github.com`.
- Evidence/artifacts: Container command stderr.
- Decision: Do not claim local builds or generated APK/EXE from this session. Continue repository inspection and writes through the authenticated GitHub connector.
- Follow-up: Use GitHub Actions for reproducible builds, or a later environment with repository network access.

## 2026-07-26 — Inventory upstream Android acquisition path

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: Upstream source inspection through GitHub connector
- Hypothesis: Background outbound failure is likely rooted in Android clipboard acquisition rather than server protocol behavior.
- Change or command: Inspected `package.json`, `AndroidManifest.xml`, `ClipboardListenerModule.kt`, `ClipboardFloatingActivity.kt`, `HeadlessTaskService.kt`, and relevant sections of `StartForegroundService.js`.
- Expected result: Identify the exact capture trigger, focus workaround, service ownership, and transport boundary.
- Observed result: The app combines a normal `OnPrimaryClipChangedListener` with a protected `READ_LOGS` logcat process watching `ClipboardService:E`. Matching log lines launch a focus-taking overlay activity, which reads the clipboard and emits to the React Native service. The same large JS module also owns transport and most service behavior.
- Evidence/artifacts: `docs/BASELINE_ARCHITECTURE_INVENTORY.md` and the listed source files.
- Decision: Treat acquisition and delivery as separate subsystems in the clean design. Do not add more triggers directly into `StartForegroundService.js`.
- Follow-up: Define a capability-backed `ClipboardAcquisitionBackend` interface and an independently observable outbound queue.

## 2026-07-26 — Inventory upstream desktop reconnect and UI state

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: Upstream source inspection through GitHub connector
- Hypothesis: Desktop invisibility and unreliable recovery come from split UI/transport state and an underspecified reconnect loop.
- Change or command: Inspected `ClipCascade_Desktop/src/stomp_ws/stomp_manager.py` and `ClipCascade_Desktop/src/gui/tray.py`.
- Expected result: Locate the authoritative connection state and GUI status source.
- Observed result: `get_stats()` returns `None`; the tray initializes itself as connected; the tray polls stats every second but receives none; reconnect sleeps a fixed interval and calls `connect()` from the close callback; several failures are only logged or silently ignored.
- Evidence/artifacts: `docs/BASELINE_ARCHITECTURE_INVENTORY.md` and the listed source files.
- Decision: Implement one explicit connection state machine consumed by both transport and GUI before extending desktop features.
- Follow-up: Specify state transitions, retry policy, timestamps, and error model; then add tests before UI work.

## 2026-07-26 — Review no-root behavioral references

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: Public Octoclip documentation
- Hypothesis: Octoclip's user-visible flows can clarify capability selection and setup UX without copying implementation.
- Change or command: Reviewed the Shizuku and Accessibility background-monitoring guides.
- Expected result: Extract setup, verification, reboot recovery, and known limitation patterns.
- Observed result: Accessibility relies on accessibility events and acknowledges missed copy actions when recognizable events are absent. Shizuku offers guided installation/activation/permission checks, a post-setup test, and distinct normal/enhanced reboot tradeoffs.
- Evidence/artifacts: Links documented in the project request and summarized in `docs/BASELINE_ARCHITECTURE_INVENTORY.md`.
- Decision: Use these only as behavioral and setup references. Do not assume implementation details.
- Follow-up: Design ClipCascade's capability wizard with explicit health checks and graceful fallback.