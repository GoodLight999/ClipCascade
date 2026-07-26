# Experiment Log — 2026-07-27 Desktop Reconnect Recovery

This is an append-only companion to `docs/EXPERIMENT_LOG.md`.

## Experiment DESKTOP-001

### Goal

Recover the independently useful Windows/Linux reconnect progress without restoring failed PR #3 wholesale and without redesigning the existing server protocol or STOMP transport.

### Recovery boundary

Permitted source of evidence:

- product-code commit `a94b830fb954d09fc742b39833cebd5915988566`;
- successful old Linux source artifact `8632420551`;
- upstream-aligned baseline `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`.

Permitted files/features:

- connection state and error models;
- retry policy;
- connection controller;
- STOMP handshake/readiness changes;
- STOMP manager integration;
- GUI/CLI tray projection;
- focused tests;
- Windows/Linux packaging workflow.

Explicitly excluded:

- failed Android diagnostics UI;
- speculative Android acquisition vocabulary/framework;
- old handoff assumptions;
- wholesale PR restoration.

### Why the old source artifact was used

The successful Linux source artifact was downloaded and inspected instead of manually re-deriving all archived code from patches. This reduced transcription risk and complied with the no-wheel-reinvention rule. Every recovered file was still compared with the upstream baseline before use.

### Review finding before recovery

The archived GUI and CLI tray changes called `logging.error(...)` but lacked `import logging`.

Decision:

- do not blindly restore the archived files;
- add the missing imports during selective recovery;
- retain focused syntax and behavior tests on both operating systems.

## Recovered implementation

### Authoritative connection model

Recovered:

- `ConnectionState`: `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECT_WAIT`, `AUTH_REQUIRED`, `STOPPING`, `FATAL_ERROR`;
- immutable `ConnectionSnapshot`;
- sanitized `ConnectionErrorInfo` and stable error codes;
- bounded exponential retry with jitter;
- cancellable timers;
- retry-generation invalidation for stale callbacks;
- observer isolation;
- last connected, disconnected, accepted-send, and valid-receive observations.

### STOMP readiness

Changed existing `stomp_ws/client.py` so connection success requires:

1. WebSocket open;
2. STOMP `CONNECT` transmission;
3. STOMP `CONNECTED` frame;
4. successful connected callback, including subscription setup.

Failure paths unblock callers and report failure for:

- WebSocket open timeout;
- STOMP handshake timeout;
- socket error;
- close before `CONNECTED`;
- STOMP `ERROR` frame;
- subscription/connected-callback failure.

Explicit disconnect suppresses the remote-close callback.

### STOMP manager integration

The existing protocol and destinations were retained.

Recovered behavior:

- one authoritative connection controller;
- fresh client per retry attempt;
- automatic runtime reconnect;
- manual immediate reconnect while waiting;
- no sleep or recursive reconnect inside socket callbacks;
- login-required and fatal states;
- clipboard monitor starts once;
- transport cleanup on disconnect;
- connection lost/restored notifications;
- send/receive observations in snapshots.

### GUI and CLI

Recovered state-derived actions:

- connect;
- cancel connecting;
- disconnect;
- reconnect now;
- login required;
- stopping;
- retry after fatal error.

P2P remains on its legacy boolean fallback until separately migrated.

## Tests

Created focused tests for:

- retry jitter, cap, and invalid inputs;
- duplicate connect rejection;
- successful transition and observations;
- recoverable retry scheduling;
- manual reconnect cancellation;
- stale timer invalidation;
- authentication failure;
- stable-connection retry reset;
- observer and scheduler failures;
- payload-free snapshots;
- WebSocket/STOMP handshake success and failure paths;
- explicit-disconnect callback suppression;
- tray projection for every state and legacy fallback;
- STOMP manager automatic/manual reconnect;
- fresh client creation;
- monitor-start-once behavior;
- send/receive observations;
- disconnect cleanup.

## Workflow DESKTOP-BUILD-001

Workflow: `.github/workflows/stability-desktop.yml`

Run: `30210415035`

Product head: `4174f87e5c4c4d5ffaf6ed07b1862fcfb77623d6`

Passed jobs:

1. syntax checks and unit tests on Ubuntu;
2. syntax checks and unit tests on Windows;
3. Windows PyInstaller EXE build;
4. Linux source-package build.

The artifact jobs depend on both OS test jobs, so packaging does not run unless both test matrices pass.

## Artifact evidence

### Windows

- Artifact ID: `8634309346`
- Artifact: `ClipCascade-Windows-stability`
- Inner file: `ClipCascade-Windows-stability.exe`
- Inner file size: `57,456,012` bytes
- SHA-256: `4dc7aa89917ae3f0779428327013745dc2c64e33809ee4633fcb2308598e0b69`
- Independent identification: PE32+ executable for Windows GUI, x86-64
- Artifact ZIP integrity: passed
- Embedded checksum: matched independently recalculated checksum

### Linux

- Artifact ID: `8634293452`
- Artifact: `ClipCascade-Linux-stability`
- Inner file: `ClipCascade-Linux-stability.tar.gz`
- Inner file size: `60,389` bytes
- SHA-256: `8e3421abf268cd9a6488ec9a8353ae92c5bd88207529324ae83eabdd390640d6`
- Independent identification: gzip-compressed Unix tar archive
- Tar integrity: passed
- Entry count: `59`
- Embedded checksum: matched independently recalculated checksum

## Conclusion

DESKTOP-001 and DESKTOP-BUILD-001 passed at the source, unit-test, and packaging levels.

This is real progress recovered from prior work, not a new desktop rewrite. The server protocol and existing application structure remain intact.

## Claims not yet proven

- generated EXE launch on the user's Windows installation;
- public-server authentication and subscription with the generated EXE;
- real forced network-loss recovery;
- real GUI tray rendering and menu behavior;
- in-process recovery from `AUTH_REQUIRED` without logoff/login;
- P2P migration to the authoritative snapshot contract;
- application-level delivery acknowledgement;
- durable outbound queue.

## Exact next evidence

1. Launch the EXE on Windows and authenticate against the existing server.
2. Confirm the tray displays connected only after the real subscription succeeds.
3. Disable/re-enable network access and record lost notification, retry countdown, reconnection, and restored notification.
4. Trigger manual reconnect during retry wait.
5. Confirm clipboard monitoring starts only once after repeated reconnects.
6. Record all runtime findings in the next append-only experiment log.
