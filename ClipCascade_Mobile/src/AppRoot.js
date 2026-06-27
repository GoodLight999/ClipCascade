import React from 'react';
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
        accessibilityLabel="バックグラウンド共有設定"
        style={styles.settingsButton}
        onPress={openRelaySettings}
      >
        <Text style={styles.settingsButtonText}>⚙ 共有設定</Text>
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
    paddingVertical: 11,
    borderRadius: 24,
    elevation: 8,
  },
  settingsButtonText: {
    color: 'white',
    fontSize: 15,
    fontWeight: 'bold',
  },
});
