# Experiment log — final static verification and packaged artifacts

Date: 2026-08-04 (Asia/Tokyo)

Branch: `stability-recovery`

Verified source head: `4a169516e2c5f87f3a4175a683ea7aff50b464c9`

Base invariant: `main` remains `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`.

This record closes the static-verification stage started in `EXPERIMENT_LOG_2026-08-03_STATIC_AUDIT_AND_UNIFIED_CAPTURE.md`. It does not claim real-device success.

## Permanent Android CI result

Workflow run: `30819520542`

Job: `91705653947` — success

Every enforced gate succeeded:

- exact `npm ci`;
- full dependency audit at high severity;
- production dependency audit at high severity;
- `node --check` for product JavaScript;
- ESLint with `--max-warnings=0`;
- Jest;
- Android lint;
- Android JVM tests;
- standalone APK build;
- embedded `assets/index.android.bundle` check;
- APK ZIP integrity;
- generated SHA-256 verification;
- build-log and APK artifact upload;
- aggregate verification gate.

Results:

- Jest: 11 suites / 61 tests passed;
- Gradle: `BUILD SUCCESSFUL in 3m 46s`;
- Gradle tasks: 480 executed;
- Android lint: 0 errors / 18 warnings;
- full and production audits: no high or critical findings;
- seven moderate `fast-xml-parser` findings remain through the React Native CLI 19 dependency graph; npm proposes CLI 20.2.0, a breaking major update, so it was not forced into this stability branch.

The 18 Android lint warnings were reviewed. They consist of guarded API-34 constant references, an available WorkManager update, resources used through runtime string names or inherited React Native build resources, bitmap placement/duplicate suggestions, and KTX style suggestions. They are not hidden; Android lint is not warning-free.

## Android artifact

Artifact ID: `8858357801`

Build-log artifact ID: `8858355884`

Artifact ZIP digest: `sha256:6dfc4eb329cb8409c9b746b2f27075a82a6ea276c44dae79c0227291751c645b`

File: `ClipCascade-Android-stability-standalone.apk`

- size: `93,543,179` bytes;
- SHA-256: `f0e6bee697d3304e6804ae3bab77369868144dd3ae869005b5fa806b6d720c4b`;
- APK entries: 538;
- ZIP integrity: passed;
- embedded checksum: matched;
- exact `assets/index.android.bundle`: present.

Independent binary Manifest decoding confirmed:

- package `com.clipcascade`;
- versionName `3.2.0`;
- versionCode `30200`;
- minSdk 26;
- targetSdk 35;
- `rikka.shizuku.ShizukuProvider`;
- authority `com.clipcascade.shizuku`;
- provider protected by `android.permission.INTERACT_ACROSS_USERS_FULL`;
- `ClipCascadeAccessibilityService` enabled and exported;
- Accessibility binding protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`;
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` absent;
- backup disabled.

Independent DEX/bundle marker inspection confirmed:

- sticky Shizuku Binder listener;
- `bindUserService`;
- `IShizukuClipboardService`;
- native pending-share wake marker `SHARED_EVENT_AVAILABLE`;
- application-owned clipboard marker;
- existing `/app/cliptext` and `/user/queue/cliptext` destinations;
- recovery repository marker.

Rejected product markers were absent from the packaged product implementation:

- manual `REQUEST_BINDER` action;
- `ClipboardEmissionGate`;
- `ignoreNextClipboardListenerEvent`.

A generic `removeAllListeners` token exists in the bundled JavaScript symbol table from the dependency/runtime vocabulary; source-contract tests and source inspection confirm that the rejected ClipCascade global-listener cleanup calls are absent.

## Permanent Desktop CI result

Workflow run: `30819520353`

All jobs succeeded:

- Ubuntu desktop tests: job `91705596002`;
- Windows desktop tests: job `91705596049`;
- Windows EXE build: job `91705767761`;
- extracted Linux package build/test: job `91705767804`.

### Windows artifact

Artifact ID: `8858276449`

Artifact ZIP digest: `sha256:505780e05977b3949017e179bd7d3412a24f7ca3dfe3211d399acf64947583da`

File: `ClipCascade-Windows-stability.exe`

- size: `57,490,611` bytes;
- SHA-256: `bf12b82fff45447d6a8c4d01d65b7bcb9f144176a711143a4529a3a0c673ce09`;
- format: PE32+ Windows GUI, x86-64;
- embedded checksum: matched.

### Linux artifact

Artifact ID: `8858226619`

Artifact ZIP digest: `sha256:5f14d4f01e71adaa24a8e27ada8f64f9e528ca982283bc1effea076bb3f56ab0`

File: `ClipCascade-Linux-stability.tar.gz`

- size: `61,873` bytes;
- SHA-256: `60187d87a11ce399fec366419264a808b9994f74bfe5631f807e6929693cc925`;
- gzip tar entries: 60;
- archive integrity: passed;
- embedded checksum: matched;
- no one-shot workflow, patch helper, dependency-trial file, or `tmp-test-ignore*` file was packaged.

## Static conclusion

The known speculative capture implementations found during this campaign were removed, and the current automatic foreground/background triggers use the same acquisition coordinator. Strict JavaScript static analysis, source-contract tests, Android lint/JVM/build/package gates, dependency high-severity gates, and Desktop package tests are green at the verified head.

This is the end of the current static-verification campaign, not proof of target-device operation.

## Remaining real-device acceptance

Still unproven:

- official/forked Shizuku Binder acquisition on the user's HONOR/MagicOS device;
- shell/root UserService clipboard access on that ROM;
- Accessibility `ACTION_COPY` coverage in each source application;
- overlay fallback focus behavior;
- foreground and background Android-to-Windows transmission;
- duplicate behavior under real overlapping triggers;
- reconnect, retry, ordering, tray, visible GUI, and battery behavior;
- Binder transaction liveness if the vendor implementation never returns.

No guessed Binder timeout was added. A real-device failure must be diagnosed from the payload-free setup report and exact preceding action.