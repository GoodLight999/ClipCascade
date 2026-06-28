# Latest Green Artifacts — 2026-06-28

This file records the final matching Android and Windows artifacts prepared for isolated background-outbound validation.

## Source and CI

- source commit: `f4e9945e8e69aba7b1a7d8bfd554f7bbf6ceab0e`
- PR #1 state at inspection: open and Draft
- Android CI run: `28316868414` — success
- Windows CI run: `28316868423` — success

## Android

- application: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.5-standalone`
- versionCode: `320109`
- artifact ID: `7932913870`
- artifact ZIP SHA-256: `8e6e861eb98417122fef0c9a713c5171152d6ab33d5ec90ce85eaef7a686033a`
- extracted APK SHA-256: `c2b80e0cd63dbb72ce8d595c51238e2e15aa8a812e733367f44f1caabdc0f934`
- expected signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

Android CI passed source transforms, JavaScript bundle generation, unit tests, Android resource/Kotlin compilation, APK assembly, embedded-bundle verification, and stable-signer verification.

## Windows

- artifact ID: `7932907208`
- artifact ZIP SHA-256: `63ac365c1907438e469b7b741e3752533e8e2c4ac03834cdf2f3b5acc148b801`
- extracted EXE SHA-256: `a0ef1c1f39d02d36acbc153434614d8304f1d6cac4f519ca504a60552299e810`

Windows CI passed authenticated HTTP tests, existing P2P peer-acknowledgement tests, shutdown/status tests, compilation, and PyInstaller packaging.

## Test isolation

Before Android outbound validation, disable Microsoft Phone Link clipboard synchronization and every other clipboard synchronizer. A successful test must be attributed using the native queue, exact peer clipboard application, and the defined acknowledgement—not clipboard appearance alone.

Keep PR #1 Draft until the real target-device matrix passes.
