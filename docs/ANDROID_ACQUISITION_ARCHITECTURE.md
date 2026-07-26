# Android Clipboard Acquisition Architecture

Last updated: 2026-07-26 (Asia/Tokyo)

Status: implementation contract; no capture backend has yet been migrated

## Purpose

Make Android clipboard acquisition observable, replaceable, and testable without changing the ClipCascade server protocol.

The current Android client combines:

- clipboard-change detection;
- protected logcat monitoring;
- overlay/focus acquisition;
- clipboard reading;
- React Native event emission;
- foreground-service transport;
- duplicate suppression.

That coupling makes background failures indistinguishable. A copy can be missed because no trigger occurred, because the chosen backend lacked permission, because a focus workaround failed, because the React context was unavailable, or because transport was disconnected. The clean architecture must report those as different states.

## Non-goals for the first implementation

- No server changes.
- No root requirement.
- No immediate Shizuku dependency.
- No deletion of the existing logcat/overlay path before replacement evidence exists.
- No assumption that Accessibility alone covers every application.
- No direct clipboard payload in diagnostics.
- No durable outbound queue in the acquisition package itself.

## Separation of responsibilities

```text
Clipboard trigger backend
        ↓
Acquisition coordinator
        ↓
Clipboard reader
        ↓
Normalized acquisition event
        ↓
React Native bridge / transport ingress
        ↓
Outbound queue and connection manager
```

### Trigger backend

Observes a condition that may indicate clipboard content changed. It does not send data and does not own transport.

Examples:

- ordinary `OnPrimaryClipChangedListener`;
- Accessibility events;
- protected logcat signal plus focus workaround;
- Shizuku-mediated privileged observation/read;
- optional ADB-granted capability.

### Acquisition coordinator

Owns backend selection, lifecycle, deduplication of trigger attempts, read scheduling, health state, and fallback decisions.

It must be the only component allowed to start or stop acquisition backends.

### Clipboard reader

Attempts to read the current clipboard after a trigger and returns a typed result. Reading is separate because multiple trigger backends may use the same reader, while a privileged backend may provide its own direct reader.

### Transport ingress

Receives normalized content events. It does not decide which Android capability generated them.

## Stable backend identities

```text
ORDINARY_LISTENER
ACCESSIBILITY
LOGCAT_OVERLAY
SHIZUKU
ADB_ASSISTED
MANUAL_SHARE
```

Identity is stable across UI, logs, diagnostics, and tests. User-facing labels may be localized later.

## Backend contract

Conceptual Kotlin interface:

```kotlin
interface ClipboardAcquisitionBackend {
    val id: AcquisitionBackendId

    fun inspectCapability(): BackendCapability
    fun start(triggerSink: (AcquisitionTrigger) -> Unit): BackendStartResult
    fun stop(): BackendStopResult
    fun snapshot(): BackendSnapshot
}
```

Requirements:

- `inspectCapability()` performs no long-running background work.
- `start()` is idempotent.
- one backend instance has at most one active registration/thread/service.
- `stop()` is idempotent and bounded.
- callbacks never contain clipboard content.
- backend callbacks are serialized by the coordinator before reads.
- backend exceptions become stable error codes rather than escaping into React Native.

## Capability model

```text
UNAVAILABLE
NEEDS_USER_ACTION
AVAILABLE
DEGRADED
BLOCKED
```

Each capability includes:

- backend ID;
- capability state;
- stable reason code;
- required user action, if any;
- whether the action can be deep-linked;
- whether reboot or service restart may invalidate it;
- expected coverage class;
- current Android SDK and OEM context without personal data.

Suggested stable reason codes:

```text
SUPPORTED
ANDROID_BACKGROUND_RESTRICTION
ACCESSIBILITY_DISABLED
ACCESSIBILITY_NOT_DECLARED
SHIZUKU_NOT_INSTALLED
SHIZUKU_NOT_RUNNING
SHIZUKU_PERMISSION_REQUIRED
READ_LOGS_PERMISSION_REQUIRED
OVERLAY_PERMISSION_REQUIRED
BATTERY_OPTIMIZATION_ACTIVE
OEM_BACKGROUND_RESTRICTION
REACT_CONTEXT_UNAVAILABLE
BACKEND_START_FAILED
BACKEND_STOP_FAILED
UNKNOWN_CAPABILITY_ERROR
```

## Coverage classes

Capability does not imply complete coverage. Each backend reports one of:

```text
FOREGROUND_ONLY
EVENT_DEPENDENT
FOCUS_WORKAROUND
PRIVILEGED
MANUAL_ONLY
```

This lets the UI say, for example, that Accessibility is active but event-dependent rather than falsely declaring complete reliability.

## Trigger model

```kotlin
data class AcquisitionTrigger(
    val backendId: AcquisitionBackendId,
    val triggerType: TriggerType,
    val monotonicTimestampMs: Long,
    val sourcePackage: String?,
    val confidence: TriggerConfidence,
)
```

Trigger types:

```text
PRIMARY_CLIP_CHANGED
ACCESSIBILITY_COPY_ACTION
ACCESSIBILITY_SELECTION_CHANGED
LOGCAT_CLIPBOARD_DENIAL
PRIVILEGED_CLIP_CHANGED
MANUAL_SHARE
EXPLICIT_SELF_TEST
```

Trigger confidence:

```text
DIRECT
INFERRED
DIAGNOSTIC
```

The source package is optional and must be sanitized in exported diagnostics when necessary. No selected text or clipboard content appears in the trigger.

## Read result model

```text
SUCCESS
UNCHANGED
EMPTY
ACCESS_DENIED
FOCUS_REQUIRED
REACT_CONTEXT_UNAVAILABLE
UNSUPPORTED_CONTENT
READ_FAILED
```

A successful read produces a content event containing the content only on the private in-process path to transport ingress. Diagnostic snapshots receive only:

- content type;
- byte count;
- hash/fingerprint scoped to the current session;
- backend ID;
- timing;
- result code.

Persistent logs must not contain the content or a reusable unsalted hash.

## Coordinator states

```text
STOPPED
INSPECTING
STARTING
ACTIVE
DEGRADED
WAITING_FOR_USER_ACTION
STOPPING
ERROR
```

The coordinator snapshot includes:

```text
state
stateSince
selectedBackend
activeBackends
availableBackends
lastTriggerAt
lastTriggerBackend
lastReadAttemptAt
lastSuccessfulReadAt
lastReadResult
lastErrorCode
pendingUserAction
triggerCount
successfulReadCount
failedReadCount
suppressedDuplicateTriggerCount
```

All counters resettable by the user-facing diagnostic mode. No clipboard data is included.

## Backend-selection policy

Initial conservative policy:

1. Always register `ORDINARY_LISTENER` when available; it is cheap and useful while the app has access.
2. Keep `MANUAL_SHARE` available independently.
3. Use exactly one primary background-enabling backend at a time unless an experiment explicitly tests a redundant combination.
4. Prefer a verified privileged path over focus-stealing workarounds when configured.
5. Do not automatically enable Accessibility without explicit user action.
6. Do not automatically fall back from a failed Shizuku path to overlay behavior without reporting the transition.
7. Retain `LOGCAT_OVERLAY` as a legacy fallback until real-device evidence shows a replacement covers the target cases.
8. Record why a backend was selected and why another was rejected.

Tentative priority after capability inspection:

```text
SHIZUKU verified
→ ACCESSIBILITY verified
→ ADB-assisted READ_LOGS + overlay verified
→ ordinary listener only
→ manual share
```

This ordering is not final. Real-device coverage, intrusiveness, and power measurements decide it.

## Duplicate-trigger handling

Different backends may report the same copy operation. The coordinator suppresses duplicate read attempts using a short monotonic trigger window and the final content fingerprint.

Rules:

- trigger suppression is separate from content suppression;
- a suppressed trigger increments a counter;
- content is not discarded solely because two backends fired;
- a failed read from one backend may permit a second backend-triggered read;
- transport duplicate suppression remains separate.

## Power rules

- Event-driven registration preferred over polling.
- No unbounded logcat reader restart loop.
- No one-second health polling in production.
- Health changes are event-driven where Android permits, with coarse periodic verification only when justified.
- Backend restart uses bounded backoff and a cap.
- Acquisition self-test is user initiated or explicitly scheduled by diagnostics, not continuously repeated.
- Wake locks require measured evidence and bounded duration.

## Accessibility backend requirements

The Accessibility backend is a trigger source, not permission to rewrite unrelated UI.

It must:

- declare only required event types and flags;
- avoid changing view focus, text, or selection;
- never inject gestures for ordinary monitoring;
- ignore ClipCascade's own UI unless a self-test is active;
- identify explicit copy actions when available;
- report event-dependent coverage honestly;
- expose event counts and last recognized copy action;
- be disableable without breaking transport or manual sharing.

Amazon/search-field regressions observed with another application are an explicit acceptance concern. Tests must verify that enabling the service does not consume clicks, alter focus, replace actions, or intercept text entry.

## Shizuku backend requirements

Shizuku integration must be isolated behind the same contract.

Required setup states:

```text
NOT_INSTALLED
INSTALLED_NOT_RUNNING
RUNNING_PERMISSION_REQUIRED
AUTHORIZED
AUTHORIZED_UNVERIFIED
VERIFIED
REVOKED
```

The setup wizard must:

- detect installation and binder availability;
- deep-link to the relevant Shizuku screen where possible;
- explain wireless-debugging/restart requirements in plain language;
- request permission;
- run a non-destructive clipboard acquisition self-test;
- show the exact verified capability;
- explain reboot persistence for the selected mode;
- never show a generic success merely because permission was granted.

## Legacy logcat/overlay adapter

The existing behavior must first be wrapped, not expanded.

The adapter must expose separately:

- `READ_LOGS` permission state;
- overlay permission state;
- logcat process running state;
- last matching log line time;
- overlay launch attempt time;
- clipboard read result;
- React-context delivery result.

The current implementation conflates these stages. Wrapping them makes failures measurable before replacement.

The adapter must also remove raw `printStackTrace()` from normal operation and route failures through stable diagnostic events.

## React Native bridge

The first bridge extension should add control and health methods without changing the existing `onClipboardChange` payload contract:

```text
inspectAcquisitionCapabilities()
startAcquisitionCoordinator(configuration)
stopAcquisitionCoordinator()
getAcquisitionSnapshot()
runAcquisitionSelfTest()
```

New event names:

```text
onAcquisitionStateChanged
onAcquisitionDiagnosticEvent
```

`onClipboardChange` remains the private content event into the current JS transport until a later transport-ingress refactor.

## One-action self-test

The acquisition self-test must distinguish:

1. backend capability inspection;
2. backend start;
3. explicit test trigger observed;
4. clipboard read attempted;
5. content fingerprint changed;
6. React Native event delivered;
7. transport ingress accepted the event.

The user-facing result must show the first failed stage. It must not say only “test failed.”

A self-test payload should be generated locally with a random nonce and removed/replaced only when doing so does not overwrite user content unexpectedly. The exact UX and restoration behavior must be specified before implementation.

## Test strategy

### Pure JVM tests

- capability ordering;
- selection and fallback reasons;
- idempotent start/stop;
- trigger deduplication;
- failed-read fallback;
- stale callback rejection;
- state/counter updates;
- sanitized diagnostic serialization;
- bounded backend restart policy.

### Android instrumentation tests

- ordinary listener foreground behavior;
- Accessibility service enable/disable lifecycle;
- no view mutation or focus interception;
- overlay permission absent/present paths;
- React context unavailable/recovery;
- process recreation and boot handling;
- Shizuku binder loss/recovery when added.

### Real-device matrix

At minimum:

- HONOR/MagicOS target device;
- AOSP-like Android device or emulator;
- foreground app;
- ClipCascade backgrounded;
- screen on/locked where permitted;
- common text field;
- browser address/search field;
- launcher drawer search;
- Amazon search field;
- selection toolbar copy;
- notification/OTP copy if supported;
- repeated copies and rapid changes;
- reboot and service restart.

Every cell records trigger backend, read result, outbound result, latency, duplicate count, and battery context.

## Delivery sequence

1. Add pure capability, snapshot, trigger, and selection models with JVM tests.
2. Wrap the ordinary listener as `ORDINARY_LISTENER` without changing its event payload.
3. Wrap the current logcat/overlay path and expose stage-specific health.
4. Add coordinator and bridge control/snapshot methods.
5. Add one-action acquisition-only self-test.
6. Real-device baseline measurements of existing ordinary and legacy paths.
7. Add Accessibility trigger backend behind the contract.
8. Evaluate UI regressions and coverage gaps.
9. Add Shizuku backend and guided setup.
10. Add adaptive selection only after comparative evidence.
11. Connect normalized acquisition events to a durable outbound queue in a separate transport change.

## Acceptance gate before removing legacy behavior

The logcat/overlay path may be disabled by default only when another configured path:

- passes the real-device matrix for the target device;
- reports failures through the coordinator;
- survives process/service restart as specified;
- does not regress affected search fields or selection UI;
- has measured power behavior no worse than the legacy path within the chosen test window;
- preserves manual share as an escape hatch;
- produces installable APK artifacts through CI.

## Immediate implementation scope

The next code change is deliberately narrow:

- pure Kotlin enums/data classes for backend ID, capability, trigger, result, coordinator state, and snapshot;
- deterministic backend-selection policy;
- local JVM tests;
- no manifest change;
- no Accessibility service;
- no Shizuku dependency;
- no product behavior change.
