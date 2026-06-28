# Background Send Failure Handoff — 2026-06-28

Read after `docs/LATEST_PRIORITY1_VALIDATION_HANDOFF.md`.

## Evidence correction

Previous Android-to-Windows successes were misattributed to ClipCascade. Microsoft Phone Link clipboard sync was active. With Phone Link excluded, Android outbound sending did not work while ClipCascade was not visible.

The user separately observed that peer-to-Android reception still works while ClipCascade is backgrounded and apparently while the screen is off. The broken direction is Android outbound capture/dispatch.

Therefore:

- prior ordinary Android-copy success is invalid;
- prior Yahoo! JAPAN SMS end-to-end success is invalid;
- no Android foreground/background/locked/screen-off outbound success is currently proven;
- disable Phone Link and every other clipboard synchronizer during future validation;
- older documents claiming those successes are superseded by this file.

This is a product-blocking defect because background, locked, and screen-off outbound delivery is mandatory.

## Root causes

### Background clipboard API restriction

Android 10+ does not return clipboard data to an app unless it is the default IME or currently has input focus. The old Accessibility path used Accessibility only as a copy trigger and then attempted `ClipboardManager.primaryClip`. This explains why sending could work while ClipCascade was visible but fail after another app became foreground.

The selected-text fallback was too fragile: if `TYPE_VIEW_TEXT_SELECTION_CHANGED` was missed or the selection collapsed before a delayed read, there was no value to send.

### Queue dispatch could remain stuck

`ClipboardRelayDispatcher` and `OtpRelayDispatcher` retried pending values but did not request recovery when the connected React transport/context was missing. A vendor process kill or generation replacement could leave a queue pending indefinitely.

### First recovery could be suppressed

The recovery cooldown treated the first request during the first 60 seconds after boot as a duplicate because its previous timestamp started at zero.

### Startup disabled a recoverable session

When persisted synchronization intent was true but the service did not answer within 3.5 seconds, startup changed synchronization to off and displayed `Foreground service stopped running` instead of recreating the service.

### Windows warning was premature

The supplied Windows log reached `ICE completed`; the early watchdog warning occurred during normal ICE negotiation. Failed `169.254.*` candidate binds were noisy candidates, not the final outcome.

## Repairs

### Accessibility capture

`ClipboardAccessibilityService` now:

- observes floating toolbar and system copy-confirmation events;
- includes non-important Accessibility views;
- accepts `ACTION_COPY` as an explicit copy cue;
- captures immediately, then performs bounded delayed retries;
- retains recent selected text for 60 seconds;
- scans all interactive Accessibility windows for a live selected range when the remembered selection is absent;
- queues only the selected substring;
- still requires copy-cue validation before dispatch.

The validation matrix must explicitly verify that selecting text without pressing Copy does not relay anything.

### Queue-driven recovery

Clipboard and OTP dispatchers now request bounded recovery when synchronization is enabled but transport status, React context, or event delivery is unavailable. A healthy P2P session with zero remote peers remains a normal queued state.

### Recovery and startup

The first recovery request is no longer incorrectly rate-limited. Failed attempts are still bounded. Startup now tries to recreate a missing foreground-service generation instead of leaving synchronization disabled.

### Wording and Windows log

The test button now targets `connected devices` / `接続中の端末`, not Windows specifically. The Windows watchdog waits ten seconds before reporting a persistently unhealthy connection; the 25-second restart threshold remains.

## Build identity

Prepared Android validation build:

- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.5-standalone`
- versionCode: `320109`
- deterministic test signer unchanged
- certificate SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`

It is intended to update stable-signed `320107` or `320108` in place.

## CI and artifacts

Final synchronized HEAD for this handoff:

- commit: `1d44b24c85ba01db264e0182c5b15194af82a28a`
- Android CI run: `28316778157` — success
- Windows CI run: `28316778154` — success
- Android artifact ID: `7932883811`
- Windows artifact ID: `7932876679`
- Android artifact ZIP SHA-256: `0aa71d2f47c2c4cc46df6924ff8e6bf734a4ceba01b55374c1f0acc6ed84583c`
- Windows artifact ZIP SHA-256: `926fd947417e785d6e8e60dbad630c1ce17a5c594c30ddc0617f810e7abd2b01`
- extracted APK SHA-256: `16520558ef0780e713591242baf9892594030e8a9b15b8ed3fd32f2d6f1e343a`
- extracted EXE SHA-256: `a2d87994e3afb595565ab1ecc633d1562d51105a3d665d97b27ef0c3c11b8821`

Android CI passed source transforms, bundle generation, Gradle unit tests, Kotlin/resource compilation, APK assembly, embedded-bundle verification, and deterministic signer verification. Windows CI passed authenticated HTTP, existing P2P ACK, shutdown/status, and packaging tests.

### Failed CI attempt retained

Android run `28316609479` failed at AAPT linking because the Accessibility XML enum was written as `typeViewContextClicked`. The valid XML name is `typeContextClicked`; Kotlin continues to use `TYPE_VIEW_CONTEXT_CLICKED`. Commit `f3ca3b45c51a48f18e9811b06786c030a7b818bb` fixed it.

## ACK invariants preserved

The native persistent queue, relay IDs, P2P Windows-applied ACK ordering, ACK-envelope handling, delayed compatibility fallback, and native ACK-based deletion were not removed or bypassed.

## Mandatory validation

Before every run, disable Phone Link clipboard sync and all other clipboard synchronization utilities.

Test separately:

1. Android app visible.
2. Android app backgrounded for at least 30 seconds.
3. App removed from recents.
4. Device locked and screen off.
5. Synthetic OTP notification, then real SMS/email without recording the value.
6. Select text without pressing Copy and confirm no relay occurs.

After a failure, record:

- clipboard diagnostic trigger/path/result;
- pending clipboard queue count;
- recovery diagnostic trigger/path/result;
- whether peer-to-Android reception still works.

Interpretation:

- no diagnostic and queue zero: Accessibility missed the copy cue/selection;
- `no_text_available` or `clipboard_denied_no_fallback`: cue observed but selected text unavailable;
- queue greater than zero: capture succeeded and outbound transport/ACK remains blocked;
- queue drains plus exact peer clipboard update and peer ACK: full success.

CI success is not real-device proof. Keep PR #1 Draft.
