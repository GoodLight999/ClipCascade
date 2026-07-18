# Selection-only Copy False Positive Handoff — 2026-07-18

## User report

The user is still running an older Android build and reported that merely selecting text can cause the selected value to be relayed even though Copy was never pressed.

The user asked whether the latest `.12` build already fixed this.

## Audit result

It did not fully fix it.

The Accessibility selection event itself only remembered the selected range and did not enqueue it. However, selecting text commonly opens Android's floating selection toolbar. That toolbar contains a visible `Copy` / `コピー` command and can emit `TYPE_WINDOW_CONTENT_CHANGED` or `TYPE_WINDOW_STATE_CHANGED`.

The `.12` reliability repair restored source-node inspection for those passive window events. Its generic Copy-marker matcher therefore could mistake the appearance of the toolbar command for proof that Copy had completed, then enqueue the remembered selection.

This explains a selection-only false positive without requiring an actual clipboard change.

## `.13` repair

Build identity:

- versionName: `3.2.1-extended.13-standalone`
- versionCode: `320117`

A new pure classifier, `CopyCueClassifier`, separates two classes of evidence:

1. **Direct interaction evidence**
   - `AccessibilityNodeInfo.ACTION_COPY`;
   - `TYPE_VIEW_CLICKED` or `TYPE_VIEW_CONTEXT_CLICKED` on a `Copy` / `コピー` control.

2. **Passive completion evidence**
   - announcement, notification-state, window-state, or window-content events must contain completion wording such as `Copied`, `Copied to clipboard`, `コピーしました`, `クリップボードにコピーしました`, or `コピー済み`.

A passive event containing only the command label `Copy` / `コピー` is rejected.

The existing `ClipboardManager.OnPrimaryClipChangedListener` remains as a second real-copy signal. It is accepted only when a non-empty Accessibility selection was remembered in the preceding three seconds. ClipCascade's own clipboard writes remain suppressed by `ClipboardWriteGuard`.

## Regression tests

`CopyCueClassifierTest` verifies:

- floating-toolbar `Copy` is not a passive completion;
- floating-toolbar `コピー` is not a passive completion;
- direct click on `Copy` / `コピー` is accepted;
- `Copied`, `Copied to clipboard`, `コピーしました`, and `クリップボードにコピーしました` are accepted;
- ordinary page text discussing how to copy is not accepted as a completion.

## Validation at code head

Code head before documentation commits: `594c37224bdcfbd96c54b41bd0509cf97f22ef5b`.

- Android standalone CI run `29632041698`: success;
- Desktop Windows CI run `29632041722`: success;
- Android transform verification, unit tests, Kotlin compilation, APK assembly, embedded bundle verification, signer verification, and artifact upload all succeeded.

## Mandatory target-device proof

With Microsoft Phone Link and every competing clipboard synchronizer disabled:

1. install `.13` over the existing app;
2. select a unique string and leave the floating toolbar open without pressing Copy;
3. confirm the Android pending clipboard queue remains unchanged and Windows does not receive the string;
4. dismiss the selection without copying and confirm no delayed relay occurs;
5. repeat in Chrome, Firefox-family browser, Gmail, a notes/editor app, and at least one WebView app;
6. then select and actually press Copy, confirming exactly one peer application and ACK;
7. repeat with the app backgrounded, removed from recents, locked, and screen-off where feasible.

## Preserve

- Extended P2P peer-applied ACK;
- generation-scoped old-peer compatibility fallback;
- persistent native queues;
- relay claim protection;
- React-generation recovery;
- clipboard-change fallback;
- internal clipboard-write echo suppression;
- PR #1 Draft state.

## Do not claim

Do not claim the selection-only false positive is target-device fixed until the `.13` isolated device test passes. CI proves the classifier and build behavior, not the OEM Accessibility event stream on the target HONOR device.
