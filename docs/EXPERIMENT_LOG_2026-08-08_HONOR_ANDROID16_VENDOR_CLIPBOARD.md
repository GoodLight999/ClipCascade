# Experiment log — HONOR Android 16 vendor clipboard ABI

Date: 2026-08-08 (Asia/Tokyo)

Branch: `stability-recovery`

Base invariant: `main` remains `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`.

## Why this record exists

The static-verification campaign completed on 2026-08-04, but the first subsequent target-device test exposed a concrete Shizuku clipboard failure on HONOR/MagicOS. This record preserves the exact failure, the mistaken assumption that caused it, the source-backed correction, and the remaining device acceptance boundary.

This is a correction to the previous implementation, not a rewrite or re-baseline.

## Target-device evidence

Device report supplied 2026-08-08:

- device: HONOR DNP-NX9;
- Android 16 / API 36;
- ClipCascade 3.2.0 standalone;
- Shizuku Binder available: enabled;
- Shizuku permission: enabled;
- Shizuku UserService: enabled;
- UserService UID: 2000;
- Accessibility ACTION_COPY trigger: enabled;
- overlay fallback: enabled;
- ClipCascade runtime active: enabled;
- P2P connection: connected.

Unified capture counters at failure:

- triggers: 3;
- Shizuku attempts: 3;
- Shizuku successes: 0;
- emitted to JavaScript: 0;
- last source: `manual_shizuku_test`;
- last stage: `ignored`.

Exact error:

```text
Unsupported IClipboard#getPrimaryClip signature: (java.lang.String, java.lang.String, int, int, java.lang.String)
```

This proves that Binder acquisition, Shizuku permission, and UserService startup were not the blocking stage in this run. The read failed inside ClipCascade's direct hidden `IClipboard` signature selection.

## Mistaken assumption

The previous UserService deliberately enumerated known AOSP `IClipboard#getPrimaryClip` signatures and failed closed for unknown signatures. That was safer than inventing unknown Binder arguments, but it still embedded an incorrect portability assumption: that an OEM framework would expose one of the enumerated AOSP hidden interfaces.

On this HONOR Android 16 build, the runtime hidden interface exposes a fifth `String` parameter. Current AOSP exposes four parameters:

```text
getPrimaryClip(String pkg, String attributionTag, int userId, int deviceId)
```

Therefore the HONOR signature is a vendor extension. Treating an AOSP hidden Binder ABI as a stable cross-OEM application API was the design error.

## Sources checked before the correction

Primary references:

- AOSP `IClipboard.aidl` current source: https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/content/IClipboard.aidl
- AOSP `ClipboardManager.java` current source: https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/content/ClipboardManager.java
- Shizuku API / UserService developer guide: https://github.com/RikkaApps/Shizuku-API/blob/master/README.md

The AOSP framework `ClipboardManager` itself owns the private `IClipboard` call and supplies package, attribution, user, and device arguments. A vendor framework is compiled against its own vendor Binder interface, so the device framework is a more appropriate compatibility boundary than application-side reflection over the hidden interface.

Shizuku v13 supports a UserService constructor receiving `Context`. ClipCascade uses Shizuku API 13.1.5.

## Rejected correction

Do not add support for the observed HONOR five-argument method by guessing the meaning/value of the fifth `String`.

Rejected examples include:

- passing `null`;
- passing an empty string;
- passing the app package;
- passing `com.android.shell`;
- choosing the largest overload and filling arguments by type.

The diagnostic exposes the parameter type but not its semantic contract. Guessing it would repeat the same hidden-ABI mistake.

## Accepted correction

Commit `744f0efff09d245f0dd5b0a0ec4073e4d1c28e85` replaces direct hidden `IClipboard` reflection in `ShizukuClipboardUserService` with the device framework `ClipboardManager`.

UserService flow:

```text
Shizuku v13 supplied Context
    -> verify requested Android user
    -> create same-user `com.android.shell` package Context
    -> Context.getSystemService(ClipboardManager)
    -> ClipboardManager.primaryClip
```

Why the shell package Context is used:

- the UserService process is UID 2000 when Shizuku is started through ADB;
- ClipboardService validates package identity/AppOps for clipboard access;
- the platform shell package corresponds to the shell Binder identity and carries platform clipboard privileges;
- the device's own `ClipboardManager` now supplies any OEM-specific hidden Binder arguments.

The existing ClipCascade transport, acquisition coordinator, overlay fallback, Accessibility trigger, and STOMP destinations are unchanged.

## Regression contract

Commit `7af159555a61653953ed26817500d53b94c0a23e` replaces the old source contract that required hidden-API reflection with the opposite contract:

- UserService must obtain `ClipboardManager` from the device framework;
- it must use the shell package Context;
- direct `Class.forName` hidden Binder reflection is forbidden in this source path;
- `IClipboard$Stub`, hard-coded device ID arguments, overload selection, and synthetic argument generation are forbidden.

This prevents a future cleanup from reintroducing the cross-OEM hidden-ABI assumption.

## Verification boundary

At the time this record was written, the correction is source-backed but not yet proven on the target HONOR device.

Required next stages:

1. permanent Android CI must compile/test/package the corrected source;
2. inspect the generated APK and checksum;
3. install that APK on the same HONOR DNP-NX9;
4. run the manual Shizuku read test again;
5. verify `Shizuku successes` increments and the unsupported-signature error disappears;
6. then verify foreground and background copy delivery through the unified pipeline;
7. separately verify overlay fallback with Shizuku unavailable.

Do not call the HONOR issue fixed until stage 4/5 succeeds on the real device.
