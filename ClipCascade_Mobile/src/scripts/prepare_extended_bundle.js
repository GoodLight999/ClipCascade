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

function replaceRegexRequired(pattern, after, expectedCount) {
  const matches = source.match(pattern) || [];
  if (matches.length !== expectedCount) {
    throw new Error(
      `Expected ${expectedCount} App.js matches for ${pattern}, found ${matches.length}`,
    );
  }
  source = source.replace(pattern, after);
}

replaceRequired(
  "const APP_VERSION = '3.2.0';",
  "const APP_VERSION = '3.2.1-extended.3';",
);
replaceRequired(
  "const APP_NAME = 'ClipCascade';",
  "const APP_NAME = 'ClipCascade Extended';",
);
replaceRequired(
  `  const VERSION_URL =
    'https://raw.githubusercontent.com/Sathvik-Rao/ClipCascade/main/version.json';
  const GITHUB_URL = 'https://github.com/Sathvik-Rao/ClipCascade';
  const RELEASE_URL =
    'https://github.com/Sathvik-Rao/ClipCascade/releases/latest';
  const APP_NAME = 'ClipCascade Extended';
  const HELP_URL = \`${'${GITHUB_URL}'}/blob/main/README.md\`;
  const METADATA_URL =
    'https://raw.githubusercontent.com/Sathvik-Rao/ClipCascade/main/metadata.json';`,
  `  const APP_NAME = 'ClipCascade Extended';`,
);
replaceRequired(
  `  const [newVersionAvailable, setNewVersionAvailable] = useState([false, '']);
  const [donateUrl, setDonateUrl] = useState(null);`,
  `  // Extended builds do not query or advertise upstream releases/funding.`,
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
replaceRequired(
  `        // check for new version
        try {
          const response = await fetchTimeout(VERSION_URL);
          if (!response.ok) {
            throw new Error('Network response was not ok');
          }
          const data = await response.json();
          if (data && data.android !== APP_VERSION) {
            setNewVersionAvailable([true, data.android]);
          }
        } catch (e) {
          // Silent catch
        }

        try {
          const response = await fetchTimeout(METADATA_URL);
          if (!response.ok) {
            throw new Error('Network response was not ok');
          }
          const data = await response.json();
          if (data) {
            setDonateUrl(data.funding);
          }
        } catch (e) {
          // Silent catch
        }`,
  `        // Extended builds intentionally perform no upstream update or funding lookup.`,
);
replaceRequired(
  `            {/* new version display message */}
            {newVersionAvailable[0] && (
              <TouchableOpacity
                onPress={() => Linking.openURL(RELEASE_URL)}
                style={{ marginTop: 10 }}
              >
                <Text
                  style={[
                    styles.message,
                    {
                      color: '#008080',
                      fontWeight: 'bold',
                      textDecorationLine: 'underline',
                    },
                  ]}
                >
                  New version available! 🚀 Click here to update ({APP_VERSION}{' '}
                  ➞ {newVersionAvailable[1]})
                </Text>
              </TouchableOpacity>
            )}`,
  `            {/* Extended releases are distributed explicitly; no upstream update banner. */}`,
);
replaceRegexRequired(
  /\n\s*\{\/\* Footer \*\/\}\n\s*<View style=\{styles\.footerContainer\}>[\s\S]*?\n\s*<\/View>/g,
  '',
  2,
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
    `                  Store Password Locally (not recommended; only works if
                  encryption is disabled):`,
    `                  {tr(
                    'パスワードを端末内に保存（非推奨・暗号化OFF時のみ）:',
                    'Store password locally (not recommended; encryption must be off):',
                  )}`,
  ],
  [
    `                  Maximum Clipboard Size Local Limit (in bytes):`,
    `                  {tr(
                    'クリップボードの端末側上限（バイト）:',
                    'Local clipboard size limit (bytes):',
                  )}`,
  ],
  [
    `                  Run on system startup (disable if the READ_LOGS permission is
                  granted):`,
    `                  {tr(
                    '端末起動後に同期を自動再開:',
                    'Automatically resume synchronization after device startup:',
                  )}`,
  ],
  [
    `                  Enable WebSocket Status Notification:`,
    `                  {tr(
                    '接続状態の通知を表示:',
                    'Show connection status notification:',
                  )}`,
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
    `                  On Android 10 and above, clipboard monitoring has been
                  restricted for privacy reasons. To share clipboard content
                  using ClipCascade:`,
    `                  {tr(
                    'ユーザー補助を有効にすると、ADBなしで明示的なコピー操作を検出できます。手動共有も引き続き利用できます。',
                    'Enable Accessibility to detect explicit copy actions without ADB. Manual sharing remains available.',
                  )}`,
  ],
  [
    `                    1. Select the text, image, or file(s) you want to copy.`,
    `                    {tr(
                      '1. テキスト・画像・ファイルを選択します。',
                      '1. Select the text, image, or file(s).',
                    )}`,
  ],
  [
    `                    2. Tap 'Share', select 'ClipCascade'.`,
    `                    {tr(
                      '2. 「共有」からClipCascadeを選びます。',
                      "2. Tap 'Share' and select ClipCascade.",
                    )}`,
  ],
  [
    "<Text style={[styles.label, { marginLeft: 15 }]}>(or)</Text>",
    "<Text style={[styles.label, { marginLeft: 15 }]}>{tr('または', 'or')}</Text>",
  ],
  [
    `                    Tap 'ClipCascade' instead of 'Copy'.`,
    `                    {tr(
                      '対応アプリでは「コピー」の代わりにClipCascadeを選べます。',
                      'In supported apps, choose ClipCascade instead of Copy.',
                    )}`,
  ],
  [
    `                  There's also a workaround to enable clipboard sharing in the
                  background. Scroll down for setup instructions.`,
    `                  {tr(
                    '画面下部の「共有設定」から、ユーザー補助・通知アクセス・バックグラウンド動作を順番に設定してください。',
                    'Open Sharing setup at the bottom and complete Accessibility, notification access, and background operation.',
                  )}`,
  ],
  [
    '                  Background Clipboard Reception:',
    "                  {tr('バックグラウンド受信:', 'Background clipboard reception:')}",
  ],
  [
    `                  ClipCascade automatically receives clipboard content in the
                  background. No manual action is required to receive data.`,
    `                  {tr(
                    '同期を開始すると、他端末のクリップボードをバックグラウンドで受信します。',
                    'After synchronization starts, clipboard content from other devices is received in the background.',
                  )}`,
  ],
  [
    '                  Important Note:',
    "                  {tr('バックグラウンド動作:', 'Background operation:')}",
  ],
  [
    `                  To ensure uninterrupted performance, please disable battery
                  optimization for ClipCascade. This will prevent the system
                  from stopping the app when it's running in the foreground.`,
    `                  {tr(
                    '安定動作のため、共有設定の案内に従ってバッテリー制限とメーカー独自の自動起動制限を解除してください。',
                    'For reliable operation, follow Sharing setup to remove battery restrictions and manufacturer-specific auto-launch limits.',
                  )}`,
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
    `                  On rooted/non-rooted devices, to enable automatic clipboard
                  monitoring you need to execute these 3 ADB commands:`,
    `                  {tr(
                    'ADBコマンドは不要です。画面下部の共有設定を開き、次の案内を順に完了してください。',
                    'No ADB command is required. Open Sharing setup at the bottom and follow the guided steps.',
                  )}`,
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
    `                    2. Allow "Drawing over other apps", also accessible from
                    Settings:`,
    `                    {tr(
                      '2. SMS・メールの認証コード用に通知アクセスを許可',
                      '2. Allow notification access for SMS and email verification codes',
                    )}`,
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
console.log('Prepared bilingual ClipCascade Extended interface without upstream promotion.');
