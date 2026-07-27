from pathlib import Path

path = Path("ClipCascade_Mobile/src/App.js")
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
    """import StartForegroundService from './StartForegroundService';""",
    """import StartForegroundService from './StartForegroundService';
const { formatP2SOutboxStatus } = require('./P2SOutboxStatus');""",
    "formatter import",
)

replace_once(
    """  // State to manage websocket page p2p message
  const [wsPageP2PMessage, setWsPageP2PMessage] = useState('');

  // files download button""",
    """  // State to manage websocket page p2p message
  const [wsPageP2PMessage, setWsPageP2PMessage] = useState('');

  // Non-payload status for the persistent P2S text outbox.
  const [p2sOutboxMessage, setP2SOutboxMessage] = useState('');

  // files download button""",
    "outbox state",
)

replace_once(
    """      'p2pStatusMessage',
      'filesAvailableToDownload',""",
    """      'p2pStatusMessage',
      'p2sTextOutboxStatus',
      'filesAvailableToDownload',""",
    "poll key",
)

replace_once(
    """        if (latest.server_mode === 'P2P') {
          const msg2 = latest.p2pStatusMessage;
          if (msg2 !== null) {
            setWsPageP2PMessage(msg2);
          }
        }

        // Files available to download""",
    """        if (latest.server_mode === 'P2P') {
          const msg2 = latest.p2pStatusMessage;
          if (msg2 !== null) {
            setWsPageP2PMessage(msg2);
          }
          setP2SOutboxMessage('');
        } else if (latest.server_mode === 'P2S') {
          setWsPageP2PMessage('');
          setP2SOutboxMessage(
            formatP2SOutboxStatus(latest.p2sTextOutboxStatus),
          );
        } else {
          setWsPageP2PMessage('');
          setP2SOutboxMessage('');
        }

        // Files available to download""",
    "poll projection",
)

replace_once(
    """        setWsPageMessage('');
        setWsPageP2PMessage('');
        await clearFiles();""",
    """        setWsPageMessage('');
        setWsPageP2PMessage('');
        setP2SOutboxMessage('');
        await clearFiles();""",
    "service toggle reset",
)

replace_once(
    """            {wsPageP2PMessage !== '' && (
              <Text style={styles.message}>{wsPageP2PMessage}</Text>
            )}
            {/* File download button */}""",
    """            {wsPageP2PMessage !== '' && (
              <Text style={styles.message}>{wsPageP2PMessage}</Text>
            )}
            {/* Display persistent P2S text outbox metadata only. */}
            {p2sOutboxMessage !== '' && (
              <Text style={styles.message}>{p2sOutboxMessage}</Text>
            )}
            {/* File download button */}""",
    "outbox display",
)

path.write_text(text, encoding="utf-8")
