import React, {useEffect, useState} from 'react';
import {
  Alert,
  NativeModules,
  StyleSheet,
  Switch,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import App from './App';

const relaySettingsModule = NativeModules.RelaySettingsModule;
const initialLanguageTag = String(
  relaySettingsModule?.languageTag || '',
).toLowerCase();

export default function AppRoot() {
  const [buildLabel, setBuildLabel] = useState('');
  const [languageTag, setLanguageTag] = useState(initialLanguageTag);
  const [bootResumeEnabled, setBootResumeEnabled] = useState(false);
  const [bootResumeReady, setBootResumeReady] = useState(false);
  const isJapanese = languageTag.startsWith('ja');

  useEffect(() => {
    let active = true;

    const loadBuildInfo = async () => {
      try {
        const info = await relaySettingsModule.getBuildInfo();
        if (!active) {
          return;
        }
        const version = String(info?.versionName || '').trim();
        const commit = String(info?.sourceCommit || '').trim();
        const shortCommit =
          commit && commit !== 'local' ? commit.slice(0, 8) : 'local';
        setBuildLabel([version, shortCommit].filter(Boolean).join(' · '));
        setLanguageTag(
          String(info?.languageTag || initialLanguageTag).toLowerCase(),
        );
      } catch (error) {
        // Build identity is diagnostic only; settings must remain usable.
      }
    };

    const loadBootResume = async () => {
      try {
        const enabled = await relaySettingsModule.getBootResumeEnabled();
        if (active) {
          setBootResumeEnabled(Boolean(enabled));
        }
      } catch (error) {
        // The switch remains disabled until the native preference can be read.
      } finally {
        if (active) {
          setBootResumeReady(true);
        }
      }
    };

    loadBuildInfo();
    loadBootResume();
    return () => {
      active = false;
    };
  }, []);

  const openRelaySettings = async () => {
    try {
      await relaySettingsModule.openSettings();
    } catch (error) {
      Alert.alert(
        isJapanese ? '設定を開けませんでした' : 'Unable to open settings',
        String(error),
      );
    }
  };

  const changeBootResume = async enabled => {
    const previous = bootResumeEnabled;
    setBootResumeEnabled(enabled);
    try {
      await relaySettingsModule.setBootResumeEnabled(enabled);
    } catch (error) {
      setBootResumeEnabled(previous);
      Alert.alert(
        isJapanese ? '設定を保存できませんでした' : 'Unable to save setting',
        isJapanese
          ? '端末起動時の同期再開設定を保存できませんでした。'
          : 'The device-startup synchronization setting could not be saved.',
      );
    }
  };

  const settingsLabel = isJapanese ? '⚙ 共有設定' : '⚙ Sharing setup';
  const bootResumeLabel = isJapanese
    ? '起動時に同期を再開'
    : 'Resume sync at startup';
  const accessibilityLabel = isJapanese
    ? `バックグラウンド共有設定 ${buildLabel}`
    : `Background sharing setup ${buildLabel}`;

  return (
    <View style={styles.root}>
      <View style={styles.appContent}>
        <App />
      </View>
      <View style={styles.settingsBar}>
        <View style={styles.bootResumeControl}>
          <Text style={styles.bootResumeLabel}>{bootResumeLabel}</Text>
          <Switch
            accessibilityLabel={bootResumeLabel}
            disabled={!bootResumeReady}
            value={bootResumeEnabled}
            onValueChange={changeBootResume}
          />
        </View>
        <TouchableOpacity
          accessibilityLabel={accessibilityLabel}
          style={styles.settingsButton}
          onPress={openRelaySettings}
        >
          <Text style={styles.settingsButtonText}>{settingsLabel}</Text>
          {buildLabel ? (
            <Text style={styles.buildLabel}>{buildLabel}</Text>
          ) : null}
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  appContent: {
    flex: 1,
  },
  settingsBar: {
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#B0BEC5',
    backgroundColor: '#ECEFF1',
    paddingHorizontal: 10,
    paddingVertical: 7,
    flexDirection: 'row',
    alignItems: 'center',
  },
  bootResumeControl: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    marginRight: 8,
  },
  bootResumeLabel: {
    flex: 1,
    color: '#263238',
    fontSize: 12,
    fontWeight: '600',
    marginRight: 4,
  },
  settingsButton: {
    backgroundColor: '#263238',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    elevation: 4,
    alignItems: 'center',
  },
  settingsButtonText: {
    color: 'white',
    fontSize: 14,
    fontWeight: 'bold',
  },
  buildLabel: {
    color: '#CFD8DC',
    fontSize: 8,
    marginTop: 2,
  },
});
