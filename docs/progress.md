# Development index

Resume work in this order:

1. `docs/REQUIREMENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/NEXT_CHATGPT_HANDOFF.md`
4. `docs/TEST_MATRIX.md`

Current development phase: validate the guided bilingual Android setup and full-path synthetic verification-code test on HONOR 400 Pro, then execute the screen-off SMS/email and recovery matrix. Keep PR #1 Draft until real-device validation and upstream regression checks pass.

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
8. `5433eb8e8ffaae47ed6873af67ea1c1df6824333` adds explicit support for `verify <email-address>` while retaining candidate-overlap rejection for digits that are actually part of the address. Final CI result must be recorded after completion.

Remaining mandatory work:

- Confirm final Android and Windows CI on the same documentation HEAD.
- Fetch matching Android and Windows artifacts from that HEAD.
- Install the new APK on HONOR 400 Pro.
- Follow the five-step setup from a fresh/permission-reset state.
- Run the synthetic notification test in Extended P2P and confirm the status reaches `acknowledged` only after the Windows clipboard contains the synthetic value.
- Repeat the synthetic test with Windows offline and after reconnect.
- Measure real SMS and email notifications with screen on, background, locked, and 1/15/30+ minute screen-off states.
- Record Android 16/MagicOS redaction behavior separately from extractor and transport behavior.
- Exercise Wi-Fi/mobile handover, process kill, reboot, delayed boot fallback, and upstream text/image/file regression.
