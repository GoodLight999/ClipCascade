# Experiment Log — 2026-07-27 Shizuku Clipboard Read

This is an append-only companion to `docs/EXPERIMENT_LOG.md`.

## Experiment SHIZUKU-001

### Goal

Add an actual Shizuku-backed clipboard read path without creating another network transport, polling loop, connection engine, or speculative backend framework.

### Required references inspected before implementation

- Official Shizuku API repository and README.
- Official Shizuku `DemoActivity`, `UserService`, and AIDL example.
- Official Shizuku binder, permission, binder-death, and UserService lifecycle implementation.
- Current Android/AOSP `IClipboard` signatures and clipboard-service package/UID validation behavior.
- OctoClip's documented Shizuku and Accessibility user flows.
- Existing upstream ClipCascade Android listener, READ_LOGS trigger, overlay read, foreground service, and React Native send path.

### Reused mechanisms

The implementation deliberately reuses:

- the existing Accessibility copy trigger;
- the existing READ_LOGS trigger;
- the existing React Native `onClipboardChange` event;
- the existing JavaScript `sendClipBoard` function and content-hash duplicate suppression;
- the existing Notifee foreground transport service;
- the existing overlay clipboard-read activity as fallback;
- the official Shizuku API/provider libraries and UserService pattern.

No second WebSocket/P2P client or outbound queue was introduced.

## Design

### Trigger and read responsibilities

- Accessibility and READ_LOGS remain event triggers.
- `BackgroundClipboardCapture` is the one decision point.
- It first requests a read from a Shizuku UserService running with shell identity.
- On a successful text read, the app returns the content to the existing React Native event/send path.
- If Shizuku is absent, stopped, denied, still binding, unsupported, or returns a non-text clip, the existing overlay path is used when available.

### Why UserService

The official Shizuku API describes UserService as the supported mechanism for non-trivial privileged work and provides binder-received, binder-death, permission, bind, and restart lifecycle APIs. The deprecated remote-process command path was not used.

### Why the first version is text-only

An image or file clipboard item may contain a content URI whose temporary permission belongs to the shell-side UserService rather than the ClipCascade app process. Returning that URI as if the app could always open it would create a build-green but runtime-broken path.

Decision:

- Shizuku directly returns text clips only in SHIZUKU-001.
- Image and file clips return `needs_fallback` and use the existing overlay/app-process path.
- URI transfer is a separate future experiment requiring explicit permission/stream ownership tests.

## Source implementation

### Official dependencies

`ClipCascade_Mobile/src/android/app/build.gradle` adds:

- `dev.rikka.shizuku:api:13.1.5`
- `dev.rikka.shizuku:provider:13.1.5`

### AIDL contract

`IShizukuClipboardService.aidl` exposes only:

- reserved `destroy` transaction;
- one clipboard read;
- service UID for setup verification.

### Shell UserService

`ShizukuClipboardUserService`:

- contains no transport, queue, or polling;
- obtains the clipboard binder through `ServiceManager`;
- resolves `IClipboard.getPrimaryClip` adaptively for Android signature changes;
- identifies itself as `com.android.shell` when calling the clipboard service;
- returns JSON status to the app process;
- returns clipboard text only;
- returns only exception type/message on failure and never logs payload content.

### App-process bridge

`ShizukuClipboardBridge`:

- initializes once from `MainApplication`;
- tracks Shizuku installation, binder, permission, UserService binder, service UID, and last non-payload error;
- uses official binder-received, binder-dead, and permission-result listeners;
- binds a non-daemon UserService with a stable tag and version;
- executes clipboard reads on one background thread;
- returns callbacks on the main thread;
- opens the installed Shizuku app or official download page;
- owns no clipboard payload history.

### Existing send-path integration

`ClipboardListenerModule`:

- keeps a weak reference to the active native module;
- exposes a guarded `emitExternalClipboard` entry point;
- emits Shizuku results into the existing `onClipboardChange` event on the React Native JS queue;
- routes READ_LOGS triggers through the shared Shizuku-first capture decision.

`ClipCascadeAccessibilityService`:

- retains its conservative high-confidence copy trigger;
- requests the shared Shizuku-first read;
- still ignores generic clicks, generic selection changes, own-package events, and inactive ClipCascade runtime.

### Guided setup

`BackgroundSetupActivity` shows:

- Shizuku installed/not installed;
- Shizuku running/stopped;
- Shizuku permission;
- UserService bound/not bound;
- actual UserService UID;
- Accessibility, overlay fallback, READ_LOGS, battery exemption, and ClipCascade foreground runtime;
- last non-payload Shizuku error.

It provides buttons to:

1. open/install Shizuku;
2. request permission and connect;
3. perform a privacy-safe read test;
4. open Accessibility settings;
5. enable overlay fallback;
6. open battery settings;
7. copy the existing ADB fallback commands.

The test reports only content type and length; it never displays the clipboard content.

## Source-review corrections before build result

1. Fixed the Kotlin AIDL-stub constructors to call `super()` explicitly.
2. Changed hidden-interface method lookup from the runtime proxy class to `android.content.IClipboard`, reducing proxy/reflection ambiguity.
3. Restricted direct Shizuku output to text, with image/file fallback, to avoid invalid cross-process URI-grant assumptions.

## Current verification status

Source implementation is committed on `stability-recovery`.

The latest Android workflow for product commit `2178c5e8fa5f2c2e2c0ccaacfb81d9d35ea67298` is running at the time of this checkpoint.

No claim is yet made that:

- AIDL/Kotlin/Android resource compilation passes;
- Shizuku actually binds on the user's device;
- the hidden clipboard call succeeds on the user's Android build;
- Accessibility-triggered Shizuku reads work in the background;
- duplicate sends, power use, or focus behavior are acceptable;
- Shizuku alone can monitor clipboard changes without Accessibility/READ_LOGS triggers.

## Required next evidence

1. Green app tests and standalone APK build.
2. APK contains `assets/index.android.bundle` and passes ZIP integrity.
3. Install Shizuku and start it on the user's device.
4. Verify the setup screen reports shell UID and a bound UserService.
5. Copy text, tap the privacy-safe test, and confirm direct read succeeds.
6. Start ClipCascade foreground service and test background copy via Accessibility trigger.
7. Stop Shizuku and verify automatic overlay fallback.
8. Test Amazon, launcher, browser, search fields, and selection toolbar.
9. Record duplicates and battery/wakeup behavior.
10. Decide from evidence whether a Shizuku-side clipboard-change listener is necessary, instead of assuming it is.
