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

## 2026-07-26 — Add reproducible baseline artifact workflow

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: GitHub Actions hosted runners
- Hypothesis: The upstream project can be preserved while CI supplies a repeatable Android, Windows, and Linux artifact gate.
- Change or command: Added `.github/workflows/baseline-artifacts.yml` and `docs/BUILD_REPRODUCTION.md`. The workflow uses Node 20 and Temurin 17 for Android, Python 3.11 plus PyInstaller 6.11.1 for Windows, and Python 3.11 compile checks plus source packaging for Linux.
- Expected result: Produce a debug APK, standalone Windows executable, Linux source package, and SHA-256 files from one known commit.
- Observed result: Linux and Windows succeeded on the first complete run. Android failed during Gradle configuration before compilation.
- Evidence/artifacts: Workflow run `30192122471`; commits `ee4e0f5ffd7c30c16b2b950394ce53ff4288683e` and `7f8f6de02f149ae62ebb3559afc7290a473d79b0`.
- Decision: Preserve the successful jobs and investigate Android without changing application behavior.
- Follow-up: Capture the complete Android Gradle output as a workflow artifact.

## 2026-07-26 — Preserve and diagnose Android baseline build failure

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: GitHub Actions `ubuntu-latest`, Node 20, Temurin 17, Gradle wrapper 8.14.1
- Hypothesis: The Android failure is a missing or inconsistent upstream build setting rather than a runtime-code failure.
- Change or command: Changed the workflow to pipe complete Gradle output into `ci-logs/android/gradle-build.log` and upload it with `if: always()`.
- Expected result: Obtain an inspectable failure artifact even when APK staging is skipped.
- Observed result: Run `30192282595` reproduced the failure and uploaded log artifact `8628992649`. The exact cause was `Could not get unknown property 'hermesEnabled'` from `android/app/build.gradle`; the upstream tree did not contain `android/gradle.properties`.
- Evidence/artifacts: Workflow commit `e2239984ad2e182854d1cc7df2e338ac41e8e853`; Android build-log artifact `8628992649`.
- Decision: Restore only the standard React Native 0.80 Gradle properties required by the existing build script.
- Follow-up: Compare the matching React Native template and rerun all three build jobs.

## 2026-07-26 — Restore React Native 0.80 Android Gradle properties

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: Source comparison against the React Native 0.80 template and GitHub Actions
- Hypothesis: Adding the omitted standard `gradle.properties` values will make the existing upstream Android configuration buildable without altering product behavior.
- Change or command: Added `ClipCascade_Mobile/src/android/gradle.properties` with AndroidX, supported architectures, Gradle JVM memory, `newArchEnabled=true`, and `hermesEnabled=true`.
- Expected result: Pass Gradle configuration and complete `assembleDebug`.
- Observed result: Workflow run `30192411087` completed Android, Windows, and Linux jobs successfully.
- Evidence/artifacts: Commit `e725f479c5075baa6d32ed465322d6c0ee06979f`; `docs/BUILD_REPRODUCTION.md`.
- Decision: Keep this as an explicit, documented upstream build-configuration repair. Do not describe it as a runtime reliability fix.
- Follow-up: Download and independently verify every generated artifact.

## 2026-07-26 — Verify first green baseline artifacts

- Baseline commit: product baseline `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`; build-config head `e725f479c5075baa6d32ed465322d6c0ee06979f`
- Environment/device: GitHub Actions run `30192411087` and independent container inspection after download
- Hypothesis: Uploaded files are complete platform artifacts whose embedded checksum files match their contents.
- Change or command: Downloaded all artifacts, verified SHA-256 files, tested APK ZIP integrity, identified the PE executable format, and enumerated the Linux tarball.
- Expected result: No corruption, missing output, or artifact-name mismatch.
- Observed result: All checks passed.
- Evidence/artifacts:
  - Android artifact `8629067655`: `ClipCascade-Android-baseline-debug.apk`, 146,796,102 bytes, SHA-256 `0bac8825d51fe90bb1c07dca8fc9a39c28236b034896f23e33b87e5b29a850ba`.
  - Windows artifact `8629026745`: `ClipCascade-Windows-baseline.exe`, 57,432,247 bytes, SHA-256 `7d7c16ca582ffc6881d7a268934e4b13c56f87bd04868689d367f6cf8a381b04`.
  - Linux artifact `8629014836`: `ClipCascade-Linux-baseline.tar.gz`, 60,070 bytes, SHA-256 `6ae8433d278bfdc1e530171cdf8a7414a57d8d94927fdab607ce452d6dd99a5f`.
  - Android log artifact `8629066946`.
- Decision: Baseline build reproduction gate is complete. Compilation success does not prove runtime clipboard reliability.
- Follow-up: Freeze the desktop connection-state contract, write unit tests, and only then replace the existing reconnect booleans and UI assumptions.

## 2026-07-26 — Implement pure desktop connection model

- Baseline commit: product baseline `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: Python 3.11 unit tests on GitHub Actions `ubuntu-latest` and `windows-latest`
- Hypothesis: Reconnect behavior can be made deterministic and testable before touching STOMP or GUI code.
- Change or command: Added `connection/state.py`, `errors.py`, `retry.py`, and `controller.py`; added injected clock, scheduler, random source, immutable snapshots, stale-timer generations, observer isolation, and unit tests.
- Expected result: State transitions, bounded backoff, cancellation, auth stop, fatal stop, shutdown, stable retry reset, and timestamps are deterministic without network access.
- Observed result: The pure model compiled and all tests passed on Windows and Linux in workflow run `30193070597`.
- Evidence/artifacts: `docs/DESKTOP_CONNECTION_STATE_MACHINE.md`; `ClipCascade_Desktop/tests/test_connection_controller.py`; `test_retry_policy.py`.
- Decision: Use this controller as the only P2S connection truth. Do not adapt GUI and STOMP in the same commit.
- Follow-up: Correct low-level STOMP readiness semantics before controller integration.

## 2026-07-26 — Wait for actual STOMP readiness

- Baseline commit: clean branch before STOMP manager integration
- Environment/device: Fake WebSocket/STOMP unit tests on Windows and Linux CI
- Hypothesis: The upstream client can report success after WebSocket open but before the STOMP `CONNECTED` frame and subscription callback complete.
- Change or command: Reworked `stomp_ws/client.py` to wait separately for WebSocket open and STOMP readiness; added timeout, `ERROR`, early-close, socket-error, callback-failure, and explicit-disconnect tests.
- Expected result: `Client.connect()` returns only after the receive subscription is installed; every premature failure unblocks and raises.
- Observed result: All STOMP client tests passed on both operating systems in workflow run `30193266256`.
- Evidence/artifacts: Commit `6508bfb8936017ed6b86468b27209182f83d5da4`; `test_stomp_client.py`.
- Decision: Treat STOMP `CONNECTED` plus successful subscription as the minimum connected state.
- Follow-up: Integrate `STOMPManager` through the tested controller without callback sleeps.

## 2026-07-26 — Integrate P2S manager with authoritative state controller

- Baseline commit: clean branch after STOMP client readiness fix
- Environment/device: Fake P2S client integration tests on Windows and Linux CI
- Hypothesis: Fixed callback sleeps and recursive reconnect calls can be replaced without changing the server protocol.
- Change or command: Reworked `stomp_ws/stomp_manager.py` to create one controller, create a new client per attempt, schedule cancellable retries, normalize errors, record send/receive observations, start clipboard monitoring once, and expose snapshots/status.
- Expected result: Initial success/failure, runtime close, manual reconnect, automatic recovery, auth stop, send/receive timestamps, and explicit disconnect behave through one state machine.
- Observed result: Integration tests passed on both operating systems. Windows EXE, Linux package, and Android APK remained buildable in workflow run `30193473983`.
- Evidence/artifacts: Commit `55a643f580322326e699a2765ac51cc8b8dc06c5`; `test_stomp_manager.py`; workflow run `30193473983`.
- Decision: The old P2S `time.sleep(RECONNECT_WS_TIMER)` and recursive close-callback `connect()` path are retired.
- Follow-up: Migrate GUI and CLI controls from local booleans to snapshot-derived actions.

## 2026-07-26 — Drive GUI and CLI controls from P2S snapshots

- Baseline commit: P2S state-controller integration
- Environment/device: Pure presentation tests, Windows/Linux unit jobs, Windows PyInstaller build, Linux package build
- Hypothesis: A shared pure mapping can prevent GUI and CLI from presenting actions that contradict transport state while preserving P2P compatibility.
- Change or command: Added `connection/tray_view.py`; migrated GUI and CLI menus to snapshot-derived labels/actions; retained a legacy boolean fallback only for P2P. Also corrected already-disconnected teardown and classified `ConnectionError` before generic `OSError`.
- Expected result: P2S presents Connect, Cancel, Disconnect, Reconnect now, Login required, Stopping, or Retry according to the real snapshot. P2P behavior does not regress before its own migration.
- Observed result: All five jobs succeeded in workflow run `30193922241`: Android APK, Windows EXE, Linux package, Windows tests, and Linux tests.
- Evidence/artifacts:
  - head `bb5f047fb448b59591b9590b8762a5ba907ecc50`;
  - Android artifact `8629529566`;
  - Windows artifact `8629510789`;
  - Linux artifact `8629490534`;
  - Android log artifact `8629528730`.
- Decision: P2S connection state and primary GUI/CLI controls now share one source of truth. P2P remains explicitly legacy rather than being silently mixed into the new controller.
- Follow-up: Perform a real Windows public-server smoke test and forced network-loss recovery test.

## 2026-07-26 — Independently verify P2S status-UI artifacts

- Baseline commit: `bb5f047fb448b59591b9590b8762a5ba907ecc50`
- Environment/device: Downloaded GitHub artifacts inspected in the execution container
- Hypothesis: The status-UI build artifacts are complete and their embedded checksum files match.
- Change or command: Downloaded Windows and Android artifacts from run `30193922241`, verified embedded SHA-256 files, identified file formats, and tested APK ZIP integrity.
- Expected result: No corruption or naming mismatch.
- Observed result: All checks passed.
- Evidence/artifacts:
  - Windows `ClipCascade-Windows-baseline.exe`, 57,456,724 bytes, SHA-256 `64190743de0c19a581675db255db7736d74602c5098ebf7c632c03bf66f1552a`, identified as PE32+ x86-64 Windows GUI executable.
  - Android `ClipCascade-Android-baseline-debug.apk`, 146,796,102 bytes, SHA-256 `2aae59fc24b7d9e50b58f5be89b1f54267197f08624ba08aff161d901e6408bc`, identified as an Android APK; archive integrity passed.
- Decision: Make these files available for manual testing. Do not describe the APK as an Android reliability fix; Android product code is unchanged except for the build configuration restoration.
- Follow-up: Record actual desktop runtime behavior separately from CI success.

## 2026-07-26 — Reduce duplicate and documentation-only CI work

- Baseline commit: P2S status-UI implementation
- Environment/device: GitHub Actions trigger behavior
- Hypothesis: Simultaneous branch-push and pull-request triggers rebuild the same code, while PR path evaluation can still run the full matrix for documentation-only commits because the overall PR contains product changes.
- Change or command: Renamed the workflow to `Clean rebuild verification`, renamed artifacts from `baseline` to `clean-rebuild`, expanded compile checks to GUI/CLI, ignored Markdown/docs on branch pushes, and removed the redundant pull-request trigger.
- Expected result: One verification run per product-code push; documentation-only handoff updates do not consume Android/Windows builds.
- Observed result: A documentation update made before removing the PR trigger reproduced the unwanted PR run and cancelled its Android job. The redundant trigger was then removed in commit `04261e9d14b9e058c231a744631bbe34eb4665d1`.
- Evidence/artifacts: Workflow commits `a2ef19fc5b617137af2cb5ab3e54c8fccab41fdf` and `04261e9d14b9e058c231a744631bbe34eb4665d1`; cancelled Android job in run `30194151707` was explicitly re-run.
- Decision: Use branch-push verification plus manual dispatch. Keep documentation maintenance independent from heavyweight artifact generation.
- Follow-up: Confirm the re-run artifact names and append their IDs/hashes when complete.
