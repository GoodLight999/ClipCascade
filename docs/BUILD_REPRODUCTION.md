# Clean Rebuild Verification and Artifact Reproduction

Last updated: 2026-07-26 (Asia/Tokyo)

## Purpose

This document defines the automated test and artifact gate for `clean-rebuild`.

A green run proves that one product-code/pipeline head:

- passes desktop tests on Windows and Ubuntu;
- passes Android app JVM tests;
- compiles Android native and React Native surfaces;
- generates and packages the React Native/Hermes JavaScript bundle;
- produces a standalone Android engineering APK that does not require Metro;
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

## Android tests and standalone APK

### Toolchain

- Runner: `ubuntu-latest`
- Node.js: 20
- Java: Temurin 17
- Gradle wrapper: 8.14.1
- compile SDK: 35
- target SDK: 35
- app minimum SDK: 26
- React Native: 0.80.2
- Hermes enabled

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

### Why `assembleDebug` is not distributable here

React Native skips JS bundling for variants listed in `debuggableVariants`. The normal `debug` variant therefore expects Metro and may display:

```text
Unable to load script.
Make sure you're running Metro or that your bundle 'index.android.bundle' is packaged correctly for release.
```

The debug variant remains useful for deliberate Metro development. It must not be distributed as the ordinary physical-device test artifact.

### Standalone build type

`ClipCascade_Mobile/src/android/app/build.gradle` defines:

- `debuggableVariants = ["debug"]`;
- build type `standalone`;
- `standalone` inherits release runtime semantics;
- `standalone` uses the debug signing key;
- minification is disabled;
- dependency matching falls back to release then debug.

This combination packages the JS bundle and avoids Metro while remaining clearly non-production.

### Commands

Run from `ClipCascade_Mobile/src`:

```bash
npm ci
chmod +x android/gradlew
cd android
./gradlew --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleStandalone
```

Do not use unqualified `testDebugUnitTest`. It compiles unit-test sources from every included React Native dependency; `@react-native-module/pbkdf2` contains defective sample tests unrelated to the ClipCascade app.

Expected APK:

```text
ClipCascade_Mobile/src/android/app/build/outputs/apk/standalone/app-standalone.apk
```

Workflow filename:

```text
ClipCascade-Android-clean-rebuild-standalone.apk
```

### Mandatory packaged-bundle verification

The workflow runs these checks before artifact upload:

```bash
APK_PATH=android/app/build/outputs/apk/standalone/app-standalone.apk
test -f "$APK_PATH"
unzip -Z1 "$APK_PATH" | grep -Fxq 'assets/index.android.bundle'
unzip -tq "$APK_PATH"
```

The Android artifact is invalid if `assets/index.android.bundle` is absent, even if Gradle compilation and APK signing succeeded.

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

Android framework clipboard behavior, overlay focus behavior, actual on-device JS ACK execution, AccessibilityService, and Shizuku still require physical-device or instrumentation evidence.

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

### Payload-free bridge self-test debug build

- Run: `30202889590`
- Head: `01394199be3e40160dcd592e8d0e5ee6a85722d1`
- Purpose: native → React Native test-ID/ACK bridge and 46 tests.
- Important outcome: CI was green, but the distributed `assembleDebug` APK lacked packaged JS and required Metro. This is retained as a packaging failure, not a current artifact milestone.

### Standalone bundled Android build — current

- Run: `30203690726`
- Product-code/pipeline head: `a94b830fb954d09fc742b39833cebd5915988566`
- Purpose: preserve all 46 tests and diagnostics while producing a Metro-independent APK with an enforced packaged-bundle check.

Artifacts:

| Platform | Artifact ID | Inner file | Size | SHA-256 |
|---|---:|---|---:|---|
| Android standalone | `8632482778` | `ClipCascade-Android-clean-rebuild-standalone.apk` | 93,574,655 bytes | `ca7ee41f95f729879a5298bdc2b6e413f0f2086cbc922205e06468d7878f471b` |
| Android log | `8632482104` | `gradle-build.log` | see artifact | GitHub digest retained |
| Windows | `8632434975` | `ClipCascade-Windows-clean-rebuild.exe` | 57,456,724 bytes | `36da95c51e473b8bf33677f81142ea70bdfcd81a968e24e8a0be4878e11ce103` |
| Linux | `8632420551` | `ClipCascade-Linux-clean-rebuild.tar.gz` | 68,791 bytes | `6fadbb46751fe0e714f85d9118c69c2ecc7ea14c0d8a76b2d85c2eed0114ba6b` |

Independent post-download checks:

- Android embedded SHA-256 passed;
- APK identification passed;
- exact `assets/index.android.bundle` entry present;
- APK ZIP integrity passed;
- Windows embedded SHA-256 passed;
- Windows identified as PE32+ x86-64 GUI;
- Linux embedded SHA-256 passed;
- Linux archive identified as gzip and enumerated successfully with 53 entries.

## Recorded Android failures

### Run `30201114997`

Unqualified `testDebugUnitTest` included defective third-party sample tests from `@react-native-module/pbkdf2`. The dependency was not patched; CI was scoped to `:app:testDebugUnitTest`.

### Run `30201352946`

Five app tests compared `Int` expected values with intentionally `Long` counters and timestamps. Production types were preserved; test expectations were corrected.

### Run `30202889590` artifact used without Metro

`assembleDebug` intentionally skipped JS bundling. The installed APK displayed `Unable to load script`. The fix was not to ask the user to run Metro; the artifact gate was changed to a standalone build and an APK-internal bundle check.

Detailed evidence:

- `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_ACQUISITION.md`
- `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_STANDALONE.md`

## Success criteria

A product-code gate is green only when all five jobs succeed:

1. `Desktop unit tests (ubuntu-latest)`
2. `Desktop unit tests (windows-latest)`
3. `Android tests and standalone APK`
4. `Windows standalone EXE`
5. `Linux source package`

For every meaningful product or pipeline change, record:

- exact product-code/pipeline head;
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
7. Never classify an APK as user-installable without verifying the packaged JavaScript asset.
