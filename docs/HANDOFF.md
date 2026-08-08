# ClipCascade Stability Recovery Handoff

Last updated: 2026-08-08 (Asia/Tokyo)

## Canonical state

- Repo: `GoodLight999/Trial-and-Error-ClipCascade`
- `main` / immutable baseline: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Active branch: `stability-recovery`
- Draft PR: `#4`
- Latest artifact-verified product head: `30c179d9099f67afaa2b4e93a5164fd5ceae5808`
- Android run: `31228888126` — success
- Desktop run: `31228888150` — all jobs success
- Android APK artifact: `9013102582`
- Android build-log artifact: `9013101816`
- APK SHA-256: `1dfe70fa14be54ddb8f319c12b820e297dca8743ecbdc501f7a97c48d922c604`
- APK size: `93,543,179` bytes; 538 ZIP entries

Documentation-only commits follow the product head. If product code changes again, generate and inspect a new APK before replacing the evidence above.

Read next:

1. `docs/EXPERIMENT_LOG_2026-08-08_SHIZUKU_OFFICIAL_SOURCE_REAUDIT.md`
2. `docs/EXPERIMENT_LOG_2026-08-08_HONOR_ANDROID16_VENDOR_CLIPBOARD.md`
3. `docs/EXPERIMENT_LOG_2026-08-08_HONOR_FIX_VERIFICATION.md`
4. `docs/EXPERIMENT_LOG.md`
5. `docs/ANDROID_SETUP.md`

## Absolute constraints

- No reset, reconstruction, re-baseline, or replacement architecture.
- Keep `main` exactly at the baseline above.
- Reuse existing React Native/Notifee transport and Desktop application.
- Preserve STOMP destinations `/app/cliptext` and `/user/queue/cliptext`.
- Root must not be required.
- CI/build success is not real-device proof.
- Keep failed attempts in the experiment logs.
- Delete temporary write workflows/helpers after use.
- Never return to application-side hidden `IClipboard` reflection or guessed OEM Binder arguments.

## 2026-08-08 HONOR failure that triggered the current correction

Real device:

- HONOR DNP-NX9
- Android 16 / API 36
- ClipCascade 3.2.0 standalone

Diagnostic proved Binder, permission, UserService, UID 2000, Accessibility capability, runtime, and P2P connection were active. Clipboard read itself failed:

```text
Shizuku attempts: 3
Shizuku successes: 0
Emitted to JavaScript: 0
Last error: Unsupported IClipboard#getPrimaryClip signature: (java.lang.String, java.lang.String, int, int, java.lang.String)
```

The old implementation enumerated AOSP hidden `IClipboard#getPrimaryClip` signatures. HONOR exposes a vendor five-argument variant, so treating the AOSP hidden ABI as an OEM-stable interface was wrong.

## 2026-08-08 official-source re-audit and chronology

The supplied failure report was generated at `2026-08-08T08:21:04.855+09:00`.

The current framework-delegation correction commit `744f0efff09d245f0dd5b0a0ec4073e4d1c28e85` was committed at `08:27:43 JST`, about six minutes later. The final artifact-verified product head followed at `09:00:50 JST`.

Therefore that diagnostic is evidence for the **old hidden-Binder implementation**, not a failed execution of the current correction. Do not react to it by adding another speculative vendor-signature patch.

The current design was re-audited against only official Shizuku source/API documentation and AOSP Android 16 source. The audit confirmed:

- Shizuku v13 constructs a UserService `Context` for the calling app's Android user and may pass it through the Context constructor;
- UserService still runs with root/shell process identity and is not a normal Android app process, so each Context API must be checked against Android source;
- Android 16 ClipboardService verifies UID/package ownership using `AppOps.checkPackage(uid, callingPackage)`;
- Android 16 `com.android.shell` declares `READ_CLIPBOARD_IN_BACKGROUND`, which ClipboardService explicitly accepts for background clipboard access;
- framework `ClipboardManager` supplies its hidden Binder arguments from the device `Context`, so it remains the correct OEM compatibility boundary instead of application-side hidden-interface reflection;
- UserService behavior changes must continue to bump a dedicated implementation version while retaining the logical service tag.

No product-code change was made during that re-audit because no post-correction target-device failure has yet been observed. Full evidence is in `EXPERIMENT_LOG_2026-08-08_SHIZUKU_OFFICIAL_SOURCE_REAUDIT.md`.

## Current Shizuku clipboard implementation

The UserService no longer invokes hidden `IClipboard` itself.

```text
Shizuku v13 supplied Context
    -> verify requested Android user
    -> same-user `com.android.shell` package Context
    -> device framework ClipboardManager
    -> ClipboardManager.primaryClip
```

The device's own framework now owns private/vendor Binder arguments. The observed fifth HONOR `String` is not guessed.

A dedicated UserService implementation version is also used:

```text
USER_SERVICE_IMPLEMENTATION_VERSION = 4
.version(USER_SERVICE_IMPLEMENTATION_VERSION)
```

This is intentionally independent of app versionCode `30200`, so installing another 3.2.0 engineering APK does not silently reconnect to the old privileged implementation.

Regression tests require the framework path and forbid:

- `Class.forName` hidden clipboard reflection;
- `IClipboard$Stub`;
- `DEFAULT_DEVICE_ID` synthetic arguments;
- hidden-signature selection helpers;
- `.version(BuildConfig.VERSION_CODE)` for this UserService.

## Unified automatic capture remains unchanged

```text
ClipboardManager change signal
Accessibility exact ACTION_COPY
optional READ_LOGS
        -> BackgroundClipboardCapture
        -> Shizuku UserService first
        -> overlay fallback when unavailable/failed/non-text
        -> ClipboardListenerModule.emitExternalClipboard
        -> onClipboardChange
        -> existing StartForegroundService.js sender
```

The ordinary listener is trigger-only. Foreground and background automatic copies use the same coordinator.

Do not restore copy-text heuristics, arbitrary debounce windows, callback/hash suppression, manager scanning, manual `REQUEST_BINDER`, or a separate foreground payload path.

## Current verification evidence

Android run `31228888126` passed every aggregate gate:

- exact `npm ci`;
- full and production dependency policy;
- JavaScript syntax checks;
- ESLint zero warnings;
- Jest 11/11 suites, 61/61 tests;
- Android lint;
- JVM tests;
- standalone APK;
- exact embedded JS bundle;
- ZIP integrity;
- SHA-256;
- final aggregate gate.

Gradle: `BUILD SUCCESSFUL in 2m 57s`, 480 tasks.

Android lint: 0 errors / 18 reviewed warnings.

Independent downloaded-APK inspection confirmed:

Present:

- `com.android.shell`;
- new framework ClipboardManager reader marker;
- `clipcascade-clipboard-read-v3`;
- `/app/cliptext`;
- `/user/queue/cliptext`.

Absent from packaged DEX:

- `Unsupported IClipboard#getPrimaryClip signature`;
- `isAndroid14PlusSignature`;
- `findSupportedGetPrimaryClip`;
- `IClipboard$Stub`;
- `DEFAULT_DEVICE_ID`.

The exact code that generated the HONOR five-argument failure is therefore absent from this APK.

Desktop run `31228888150` also passed Ubuntu/Windows tests, Windows EXE build, and extracted Linux-package build/test.

## Dependency-audit exception — do not misreport this

On 2026-08-08 npm began reporting two high-severity `image-size` DoS advisories propagated through Metro. As of this recovery run there is no patched `image-size` release for those advisories. Do **not** say the dependency tree has zero high findings.

CI temporarily accepts only:

- `GHSA-w3rx-r6r6-pgpr`
- `GHSA-5p2g-fcmc-qvqq`

and only while the exact dependency shape remains Metro `0.82.5` -> `image-size` `1.2.1` (`^1.0.2`). The exception expires `2026-08-31`. Any other high/critical finding, changed dependency shape, changed direct advisory set, or expired waiver fails CI.

The first exception implementation failed on npm-audit's cyclic Metro propagation graph in run `31228471408`; that failure is retained in the detailed verification log. The corrected policy computes the exact `effects` closure from `image-size`.

Seven moderate `fast-xml-parser` findings also remain through React Native Community CLI 19. Do not force breaking CLI/React Native changes merely to silence audit output.

## Immediate next action — real HONOR test

Use **only** the APK from Android run `31228888126` / artifact `9013102582` (SHA above), not the 2026-08-04 APK and not the APK that produced the `08:21:04` diagnostic.

1. Install it over the old engineering build.
2. Open/refresh ClipCascade with Shizuku running; this must bind the new UserService implementation version.
3. Put a known non-empty text value on the clipboard.
4. Run **Shizuku読み取りをテスト**.
5. Capture the new diagnostic report.

Success criterion for this stage:

- `Shizuku successes` increments;
- the old `Unsupported IClipboard#getPrimaryClip signature` error is gone;
- the manual read reports success without exposing clipboard payload.

Only after that passes:

6. test a fresh foreground copy to Windows;
7. background ClipCascade and test real Accessibility `ACTION_COPY` delivery;
8. stop Shizuku and test overlay fallback separately;
9. test duplicate behavior/reconnect/battery over longer runtime.

If the manual read still fails, diagnose the **new exact error**. Do not invent another vendor Binder signature or argument.