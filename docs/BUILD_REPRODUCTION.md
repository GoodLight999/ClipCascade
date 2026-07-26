# Clean Rebuild Verification and Artifact Reproduction

Last updated: 2026-07-26 (Asia/Tokyo)

## Purpose

This document defines the automated test and artifact gate for `clean-rebuild`.

A green run proves that one product-code head:

- passes desktop tests on Windows and Ubuntu;
- passes Android app JVM tests;
- compiles the Android native and React Native surfaces;
- produces an Android debug APK;
- produces a standalone Windows executable;
- produces a Linux source package;
- emits platform SHA-256 files.

It does not prove Android background reliability, real server delivery, forced-network-loss recovery, power behavior, or production signing.

## Authoritative workflow

- File: `.github/workflows/baseline-artifacts.yml`
- Name: `Clean rebuild verification`
- Automatic trigger: pull requests targeting `main`
- Manual trigger: `workflow_dispatch`
- Ignored automatic changes: `docs/**`, `**/*.md`
- Artifact retention: 30 days
- Android build log uploaded even after failure
- Each platform artifact directory contains `SHA256SUMS.txt`

There is no simultaneous push trigger because PR #3 already builds every product-code update from `clean-rebuild`; push plus PR events would duplicate the matrix.

## Desktop tests

Platforms and dependencies:

- `ubuntu-latest`
- `windows-latest`
- Python 3.11
- `websocket_client==1.8.0`

Run from `ClipCascade_Desktop`:

```bash
python -m pip install websocket_client==1.8.0
python -m compileall -q src/connection src/stomp_ws src/gui src/cli tests
PYTHONPATH=src python -m unittest discover -s tests -p "test_*.py" -v
```

PowerShell:

```powershell
python -m pip install websocket_client==1.8.0
python -m compileall -q src/connection src/stomp_ws src/gui src/cli tests
$env:PYTHONPATH = "$PWD/src"
python -m unittest discover -s tests -p "test_*.py" -v
```

The suite covers connection state, retry policy, STOMP readiness, P2S integration, and GUI/CLI action mapping.

## Android tests and debug APK

### Toolchain

- Runner: `ubuntu-latest`
- Node.js: 20
- Java: Temurin 17
- Gradle wrapper: 8.14.1
- compile SDK: 35
- target SDK: 35
- app minimum SDK: 26
- React Native: 0.80.2

### Required upstream build restoration

The verified upstream tree lacked `ClipCascade_Mobile/src/android/gradle.properties`, while `app/build.gradle` referenced `hermesEnabled`. The clean branch restores standard React Native 0.80 values:

```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
android.useAndroidX=true
reactNativeArchitectures=armeabi-v7a,arm64-v8a,x86,x86_64
newArchEnabled=true
hermesEnabled=true
```

This is a build repair, not a runtime reliability fix.

### Commands

Run from `ClipCascade_Mobile/src`:

```bash
npm ci
chmod +x android/gradlew
cd android
./gradlew --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug
```

Do not use unqualified `testDebugUnitTest`. It compiles unit-test sources from every included React Native dependency; `@react-native-module/pbkdf2` contains defective sample tests unrelated to the ClipCascade app.

Expected APK:

```text
ClipCascade_Mobile/src/android/app/build/outputs/apk/debug/app-debug.apk
```

Workflow filename:

```text
ClipCascade-Android-clean-rebuild-debug.apk
```

The APK is debug-signed.

### Current Android test inventory

Exactly 46 `@Test` methods:

| Test surface | Count |
|---|---:|
| Acquisition state reducer | 12 |
| Backend selection and capability quality | 9 |
| Ordinary clipboard backend | 8 |
| Trigger deduplication | 9 |
| Diagnostics formatter | 2 |
| Native → React Native self-test tracker | 6 |
| **Total** | **46** |

The suite covers:

- explicit coordinator states and counters;
- healthy-versus-degraded backend selection;
- ordinary listener idempotency, concurrency, failures, and stale callbacks;
- trigger duplicate windows and reset behavior;
- payload-free diagnostics rendering;
- self-test matching ACK, timeout, supersession, and emit failure.

Android framework clipboard behavior, overlay focus behavior, actual JS ACK execution, AccessibilityService, and Shizuku still require device or instrumentation evidence.

## Windows standalone executable

Toolchain:

- `windows-latest`
- Python 3.11
- PyInstaller 6.11.1
- `ClipCascade_Desktop/src/requirements_win.txt`

Run from `ClipCascade_Desktop/src`:

```powershell
python -m pip install --upgrade pip
python -m pip install -r requirements_win.txt
python -m pip install PyInstaller==6.11.1
python -m compileall -q .
python -m PyInstaller --clean --noconfirm ClipCascade_win.spec
```

Expected output:

```text
ClipCascade_Desktop/src/dist/ClipCascade.exe
```

Workflow filename:

```text
ClipCascade-Windows-clean-rebuild.exe
```

## Linux source package

The upstream project has no maintained Linux PyInstaller specification. The gate compile-checks and packages source rather than inventing an unverified executable architecture.

```bash
cd ClipCascade_Desktop/src
python -m compileall -q .
```

Workflow filename:

```text
ClipCascade-Linux-clean-rebuild.tar.gz
```

The archive contains desktop source, requirements, launch code, and license.

## Verification milestones

### Upstream baseline build

- Run: `30192411087`
- Head: `e725f479c5075baa6d32ed465322d6c0ee06979f`
- Purpose: upstream product plus Gradle-properties restoration.

### Desktop P2S state/UI build

- Run: `30193922241`
- Head: `bb5f047fb448b59591b9590b8762a5ba907ecc50`
- Purpose: desktop state controller, STOMP integration, GUI/CLI mapping, artifacts.

### Android acquisition runtime build

- Run: `30201613966`
- Head: `9b7ca34ab9ffa165848e1d813b7caff04a546c0e`
- Purpose: acquisition contracts, shared native read runtime, ordinary listener, hardened logcat/overlay, 38 tests.

### Native diagnostics Activity build

- Run: `30202437538`
- Head: `b3ff8f72b3a09fda586aebcdadfba1f541446be1`
- Purpose: immutable diagnostics snapshot, formatter, native Activity, launcher long-press shortcut.

### Payload-free bridge self-test build — current

- Run: `30202889590`
- Product-code head: `01394199be3e40160dcd592e8d0e5ee6a85722d1`
- Purpose: diagnostics Activity plus native → React Native test-ID/ACK bridge, 46 tests, all artifacts.

Artifacts:

| Platform | Artifact ID | Inner file | Size | SHA-256 |
|---|---:|---|---:|---|
| Android | `8632223256` | `ClipCascade-Android-clean-rebuild-debug.apk` | 146,868,392 bytes | `a9c23f3d54c529f501ee92fe233cc434264edf862de5b1be82b6b86b2d434808` |
| Android log | `8632222540` | `gradle-build.log` | see artifact | GitHub digest retained |
| Windows | `8632211465` | `ClipCascade-Windows-clean-rebuild.exe` | 57,456,724 bytes | `ad09902ba6e6ccc22fce7b42d9aad7f6f6755647e435be05d97d413dfc910491` |
| Linux | `8632190948` | `ClipCascade-Linux-clean-rebuild.tar.gz` | 68,793 bytes | `9156f49b406e2d4acbd00007fc2194e5a3b3e66fd676541413b8b533576fff50` |

Independent post-download checks:

- Android embedded SHA-256 passed;
- APK identification passed;
- APK ZIP integrity passed;
- Windows embedded SHA-256 passed;
- Windows identified as PE32+ x86-64 GUI;
- Linux embedded SHA-256 passed;
- Linux archive identified as gzip and enumerated successfully with 53 entries.

## Recorded Android CI failures

### Run `30201114997`

Unqualified `testDebugUnitTest` included defective third-party sample tests from `@react-native-module/pbkdf2`. The dependency was not patched; CI was scoped to `:app:testDebugUnitTest`.

### Run `30201352946`

Five app tests compared `Int` expected values with intentionally `Long` counters and timestamps. Production types were preserved; test expectations were corrected.

Detailed evidence: `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_ACQUISITION.md`.

## Success criteria

A product-code gate is green only when all five jobs succeed:

1. `Desktop unit tests (ubuntu-latest)`
2. `Desktop unit tests (windows-latest)`
3. `Android tests and debug APK`
4. `Windows standalone EXE`
5. `Linux source package`

For every meaningful product or pipeline change, record:

- exact product-code head;
- workflow run ID;
- job outcomes;
- artifact IDs and filenames;
- independently checked hashes;
- automated-evidence versus runtime-evidence boundary.

## Failure handling

1. Preserve the failing log and exact head.
2. Record the failed step and error.
3. Change only the minimum surface required by one hypothesis.
4. Do not change product behavior merely to make packaging green.
5. Do not patch generated dependencies unless the product actually requires it.
6. Never erase a failed attempt because a later run succeeds.
