# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade` (public)  
Canonical requirements: `docs/REQUIREMENTS.md`

## Critical status correction — 2026-06-28

Earlier Android outbound successes were misattributed to ClipCascade because Microsoft Phone Link clipboard synchronization was active on Windows.

With Phone Link excluded, the user found:

- peer -> Android reception works while ClipCascade is backgrounded and apparently while the screen is off;
- Android -> peer sending does not work when ClipCascade is not visible;
- the old claim that Accessibility copied-text relay worked on the target device is invalid;
- the old claim that a Yahoo! JAPAN SMS verification value reached Windows through ClipCascade is invalid.

No Android foreground/background/locked/screen-off outbound success is currently proven. Read `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md` before making or testing further changes.

PR #1 must remain Draft.

## Current high-level state

Windows authentication, GUI, inbound synchronization, P2P transport, Windows-applied ACK, diagnostics, packaging, and automated tests are substantially implemented.

Android has:

- a user-authorized AccessibilityService;
- a user-authorized NotificationListenerService;
- persistent clipboard and verification-code queues;
- bilingual guided setup and diagnostics;
- deterministic development/test signing;
- boot/network/heartbeat recovery infrastructure;
- Extended P2P peer acknowledgement.

However, the mandatory Android outbound requirement is still unproven. A new repair attempts to remove foreground-only clipboard behavior by recovering the selected range through Accessibility rather than relying on background `ClipboardManager` access.

## Android platform constraint

Android 10 and later do not return clipboard data to an ordinary app unless it is the default input method editor or currently has input focus.

Consequences for this project:

- a foreground service alone does not make `ClipboardManager.primaryClip` reliable in the background;
- Accessibility must capture the selected text around an explicit Copy action;
- using Accessibility merely as a trigger and then reading the global clipboard reproduces the old foreground-only behavior;
- future implementation and tests must distinguish copy-cue detection, selected-text recovery, native queueing, transport, peer clipboard application, and acknowledgement.

## Current Android repair build

Prepared identity:

- app name: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.5-standalone`
- versionCode: `320109`
- deterministic public test signer certificate SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

The build is intended to update stable-signed versionCode `320107` or `320108` in place. The signer is public and suitable only for repeatable development/personal test builds, not publisher authentication.

## Accessibility outbound capture repair

`ClipboardAccessibilityService` now:

- listens for selection changes, copy-button/context clicks, announcements, notification-state changes, and copy-related window changes;
- includes non-important Accessibility views so floating/system toolbar nodes are less likely to be omitted;
- accepts `AccessibilityNodeInfo.ACTION_COPY` as an explicit copy cue;
- attempts capture immediately before the selection can collapse, followed by bounded delayed attempts;
- retains selected text for 60 seconds rather than 15 seconds;
- scans all interactive Accessibility windows for a node with an active selected range if the remembered selection is absent;
- queues only the selected substring;
- does not send merely because text was selected; an explicit Copy cue is still required.

Target-device proof is pending.

## Native queue and recovery repair

Previously, clipboard and OTP queues could retry indefinitely without asking the background transport to recover when React/JS state was unavailable.

Current implementation requests bounded recovery when synchronization intent is enabled but:

- transport status is offline;
- the React application/context is absent;
- React event delivery fails.

A healthy P2P signaling connection with zero remote peers remains a normal queued condition and does not trigger restarts.

`RecoveryCoordinator` now:

- does not suppress the first request merely because the device has been up for less than 60 seconds;
- records the attempt before active-React or Headless JS recovery so OS-rejected starts remain rate-limited;
- preserves user synchronization intent after a failed background recovery.

The transformed startup path now attempts to recreate a missing prior foreground-service generation instead of changing synchronization to OFF and leaving `Foreground service stopped running` as a terminal state.

Android 12+ restricts starting foreground services from the background. Recovery can still be rejected by the OS outside permitted cases; battery exemption and HONOR/MagicOS background permissions remain part of the mandatory setup and test matrix.

## Target-neutral Android UI

The manual clipboard test now says:

- Japanese: `接続中の端末へテスト文字列を送る`
- English: `Send a test string to connected devices`

The setup introduction also describes connected devices rather than Windows specifically.

## Verification-code notification relay

Implemented in code:

- local extraction through NotificationListenerService;
- optional source-package filter;
- standard text, expanded text, text lines, conversation title, and MessagingStyle inspection;
- persistence of only extracted value, timestamp, and opaque relay ID;
- bounded queue, TTL, deduplication, retry, and ACK timeout;
- synthetic local notification test;
- Japanese and English extraction rules with false-positive controls.

No real SMS/email end-to-end result is currently valid because the prior result was contaminated by Phone Link. The synthetic and real screen-state matrix must be repeated with all other clipboard synchronizers disabled.

## Delivery acknowledgement — preserve exactly

Common local path:

`QUEUED -> NATIVE_IN_FLIGHT -> JS_SEND_ATTEMPT -> LOCAL_TRANSPORT_ACCEPTED`

P2S completion:

`LOCAL_TRANSPORT_ACCEPTED -> NATIVE_ACK -> DELETE`

P2P with Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> PEER_RECEIVED -> PEER_TEXT_APPLIED -> PEER_ACK -> NATIVE_ACK -> DELETE`

P2P with old/non-Extended peer:

`LOCAL_TRANSPORT_ACCEPTED -> 5 SECOND COMPATIBILITY FALLBACK -> NATIVE_ACK -> DELETE`

Do not remove or bypass:

- `relayId` / `ackRequested` metadata;
- peer ACK only after validated text application or duplicate-already-applied handling;
- Android ACK-envelope handling before clipboard parsing;
- receive-hash commit only after validation;
- generation-scoped ACK timers;
- queue startup only after `SHARED_TEXT` listener registration.

P2S and old-peer fallback are not peer-applied acknowledgement. Multiple-peer completion still occurs on the first valid ACK.

## Windows status

User-validated:

- authentication works against the actual deployment;
- Windows GUI and synchronization work;
- Windows -> Android reception works while Android is backgrounded and apparently screen-off.

Implemented:

- persistent authenticated HTTP session and all-cookie preservation;
- validated authenticated API responses;
- visible status/control window;
- restart/reconnect/disconnect/log/diagnostic controls;
- rotating logs and second-launch activation;
- complete P2P teardown before replacement;
- Windows-applied P2P ACK;
- explicit Quit cleanup and final process-exit guarantee.

The supplied log showed failed `169.254.*` candidate binds followed by a successful candidate and `ICE completed`. The watchdog warning was emitted too early during normal ICE negotiation. It now waits ten seconds before reporting a persistent unhealthy state; the full restart threshold remains 25 seconds.

Real Task Manager proof for Quit and immediate relaunch is still pending.

## CI and trial record

- Prior ACK, authenticated HTTP, shutdown/status, extractor, package, and signing tests remain mandatory.
- Android run `28316609479` failed during AAPT resource linking because the XML event flag was written as `typeViewContextClicked`; the valid XML enum is `typeContextClicked`. Kotlin's constant remains `TYPE_VIEW_CONTEXT_CLICKED`.
- Commit `f3ca3b45c51a48f18e9811b06786c030a7b818bb` corrects the XML spelling.
- This failure was a build-resource naming error, not a transport or ACK regression.

See `docs/progress.md` and `docs/LATEST_BACKGROUND_SYNC_FAILURE_HANDOFF.md` for the complete chronology.

## Mandatory next proof

Before testing:

1. disable Phone Link clipboard synchronization;
2. stop every other clipboard synchronization utility;
3. use matching current Android and peer builds;
4. use synthetic non-secret text.

Test separately:

1. Android app visible;
2. Android app backgrounded for at least 30 seconds;
3. app removed from recents;
4. device locked and screen off;
5. synthetic OTP notification;
6. real SMS and email without recording the value.

After each failure, record:

- clipboard diagnostic trigger/path/result;
- pending clipboard queue count;
- recovery diagnostic trigger/path/result;
- whether peer -> Android reception still works.

Interpretation:

- no clipboard diagnostic and queue count zero: Accessibility missed the copy cue or selection;
- `no_text_available` or `clipboard_denied_no_fallback`: the cue was observed but no selected range was available;
- queue count greater than zero: capture succeeded but outbound transport/ACK is blocked;
- queue drains and exact peer clipboard update plus peer ACK occurs: full outbound success.

## Not yet complete

- Android background outbound proof with Phone Link disabled;
- Accessibility compatibility across representative applications;
- synthetic OTP full-path proof;
- real SMS/email foreground/background/locked/screen-off matrix;
- Android 16 notification redaction measurement;
- MagicOS process-death/reboot/handover recovery;
- one-time signer migration and subsequent update-retention proof;
- Windows Quit real-process proof;
- image/file regression;
- P2S peer-applied acknowledgement;
- explicit multiple-peer ACK policy;
- private production signing and tested tagged release.

## Do not claim

Do not describe Android outbound synchronization as working, beta-ready, screen-off capable, or real-SMS validated until it succeeds with Phone Link and every other competing synchronizer disabled.
