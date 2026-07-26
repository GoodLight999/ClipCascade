# Experiment Log — 2026-07-27 Shizuku Build Verification

This is an append-only companion to:

- `docs/EXPERIMENT_LOG.md`
- `docs/EXPERIMENT_LOG_2026-07-27_SHIZUKU.md`

## Experiment SHIZUKU-BUILD-001

### Goal

Compile the real Shizuku UserService implementation, run the existing app-scoped Android tests, package a Metro-independent APK, and independently verify the resulting artifact.

## Attempt 1 — replaced run `30211379415`

Product head: `2178c5e8fa5f2c2e2c0ccaacfb81d9d35ea67298`

The run was cancelled by the workflow concurrency group when a later handoff/log commit updated the same PR. Its build log artifact was preserved and inspected.

The partial run had already reached Kotlin compilation and exposed a concrete error:

- `IShizukuClipboardService` was unresolved;
- all dependent AIDL stub methods were therefore unresolved;
- the current Android Gradle Plugin had not generated the AIDL source.

Root cause:

- AIDL generation was not enabled in the app module.

Correction:

- add only `buildFeatures { aidl true }` to `ClipCascade_Mobile/src/android/app/build.gradle`;
- do not replace AIDL with hand-written Binder code;
- do not abandon the official Shizuku UserService pattern.

The cancelled run's build-log artifact ID is `8634584071`.

## Attempt 2 — workflow run `30211592338`

Product head: `6dc93e08b10db5f3f138cce6b6d867548f9f89c4`

Result: passed.

Verified workflow steps:

1. source checkout;
2. Node and Java setup;
3. JavaScript dependency installation;
4. React Native app code generation;
5. app-scoped debug unit-test task;
6. AIDL generation;
7. Kotlin/Android resource compilation;
8. official Shizuku API/provider dependency integration;
9. standalone APK build;
10. exact `assets/index.android.bundle` presence;
11. APK ZIP integrity;
12. build-log and APK artifact upload.

## Artifact evidence

- APK artifact ID: `8634676818`
- Build-log artifact ID: `8634676151`
- File in artifact: `ClipCascade-Android-stability-standalone.apk`
- User-facing copied file: `ClipCascade-Android-stability-shizuku.apk`
- Size: `93,585,807` bytes
- SHA-256: `d28da7716e5069ab2ae63926bc0e8b6495adaacdead69ec4590dea69bedf274b`
- Exact `assets/index.android.bundle`: present
- APK ZIP integrity: passed
- APK entry count: `538`
- Identification: Android package with Gradle app metadata
- Signing/status: debug-signed engineering artifact, not a production release

The artifact was downloaded after Actions completion and independently checked outside the workflow. The recalculated SHA-256 matched the checksum embedded in the artifact.

## Desktop regression gate on the same product head

Desktop workflow `30211592333` also passed on head `6dc93e08b10db5f3f138cce6b6d867548f9f89c4`:

- Ubuntu syntax checks and tests;
- Windows syntax checks and tests;
- Windows EXE build;
- Linux package build.

This confirms the Shizuku source changes did not break the selectively recovered desktop line at build/test level.

## Conclusion

SHIZUKU-BUILD-001 passed.

The following claims are now valid:

- the official Shizuku dependencies resolve;
- AIDL generation works;
- the UserService and app-process bridge compile;
- the Shizuku-first text-read path is connected to the existing React Native send event at source level;
- the guided setup/status UI compiles;
- a standalone APK containing the JavaScript bundle is available.

The following claims remain invalid until real-device evidence exists:

- Shizuku binds successfully on the user's Android device;
- the hidden clipboard Binder call succeeds on that device and Android vendor build;
- the returned service UID is the expected shell identity;
- background copies are detected reliably;
- overlay fallback works after Shizuku stop/denial;
- Amazon, launcher, browser, selection, and search-field behavior is unaffected;
- duplicates and battery use are acceptable;
- Shizuku alone provides clipboard-change monitoring without Accessibility or READ_LOGS triggers.

## Exact next evidence

1. Install `ClipCascade-Android-stability-shizuku.apk`.
2. Start Shizuku using its normal wireless-debugging or computer-assisted flow.
3. Long-press ClipCascade and open `バックグラウンド設定`.
4. Confirm Shizuku installed/running, grant ClipCascade permission, and wait for UserService connection.
5. Confirm the reported UserService UID is a shell UID rather than the ClipCascade app UID.
6. Copy text and run the privacy-safe Shizuku read test; content must not be displayed.
7. Start the existing ClipCascade foreground service and test background text copies through the conservative Accessibility trigger.
8. Stop Shizuku and repeat to confirm automatic overlay fallback.
9. Test Amazon, launcher drawer, browser, search fields, and selection toolbar for focus/input regressions.
10. Record duplicate sends, missed sends, wakeups, and battery behavior before deciding whether to add a Shizuku-side clipboard-change listener.
