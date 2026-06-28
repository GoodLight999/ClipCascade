# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`

Current development phase: validate the guided bilingual Android setup and full-path synthetic verification-code test on HONOR 400 Pro, then execute the screen-off SMS/email and recovery matrix. Keep PR #1 Draft until real-device validation and upstream regression checks pass.

Latest runtime addendum: read `docs/LATEST_RUNTIME_FIXES_HANDOFF.md` for the real Yahoo! JAPAN SMS success, stable Android test-signing migration, Windows Quit repair, and automatic-recovery status wording.

## 2026-06-28 Android stability and redundancy work

Starting branch head: `912271763def1bf048f5bf626cf004c8b472e84b`.

- Read all canonical handoff documents in the required order.
- Confirmed that screen-off/locked SMS and email verification-code delivery was already an explicit product goal, verification requirement, Definition-of-Done item, handoff requirement, and test-matrix section.
- Confirmed PR #1 remained open and Draft.
- Fetched matching baseline Android/Windows artifacts and confirmed the APK contained `assets/index.android.bundle`.
- Investigation correction: an initial hypothesis that notification-listener rebind handling was missing was wrong. `NotificationCodeListenerService.onListenerDisconnected()` already called `requestRebind(...)`; that implementation was preserved.

Implemented and CI-validated changes:

1. `852141452014ea7265ebbf7587f4d56e71f525b8` adds a delayed WorkManager heartbeat while preserving immediate Headless JS boot recovery. Android run `28304486580`: success.
2. `0ec58ffe0facb1934010f0270b8803ea65eb6a15` tracks the replacement default network so a late `onLost` for the old network cannot cancel recovery. Android run `28304554055`: success.
3. `eb8dfe5a3de9d8e00faffbea5fb541b34dd1af5b`, `1b95ccf7074d614a97521d519f3bb9b842a1f0f6`, and `230a10562456dc96d0f354e2d78e01123d10a800` stop treating `Disconnected` as a connected state in recovery, clipboard dispatch, and verification dispatch. Android runs `28304618791`, `28304684578`, and `28304755556`: success.

The P2P Windows-applied ACK transformation scripts, relay IDs, peer ACK handling, fallback timer, queue deletion semantics, and Windows receiver were not modified by these changes.

## 2026-06-28 Windows authentication/API failure and real validation

Observed failure:

- `POST /login` ended with HTTP 200 and was logged as successful.
- `/csrf-token` and `/server-mode` then returned responses that failed JSON decoding at byte zero.
- The unhandled `/server-mode` decode exception terminated Windows startup.

Confirmed client defects:

- almost any HTTP 200 without `bad credentials` was treated as authenticated;
- login used a `requests.Session`, but follow-up calls discarded it and forwarded only `JSESSIONID`;
- mandatory JSON endpoints did not validate empty/HTML responses, final redirect path, or payload shape;
- mandatory API validation errors escaped the login flow.

Repairs:

- `bac2ed9f33391cee98d91532df883abb370fc4b7`: retain one authenticated session, preserve all cookies, validate responses, add timeouts, and log only content-free response metadata.
- `9b2c04544a8ed407b44575891ebaf512f1f7fa90`: return rejected authenticated sessions to the login flow instead of terminating Windows.
- `fb4dd2ae60c2ba4622a54a4b4368bf681a464dc0` and `fae212a9fbe127f582e6bdace5c8a0c8f25fb575`: cover empty/HTML responses, additional cookies, HTTP-200 login-form false positives, and transport failures.
- `57293aa060efadcef87c3442108c0ed43c27ec45`: make HTTP tests mandatory beside P2P ACK tests.

Every listed code commit passed both Windows and Android CI. The user then ran the repaired Windows artifact against the real deployment and confirmed that login, the Windows GUI, and synchronization worked. The complete authenticated session/additional-cookie repair was therefore effective in the user's deployment.

## 2026-06-28 Guided setup, bilingual UI, and verification-code test

User-confirmed baseline before this phase:

- Windows GUI works against the real deployment.
- Android automatic copied-text relay works through Accessibility in the user's current test scenario.
- The current Extended path does not depend on the old ADB commands: READ_LOGS and overlay permissions are absent from the manifest, and the transformed distributed UI removes the old commands.

Implemented:

- English default Android resources and complete Japanese `values-ja` resources for settings, service labels/descriptions, setup steps, diagnostics, and synthetic test status.
- System-locale selection exposed to React Native; the persistent settings entry and primary Extended React UI labels now select Japanese or English.
- A display-only status localizer translates connection/login/P2P status for Japanese users without changing internal protocol/storage tokens.
- Five-step guided setup:
  1. Android 13+ notification permission
  2. Accessibility clipboard-sharing service
  3. notification-listener access
  4. battery-optimization exemption
  5. HONOR/MagicOS background/auto-launch confirmation
- Direct routes to the relevant Android settings. Manufacturer-specific switches remain honestly user-confirmed because Android has no reliable common query API for every vendor page.
- A real local synthetic notification with a fresh random six-digit value.
- The synthetic test traverses NotificationListenerService, the local extractor, persistent verification queue, existing React transport, and the existing native acknowledgement path.
- Live test states: posted, detected, queued, extraction failed, deduplicated, post failed, and acknowledged.
- Synthetic status/value expires after five minutes.
- Test notification bypasses the optional source-app filter only when explicitly marked with the private synthetic-test extra; normal ClipCascade foreground notifications remain ignored.
- Standard title/text, expanded text, text lines, conversation title, MessagingStyle current messages, and historic messages are inspected transiently.
- P2P ACK semantics remain unchanged. Test acknowledgement is marked only when the existing native ACK callback occurs.
- Build version bumped to `3.2.1-extended.2`.

Extractor improvements:

- Japanese authentication/confirmation, login/sign-in, one-time password, identity verification, two-step authentication, and security-code language.
- English OTP, one-time password/passcode, verification/security/auth/login/sign-in/confirmation/access/two-factor language.
- Numeric, compact alphanumeric, prefixed, grouped, full-width, code-before-keyword, and code-after-keyword forms.
- Rejection of dates, times, years, nearby monetary values, phone numbers, tracking references, URL/email substrings, and generic postal/promotion/error codes.
- Candidate scoring favors explicit relation, same line, standard lengths, strong authentication terms, and expiry language.
- A transaction amount or destination email address may coexist with a separate legitimate OTP.

Trial-and-error record:

1. `000d964408d7f4824cd0f16fb9d47954ccddf425` failed Android run `28307536157`. The grouped-candidate pattern joined ordinary words with following values, producing candidates such as `ON202606` from `on 2026-06-27`, and swallowed a valid alphanumeric code with its preceding word.
2. `acd5cb9cf0dad674fb58b1d1b691d4f3cdf941ee` restricted space grouping to numeric groups and hyphen grouping to alphanumeric groups.
3. `aa799b3553b2bfa160f793324972b072edead18b` expanded the Japanese/English positive and negative corpus. Android run `28307672613`: success.
4. `9d06c1b175aaff118f09be7df929486155cc67e9` added transaction-notification positive cases. Android run `28307814762`: success.
5. `abb9ca720ab728c56d8ee490132f0c9c1f6ae572` failed Android run `28307830787` only in a new bilingual-bundle assertion. Metro escaped Japanese literals, so raw Japanese grep against the bundle was invalid; compilation was not reached.
6. `9b4e5da9b62c6a3054421c90697cf6363ce67134` moved bilingual/no-ADB assertions to transformed `App.js`/`AppRoot.js`. Run `28307959797` completed status localization, source assertions, bundle creation, unit tests, APK assembly, embedded-bundle verification, and artifact upload successfully.
7. `bc7cbfc7005fa925662bc0dc3e9969e0b799a9a2` failed Android run `28308003116` in one new positive test: `Use 462881 to verify user1234@example.com`. The extractor rejected no false candidate; it lacked an authentication keyword pattern for a `verify` phrase addressed directly to an email address.
8. `5433eb8e8ffaae47ed6873af67ea1c1df6824333` adds explicit support for `verify <email-address>` while retaining candidate-overlap rejection for digits that are actually part of the address. Android run `28308127548`: success. Windows run `28308127573`: success.

## 2026-06-28 Upstream-promotion cleanup and visible boot resume

User feedback after running the prior artifact: synchronization remained very stable, but the app still showed the upstream project's update banner and footer links, and the floating Sharing setup button covered the footer.

Implemented:

- removed the upstream version and funding network lookups from the transformed Extended app;
- removed the upstream release banner entirely;
- removed the `GITHUB`, `HELP`, `DONATE`, and `HOMEPAGE` footer controls from both login and connected pages;
- moved Sharing setup from an absolute overlay to a dedicated non-overlapping bottom bar;
- exposed an always-visible localized `Resume sync at startup` / `起動時に同期を再開` switch in the same bottom bar;
- connected the switch directly to the existing `relaunch_on_boot` AsyncStorage value used by `BootReceiver`;
- preserved existing boot semantics: automatic recovery is requested after `BOOT_COMPLETED` only when the switch is enabled and synchronization was active before reboot;
- preserved the immediate Headless JS attempt plus the delayed 30-second WorkManager fallback;
- removed the duplicate boot checkbox from the transformed advanced login settings;
- refreshes `relaunch_on_boot` from native storage before the login form persists its complete state, preventing stale React state from overwriting the always-visible switch;
- CI now rejects upstream URLs, release-banner text, removed footer labels, old ADB commands, duplicate boot controls, and missing canonical boot-switch plumbing;
- Android version bumped to `3.2.1-extended.3`.

Trial-and-error record:

1. `c0bcdc59a80e35e15794e778f16c1a5b87c5833b` validated the upstream-promotion removal and non-overlapping bottom bar. Android run `28310040428`: success.
2. `9c1e7faf88d9fdfa6f92f5b608879299fd1903fb` failed Android run `28310252416` after the first boot-control transform. Its regex began at the first login-form row and consumed every row through the boot checkbox, so the following status-localization transform could not find the login-status element.
3. `1744ceb79eae126f04ed540b26ba3c61afbfabfe` replaced the broad regex with index-based isolation of the nearest row containing the unique `relaunch_on_boot` marker. The transformed UI/source assertions then passed.

## 2026-06-28 Runtime hardening after real SMS success

- The user deliberately chose Yahoo! JAPAN SMS authentication instead of a passkey, and the real SMS verification value reached the Windows clipboard. The screen/lock duration was not specified, so this is recorded as one real SMS success rather than completion of the screen-off matrix.
- Repeated Android package conflicts were traced to ephemeral default debug certificates on GitHub-hosted runners.
- A deterministic public test signer now produces one stable certificate for development APKs. The currently installed old certificate requires one final uninstall; later stable-signed builds can update in place when versionCode increases.
- The test certificate is intentionally public and is not production publisher authentication.
- Windows Quit previously stopped the tray but could leave the nested status window, asynchronous P2P loop, or incomplete transport teardown alive.
- Explicit Quit now closes all Tk windows, stops the tray/watchdog, awaits transport teardown, stops the P2P event loop, flushes logs, and applies a final process-exit guarantee.
- The ambiguous `Automatic reconnect: False` Boolean meant only that no reconnect attempt was active. It is now displayed as `Automatic recovery: Standing by`, `Retrying now`, or `Paused by user`.
- Windows run `28312102162` succeeded with authenticated HTTP, P2P ACK, shutdown/status tests, EXE packaging, and artifact upload.
- Android run `28312102175` built and tested the APK but failed only at the first signer-fingerprint assertion. Diagnostic preservation was added to distinguish expected/generated/APK certificate digests. See `docs/LATEST_RUNTIME_FIXES_HANDOFF.md` for exact semantics and migration tests.

Remaining mandatory work:

- Confirm final Android and Windows CI on the same documentation HEAD.
- Fetch matching Android and Windows artifacts from that HEAD.
- Perform the one-time migration to the stable-signed APK, then prove the next build updates in place without data loss.
- Confirm Windows Quit removes the EXE from Task Manager and immediate relaunch is not blocked by a stale mutex.
- Confirm the bottom bar does not obscure app content and the boot-resume switch persists across process restart.
- Reboot once while synchronization is running and verify automatic background recovery plus the 30-second fallback behavior.
- Follow the five-step setup from a fresh/permission-reset state.
- Run the synthetic notification test in Extended P2P and confirm the status reaches `acknowledged` only after the Windows clipboard contains the synthetic value.
- Repeat the synthetic test with Windows offline and after reconnect.
- Measure real SMS and email notifications with screen on, background, locked, and 1/15/30+ minute screen-off states.
- Record Android 16/MagicOS redaction behavior separately from extractor and transport behavior.
- Exercise Wi-Fi/mobile handover, process kill, reboot, delayed boot fallback, and upstream text/image/file regression.
