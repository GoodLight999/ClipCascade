import React, {useEffect, useState} from 'react';
import {
  Alert,
  NativeModules,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import App from './App';

export default function AppRoot() {
  const [buildLabel, setBuildLabel] = useState('');

  useEffect(() => {
    let active = true;

    const loadBuildInfo = async () => {
      try {
        const info = await NativeModules.RelaySettingsModule.getBuildInfo();
        if (!active) {
          return;
        }
        const version = String(info?.versionName || '').trim();
        const commit = String(info?.sourceCommit || '').trim();
        const shortCommit =
          commit && commit !== 'local' ? commit.slice(0, 8) : 'local';
        setBuildLabel([version, shortCommit].filter(Boolean).join(' · '));
      } catch (error) {
        // Build identity is diagnostic only; settings must remain usable.
      }
    };

    loadBuildInfo();
    return () => {
      active = false;
    };
  }, []);

  const openRelaySettings = async () => {
    try {
      await NativeModules.RelaySettingsModule.openSettings();
    } catch (error) {
      Alert.alert('設定を開けませんでした', String(error));
    }
  };

  return (
    <View style={styles.root}>
      <App />
      <TouchableOpacity
        accessibilityLabel={`バックグラウンド共有設定 ${buildLabel}`}
        style={styles.settingsButton}
        onPress={openRelaySettings}
      >
        <Text style={styles.settingsButtonText}>⚙ 共有設定</Text>
        {buildLabel ? (
          <Text style={styles.buildLabel}>{buildLabel}</Text>
        ) : null}
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  settingsButton: {
    position: 'absolute',
    right: 14,
    bottom: 18,
    backgroundColor: '#263238',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 24,
    elevation: 8,
    alignItems: 'center',
  },
  settingsButtonText: {
    color: 'white',
    fontSize: 15,
    fontWeight: 'bold',
  },
  buildLabel: {
    color: '#CFD8DC',
    fontSize: 9,
    marginTop: 2,
  },
});
