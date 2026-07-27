from pathlib import Path

path = Path("ClipCascade_Mobile/src/StartForegroundService.js")
text = path.read_text(encoding="utf-8")

fixes = [
    (
        "          // start websocket stomp connection          // start websocket stomp connection",
        "          // start websocket stomp connection",
        "duplicated connection comment",
    ),
    (
        """                      if (await newCB(hcb)) {
                        previous_clipboard_content_hash = hcb;""",
        """                      if (!acknowledgedQueuedText && (await newCB(hcb))) {
                        previous_clipboard_content_hash = hcb;""",
        "own echo must not overwrite the local clipboard",
    ),
]

for old, new, label in fixes:
    count = text.count(old)
    if count == 1:
        text = text.replace(old, new, 1)
    elif count == 0 and new in text:
        continue
    else:
        raise SystemExit(f"{label}: expected one old occurrence, found {count}")

path.write_text(text, encoding="utf-8")
