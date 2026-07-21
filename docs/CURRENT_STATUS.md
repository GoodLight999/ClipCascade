# Current Implementation Status

Branch: `stability-mobile-otp`  
Draft PR: `#1`  
Repository: `GoodLight999/ClipCascade`

## Current alpha target

- application: `ClipCascade Extended`
- package: `com.clipcascade.extended`
- versionName: `3.2.1-extended.20-alpha.1-standalone`
- versionCode: `320125`
- implementation/release anchor: `290d6690e749fe34367b2383676faf27c0a4ba76`
- tag intended by release workflow: `v3.2.1-extended.20-alpha.1`
- Android CI: `29843413287`, success
- Windows CI: `29843413149`, success
- Android Actions artifact ID: `8500372630`

Hashes and signer are recorded in `docs/LATEST_GREEN_ARTIFACTS.md`.

## Target-device truth

`.19-alpha.2` was tested and failed:

- real NotificationListener-path self-test did not complete;
- outbound still failed whenever the main app UI was not open;
- inbound continued to work.

Treat background outbound and real Gmail as broken until `.20-alpha.1` produces contrary target evidence.

## Root cause addressed in `.20`

The queue-drain code from `.19-alpha.2` lived inside the Notifee foreground runner, but that runner was registered lazily from UI/recovery code. It was not guaranteed to be registered when Android recreated a process for the native foreground service.

`.20-alpha.1` registers the runner from `index.js` before `AppRegistry.registerComponent`, outside mounted React components, and guards one registration per process generation.

## Foreground runner diagnostics

`ForegroundTransportRuntimeStore` keeps content-free lifecycle timestamps:

- registered;
- started;
- heartbeat;
- stopped.

Heartbeat interval: 15 seconds.  
Stale threshold: 45 seconds.

Settings now reports:

- `Foreground transport runner: active`; or
- `Foreground transport runner: no fresh heartbeat`.

A test should not be interpreted until this state is recorded.

## Self-test changes

- component and listener-path tests request bounded recovery if the runner is stale;
- synthetic notifications remain active for five minutes;
- listener-path test schedules active scans at 0.5, 2, and 5 seconds after rebind;
- listener-path test still cannot directly insert its value into the queue.

## Copy detection changes

The exact Android active-locale `copy` / `copyUrl` rule remains. Candidate collection now covers the clicked node, parent, immediate children/siblings, and Accessibility action labels.

Selection events only remember text. Selection alone never sends.

## Fork relationship

The Go fork owns its connection in a sticky native service and binds Accessibility directly. It also uses a transparent overlay to read clipboard data.

Extended adopts the service-ownership/lifecycle principle only. It does not add overlay permission, ADB, root, READ_LOGS, or Shizuku, and it retains Extended P2P Windows-applied ACK.

## ACK path — preserve exactly

`NATIVE_QUEUE -> NATIVE_IN_FLIGHT -> FOREGROUND_RUNNER_CLAIM -> sendClipBoard -> LOCAL_TRANSPORT_ACCEPTED -> WINDOWS_VALIDATE -> WINDOWS_APPLY -> PEER_ACK -> NATIVE_ACK -> DELETE`

Old/non-Extended peers retain the bounded compatibility fallback.

## Not proven

CI is not target proof. The following remain unproven:

- in-place alpha.20 install/settings retention;
- fresh foreground runner heartbeat on HONOR;
- deterministic component/transport test;
- true listener-path test;
- background Copy cue exposure and queueing;
- UI-closed, removed-from-recents, locked, or screen-off outbound;
- real Gmail notification delivery/extras;
- exactly-once target behavior;
- battery and tray behavior.

Canonical handoff: `docs/NEXT_CHATGPT_HANDOFF.md`. PR #1 remains open and Draft.
