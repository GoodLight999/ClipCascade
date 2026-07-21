# Foreground Runner Lifecycle Handoff — 2026-07-21

## Target evidence that invalidated `.19-alpha.2`

The user tested `.19-alpha.2` and reported:

- the real NotificationListener-path self-test did not complete;
- Android outbound still failed whenever the main app UI was not open;
- inbound continued to work.

Therefore `.19-alpha.2` failed its two primary target goals. Do not describe it as fixed, partially proven, or awaiting only Gmail evidence.

## Why the Go fork mattered

`wuxinkami/ClipCascade_go_fork` keeps its network engine inside a sticky native Android foreground service. Accessibility binds to that service and requests clipboard synchronization from the runtime that owns the live connection.

The fork also uses a transparent overlay and `SYSTEM_ALERT_WINDOW` to read Android's clipboard in the background. Extended must not copy that part because the canonical requirements prohibit overlay/ADB/root/Shizuku workarounds and require selection-only safety.

The useful invariant is narrower:

> The activity/UI runtime must not be responsible for registering or owning the background transport.

## Confirmed Notifee lifecycle defect

The Extended implementation registered `notifee.registerForegroundService(...)` only inside `StartForegroundService()`, which was invoked lazily from mounted UI/recovery code.

Notifee requires the foreground-service runner to be registered outside React components and as early as possible from the JavaScript entry point. A recreated Android process could therefore retain or restart the native foreground service while the JavaScript transport runner had never been registered in that process generation.

This explains why `.19-alpha.2` queue polling code could exist and pass CI while no live runner performed it on the target.

## `.20-alpha.1` implementation

- implementation/release SHA: `290d6690e749fe34367b2383676faf27c0a4ba76`
- versionName: `3.2.1-extended.20-alpha.1-standalone`
- versionCode: `320125`
- Android CI: `29843413287`, success
- Windows CI: `29843413149`, success

### Early registration

`index.js` now invokes `StartForegroundService({registerOnly: true})` before `AppRegistry.registerComponent`.

The registration-only path:

- synchronously registers the Notifee foreground runner;
- does not display or restart the foreground notification;
- is guarded so each process generation registers once;
- remains compatible with the existing UI/recovery service-start calls.

### Runner heartbeat

`ForegroundTransportRuntimeStore` stores only content-free lifecycle timestamps and a bounded stop reason:

- registered;
- started;
- heartbeat;
- stopped.

The active runner records a heartbeat every 15 seconds. Settings treats a heartbeat older than 45 seconds as stale and displays either:

- `Foreground transport runner: active`; or
- `Foreground transport runner: no fresh heartbeat`.

The component and listener-path tests request bounded recovery when no fresh runner exists.

### Listener-path test recovery

- synthetic test notifications remain active for five minutes;
- the listener-path test still does not directly queue its value;
- after requestRebind, active-notification rescans run at 0.5, 2, and 5 seconds;
- normal NotificationListener callback/rescan, extraction, persistent queue, transport, Windows application, peer ACK, and native deletion remain required.

### Copy cue expansion

Exact Android-framework-localized Copy matching remains. Candidate collection now includes:

- clicked node text and description;
- parent;
- immediate children and siblings;
- Accessibility action labels.

Selection events still only remember text. Selection alone cannot send. Approximate labels remain rejected.

## Transport and ACK preservation

No Windows implementation file changed.

The final path remains:

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> sendClipBoard -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

Preserve:

- validation before ACK;
- Windows application before peer ACK;
- peer ACK before native deletion;
- P2S local acceptance semantics;
- relay IDs;
- native bounded in-flight timeout;
- old-peer generation-scoped fallback;
- ordinary queue capacity 16, no TTL, no accepted-item eviction;
- debug notification default OFF and ACK isolation.

## CI and artifact

- Actions artifact ID: `8500372630`
- Actions ZIP SHA-256: `ba2152241fdfe8c5bb99e3087b8781faa15915a281df3e10a9e8065fad177660`
- APK SHA-256: `1b48a7bb7e6d4ab757a3a044fda233e8363ce58d6d634b071e7f90a90ac35cbe`
- APK size: `147937563` bytes
- signer diagnostics artifact ID: `8500368947`
- signer diagnostics ZIP SHA-256: `74aa5e5b0e966abc73e1e0d68750564e051fb0f4e9f270f84c05a0dda0c92e57`
- signer SHA-256: `b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0`
- artifact expiry: `2026-10-19T15:19:19Z`

CI proves transform order, bundle syntax, Kotlin/resources/unit tests, APK signing, and retained Windows ACK tests. It does not prove HONOR target behavior.

## Trial and error retained

1. `.19-alpha.2` passed CI but failed both target self-test and UI-closed outbound.
2. The first `.20` Android run `29842404023` stopped before APK generation because the runner-start insertion assumed the pre-ACK `NativeModules` formatting.
3. The second `.20` Android run `29842757548` stopped before APK generation because the script assumed `Recovery` / `回復処理` rather than reading the existing `Automatic recovery` / `自動復旧` resource values.
4. The third implementation run `29843090432` passed; Windows `29843090421` passed.
5. Final release SHA runs Android `29843413287` and Windows `29843413149` passed.
6. During connector staging, `_probe_should_not_create.txt` was accidentally added and removed twice. Commits: add `234ec1aa...`, remove `35930884...`, add `6d86c2bd...`, remove `a6f20d8e...`. Net tree effect is zero. The error must remain documented.
7. No force push was used.

## Mandatory target test order

1. Install `.20-alpha.1` in place; do not uninstall.
2. Disable Phone Link and every competing clipboard synchronizer.
3. Open Extended settings.
4. Record whether the foreground transport runner is active before testing.
5. Run deterministic component/transport test.
6. Record queue count, `foreground_poll / claimed`, debug notice, Windows application, ACK, and deletion.
7. Run the true listener-path self-test.
8. Record listener connected, listener-test, seen, eligible, text/auth/queued, foreground claim, debug, Windows, ACK, and deletion.
9. Leave the main UI without force-stop and perform a unique explicit Copy.
10. Record Copy detection, capture, queue, runner claim, debug, Windows, ACK, and deletion.
11. Only after these succeed, test removed-from-recents, lock, screen-off, long disconnect, and queue-full.

## Failure interpretation

- runner inactive before a test: process/Notifee service lifecycle remains broken;
- runner active, component test queued, no foreground claim: queue-drain/module call failure;
- foreground claim, no debug: transport acceptance failure;
- listener connected but test seen unchanged: HONOR NotificationListener delivery/rescan failure;
- listener seen/eligible/text/auth but no queue: extractor/receipt boundary;
- Copy selection remembered but no Copy cue: target app does not expose usable toolbar events;
- Copy queued but no foreground claim: runner poll failure;
- debug but no Windows: peer transport/application;
- Windows applied but queue remains: peer ACK/native deletion.

PR #1 must remain open and Draft. No target success is claimed until the user reports it.
