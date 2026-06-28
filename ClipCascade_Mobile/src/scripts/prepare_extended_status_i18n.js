const fs = require('fs');
const path = require('path');

const appPath = path.resolve(__dirname, '..', 'App.js');
let source = fs.readFileSync(appPath, 'utf8');

function replaceRequired(before, after) {
  if (!source.includes(before)) {
    throw new Error(`Expected App.js text was not found: ${before.slice(0, 100)}`);
  }
  source = source.replace(before, after);
}

replaceRequired(
  `  const tr = (ja, en) => (isJapanese ? ja : en);`,
  `  const tr = (ja, en) => (isJapanese ? ja : en);\n` +
    `  const localizeStatus = value => {\n` +
    `    const text = String(value ?? '');\n` +
    `    if (!isJapanese) return text;\n` +
    `    const replacements = [\n` +
    `      ['Disconnected', '未接続'],\n` +
    `      ['Connecting', '接続中'],\n` +
    `      ['Connected', '接続済み'],\n` +
    `      ['Reconnecting', '再接続中'],\n` +
    `      ['Login successful', 'ログイン成功'],\n` +
    `      ['Login Successful', 'ログイン成功'],\n` +
    `      ['Login failed', 'ログイン失敗'],\n` +
    `      ['Request timed out', '通信がタイムアウトしました'],\n` +
    `      ['Please wait', 'お待ちください'],\n` +
    `      ['Starting foreground service', 'バックグラウンド同期を開始中'],\n` +
    `      ['Stopping foreground service', 'バックグラウンド同期を停止中'],\n` +
    `      ['Peers', 'ピア'],\n` +
    `      ['Live Connections', '有効な接続'],\n` +
    `      ['Files available', '受信可能なファイル'],\n` +
    `      ['Error', 'エラー'],\n` +
    `    ];\n` +
    `    return replacements.reduce(\n` +
    `      (result, [from, to]) => result.split(from).join(to),\n` +
    `      text,\n` +
    `    );\n` +
    `  };`,
);

const replacements = [
  [
    '<Text style={styles.loadingText}>{loadingPageMessage}</Text>',
    '<Text style={styles.loadingText}>{localizeStatus(loadingPageMessage)}</Text>',
  ],
  [
    '<Text style={styles.message}>{loginStatusMessage}</Text>',
    '<Text style={styles.message}>{localizeStatus(loginStatusMessage)}</Text>',
  ],
  [
    '<Text style={styles.message}>{wsPageMessage}</Text>',
    '<Text style={styles.message}>{localizeStatus(wsPageMessage)}</Text>',
  ],
  [
    '<Text style={styles.message}>{wsPageP2PMessage}</Text>',
    '<Text style={styles.message}>{localizeStatus(wsPageP2PMessage)}</Text>',
  ],
  [
    '<Text style={styles.loadingText}>Init Error: {initError[1]}</Text>',
    "<Text style={styles.loadingText}>{tr('初期化エラー: ', 'Init error: ')}{localizeStatus(initError[1])}</Text>",
  ],
  [
    `                  New version available! 🚀 Click here to update ({APP_VERSION}{' '}\n                  ➞ {newVersionAvailable[1]})`,
    `                  {tr('新しいバージョンがあります。タップして更新', 'New version available. Tap to update')} ({APP_VERSION}{' '}\n                  ➞ {newVersionAvailable[1]})`,
  ],
];

for (const [before, after] of replacements) {
  replaceRequired(before, after);
}

fs.writeFileSync(appPath, source, 'utf8');
console.log('Prepared localized user-visible status messages.');
