# Android Standalone Packaging Experiment Log — 2026-07-26

This is an append-only experiment record for Android artifact packaging. It supplements `docs/EXPERIMENT_LOG.md` and `docs/EXPERIMENT_LOG_2026-07-26_ANDROID_ACQUISITION.md`.

## User-installed debug APK could not load JavaScript

- Baseline product-code head: `01394199be3e40160dcd592e8d0e5ee6a85722d1`
- Baseline workflow: `30202889590`
- Baseline Android artifact: `8632223256`
- Environment/device: physical Android device, APK installed without Metro, USB development connection, or `adb reverse`
- Initial assumption: a CI-generated debug APK was adequate for direct engineering-device installation.
- User-observed result:

```text
Unable to load script.

Make sure you're running Metro or that your bundle 'index.android.bundle' is packaged correctly for release.
```

- Root cause:
  - the workflow ran `:app:assembleDebug`;
  - React Native treats `debug` as a debuggable variant and intentionally skips JavaScript bundling;
  - the APK therefore expected Metro at runtime;
  - prior CI checked APK structure and checksum but did not check for `assets/index.android.bundle`.
- Classification: packaging/test-delivery defect. The APK was structurally valid but was the wrong artifact class for standalone device testing.
- Decision:
  - do not ask the user to configure Metro or `adb reverse` for ordinary artifact testing;
  - retain `debug` for deliberate Metro development only;
  - create a separate standalone engineering variant with release runtime semantics and debug signing;
  - require CI to inspect the final APK for the packaged JavaScript bundle.

## Implement standalone bundled variant

- Hypothesis: a non-debuggable `standalone` build type that inherits release runtime behavior, while using the debug signing key, will package the Hermes bundle and remain installable as a non-production engineering artifact.
- Product changes:
  - explicitly set React Native `debuggableVariants = ["debug"]`;
  - added Android build type `standalone`;
  - `standalone` uses `initWith release`;
  - `standalone` overrides signing with `signingConfigs.debug`;
  - minification remains disabled;
  - dependency matching falls back to `release` then `debug`.
- Pipeline changes:
  - replaced `:app:assembleDebug` with `:app:assembleStandalone`;
  - renamed the Android job and artifact as standalone;
  - added a hard check for `assets/index.android.bundle`;
  - added APK ZIP integrity verification before upload.
- Implementation commits:
  - `f6eb5b61931b2e51ea2243ecbfac2177e4e17e22` — standalone Android build type;
  - `a94b830fb954d09fc742b39833cebd5915988566` — standalone build and bundle verification in CI.

## Verification

- Workflow run: `30203690726`
- Product-code/pipeline head: `a94b830fb954d09fc742b39833cebd5915988566`
- Job results:
  - desktop unit tests Ubuntu: success;
  - desktop unit tests Windows: success;
  - Android tests and standalone APK: success;
  - Windows standalone EXE: success;
  - Linux source package: success.
- Android verification step `Verify packaged JavaScript and stage APK`: success.
- Android artifact: `8632482778`
- Android build-log artifact: `8632482104`
- Inner APK: `ClipCascade-Android-clean-rebuild-standalone.apk`
- Inner APK size: `93,574,655` bytes
- Inner APK SHA-256: `ca7ee41f95f729879a5298bdc2b6e413f0f2086cbc922205e06468d7878f471b`
- GitHub artifact ZIP digest: `sha256:d147a36c51a96282253ce4552653be33ceda2920aa5fe0103befd8c939c582cd`
- Independent post-download checks:
  - embedded SHA-256: passed;
  - Android APK identification: passed;
  - APK ZIP integrity: passed;
  - exact APK entry `assets/index.android.bundle`: present.

Same-head companion artifacts:

- Windows artifact `8632434975`:
  - inner EXE size `57,456,724` bytes;
  - SHA-256 `36da95c51e473b8bf33677f81142ea70bdfcd81a968e24e8a0be4878e11ce103`;
  - embedded checksum and PE32+ x86-64 GUI identification passed.
- Linux artifact `8632420551`:
  - inner tarball size `68,791` bytes;
  - SHA-256 `6fadbb46751fe0e714f85d9118c69c2ecc7ea14c0d8a76b2d85c2eed0114ba6b`;
  - embedded checksum, gzip identification, and 53-entry enumeration passed.

## Result and evidence boundary

- Proven automatically:
  - 46 Android app JVM tests still pass;
  - standalone Android native code compiles;
  - React Native/Hermes bundle is generated;
  - the JavaScript bundle is packaged inside the APK;
  - the APK archive is structurally valid;
  - all five artifact jobs succeed from one head.
- Not yet proven:
  - the new standalone APK launches successfully on the user's physical device;
  - the native → React Native self-test reports `PASSED` on device;
  - real clipboard capture works in foreground or background;
  - server delivery or remote clipboard application works;
  - Accessibility, Shizuku, ADB-assisted capture, durable queueing, or power behavior.

## Follow-up

1. Replace the previously installed debug APK with the standalone APK.
2. Launch without Metro, USB, or `adb reverse` and verify the main React Native screen loads.
3. Start the service.
4. Open the launcher long-press `Capture status` shortcut.
5. Run the payload-free native → React Native self-test.
6. Continue real foreground/background acquisition tests only after standalone launch succeeds.

The debug APK from workflow `30202889590` is superseded for ordinary installation and must not be redistributed as the current Android test artifact.
