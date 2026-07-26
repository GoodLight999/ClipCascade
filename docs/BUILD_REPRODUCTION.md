# Baseline Build Reproduction

Last updated: 2026-07-26 (Asia/Tokyo)

## Purpose

This document defines the first reproducible build gate for the clean rebuild. It builds the upstream product code from baseline commit `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`, plus one documented restoration of the React Native 0.80 standard Android Gradle properties that were absent from the upstream tree.

The build does not claim that runtime behavior is correct. It proves only that installable/distributable baseline artifacts can be produced repeatedly from a known commit.

## Authoritative workflow

- Workflow: `.github/workflows/baseline-artifacts.yml`
- Trigger: pushes to `clean-rebuild`, pull requests targeting `main`, or manual dispatch
- Artifact retention: 30 days
- Every artifact directory contains `SHA256SUMS.txt`
- Android Gradle output is uploaded even when the Android job fails

## Android baseline APK

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

The upstream baseline did not contain `ClipCascade_Mobile/src/android/gradle.properties`, while `android/app/build.gradle` directly referenced `hermesEnabled`. The first CI attempt therefore failed during Gradle configuration with:

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

This is a build-configuration repair. It does not change the ClipCascade server protocol or application feature logic.

### Reproduction commands

Run from `ClipCascade_Mobile/src`:

```bash
npm ci
chmod +x android/gradlew
cd android
./gradlew --no-daemon --stacktrace assembleDebug
```

### Expected output

```text
ClipCascade_Mobile/src/android/app/build/outputs/apk/debug/app-debug.apk
```

The workflow renames it to:

```text
ClipCascade-Android-baseline-debug.apk
```

This is a debug-signed APK for baseline validation. It is not a production release signature.

## Windows standalone executable

### Toolchain

- Runner: `windows-latest`
- Python: 3.11
- PyInstaller: 6.11.1
- Application dependencies: `ClipCascade_Desktop/src/requirements_win.txt`

### Reproduction commands

Run from `ClipCascade_Desktop/src`:

```powershell
python -m pip install --upgrade pip
python -m pip install -r requirements_win.txt
python -m pip install PyInstaller==6.11.1
python -m compileall -q .
python -m PyInstaller --clean --noconfirm ClipCascade_win.spec
```

### Expected output

```text
ClipCascade_Desktop/src/dist/ClipCascade.exe
```

The workflow renames it to:

```text
ClipCascade-Windows-baseline.exe
```

## Linux package

The upstream project distributes Linux as source rather than through a maintained Linux PyInstaller specification. The baseline gate therefore compile-checks the Python source and packages the upstream Linux client source tree without inventing a new executable architecture.

### Toolchain

- Runner: `ubuntu-latest`
- Python: 3.11

### Reproduction commands

```bash
cd ClipCascade_Desktop/src
python -m compileall -q .
```

The workflow then creates:

```text
ClipCascade-Linux-baseline.tar.gz
```

The archive contains the upstream desktop client source, `requirements.txt`, launch code, and the repository license. A native Linux executable may be added later as a separately tested delivery task; it must not silently replace the upstream source-package model.

## First green baseline result

- Workflow run: `30192411087`
- Head commit: `e725f479c5075baa6d32ed465322d6c0ee06979f`
- Android job: success
- Windows job: success
- Linux job: success

Generated artifacts:

| Platform | GitHub artifact ID | Inner file | Size | SHA-256 |
|---|---:|---|---:|---|
| Android | `8629067655` | `ClipCascade-Android-baseline-debug.apk` | 146,796,102 bytes | `0bac8825d51fe90bb1c07dca8fc9a39c28236b034896f23e33b87e5b29a850ba` |
| Windows | `8629026745` | `ClipCascade-Windows-baseline.exe` | 57,432,247 bytes | `7d7c16ca582ffc6881d7a268934e4b13c56f87bd04868689d367f6cf8a381b04` |
| Linux | `8629014836` | `ClipCascade-Linux-baseline.tar.gz` | 60,070 bytes | `6ae8433d278bfdc1e530171cdf8a7414a57d8d94927fdab607ce452d6dd99a5f` |

Independent post-download checks performed in the execution container:

- Android artifact ZIP checksum file passed.
- APK archive integrity test passed with no compressed-data errors.
- The APK was identified as an Android package containing Gradle app metadata.
- Windows artifact ZIP checksum file passed.
- The executable was identified as a PE32+ x86-64 Windows GUI executable.
- Linux artifact ZIP checksum file passed.
- The tarball contents were enumerated successfully.

## Success criteria

A baseline build is green only when all three jobs complete and upload their expected artifacts:

1. `ClipCascade-Android-baseline-debug`
2. `ClipCascade-Windows-baseline`
3. `ClipCascade-Linux-baseline`

The exact workflow run ID, job outcomes, artifact IDs, filenames, and SHA-256 values must be appended to `docs/EXPERIMENT_LOG.md` after every meaningful change to the build pipeline.

## Failure handling

When a job fails:

1. Preserve the failing log and exact commit SHA.
2. Record the failed step and error text in `docs/EXPERIMENT_LOG.md`.
3. Change only the minimum build or CI surface needed to test one hypothesis.
4. Do not alter product behavior merely to make packaging green.
5. Re-run the failed job or create a new commit, then record the result.
