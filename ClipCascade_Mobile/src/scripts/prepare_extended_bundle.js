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
  "const APP_VERSION = '3.2.0';",
  "const APP_VERSION = '3.2.1-extended.2';",
);
replaceRequired(
  "const APP_NAME = 'ClipCascade';",
  "const APP_NAME = 'ClipCascade Extended';",
);
replaceRequired(
  '  const { NativeBridgeModule } = NativeModules;',
  `  const { NativeBridgeModule, RelaySettingsModule } = NativeModules;\n` +
    `  const languageTag = String(RelaySettingsModule?.languageTag || '').toLowerCase();\n` +
    `  const isJapanese = languageTag.startsWith('ja');\n` +
    `  const tr = (ja, en) => (isJapanese ? ja : en);`,
);
replaceRequired(
  '  PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);',
  '  // Notification permission is requested by the guided Sharing setup flow.',
);

const replacements = [
  [
    '<Text style={styles.label}>Username:</Text>',
    "<Text style={styles.label}>{tr('ユーザー名:', 'Username:')}</Text>",
  ],
  [
    '<Text style={styles.label}>Password:</Text>',
    "<Text style={styles.label}>{tr('パスワード:', 'Password:')}</Text>",
  ],
  [
    '<Text style={styles.label}>Server URL:</Text>',
    "<Text style={styles.label}>{tr('サーバーURL:', 'Server URL:')}</Text>",
  ],
  [
    '<Text style={styles.label}>Enable Encryption (recommended):</Text>',
    "<Text style={styles.label}>{tr('暗号化を有効化（推奨）:', 'Enable encryption (recommended):')}</Text>",
  ],
  [
    '<Text style={styles.loginButtonText}>Login</Text>',
    "<Text style={styles.loginButtonText}>{tr('ログイン', 'Login')}</Text>",
  ],
  [
    "{showExtraConfig ? 'Hide Extra Config' : 'Enable Extra Config'}",
    "{showExtraConfig ? tr('詳細設定を隠す', 'Hide advanced settings') : tr('詳細設定を表示', 'Show advanced settings')}",
  ],
  [
    '<Text style={styles.label}>Hash Rounds:</Text>',
    "<Text style={styles.label}>{tr('ハッシュ反復回数:', 'Hash rounds:')}</Text>",
  ],
  [
    '<Text style={styles.label}>Salt:</Text>',
    "<Text style={styles.label}>{tr('ソルト:', 'Salt:')}</Text>",
  ],
  [
    `                  Store Password Locally (not recommended; only works if\n                  encryption is disabled):`,
    `                  {tr(\n                    'パスワードを端末内に保存（非推奨・暗号化OFF時のみ）:',\n                    'Store password locally (not recommended; encryption must be off):',\n                  )}`,
  ],
  [
    `                  Maximum Clipboard Size Local Limit (in bytes):`,
    `                  {tr(\n                    'クリップボードの端末側上限（バイト）:',\n                    'Local clipboard size limit (bytes):',\n                  )}`,
  ],
  [
    `                  Run on system startup (disable if the READ_LOGS permission is\n                  granted):`,
    `                  {tr(\n                    '端末起動時に同期を再開:',\n                    'Resume synchronization after device startup:',\n                  )}`,
  ],
  [
    `                  Enable WebSocket Status Notification:`,
    `                  {tr(\n                    '接続状態の通知を表示:',\n                    'Show connection status notification:',\n                  )}`,
  ],
  [
    '<Text style={styles.label}>Enable Periodic Checks:</Text>',
    "<Text style={styles.label}>{tr('定期ヘルスチェックを有効化:', 'Enable periodic health checks:')}</Text>",
  ],
  [
    '<Text style={styles.label}>Enable Image Sharing:</Text>',
    "<Text style={styles.label}>{tr('画像共有を有効化:', 'Enable image sharing:')}</Text>",
  ],
  [
    '<Text style={styles.label}>Enable File Sharing:</Text>',
    "<Text style={styles.label}>{tr('ファイル共有を有効化:', 'Enable file sharing:')}</Text>",
  ],
  [
    "{wsIsRunning === 'true' ? 'Stop' : 'Start'}",
    "{wsIsRunning === 'true' ? tr('停止', 'Stop') : tr('開始', 'Start')}",
  ],
  [
    '<Text style={styles.loginButtonText}>Logout</Text>',
    "<Text style={styles.loginButtonText}>{tr('ログアウト', 'Logout')}</Text>",
  ],
  [
    '                    📥 Download File(s)',
    "                    {tr('📥 ファイルを保存', '📥 Download file(s)')}",
  ],
  [
    '                Instructions',
    "                {tr('使い方', 'Instructions')}",
  ],
  [
    '                  Clipboard Sharing on Android 10+:',
    "                  {tr('Androidでの自動クリップボード共有:', 'Automatic clipboard sharing on Android:')}",
  ],
  [
    `                  On Android 10 and above, clipboard monitoring has been\n                  restricted for privacy reasons. To share clipboard content\n                  using ClipCascade:`,
    `                  {tr(\n                    'ユーザー補助を有効にすると、ADBなしで明示的なコピー操作を検出できます。手動共有も引き続き利用できます。',\n                    'Enable Accessibility to detect explicit copy actions without ADB. Manual sharing remains available.',\n                  )}`,
  ],
  [
    `                    1. Select the text, image, or file(s) you want to copy.`,
    `                    {tr(\n                      '1. テキスト・画像・ファイルを選択します。',\n                      '1. Select the text, image, or file(s).',\n                    )}`,
  ],
  [
    `                    2. Tap 'Share', select 'ClipCascade'.`,
    `                    {tr(\n                      '2. 「共有」からClipCascadeを選びます。',\n                      "2. Tap 'Share' and select ClipCascade.",\n                    )}`,
  ],
  [
    "<Text style={[styles.label, { marginLeft: 15 }]}>(or)</Text>",
    "<Text style={[styles.label, { marginLeft: 15 }]}>{tr('または', 'or')}</Text>",
  ],
  [
    `                    Tap 'ClipCascade' instead of 'Copy'.`,
    `                    {tr(\n                      '対応アプリでは「コピー」の代わりにClipCascadeを選べます。',\n                      'In supported apps, choose ClipCascade instead of Copy.',\n                    )}`,
  ],
  [
    `                  There's also a workaround to enable clipboard sharing in the\n                  background. Scroll down for setup instructions.`,
    `                  {tr(\n                    '右下の「共有設定」から、ユーザー補助・通知アクセス・バックグラウンド動作を順番に設定してください。',\n                    'Open Sharing setup at the bottom-right and complete Accessibility, notification access, and background operation.',\n                  )}`,
  ],
  [
    '                  Background Clipboard Reception:',
    "                  {tr('バックグラウンド受信:', 'Background clipboard reception:')}",
  ],
  [
    `                  ClipCascade automatically receives clipboard content in the\n                  background. No manual action is required to receive data.`,
    `                  {tr(\n                    '同期を開始すると、他端末のクリップボードをバックグラウンドで受信します。',\n                    'After synchronization starts, clipboard content from other devices is received in the background.',\n                  )}`,
  ],
  [
    '                  Important Note:',
    "                  {tr('バックグラウンド動作:', 'Background operation:')}",
  ],
  [
    `                  To ensure uninterrupted performance, please disable battery\n                  optimization for ClipCascade. This will prevent the system\n                  from stopping the app when it's running in the foreground.`,
    `                  {tr(\n                    '安定動作のため、共有設定の案内に従ってバッテリー制限とメーカー独自の自動起動制限を解除してください。',\n                    'For reliable operation, follow Sharing setup to remove battery restrictions and manufacturer-specific auto-launch limits.',\n                  )}`,
  ],
  [
    '                  Battery Optimization Settings',
    "                  {tr('バッテリー最適化設定', 'Battery optimization settings')}",
  ],
  [
    '                  Power Manager Settings',
    "                  {tr('電源管理設定', 'Power manager settings')}",
  ],
  [
    '                  Automatic Clipboard Monitoring Setup:',
    "                  {tr('ADB不要の共有設定:', 'ADB-free sharing setup:')}",
  ],
  [
    `                  On rooted/non-rooted devices, to enable automatic clipboard\n                  monitoring you need to execute these 3 ADB commands:`,
    `                  {tr(\n                    'ADBコマンドは不要です。右下の共有設定を開き、次の案内を順に完了してください。',\n                    'No ADB command is required. Open Sharing setup at the bottom-right and follow the guided steps.',\n                  )}`,
  ],
  [
    '                    1. Enable the READ_LOGS permission:',
    "                    {tr('1. ユーザー補助のクリップボード共有を有効化', '1. Enable Clipboard Sharing in Accessibility')}",
  ],
  [
    '{`> adb -d shell pm grant com.clipcascade android.permission.READ_LOGS`}',
    "{tr('旧READ_LOGS権限は使用しません。', 'The legacy READ_LOGS permission is not used.')}",
  ],
  [
    `                    2. Allow "Drawing over other apps", also accessible from\n                    Settings:`,
    `                    {tr(\n                      '2. SMS・メールの認証コード用に通知アクセスを許可',\n                      '2. Allow notification access for SMS and email verification codes',\n                    )}`,
  ],
  [
    '{`> adb -d shell appops set com.clipcascade SYSTEM_ALERT_WINDOW allow`}',
    "{tr('画面オーバーレイ権限は使用しません。', 'Overlay permission is not used.')}",
  ],
  [
    '                    3. Kill the app for the new permissions to take effect:',
    "                    {tr('3. バッテリー・バックグラウンド動作を許可', '3. Allow unrestricted battery and background operation')}",
  ],
  [
    '{`> adb -d shell am force-stop com.clipcascade`}',
    "{tr('共有設定のテスト通知で配送を確認できます。', 'Use the test notification in Sharing setup to verify delivery.')}",
  ],
  [
    "const [loadingPageMessage, setLoadingPageMessage] = useState('Loading...');",
    "const [loadingPageMessage, setLoadingPageMessage] = useState(tr('読み込み中...', 'Loading...'));",
  ],
  [
    "setLoadingPageMessage('Checking foreground service...');",
    "setLoadingPageMessage(tr('バックグラウンドサービスを確認中...', 'Checking background service...'));",
  ],
  [
    "setLoadingPageMessage('Verifying Session...');",
    "setLoadingPageMessage(tr('セッションを確認中...', 'Verifying session...'));",
  ],
  [
    "setWsPageMessage('⌛ Please wait...');",
    "setWsPageMessage(tr('⌛ お待ちください...', '⌛ Please wait...'));",
  ],
  [
    "setWsPageMessage('🚀 Starting foreground service...');",
    "setWsPageMessage(tr('🚀 バックグラウンド同期を開始中...', '🚀 Starting background synchronization...'));",
  ],
  [
    "setWsPageMessage('⌛ Stopping foreground service...');",
    "setWsPageMessage(tr('⌛ バックグラウンド同期を停止中...', '⌛ Stopping background synchronization...'));",
  ],
];

for (const [before, after] of replacements) {
  replaceRequired(before, after);
}

fs.writeFileSync(appPath, source, 'utf8');
console.log('Prepared bilingual ClipCascade Extended interface for bundling.');
