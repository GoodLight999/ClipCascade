# Clean Rebuild Handoff

Last updated: 2026-07-26 (Asia/Tokyo)

## Canonical repository and branches

- Working repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Upstream source of truth: `Sathvik-Rao/ClipCascade`
- Upstream/default branch: `main`
- Verified upstream baseline SHA: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Mirror branch: `main`
- Active development branch: `clean-rebuild`

`main` was compared directly against the verified upstream SHA and was identical:

- ahead: 0
- behind: 0
- changed files: 0

The old patchwork draft PR was closed. Do not use its branch, files, assumptions, or architecture as a baseline.

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
- established canonical handoff and experiment-log requirements.

No product code has been changed yet.

## Next actions

1. Inventory the upstream project structure and existing build/release workflows.
2. Reproduce upstream Android, Windows, and Linux builds without modifications.
3. Record exact commands, toolchain versions, outputs, and failures.
4. Identify the existing Android clipboard listener, lifecycle boundaries, transport ownership, and reconnect implementation.
5. Write a narrow architecture proposal before modifying product code.

## Definition of a valid handoff

A new thread must be able to continue by reading only:

1. this file;
2. `docs/EXPERIMENT_LOG.md`;
3. the active pull request description and latest comments;
4. the latest commit diff.

If those sources disagree, this file and the experiment log must be corrected before further implementation.