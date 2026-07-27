from pathlib import Path

path = Path("ClipCascade_Mobile/src/StartForegroundService.js")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count == 1:
        text = text.replace(old, new, 1)
        return
    if count == 0 and new in text:
        return
    raise SystemExit(f"{label}: expected one old block, found {count}")


replace_once(
    """const { P2STextOutbox } = require('./P2STextOutbox');""",
    """const { P2STextOutbox } = require('./P2STextOutbox');
const {
  getP2SRetryDelayMs,
  DEFAULT_MAX_DELAY_MS,
} = require('./P2SRetryPolicy');""",
    "retry policy import",
)

replace_once(
    """          const clearP2STextEchoTimer = () => {
            if (p2sTextEchoTimer != null) {
              clearTimeout(p2sTextEchoTimer);
              p2sTextEchoTimer = null;
            }
          };

          const releaseP2STextInFlight = async () => {""",
    """          const clearP2STextEchoTimer = () => {
            if (p2sTextEchoTimer != null) {
              clearTimeout(p2sTextEchoTimer);
              p2sTextEchoTimer = null;
            }
          };

          const reportP2STextRetryError = async error => {
            await setDataInAsyncStorage(
              'wsStatusMessage',
              '❌ Text outbox retry error: ' + error,
            );
          };

          const scheduleP2STextDrain = delayMs => {
            clearP2STextEchoTimer();
            p2sTextEchoTimer = setTimeout(() => {
              p2sTextEchoTimer = null;
              drainP2STextOutbox().catch(reportP2STextRetryError);
            }, Math.max(1, delayMs));
          };

          const releaseP2STextInFlight = async () => {""",
    "retry scheduler",
)

replace_once(
    """              const head = await p2sTextOutbox.peek();
              if (!head || head.state === 'inflight') return;
              if (!(await p2sTextOutbox.markAttempt(head.id))) return;

              try {""",
    """              const head = await p2sTextOutbox.peek();
              if (!head || head.state === 'inflight') return;

              const now = Date.now();
              const retryWaitMs = Number.isFinite(head.nextAttemptAt)
                ? Math.max(
                    0,
                    Math.min(DEFAULT_MAX_DELAY_MS, head.nextAttemptAt - now),
                  )
                : 0;
              if (retryWaitMs > 0) {
                scheduleP2STextDrain(retryWaitMs);
                await setDataInAsyncStorage(
                  'wsStatusMessage',
                  `⏳ Queued text retry in ${Math.ceil(retryWaitMs / 1000)}s`,
                );
                await updateP2STextOutboxStatus();
                return;
              }

              const attemptNumber = head.attempts + 1;
              const retryDelayMs = getP2SRetryDelayMs(attemptNumber);
              if (!(await p2sTextOutbox.markAttempt(head.id, retryDelayMs))) return;

              try {""",
    "not-before gate",
)

replace_once(
    """                p2sTextEchoTimer = setTimeout(() => {
                  p2sTextEchoTimer = null;
                  p2sTextOutbox
                    .releaseInFlight(head.id)
                    .then(updateP2STextOutboxStatus)
                    .then(drainP2STextOutbox)
                    .catch(async error => {
                      await setDataInAsyncStorage(
                        'wsStatusMessage',
                        '❌ Text outbox retry error: ' + error,
                      );
                    });
                }, 30000);

                await setDataInAsyncStorage(
                  'wsStatusMessage',
                  `📤 Sending queued text (attempt ${head.attempts + 1})`,
                );
              } catch (error) {
                await p2sTextOutbox.releaseInFlight(head.id);
                throw error;""",
    """                p2sTextEchoTimer = setTimeout(() => {
                  p2sTextEchoTimer = null;
                  p2sTextOutbox
                    .releaseInFlight(head.id)
                    .then(updateP2STextOutboxStatus)
                    .then(drainP2STextOutbox)
                    .catch(reportP2STextRetryError);
                }, retryDelayMs);

                await setDataInAsyncStorage(
                  'wsStatusMessage',
                  `📤 Sending queued text (attempt ${attemptNumber}; retry timeout ${Math.ceil(retryDelayMs / 1000)}s)`,
                );
              } catch (error) {
                await p2sTextOutbox.releaseInFlight(head.id);
                scheduleP2STextDrain(retryDelayMs);
                throw error;""",
    "echo timeout backoff",
)

path.write_text(text, encoding="utf-8")
