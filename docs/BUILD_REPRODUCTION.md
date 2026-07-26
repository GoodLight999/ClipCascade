# Clean Rebuild Verification and Artifact Reproduction

Last updated: 2026-07-26 (Asia/Tokyo)

## Purpose

This document defines the reproducible build and automated-test gate for `clean-rebuild`.

The gate proves that a specific commit:

- passes the desktop test suite on Windows and Linux;
- compiles the complete desktop Python source;
- produces an Android debug APK;
- produces a standalone Windows executable;
- produces a Linux source package;
- emits SHA-256 files with each platform artifact.

A green build does **not** prove real-device Android background capture, public-server connectivity, or forced-network-loss recovery. Those require separate runtime evidence.

## Authoritative workflow

- File: `.github/workflows/baseline-artifacts.yml`
- Display name: `Clean rebuild verification`
- Automatic trigger: non-documentation pushes to `clean-rebuild`
- Manual trigger: `workflow_dispatch`
- Documentation-only pushes ignored: `docs/**`, `**/*.md`
- Artifact retention: 30 days
- Every platform artifact contains `SHA256SUMS.txt`
- Android Gradle output is uploaded even after Android build failure

There is intentionally no pull-request trigger. The active draft PR uses `clean-rebuild` as its head, so a PR trigger duplicated every branch-push build and could rebuild the entire historical PR diff for documentation-only updates.

## Desktop automated tests

### Platforms

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

PowerShell equivalent:

```powershell
python -m pip install websocket_client==1.8.0
python -m compileall -q src/connection src/stomp_ws src/gui src/cli tests
$env:PYTHONPATH = "$PWD/src"
python -m unittest discover -s tests -p "test_*.py" -v
```

The suite currently covers the pure connection controller, retry policy, low-level STOMP handshake, P2S manager integration, and GUI/CLI action mapping.

## Android debug APK

### Toolchain

- Runner: `ubuntu-latest`
- Node.js: 20
- Java: Temurin 17
- Gradle wrapper: 8.14.1
- Android compile SDK: 35
- Android target SDK: 35
- Android minimum SDK: 26
- React Native: 0.80.2

### Required upstream build-config restoration

The verified upstream baseline did not contain `ClipCascade_Mobile/src/android/gradle.properties`, while `android/app/build.gradle` directly referenced `hermesEnabled`. The first build failed during Gradle configuration with:

```text
Could not get unknown property 'hermesEnabled'
```

The clean branch restores the React Native 0.80 template defaults:

```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
android.useAndroidX=true
reactNativeArchitectures=armeabi-v7a,arm64-v8a,x86,x86_64
newArchEnabled=true
hermesEnabled=true
```

This is a build-configuration repair. It does not change the ClipCascade server protocol or Android clipboard behavior.

### Reproduction commands

Run from `ClipCascade_Mobile/src`:

```bash
npm ci
chmod +x android/gradlew
cd android
./gradlew --no-daemon --stacktrace assembleDebug
```

Gradle output:

```text
ClipCascade_Mobile/src/android/app/build/outputs/apk/debug/app-debug.apk
```

Current workflow filename:

```text
ClipCascade-Android-clean-rebuild-debug.apk
```

The file is debug-signed. It is not a production-release signature.

## Windows standalone executable

### Toolchain

- Runner: `windows-latest`
- Python: 3.11
- PyInstaller: 6.11.1
- Dependencies: `ClipCascade_Desktop/src/requirements_win.txt`

### Reproduction commands

Run from `ClipCascade_Desktop/src`:

```powershell
python -m pip install --upgrade pip
python -m pip install -r requirements_win.txt
python -m pip install PyInstaller==6.11.1
python -m compileall -q .
python -m PyInstaller --clean --noconfirm ClipCascade_win.spec
```

PyInstaller output:

```text
ClipCascade_Desktop/src/dist/ClipCascade.exe
```

Current workflow filename:

```text
ClipCascade-Windows-clean-rebuild.exe
```

## Linux package

The upstream project distributes Linux as source rather than through a maintained Linux PyInstaller specification. The current gate therefore compile-checks the complete Python source and packages it without inventing an untested native-executable architecture.

### Toolchain

- Runner: `ubuntu-latest`
- Python: 3.11

### Reproduction command

```bash
cd ClipCascade_Desktop/src
python -m compileall -q .
```

Current workflow filename:

```text
ClipCascade-Linux-clean-rebuild.tar.gz
```

The archive contains the desktop source, requirements, launch code, and repository license. A native Linux executable remains a separate release task.

## First green upstream-baseline result

- Run: `30192411087`
- Head: `e725f479c5075baa6d32ed465322d6c0ee06979f`
- Android: success
- Windows: success
- Linux: success

This run established that the upstream product plus the Android Gradle-properties restoration could be built.

## First fully green P2S state/UI result

- Run: `30193922241`
- Head: `bb5f047fb448b59591b9590b8762a5ba907ecc50`
- Android APK: success
- Windows EXE: success
- Linux package: success
- Ubuntu desktop tests: success
- Windows desktop tests: success

Verified artifacts:

| Platform | Artifact ID | Inner file | Size | SHA-256 |
|---|---:|---|---:|---|
| Android | `8629529566` | `ClipCascade-Android-baseline-debug.apk` | 146,796,102 bytes | `2aae59fc24b7d9e50b58f5be89b1f54267197f08624ba08aff161d901e6408bc` |
| Windows | `8629510789` | `ClipCascade-Windows-baseline.exe` | 57,456,724 bytes | `64190743de0c19a581675db255db7736d74602c5098ebf7c632c03bf66f1552a` |
| Linux | `8629490534` | baseline Linux tarball | recorded inside artifact | embedded `SHA256SUMS.txt` |
| Android log | `8629528730` | `gradle-build.log` | recorded by GitHub | GitHub artifact digest available |

Independent post-download checks passed for the Android embedded checksum, APK ZIP integrity, Windows embedded checksum, and Windows PE32+ x86-64 GUI format.

## First green clean artifact-name result

Workflow run `30194151707`, head `a2ef19fc5b617137af2cb5ab3e54c8fccab41fdf`, was initially interrupted by a later documentation-triggered run. The cancelled Android job was re-run explicitly. The latest attempt completed all five jobs successfully.

Artifacts using the current names:

| Platform | Artifact ID | Artifact name |
|---|---:|---|
| Android | `8629632706` | `ClipCascade-Android-clean-rebuild-debug` |
| Android log | `8629631994` | `ClipCascade-Android-clean-rebuild-build-log` |
| Windows | `8629573494` | `ClipCascade-Windows-clean-rebuild` |
| Linux | `8629557341` | `ClipCascade-Linux-clean-rebuild` |

The duplicate PR trigger was subsequently removed in commit `04261e9d14b9e058c231a744631bbe34eb4665d1`.

## Success criteria

A product-code verification run is green only when all five jobs succeed:

1. `Desktop unit tests (ubuntu-latest)`
2. `Desktop unit tests (windows-latest)`
3. `Android debug APK`
4. `Windows standalone EXE`
5. `Linux source package`

For every meaningful build-pipeline or product change, record:

- exact head SHA;
- workflow run ID;
- job outcomes;
- artifact IDs and filenames;
- SHA-256 values when independently downloaded;
- any distinction between compile-time evidence and runtime evidence.

## Failure handling

1. Preserve the failing log and exact commit SHA.
2. Record the failed step and exact error in `docs/EXPERIMENT_LOG.md`.
3. Change only the minimum surface needed to test one hypothesis.
4. Do not alter product behavior merely to make packaging green.
5. Re-run the isolated failed/cancelled job when the successful jobs remain valid.
6. Never overwrite a prior failure record merely because a later retry succeeds.
