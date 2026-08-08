# Experiment Log — 2026-07-27 Android Capture Pipeline Consolidation

This is an append-only companion to `docs/EXPERIMENT_LOG.md`.

## Experiment CAPTURE-PIPELINE-001

### Goal

Eliminate duplicate and ambiguous Android emissions caused by ordinary listener, Shizuku, and overlay paths independently reaching JavaScript, while preserving deliberate re-copy actions and never retaining clipboard content in diagnostics.

### Source review finding

The branch had three capture routes:

1. ordinary `ClipboardManager` listener through `ClipboardListenerModule`;
2. Shizuku read through `ClipboardListenerModule.emitExternalClipboard`;
3. overlay fallback directly acquiring a React context and emitting `onClipboardChange` itself.

The overlay route bypassed the native module's emission path. Accessibility and READ_LOGS could also trigger the same copy close together, causing parallel Shizuku reads or overlapping overlay activities. Existing JavaScript hashing might suppress some duplicates later, but it could not explain which source fired or prevent duplicate native work and UI disturbance.

### Accepted design

- Keep the existing React Native `onClipboardChange -> sendClipBoard` transport path.
- Add one process-local `ClipboardEmissionGate` owned by the active native module.
- Route ordinary listener, Shizuku, and overlay content through that gate.
- Suppress only the same `(type, content)` fingerprint inside a short 1.5-second window.
- Permit the same content again after the window because deliberate re-copy is a valid user action.
- Coalesce concurrent Accessibility/READ_LOGS capture requests before Shizuku or overlay work begins.
- Keep diagnostics payload-free.

### Implementation

#### `ClipboardEmissionGate.kt`

- SHA-256 fingerprint of type, a boundary byte, and content;
- synchronized state;
- 1.5-second default duplicate window;
- clock rollback fails open rather than suppressing a legitimate copy;
- no durable clipboard history.

#### `ClipboardListenerModule.kt`

- owns the shared gate;
- ordinary listener includes source `ordinary_listener`;
- external emit accepts a source label;
- all accepted content uses the same `onClipboardChange` event;
- duplicate, ignored, and scheduling failures go to payload-free diagnostics.

#### `ClipboardFloatingActivity.kt`

- no longer acquires a React context or emits directly;
- returns content to `ClipboardListenerModule.emitExternalClipboard` with source `overlay`;
- retains the existing overlay implementation only as a fallback.

#### `BackgroundClipboardCapture.kt`

- one in-flight Shizuku/fallback decision at a time;
- concurrent triggers are coalesced into at most one pending re-evaluation;
- prevents overlapping Shizuku reads and overlay activities;
- records source/stage outcomes without payload.

#### `CopySignalClassifier.kt`

- extracted from AccessibilityService into a pure classifier;
- explicit ACTION_COPY remains accepted;
- copy-labelled clicks and copied announcements remain accepted;
- generic clicks and generic text-selection events remain rejected.

#### `CaptureDiagnostics.kt`

Process-local counters only:

- trigger count;
- coalesced trigger count;
- Shizuku attempt/success;
- overlay fallback;
- emitted event;
- duplicate suppressed;
- ignored/unavailable;
- last source, stage, reason/error class, and timestamp.

Clipboard payload is never stored.

#### `BackgroundSetupActivity.kt`

- displays the payload-free capture pipeline counters;
- can reset the counters;
- manual Shizuku test records only status, type, and content length.

### Regression tests added

`ClipboardEmissionGateTest` covers:

- duplicate inside window;
- same content after window;
- type participation;
- clock rollback;
- unambiguous type/content boundaries;
- invalid configuration.

`CopySignalClassifierTest` covers:

- explicit copy action;
- copy-labelled click;
- copied announcement;
- Amazon search-field click does not trigger;
- generic text selection does not trigger;
- `Copyright` does not match `copied`;
- Japanese, Chinese, and Korean commands.

### Current verification status

Source implementation and JVM tests are committed. Android CI now runs `:app:testDebugUnitTest`, which includes these tests, before packaging.

At this checkpoint, the latest final-head Android run has not completed. No device/runtime claim is made yet.

### Device evidence still required

- ordinary and Shizuku signals for one copy produce one outbound send;
- overlay fallback and ordinary listener do not double-send;
- Amazon/browser search fields retain focus;
- launcher drawer is not dismissed;
- deliberate same-text re-copy after a pause is accepted;
- capture diagnostics correctly identify source and fallback without payload;
- battery/wakeup behavior remains acceptable.

---

## Experiment P2S-OUTBOX-001 — source and test-core checkpoint

### Problem observed in existing P2S code

The existing `sendClipBoardP2S` sends only while STOMP is connected and `toggle` is false. Text copied while disconnected is silently lost. It also sets the previous-content hash before publish and relies on the server returning the message to clear `toggle`.

### Current implementation boundary

A separate `P2STextOutbox.js` core has been added but is **not yet connected to `StartForegroundService.js`**.

The core provides:

- bounded persistent queue;
- single in-flight head;
- reload converts interrupted in-flight state back to queued;
- content-hash deduplication among pending items;
- expiry and count/byte limits;
- matching self-echo acknowledgement;
- release for reconnect retry;
- serialized concurrent operations;
- payload-free snapshots.

The caller supplies an already prepared wire payload and plaintext content hash. When ClipCascade encryption is enabled, the stored wire payload is ciphertext rather than plaintext.

### Safety limits

- default maximum items: 20;
- default stored wire bytes: 256 KiB;
- default maximum age: 24 hours;
- text only for the first integration;
- no new server protocol or transport;
- no claim that server self-echo is an application-level delivery receipt.

### JavaScript tests added

`P2STextOutbox.test.js` covers:

- load-before-use contract;
- persistence and payload-free snapshots;
- pending hash deduplication;
- interrupted in-flight recovery;
- matching in-flight acknowledgement only;
- reconnect release;
- count/byte bounds;
- in-flight item protection;
- oversized rejection;
- expiry and clock rollback;
- concurrent enqueue serialization.

### Next gate

1. Run Jest in CI.
2. Do not integrate the outbox into the large foreground-service file unless the isolated tests pass.
3. Integrate with exact, reviewed replacements only.
4. Build the bundled APK.
5. Record that protocol-level self-echo confirmation remains runtime-unproven until a real server test.
