# ClipCascade Stability Recovery Handoff

Last updated: 2026-08-08 (Asia/Tokyo)

## Read first

1. `docs/HANDOFF.md`
2. `docs/EXPERIMENT_LOG.md`
3. `docs/EXPERIMENT_LOG_2026-08-08_HONOR_ANDROID16_VENDOR_CLIPBOARD.md`
4. `docs/EXPERIMENT_LOG_2026-08-03_STATIC_AUDIT_AND_UNIFIED_CAPTURE.md`
5. `docs/EXPERIMENT_LOG_2026-08-04_FINAL_STATIC_VERIFICATION.md`
6. `docs/ANDROID_SETUP.md`
7. Draft PR `#4` and its latest Actions runs

Older detailed records remain authoritative history for their individual experiments.

## Canonical repository state

- Repository: `GoodLight999/Trial-and-Error-ClipCascade`
- Baseline and `main`: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Active branch: `stability-recovery`
- Draft PR: `#4`
- Previous artifact-verified product head: `4a169516e2c5f87f3a4175a683ea7aff50b464c9`
- HONOR vendor-ABI correction: `744f0efff09d245f0dd5b0a0ec4073e4d1c28e85`
- Regression contract: `7af159555a61653953ed26817500d53b94c0a23e`

The 2026-08-04 APK is **not** the target for the next HONOR Shizuku test. It contains the now-proven-incompatible direct hidden-`IClipboard` implementation. Generate/inspect a new APK containing `7af159555...` or later before retesting.

## Latest real-device result — 2026-08-08

Target:

- HONOR DNP-NX9;
- Android 16 / API 36;
- ClipCascade 3.2.0 standalone.

The diagnostic proved the following stages work on that device/build:

- Shizuku Binder available;
- Shizuku permission granted;
- Shizuku UserService running;
- UserService UID 2000;
- Accessibility ACTION_COPY trigger enabled;
- overlay fallback capability enabled;
- ClipCascade runtime active;
- P2P connection connected.

The blocking failure was exact and later than all of those stages:

```text
Unsupported IClipboard#getPrimaryClip signature: (java.lang.String, java.lang.String, int, int, java.lang.String)
```

Counters showed 3 Shizuku attempts, 0 Shizuku successes, and 0 emissions to JavaScript.

Current AOSP `IClipboard#getPrimaryClip` uses four parameters. HONOR exposes a vendor-extended five-parameter hidden interface. The prior implementation incorrectly treated enumerated AOSP hidden Binder signatures as a portable OEM boundary.

## Absolute constraints

- Do not reset, reconstruct, re-baseline, clean rebuild, or replace the architecture.
- Keep `main` exactly aligned with the upstream baseline above.
- Extend the existing React Native/Notifee foreground-service transport and existing Desktop application.
- Preserve STOMP destinations:
  - publish `/app/cliptext`
  - subscribe `/user/queue/cliptext`
- Root must not be required.
- CI success is not real-device proof.
- Record failed attempts and corrections; do not silently rewrite history.
- Remove every temporary write workflow, transformation helper, patch, and scratch file after use.
- Do not restore closed-PR scaffolding or reintroduce a parallel capture/send backend.
- Do not directly reflect/invoke hidden `IClipboard` from the Shizuku UserService again.
- Do not guess OEM Binder arguments from parameter types.

## Current Android automatic-capture architecture

Foreground and background automatic copies intentionally exercise the same acquisition coordinator.

```text
ClipboardManager change notification
Accessibility exact ACTION_COPY
optional READ_LOGS trigger
        ↓
BackgroundClipboardCapture
        ↓
Shizuku UserService first
        ↓ unavailable / denied / failed / non-text
overlay fallback
        ↓
ClipboardListenerModule.emitExternalClipboard
        ↓
onClipboardChange
        ↓
existing StartForegroundService.js sender
```

The ordinary listener is trigger-only. It does not read the clipboard payload directly.

Explicit Android Sharesheet / PROCESS_TEXT inputs remain direct user-provided content, but delivery is protected by a native pending-share queue until JavaScript transport listeners are ready.

## Current Shizuku clipboard implementation

Retained official/API-backed lifecycle:

- manifest provider `rikka.shizuku.ShizukuProvider`;
- sticky Binder-received listener;
- Binder-dead and permission-result listeners;
- official permission request;
- `Shizuku.bindUserService` and explicit unbind;
- Shizuku API/provider 13.1.5;
- read-only text UserService;
- Android user ID checks;
- shell/root UserService UID validation.

### OEM-compatible clipboard boundary

The UserService no longer calls hidden `android.content.IClipboard` itself.

Current flow:

```text
Shizuku v13 supplied Context
    -> verify requested Android user
    -> create same-user `com.android.shell` package Context
    -> Context.getSystemService(ClipboardManager)
    -> ClipboardManager.primaryClip
```

Why:

- Shizuku started through ADB runs UserService as shell UID 2000;
- the platform shell package is the matching package identity;
- the device framework `ClipboardManager` is compiled against that device's own clipboard Binder ABI;
- therefore HONOR/vendor-specific hidden arguments are supplied by HONOR's framework rather than invented by ClipCascade.

The source-contract test now forbids in this UserService path:

- `Class.forName` hidden Binder reflection;
- `IClipboard$Stub`;
- hard-coded `DEFAULT_DEVICE_ID` argument construction;
- overload selection by signature;
- synthesized `argumentsFor(...)` logic.

### Rejected Shizuku designs

Do not restore:

- manual `rikka.shizuku.intent.action.REQUEST_BINDER` broadcast;
- manager package/launcher/label/fork-name scanning;
- official-manager hard-coding;
- Shizuku download URL in the product;
- direct hidden `IClipboard` reflection;
- exact AOSP hidden-overload enumeration as an OEM compatibility strategy;
- largest-overload selection;
- guessed fifth HONOR `String` argument (`null`, empty string, package name, shell name, or anything else without vendor contract evidence).

## Accessibility implementation

Accessibility is trigger-only.

- accepted event: exact `AccessibilityNodeInfo.ACTION_COPY`;
- no translated “copied” dictionary;
- no button-label, toast, notification, or content-description inference;
- no generic click or selection trigger;
- no arbitrary window-content retrieval;
- `canRetrieveWindowContent=false`;
- service exported so Android can bind it;
- binding protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`.

Coverage still depends on whether the source application emits ACTION_COPY.

## Trigger coalescing and app-owned writes

### Causal coalescing

There is no guessed debounce duration.

- triggers use `SystemClock.elapsedRealtimeNanos()`;
- a pending trigger timestamp at or before the completed read is already represented and is discarded;
- a trigger after completion is a new request;
- identical content can be copied again as a new action.

### App-owned clipboard marker

ClipCascade writes set an explicit `ClipDescription.extras` marker. The automatic listener reads only description metadata and ignores marked writes.

Removed suppression mechanisms:

- callback counters;
- delay windows;
- one-shot image flags;
- short-window native hash gate;
- obsolete `ClipboardEmissionGate`.

## Android share-intent delivery

`PendingShareStore` is a process-local bounded queue.

- maximum 64 events;
- oldest event discarded at the bound;
- one `SHARED_EVENT_AVAILABLE` wake event;
- atomic JavaScript drain after transport initialization;
- generation-owned subscription;
- JVM tests cover ordering, bound, and empty-after-drain.

It is not a durable cross-process queue.

## Foreground-service lifecycle

The foreground service uses monotonically increasing generations.

- each instance owns its listeners and transport cleanup;
- superseded poll loops exit;
- old instances cannot remove current listeners or stop the current Notifee service;
- poll-loop rejection enters local cleanup;
- rejected global `DeviceEventEmitter.removeAllListeners` cleanup is absent from ClipCascade source.

## Existing transport reliability

P2S text keeps the existing durable bounded FIFO outbox:

- existing validation/encryption before persistence;
- scope includes server/account/encryption fingerprint;
- one in-flight head;
- matching server echo acknowledges/removes the head;
- disconnect/error/shutdown/missing echo release it;
- restart converts persisted `inflight` to `queued`;
- retry sequence `30s → 60s → 120s → 240s → 480s → 600s`;
- maximum 10 minutes with default ±20% jitter;
- persisted `nextAttemptAt` and UI countdown;
- own queued echo does not roll Android clipboard backward.

P2P, images, and files do not use the P2S text outbox.

## Other concrete static fixes retained

- strict JavaScript syntax and ESLint gate;
- undeclared variables and wrong-password-variable defect fixed;
- erroneous assignment expression in `clearFiles` fixed;
- fetch abort timer cleared;
- render-time notification permission moved to initialization and limited to API 33+;
- native AsyncStorage bridge no longer closes the process-wide database;
- native AsyncStorage strings use `JSONObject.quote` / `JSONTokener`;
- application version corrected to `3.2.0`, APK versionCode `30200`;
- direct battery-allowlist request permission removed;
- backup/device-transfer rules exclude application state;
- old storage permissions removed;
- high-contrast light/dark palettes;
- server-supplied DONATE link and upstream product-navigation links removed;
- visible Windows Tk status GUI retained with tray integration.

## Dependency state

- React Native: `0.80.2` retained;
- React Native Community CLI: `19.1.2`;
- unused `@react-native-clipboard/clipboard` removed;
- nonbreaking lockfile fixes applied without `--force`;
- full audit: no high or critical findings at last verification;
- production audit: no high or critical findings at last verification;
- seven moderate `fast-xml-parser` findings remained through CLI 19;
- npm proposed CLI 20.2.0, a breaking major update, so it was not forced into this stability branch.

## Permanent CI gates

Android:

- exact `npm ci`;
- full and production dependency audits at high severity;
- `node --check`;
- ESLint with zero warnings;
- Jest;
- Android lint;
- Android JVM tests;
- standalone APK;
- embedded JS bundle;
- ZIP integrity and SHA-256;
- complete reports/log artifacts;
- aggregate gate.

Desktop:

- Ubuntu and Windows compile/tests;
- Windows EXE and SHA-256;
- extracted Linux package compile/tests;
- archive integrity and SHA-256.

Permanent workflows have read-only repository permissions. Temporary write workflows/helpers must remain absent.

## Previous artifact evidence — historical only for Android Shizuku

The 2026-08-04 product artifact remains useful as static-history evidence but **must not be used to retest the HONOR clipboard bug** because it predates `744f0eff...`.

Previous Android:

- run `30819520542`;
- artifact `8858357801`;
- SHA-256 `f0e6bee697d3304e6804ae3bab77369868144dd3ae869005b5fa806b6d720c4b`;
- 11 Jest suites / 61 tests;
- Android lint 0 errors / 18 reviewed warnings;
- standalone APK/package gates passed.

Desktop product code was not changed by the 2026-08-08 HONOR correction. Previous verified artifacts remain:

Windows:

- run `30819520353`;
- artifact `8858276449`;
- SHA-256 `bf12b82fff45447d6a8c4d01d65b7bcb9f144176a711143a4529a3a0c673ce09`.

Linux:

- run `30819520353`;
- artifact `8858226619`;
- SHA-256 `60187d87a11ce399fec366419264a808b9994f74bfe5631f807e6929693cc925`.

## Failed attempts that must remain rejected

- manual Binder reprobe/manager scanning product head `3ea70722...`;
- independent foreground payload-read path;
- Accessibility text/label/toast heuristics;
- direct hidden `IClipboard` reflection as an OEM-stable API;
- explicit AOSP hidden-signature enumeration as an OEM compatibility layer;
- largest-overload hidden API invocation;
- synthetic unknown Binder arguments;
- short-window duplicate gates;
- callback-count suppression;
- global event-listener cleanup;
- unbounded old foreground-service polling;
- React-context-only share delivery;
- forced CLI 20 upgrade;
- old gray UI, upstream links, and tray-only Windows result.

## Immediate next acceptance sequence

1. Let permanent CI compile/test/package `7af159555...` or later.
2. Download and independently inspect the new standalone APK.
3. Install that APK on HONOR DNP-NX9.
4. With Shizuku running and permission granted, run the manual Shizuku read test.
5. Require `Shizuku successes` to increment and the five-argument unsupported-signature error to disappear before calling the correction successful.
6. Copy fresh text in foreground and confirm Android-to-Windows delivery.
7. Background ClipCascade and test actual ACTION_COPY-triggered delivery.
8. Disable/stop Shizuku and test overlay fallback separately.
9. Record exact diagnostics and preceding action for any failure.

## Remaining real-device acceptance

Still unproved after the 2026-08-08 source correction:

1. Framework `ClipboardManager.primaryClip` succeeds from this Shizuku shell UserService Context on HONOR/MagicOS.
2. ACTION_COPY coverage in each source application.
3. Overlay focus behavior.
4. Foreground and background Android-to-Windows delivery.
5. Duplicate behavior under overlapping real triggers.
6. Reconnect, retry, ordering, visible GUI, tray, and battery behavior.
7. Binder/framework liveness if a vendor transaction never returns.

No guessed Binder timeout or guessed vendor argument was added. Use the payload-free setup diagnostic and record the exact preceding action for every failure.
