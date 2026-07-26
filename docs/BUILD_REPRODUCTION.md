# Clean Rebuild Verification and Artifact Reproduction

Last updated: 2026-07-26 (Asia/Tokyo)

## Purpose

This document defines the automated test and artifact gate for `clean-rebuild`.

A green run proves that one product-code head:

- passes desktop tests on Windows and Ubuntu;
- compiles the desktop source;
- passes Android app JVM tests;
- produces an Android debug APK;
- produces a standalone Windows executable;
- produces a Linux source package;
- emits platform SHA-256 files.

A green run does **not** prove Android background reliability, public-server connectivity, forced-network-loss recovery, power behavior, or production signing.

## Authoritative workflow

- File: `.github/workflows/baseline-artifacts.yml`
- Display name: `Clean rebuild verification`
- Automatic trigger: `pull_request` updates targeting `main`
- Manual trigger: `workflow_dispatch`
- Ignored automatic changes: `docs/**`, `**/*.md`
- Artifact retention: 30 days
- Android Gradle output is uploaded even after failure
- Every platform artifact directory contains `SHA256SUMS.txt`

There is intentionally no simultaneous `push` trigger. PR #3 uses `clean-rebuild` as its head, so push plus PR events would build the same product commit twice.

## Desktop automated tests

### Platforms and dependencies

- `ubuntu-latest`
- `windows-latest`
- Python 3.11
- `websocket_client==1.8.0`

### Commands

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

## Android app tests and debug APK

### Toolchain

- Runner: `ubuntu-latest`
- Node.js: 20
- Java: Temurin 17
- Gradle wrapper: 8.14.1
- Android compile SDK: 35
- Android target SDK: 35
- application minimum SDK: 26
- React Native: 0.80.2

### Required upstream build restoration

The verified upstream tree lacked `ClipCascade_Mobile/src/android/gradle.properties`, while `app/build.gradle` referenced `hermesEnabled`. The initial build therefore failed with:

```text
Could not get unknown property 'hermesEnabled'
```

The clean branch restores standard React Native 0.80 values:

```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
android.useAndroidX=true
reactNativeArchitectures=armeabi-v7a,arm64-v8a,x86,x86_64
newArchEnabled=true
hermesEnabled=true
```

This is a build-configuration repair, not a runtime reliability fix.

### Commands

Run from `ClipCascade_Mobile/src`:

```bash
npm ci
chmod +x android/gradlew
cd android
./gradlew --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug
```

Do **not** use unqualified `testDebugUnitTest`. It also compiles unit-test sources from every included React Native dependency. `@react-native-module/pbkdf2` currently contains defective sample tests that are unrelated to the ClipCascade app.

Expected APK:

```text
ClipCascade_Mobile/src/android/app/build/outputs/apk/debug/app-debug.apk
```

Workflow filename:

```text
ClipCascade-Android-clean-rebuild-debug.apk
```

The APK is debug-signed.

### Current Android test scope

The app JVM suite currently contains 38 tests covering:

- acquisition backend selection;
- healthy-versus-degraded capability ordering;
- acquisition coordinator transitions;
- trigger deduplication model;
- ordinary listener start/stop idempotency;
- concurrent starts;
- stale callbacks after stop;
- registration/unregistration failures;
- trigger-delivery failure isolation;
- counters and monotonic timestamps.

Android framework behavior, overlay focus behavior, AccessibilityService behavior, and Shizuku behavior still require device or instrumentation evidence.

## Windows standalone executable

### Toolchain

- Runner: `windows-latest`
- Python: 3.11
- PyInstaller: 6.11.1
- dependencies: `ClipCascade_Desktop/src/requirements_win.txt`

### Commands

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

The upstream project has no maintained Linux PyInstaller specification. The current gate compile-checks and packages the source instead of inventing an unverified native delivery architecture.

```bash
cd ClipCascade_Desktop/src
python -m compileall -q .
```

Workflow filename:

```text
ClipCascade-Linux-clean-rebuild.tar.gz
```

The archive contains desktop source, requirements, launch code, and the repository license.

## Historical verification milestones

### First green upstream-baseline build

- Run: `30192411087`
- Head: `e725f479c5075baa6d32ed465322d6c0ee06979f`
- Purpose: prove upstream product plus Gradle-properties restoration can build.

### First green desktop P2S state/UI build

- Run: `30193922241`
- Head: `bb5f047fb448b59591b9590b8762a5ba907ecc50`
- Purpose: verify the desktop state-controller, STOMP integration, GUI/CLI mapping, and all platform packages.

### First green Android acquisition-runtime build

- Run: `30201613966`
- Head: `9b7ca34ab9ffa165848e1d813b7caff04a546c0e`
- Purpose: verify the Android acquisition model, shared native read runtime, ordinary backend, hardened logcat/overlay lifecycle, 38 app tests, and all platform artifacts.

Artifacts:

| Platform | Artifact ID | Inner file | Size | SHA-256 |
|---|---:|---|---:|---|
| Android | `8631864819` | `ClipCascade-Android-clean-rebuild-debug.apk` | 146,845,313 bytes | `e7097304688544dd0251e3ba19f56fa3da18a51c7994937ea0877d9accf7a103` |
| Android log | `8631864069` | `gradle-build.log` | see artifact | GitHub digest retained |
| Windows | `8631829156` | `ClipCascade-Windows-clean-rebuild.exe` | 57,456,724 bytes | `a78e8e26d486a3fff014c860149a4b88fd2eec6799b6c1ade01c2e90a84bcda0` |
| Linux | `8631812042` | `ClipCascade-Linux-clean-rebuild.tar.gz` | see artifact | embedded checksum generated |

Independent post-download checks passed for:

- Android embedded SHA-256;
- APK identification;
- APK ZIP integrity;
- Windows embedded SHA-256;
- PE32+ x86-64 GUI identification.

## Recorded Android CI failures

### Run `30201114997`

Unqualified `testDebugUnitTest` included defective third-party sample tests from `@react-native-module/pbkdf2`. The app code was not patched; CI was scoped to `:app:testDebugUnitTest`.

### Run `30201352946`

Five app tests compared `Int` expected values with intentionally `Long` counters and timestamps. Production types were preserved; test expectations were corrected.

See `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_ACQUISITION.md` for exact evidence.

## Success criteria

A product-code gate is green only when all five jobs succeed:

1. `Desktop unit tests (ubuntu-latest)`
2. `Desktop unit tests (windows-latest)`
3. `Android tests and debug APK`
4. `Windows standalone EXE`
5. `Linux source package`

For each meaningful product or pipeline change, record:

- exact product head SHA;
- workflow run ID;
- every job outcome;
- artifact IDs and filenames;
- independently checked SHA-256 values;
- the boundary between automated evidence and runtime evidence.

## Failure handling

1. Preserve the failing log and exact head.
2. Record the failed step and error.
3. Change only the minimum surface required by one hypothesis.
4. Do not alter product behavior merely to make packaging green.
5. Do not patch generated dependencies unless the product actually requires it.
6. Never erase a failed attempt because a later retry succeeds.
