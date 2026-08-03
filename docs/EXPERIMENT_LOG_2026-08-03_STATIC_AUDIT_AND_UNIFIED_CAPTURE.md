# Experiment log — unified capture and static audit campaign

Date: 2026-08-03 (Asia/Tokyo)

Branch: `stability-recovery`

Base invariant: `main` remains the upstream-aligned baseline `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`.

## User correction that started this campaign

The previous implementation had restored the foreground `ClipboardManager` path independently from the background Shizuku/overlay path. This allowed foreground copying to appear healthy while the background acquisition mechanism remained broken.

The user correctly required that foreground and background automatic copies exercise the same acquisition mechanism. The direct foreground payload read was therefore removed.

Current automatic flow:

```text
ClipboardManager change notification
Accessibility exact ACTION_COPY
optional READ_LOGS trigger
        ↓
BackgroundClipboardCapture
        ↓
Shizuku UserService
        ↓ unavailable / denied / failed / non-text
overlay fallback
        ↓
onClipboardChange
        ↓
existing StartForegroundService.js transport
```

## Unsupported or speculative capture implementations removed

The repository was searched repeatedly for old implementations and markers. Removed or rejected:

- foreground-only `primaryClip` payload read and direct emission;
- manual `rikka.shizuku.intent.action.REQUEST_BINDER` broadcast;
- package-name, launcher-label, product-name, or fork-name manager discovery;
- hard-coded official Shizuku download URL;
- Accessibility inference from translated “copied” phrases, button text, content descriptions, toasts, or notifications;
- generic click and generic selection triggers;
- arbitrary delays and duplicate windows such as 250/600/750/1000 ms;
- native short-window hash gate and obsolete `ClipboardEmissionGate`;
- callback-count-based suppression for ClipCascade's own clipboard writes;
- `block_image_once` and event-order-dependent image resend suppression;
- reliance on the previous clipboard hash to suppress received text;
- `DeviceEventEmitter.removeAllListeners` for shared-content and clipboard lifecycle;
- a foreground-service polling loop that continued after a newer service generation replaced it.

## Official/API-backed implementations retained

### Shizuku

- explicit `rikka.shizuku.ShizukuProvider`;
- sticky Binder-received listener;
- Binder-dead listener;
- permission-result listener and official request;
- `Shizuku.bindUserService` / `unbindUserService`;
- binding-death, null-binding, and disconnect handling;
- explicit AOSP `IClipboard#getPrimaryClip` signatures only;
- client Android user ID from the AOSP UID/user relation;
- default device ID as the inlined AOSP integer value `0` for the Android 14+ signature;
- unknown signatures and unsupported UserService UIDs fail closed.

### Accessibility

- `AccessibilityEvent.action == AccessibilityNodeInfo.ACTION_COPY` only;
- no window-content retrieval;
- no copy-text dictionary;
- service exported so the Android system can bind it;
- binding protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`.

### Overlay

- one-pixel transparent overlay;
- global-layout listener registered before `addView`;
- coordinator ownership retained until the actual clipboard read and activity teardown;
- API 34+ transition suppression through `overrideActivityTransition`, legacy fallback only below API 34.

## Causal trigger coalescing

The original pending-trigger implementation merely reran one latest trigger after a read. With listener and Accessibility reporting the same copy, this could launch a second overlay sequentially.

The replacement records each trigger with `SystemClock.elapsedRealtimeNanos()` and compares it with the actual Shizuku/overlay read-completion timestamp:

- a pending trigger at or before completion was already represented by that read and is discarded;
- a trigger after completion is a new request and is processed;
- no guessed debounce duration is used;
- identical content can still be copied again as a new action.

A first version used millisecond elapsed time. It was changed to nanoseconds to avoid ambiguity when a new copy occurred in the same millisecond as the prior completion.

## Application-owned clipboard marker

Every clipboard write owned by ClipCascade now sets an explicit `ClipDescription.extras` marker:

- received P2S text/image;
- received P2P text/image;
- Android content shared into ClipCascade;
- setup commands copied by the native setup screen.

The automatic listener checks metadata only and ignores marked writes. The listener no longer depends on a counter, hash timing, delay, or “block once” state.

## Foreground-service generation lifecycle

A concrete restart race was found:

- old foreground-service instances retained their `pollFlagsLoop` indefinitely;
- old failure cleanup could remove the current instance's listeners;
- a rejected polling Promise could become unhandled.

The current service lifecycle:

- assigns a monotonically increasing service/listener generation;
- subscriptions belong to the instance that created them;
- only the active generation stops native monitoring or Notifee foreground service;
- a superseded poll loop exits on generation mismatch;
- old instances clean only their own P2S/P2P transport and subscriptions;
- poll-loop rejection enters generation-local cleanup.

## Android share-intent delivery

Two successive loss modes were found.

### React context not yet created

The original `MainActivity` dropped a shared text/image/file when `currentReactContext` was null. A first repair retained events until React context creation.

### React context exists but JS listener is not registered

The first repair could still emit before `StartForegroundService.js` registered the listener. The final design uses:

- process-local bounded `PendingShareStore`;
- maximum 64 events;
- oldest event discarded with a warning at the bound;
- a single `SHARED_EVENT_AVAILABLE` wake signal;
- JavaScript atomic drain after transport initialization;
- generation-owned wake subscription;
- JVM tests for ordering, bound, and empty-after-drain.

This is not a durable cross-process queue and is documented as such.

## JavaScript static-analysis defects discovered

The previous CI did not run strict ESLint. Adding `--max-warnings=0` exposed actual runtime defects:

- undeclared `validResult`;
- undeclared `hashResult` and use of the wrong password variable;
- undeclared `wsIsRunning_s`;
- undeclared P2P file map `temp`;
- assignment expression passed as `clearFiles((expensiveCall = true))`;
- render-time notification permission request;
- fetch timeout not cleared;
- obsolete donation metadata request/state;
- loose equality comparisons;
- unused imports and dead paths.

The repaired files passed `node --check`, ESLint warning zero, and Jest before commit.

## Dependency audit

Initial complete tree:

- 4 critical;
- 5 high;
- 10 moderate;
- 1 low.

Bounded updates:

- React Native retained at `0.80.2`;
- React Native Community CLI updated to compatible `19.1.2`;
- unused `@react-native-clipboard/clipboard` removed;
- nonbreaking transitive lockfile fixes applied without `--force`.

Result:

- full audit: no high or critical findings;
- production audit: no high or critical findings;
- seven moderate findings remain through CLI 19's `fast-xml-parser` dependency;
- npm's offered fix installs CLI 20.2.0, a breaking major update, so it was not forced into this stability branch.

The permanent Android CI now gates both full and production audits at `--audit-level=high`.

## AsyncStorage native bridge defects

Two concrete defects were found:

1. `disconnect()` closed the process-wide database returned by `ReactDatabaseSupplier`, potentially breaking JavaScript AsyncStorage and other native bridge instances.
2. Native writes wrapped values in quotes without JSON escaping, and reads stripped leading/trailing quotes with a regular expression, corrupting strings containing quotes or backslashes.

Current behavior:

- bridge release only drops its local reference;
- the process-wide supplier retains database ownership;
- writes use `JSONObject.quote`;
- reads use `JSONTokener`;
- failed database access fails closed;
- source-contract tests prevent return of the old close/regex/manual-quote implementation.

## Manifest, permission, version, UI, and security corrections

- explicit React Native light/dark palette added;
- native setup screen colors set directly as well as through styles;
- API-specific system-bar/style attributes moved to appropriate resource qualifiers;
- old READ/WRITE external-storage permissions removed because the app uses SAF/ContentResolver/FileProvider;
- backup and device-transfer rules exclude all application state domains;
- direct battery allowlist-request permission removed; setup opens the system settings list instead;
- `POST_NOTIFICATIONS` requested only on API 33+;
- Accessibility service corrected to `exported=true` with system-only binding permission;
- NativeBridge lifecycle moved from deprecated `onCatalystInstanceDestroy` to `invalidate`;
- overlay transition API split at API 34;
- APK version corrected from `1.0 / code 1` to `3.2.0 / code 30200` and tied by test to `App.js` and `version.json`;
- old upstream repository/docs/issues/discussions URLs removed from mobile and desktop product metadata;
- server-supplied DONATE link removed;
- React Native and native setup UI use explicit high-contrast palettes.

## CI and one-shot workflow failures retained

The campaign used temporary workflows only when a large verified patch could not be safely sent through the single-file API. Every successful helper/workflow was deleted afterward. Failed attempts were retained in history rather than hidden.

Observed failures and corrections:

- ESLint initially revealed 18 errors/warnings after previous CI had been called green.
- first repair helper asserted five `wsIsRunning_s` occurrences; actual count was four.
- a one-shot workflow listened only for `push`, while connector writes surfaced as PR synchronization; `pull_request` was added.
- a restore command using `HEAD^` became invalid after another trigger commit; restore source was pinned.
- temporary Git-data API experiments accidentally created empty `tmp-test-ignore*` files; all were deleted and the error was recorded.
- a Git tree attempt used a commit SHA where a tree SHA was required.
- gzip/Base64 helper transport suffered CRC corruption; exact source transformations replaced it.
- a patch passed ESLint but failed stale source-contract tests that still required removed donation metadata.
- a test banned every `primaryClip` substring and accidentally rejected safe `primaryClipDescription` metadata access.
- a test banned `Context.DEVICE_ID_DEFAULT` and `debounce` even in explanatory comments; comments were reworded while the implementation ban remained.
- a pending-share helper correctly stopped because legacy global `removeAllListeners` calls remained; they were explicitly removed.
- strict ESLint rejected two `void` fire-and-forget expressions; normal calls were used under the codebase's Promise handling convention.
- `npm audit fix` returned exit code 1 after fixing high findings because moderate CLI findings remained; acceptance was changed to the resulting high/critical audit, not the intermediate command exit.
- final Android CI built and packaged successfully but failed the aggregate gate because a test file had four `no-useless-escape` warnings; those warnings were removed without weakening the product contract.

## Verification expansion

Permanent Android CI now runs:

- exact `npm ci`;
- full dependency audit high gate;
- production dependency audit high gate;
- `node --check` on product JavaScript;
- ESLint with zero warnings allowed;
- Jest source/contracts;
- Android lint;
- Android JVM tests;
- standalone APK build;
- exact embedded JS bundle check;
- ZIP integrity;
- SHA-256 generation and verification;
- complete lint/test/log artifact collection.

Permanent Desktop CI runs:

- Ubuntu and Windows compile/tests;
- Windows EXE build and SHA-256;
- Linux package extraction, compile, tests, archive integrity, and SHA-256.

Actions were moved to current Node-24-based major versions:

- `actions/checkout@v6`;
- `actions/setup-node@v6`;
- `actions/setup-java@v5`;
- `actions/setup-python@v6`;
- `actions/upload-artifact@v7`.

## Remaining unproven items

Static verification does not prove:

- Binder acquisition and UserService behavior on the user's forked Shizuku manager and HONOR/MagicOS;
- whether the vendor ROM permits the hidden clipboard call for shell/root UserService;
- liveness if the Binder transaction itself never returns; no guessed timeout was introduced;
- Accessibility `ACTION_COPY` coverage for each source application;
- overlay focus behavior on the target ROM;
- real Android-to-Windows delivery, reconnect, retry, ordering, and battery behavior;
- Windows GUI/tray behavior on the user's machine.

These remain the next real-device acceptance stage.
