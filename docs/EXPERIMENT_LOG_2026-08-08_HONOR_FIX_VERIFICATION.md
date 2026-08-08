# Experiment log — HONOR clipboard correction verification

Date: 2026-08-08 (Asia/Tokyo)

Branch: `stability-recovery`

Base invariant: `main` remains `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`.

This record continues `EXPERIMENT_LOG_2026-08-08_HONOR_ANDROID16_VENDOR_CLIPBOARD.md`. It preserves the build/test failures encountered after the source correction and the final package evidence. It still does **not** claim success on the HONOR device; the new APK must pass the manual Shizuku read there.

## Starting real-device failure

HONOR DNP-NX9, Android 16 / API 36, ClipCascade 3.2.0 reported:

```text
Shizuku Binder available: enabled
Shizuku permission: enabled
Shizuku UserService: enabled
Shizuku service UID: 2000
Shizuku attempts: 3
Shizuku successes: 0
Emitted to JavaScript: 0
Last error: Unsupported IClipboard#getPrimaryClip signature: (java.lang.String, java.lang.String, int, int, java.lang.String)
```

The previous application-side hidden `IClipboard` reflection therefore failed specifically on the HONOR vendor Binder ABI after Shizuku startup had already succeeded.

## Product correction retained

The corrected UserService no longer reflects or invokes hidden `IClipboard` directly.

```text
Shizuku v13 supplied Context
    -> same-user com.android.shell package Context
    -> device framework ClipboardManager
    -> ClipboardManager.primaryClip
```

The device framework now owns its private/vendor Binder arguments. No guessed fifth HONOR `String` was added.

The Shizuku UserService also has a dedicated implementation version (`4`) independent of application versionCode `30200`, preventing an already-running old privileged service from being silently reused after an engineering APK update.

## First CI after the correction — useful failure

Android run `31227720486` failed its aggregate gate even though Android compilation/package generation succeeded.

Concrete failures:

1. `npm audit` began reporting two new `image-size` high-severity denial-of-service advisories. They propagate through Metro/React Native. The available npm `--force` suggestion was a breaking React Native downgrade, so it was not applied.
2. `__tests__/App.test.tsx` still asserted that the old direct AOSP hidden-signature helper `isAndroid14PlusSignature` must exist. That test was stale and contradicted the newly accepted OEM-compatible design.

Evidence from the run:

- ESLint: success;
- Jest: 10 suites passed / 1 failed, 60 tests passed / 1 failed;
- Android Gradle: `BUILD SUCCESSFUL in 3m 49s`;
- 480 actionable tasks;
- package verification: success;
- aggregate: failure because full/prod audit and Jest were red.

The stale test was replaced with contracts requiring device-framework `ClipboardManager`, shell package Context, dedicated UserService implementation version, and absence of direct hidden Binder reflection.

## `image-size` advisory policy

As of 2026-08-08 the two relevant advisories have no patched npm release. ClipCascade does not globally ignore high-severity audit results.

A narrow temporary CI policy was added:

- exact accepted advisories only:
  - `GHSA-w3rx-r6r6-pgpr`;
  - `GHSA-5p2g-fcmc-qvqq`;
- exact dependency shape only:
  - Metro `0.82.5`;
  - `image-size` `1.2.1`;
  - Metro dependency range `^1.0.2`;
- waiver expires `2026-08-31`;
- any different high/critical advisory fails;
- any changed Metro/image-size dependency shape fails;
- raw `npm audit --json` output is retained in CI artifacts.

### Failed first implementation of the policy

Run `31228471408` exposed a bug in the first audit-policy script.

The npm audit graph contains cycles among `metro`, `metro-config`, and `metro-transform-worker`. The first implementation recursively followed `via` edges and treated a node already being evaluated as an unknown vulnerability. It therefore rejected propagated packages even though all high nodes came from the two accepted `image-size` advisories.

All non-audit product gates in that run succeeded, including 11/11 Jest suites, 61/61 tests, Gradle, and APK verification, but the aggregate correctly remained red because the audit policy itself was not trustworthy yet.

The policy was corrected to:

1. verify the exact two direct `image-size` GHSA records;
2. build the transitive `effects` closure starting at `image-size`;
3. allow high/critical nodes only inside that exact closure;
4. reject any direct advisory object other than the two accepted GHSA records;
5. retain dependency-shape and expiry checks.

This handles npm's cyclic propagation graph without weakening the high/critical gate outside the exact known issue.

## Final product-code CI

Product head:

`30c179d9099f67afaa2b4e93a5164fd5ceae5808`

Android run:

`31228888126` — **success**

Job:

`93028502519` — **success**

Every aggregate gate succeeded:

- exact `npm ci`;
- full audit policy;
- production audit policy;
- JavaScript syntax checks;
- ESLint zero-warning gate;
- Jest;
- Android lint;
- Android JVM tests;
- standalone APK build;
- embedded JS bundle check;
- ZIP integrity;
- SHA-256 check;
- log/APK artifact uploads;
- final aggregate gate.

Results:

- Jest: 11 suites / 61 tests passed;
- Gradle: `BUILD SUCCESSFUL in 2m 57s`;
- Gradle tasks: 480 executed;
- Android lint: 0 errors / 18 reviewed warnings;
- full audit: only the exact two waived `image-size` advisories and their npm-audit effects closure were accepted;
- production audit: same exact policy.

## Final Android artifact

Artifact ID:

`9013102582`

Build-log artifact ID:

`9013101816`

Artifact ZIP digest:

`sha256:fb1b70f3017a369e52a1d8b351b41e8bcd50cebc3b6fd75229ad1aefcdcd7ad7`

APK:

- file: `ClipCascade-Android-stability-standalone.apk`;
- size: `93,543,179` bytes;
- SHA-256: `1dfe70fa14be54ddb8f319c12b820e297dca8743ecbdc501f7a97c48d922c604`;
- APK entries: 538;
- ZIP integrity: passed;
- embedded `assets/index.android.bundle`: present;
- generated `SHA256SUMS.txt`: matched.

## Independent APK inspection

The downloaded APK was independently extracted and searched after CI.

Expected packaged markers present:

- `com.android.shell` in DEX;
- `Framework ClipboardManager is unavailable` in DEX, proving the new framework reader implementation is packaged;
- `clipcascade-clipboard-read-v3` UserService tag in DEX;
- `/app/cliptext` in the JavaScript bundle;
- `/user/queue/cliptext` in the JavaScript bundle.

Rejected/old markers absent from all packaged DEX files:

- `Unsupported IClipboard#getPrimaryClip signature`;
- `isAndroid14PlusSignature`;
- `findSupportedGetPrimaryClip`;
- `IClipboard$Stub`;
- `DEFAULT_DEVICE_ID`.

Therefore the exact implementation that produced the HONOR five-argument error is not present in this APK.

## Desktop regression status

Desktop run `31228888150` for the same product head succeeded:

- Ubuntu tests: success;
- Windows tests: success;
- Windows EXE build: success;
- extracted Linux package build/test: success.

No Desktop product code was intentionally changed for the HONOR clipboard correction.

## Remaining runtime boundary

The correction is now source-tested, CI-green, packaged, and independently inspected. It is **not yet HONOR-runtime-proven**.

Next acceptance on the same HONOR DNP-NX9:

1. install the APK from Android run `31228888126` over the previous engineering APK;
2. open/refresh ClipCascade with Shizuku running so the dedicated UserService version replaces the stale service;
3. run **Shizuku読み取りをテスト**;
4. require `Shizuku successes` to increment;
5. require the old unsupported-signature error to disappear;
6. if the manual read succeeds, test a real foreground copy to Windows;
7. then background ClipCascade and test Accessibility ACTION_COPY -> Shizuku -> existing sender;
8. finally stop Shizuku and test overlay fallback separately.

If step 3 fails, preserve the exact new diagnostic. Do not guess a vendor Binder argument or return to direct hidden `IClipboard` reflection.
