# Baseline Build Reproduction

Last updated: 2026-07-26 (Asia/Tokyo)

## Purpose

This document defines the first reproducible build gate for the clean rebuild. It builds the unmodified upstream product code from baseline commit `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39` plus documentation and CI-only files on `clean-rebuild`.

The build does not claim that runtime behavior is correct. It proves only that installable/distributable baseline artifacts can be produced repeatedly from a known commit.

## Authoritative workflow

- Workflow: `.github/workflows/baseline-artifacts.yml`
- Trigger: pushes to `clean-rebuild`, pull requests targeting `main`, or manual dispatch
- Artifact retention: 30 days
- Every artifact directory contains `SHA256SUMS.txt`

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
