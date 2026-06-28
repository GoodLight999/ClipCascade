# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`

Current development phase: fetch the matching Android and Windows artifacts built with P2P clipboard-applied acknowledgement, then execute the HONOR 400 Pro / Windows test matrix. Keep PR #1 Draft until real-device validation and upstream regression checks pass.

## 2026-06-28 Android stability and redundancy work

Starting branch head: `912271763def1bf048f5bf626cf004c8b472e84b`.

- Read all canonical handoff documents in the required order.
- Confirmed that screen-off/locked SMS and email verification-code delivery is already an explicit product goal, verification requirement, Definition-of-Done item, handoff requirement, and test-matrix section. No duplicate requirement was added.
- Confirmed PR #1 remains open and Draft.
- Fetched the matching baseline artifacts from successful Android run `28304257167` and Windows run `28304257166`. The baseline APK contained `assets/index.android.bundle`.
- Investigation correction: an initial hypothesis that notification-listener rebind handling was missing was wrong. `NotificationCodeListenerService.onListenerDisconnected()` already calls `requestRebind(...)`; that implementation was preserved.

Implemented and CI-validated changes:

1. `852141452014ea7265ebbf7587f4d56e71f525b8` — added a delayed WorkManager heartbeat as a redundant boot-recovery path. The existing Headless JS boot path remains primary; if Android declines or blocks it, the delayed worker remains scheduled. The worker exits after a successful heartbeat and therefore does not restart an already healthy service. Android run `28304486580`: success.
2. `0ec58ffe0facb1934010f0270b8803ea65eb6a15` — made default-network handover tracking network-specific, so a late `onLost` callback for the old network cannot cancel recovery scheduled for the replacement Wi-Fi/mobile network. Android run `28304554055`: success.
3. `eb8dfe5a3de9d8e00faffbea5fb541b34dd1af5b` — corrected network recovery readiness parsing so `Disconnected` is not treated as containing a valid `Connected` state. Android run `28304618791`: success.
4. `1b95ccf7074d614a97521d519f3bb9b842a1f0f6` — applied the strict connected-state check to the persistent clipboard queue. Android run `28304684578`: success.
5. `230a10562456dc96d0f354e2d78e01123d10a800` — applied the strict connected-state check to the persistent verification-code queue. Android run `28304755556`: success.

The P2P Windows-applied ACK transformation scripts, relay IDs, peer ACK handling, fallback timer, queue deletion semantics, and Windows receiver were not modified by these changes. Every code commit above also completed the matching Windows CI successfully.

## 2026-06-28 Windows authentication/API failure

Observed on the real Windows artifact:

- `POST /login` ended with HTTP 200 and was logged as successful.
- `/csrf-token` and `/server-mode` then returned responses that failed JSON decoding at byte zero.
- The unhandled `/server-mode` decode exception terminated Windows startup before P2P could be tested.

The old build did not record response status metadata beyond the JSON exception, so the server-side cause cannot yet be distinguished between an empty response, HTML/login redirect, reverse-proxy interception, missing additional session cookie, or server/client endpoint mismatch.

Confirmed client defects:

- login treated almost any HTTP 200 without the words `bad credentials` as authenticated;
- the login used a `requests.Session`, but later authenticated calls discarded that session and manually forwarded only `JSESSIONID`;
- mandatory JSON endpoints called `.json()` without checking empty bodies, HTML, final redirect path, or payload shape;
- mandatory API validation errors escaped the login flow and shut down the Windows application.

Implemented and CI-validated changes:

1. `bac2ed9f33391cee98d91532df883abb370fc4b7` — retain one authenticated `requests.Session`, preserve all server/proxy cookies, validate JSON responses, classify empty/HTML/non-object responses using content-free metadata, add request timeouts, and stop logging private server URLs from generic request errors. Windows run `28305923495`: success. Android run `28305923474`: success.
2. `9b2c04544a8ed407b44575891ebaf512f1f7fa90` — keep authenticated API validation failures inside the Windows login flow, clear the rejected session, display a specific diagnosis, and reopen login instead of reporting an unexpected application crash. Windows run `28305936639`: success. Android run `28305936584`: success.
3. `fb4dd2ae60c2ba4622a54a4b4368bf681a464dc0` — add unit coverage for valid JSON, empty bodies, HTML redirects, unsupported modes, additional-cookie retention, and response-body secrecy. Windows run `28305951497`: success. Android run `28305951534`: success.
4. `57293aa060efadcef87c3442108c0ed43c27ec45` — make the authenticated HTTP tests mandatory in Desktop Windows CI alongside the existing P2P ACK tests. Windows run `28305957237`: success. Android run `28305957212`: success.
5. `03126bea1369d68d3afc6565ba38f295b585ac51` — normalize authenticated endpoint connection/timeout failures into the same safe login-recovery path. Windows run `28306013776`: success. Android run `28306013777`: success.
6. `fae212a9fbe127f582e6bdace5c8a0c8f25fb575` — add tests for HTTP-200 login-form false positives and content-free transport-error normalization. Windows run `28306019657`: success. Android run `28306019658`: success.

Expected next observation:

- If preserving the complete authenticated session fixes the deployment, `/server-mode` will return valid JSON and startup will continue.
- If it does not, the next log will identify `endpoint`, HTTP status, content type, body byte count, final path, and redirect status codes without writing response contents, cookies, credentials, or the private server URL.
- An empty `/csrf-token` remains non-fatal because it is used for logout; `/server-mode` remains mandatory because choosing P2S/P2P by guess would be unsafe.

Next mandatory work:

- Build matching Windows and Android artifacts from the final documentation HEAD.
- Run the repaired Windows login against the user's actual server and retain the new content-free response diagnosis if authentication still fails.
- Install the matching APK on HONOR 400 Pro and run `docs/TEST_MATRIX.md`, beginning with settings and synthetic test relay.
- Measure real SMS and email notification fields with screen on, background, locked, and 1/15/30+ minute screen-off states.
- Record whether Android 16/MagicOS redacts OTP-like notification content. Do not claim screen-off reliability before those tests.
- Exercise Wi-Fi/mobile handover, Windows-offline queueing, process kill, reboot, and P2P clipboard-applied ACK on real devices.
