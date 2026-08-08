# Experiment log — Shizuku official-source re-audit after HONOR failure

Date: 2026-08-08 (Asia/Tokyo)

Branch: `stability-recovery`

Base invariant: `main` remains `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`.

## Why this record exists

A HONOR DNP-NX9 / Android 16 diagnostic generated at `2026-08-08T08:21:04.855+09:00` was supplied again after repeated Shizuku frustration. The report contains the exact old failure:

```text
Unsupported IClipboard#getPrimaryClip signature: (java.lang.String, java.lang.String, int, int, java.lang.String)
```

Before changing product code again, the current correction was re-audited only against Shizuku's official source/API documentation and AOSP Android source. The purpose was to prevent another speculative vendor-Binder patch.

## Chronology matters

The diagnostic report predates the current correction.

- failing diagnostic: `08:21:04.855 JST`;
- commit `744f0efff09d245f0dd5b0a0ec4073e4d1c28e85` (`fix(android): delegate Shizuku clipboard reads to device framework`): `08:27:43 JST`;
- final artifact-verified product head `30c179d9099f67afaa2b4e93a5164fd5ceae5808`: `09:00:50 JST`.

Therefore the supplied report cannot be evidence that the current framework-delegation implementation failed. It is evidence for the rejected direct-hidden-`IClipboard` implementation that existed immediately before the correction.

## Official Shizuku source findings

Primary sources:

- Shizuku-API README / UserService documentation:
  https://github.com/RikkaApps/Shizuku-API/blob/master/README.md
- Shizuku-API UserService implementation:
  https://github.com/RikkaApps/Shizuku-API/blob/master/server-shared/src/main/java/rikka/shizuku/server/UserService.java
- Shizuku ServiceStarter:
  https://github.com/RikkaApps/Shizuku/blob/master/starter/src/main/java/moe/shizuku/starter/ServiceStarter.java

Findings:

1. A UserService runs with Shizuku's root or ADB-shell identity, but is not a normal Android application process. Shizuku explicitly warns that even when a `Context` is supplied, not all ordinary `Context` APIs are guaranteed to work; Android source must be checked for the API being used.
2. Since Shizuku v13, the service class may expose a constructor receiving `Context`; Shizuku tries that constructor first.
3. Shizuku's implementation computes the Android user from the client application's UID, creates the client package context for that user, constructs an Application from it, and passes that Application as the constructor `Context`.
4. UserService `tag` identifies the logical service. A changed `UserServiceArgs.version` for the same tag causes a new implementation to be started and the previous implementation to be destroyed. This supports keeping a dedicated UserService implementation version independent of product `versionCode`.
5. The expected Shizuku identities remain UID `0` for root and UID `2000` for ADB shell.

These findings support the current Context-constructor path and the existing dedicated `USER_SERVICE_IMPLEMENTATION_VERSION = 4` mechanism. They do not support returning to hidden Binder reflection.

## AOSP Android 16 clipboard findings

Primary sources:

- Android 16 ClipboardService:
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/clipboard/ClipboardService.java
- Android 16 Shell manifest:
  https://android.googlesource.com/platform/frameworks/base/+/android16-release/packages/Shell/AndroidManifest.xml
- AOSP ClipboardManager:
  https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/content/ClipboardManager.java

Findings:

1. `ClipboardService.clipboardAccessAllowed` first verifies UID/package ownership with `AppOpsManager.checkPackage(uid, callingPackage)`.
2. Android 16 then explicitly permits a calling package holding `READ_CLIPBOARD_IN_BACKGROUND` to read the clipboard without foreground focus.
3. Android 16's platform Shell package is `com.android.shell` and declares `android.permission.READ_CLIPBOARD_IN_BACKGROUND`.
4. Framework `ClipboardManager.getPrimaryClip()` does not ask application code to synthesize the hidden Binder call. It obtains operation package, attribution tag, user ID, and device ID from its own `Context` and invokes the platform `IClipboard` implementation.
5. A package Context therefore matters for clipboard access: using a `com.android.shell` Context in a shell-UID UserService aligns the public framework manager's calling package with the Binder identity and with the platform permission checked by `ClipboardService`.
6. The device framework is the appropriate compatibility boundary for an OEM-modified hidden Binder ABI. The application must not guess the meaning of HONOR's observed fifth `String` parameter.

## Current product path re-audited

Current source remains:

```text
Shizuku v13 supplied Context
    -> verify requested Android user
    -> same-user com.android.shell package Context
    -> device framework ClipboardManager
    -> ClipboardManager.primaryClip
```

This is consistent with the inspected Shizuku and AOSP sources for the actual target mode: Shizuku started through ADB, UserService UID `2000`.

The existing application-side direct hidden `IClipboard` reflection remains rejected.

## Decision: no new speculative product patch

No product-code change was made during this re-audit.

Reason: the only newly supplied runtime evidence is timestamped before the framework-delegation fix. Changing the current implementation before it receives one target-device execution would discard a source-backed correction without contrary runtime evidence.

This is deliberate experimental discipline, not an assumption that the runtime issue is solved.

## Current verification boundary

The framework-delegation implementation is:

- source-backed by official Shizuku implementation and AOSP Android source;
- source-tested and regression-guarded;
- CI-green;
- packaged into the accepted APK;
- independently inspected to exclude the old hidden-`IClipboard` error path.

It is still **not HONOR-runtime-proven**.

The next target-device experiment must use the accepted corrected APK, then run the manual Shizuku read test. Acceptance requires:

- `Shizuku successes` increments;
- the old unsupported-signature error does not recur;
- a known non-empty text clipboard is returned through the manual test without leaking payload into diagnostics.

If that corrected APK fails, preserve the **new exact error/state** and diagnose that evidence against official Shizuku/AOSP sources. Do not guess OEM Binder arguments.

## Shared-documentation contribution

The reusable findings from this audit were also added to the user's Notion `Shizukuマニュアル`:

- UserService is privileged but not a normal app process;
- v13 Context construction behavior;
- tag/version replacement semantics;
- Binder UID/package identity checks;
- Android 16 shell clipboard permission;
- prohibition on treating hidden AOSP Binder signatures as stable OEM ABI;
- device-framework manager as the preferred compatibility boundary when it owns the hidden Binder call;
- requirement to separate CI/package proof from real-device proof.
