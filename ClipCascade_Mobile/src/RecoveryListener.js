import {DeviceEventEmitter} from 'react-native';
import notifee from '@notifee/react-native';

import {
  getDataFromAsyncStorage,
  setDataInAsyncStorage,
} from './AsyncStorageManagement';
import StartForegroundService from './StartForegroundService';

const EVENT_NAME = 'CLIPCASCADE_RECOVERY_REQUEST';
const MIN_RECOVERY_INTERVAL_MS = 60_000;
const SERVICE_STOP_SETTLE_MS = 500;

let listenerRegistered = false;
let recoveryInProgress = false;
let lastRecoveryStartedAt = 0;

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

export async function recoverSynchronization(reason = 'unspecified') {
  if (recoveryInProgress) {
    return false;
  }

  const wsIsRunning = await getDataFromAsyncStorage('wsIsRunning');
  if (wsIsRunning !== 'true') {
    return false;
  }

  const now = Date.now();
  if (now - lastRecoveryStartedAt < MIN_RECOVERY_INTERVAL_MS) {
    return false;
  }

  recoveryInProgress = true;
  lastRecoveryStartedAt = now;

  try {
    await setDataInAsyncStorage(
      'wsStatusMessage',
      `♻️ Recovering synchronization (${String(reason)})`,
    );
    await setDataInAsyncStorage('wsForegroundServiceTerminated', 'false');

    try {
      await notifee.stopForegroundService();
      await sleep(SERVICE_STOP_SETTLE_MS);
    } catch (stopError) {
      // A missing/dead foreground service is an expected recovery condition.
    }

    const result = await StartForegroundService();
    if (!Array.isArray(result) || result[0] !== true) {
      const detail = Array.isArray(result) ? result[1] : result;
      throw new Error(String(detail || 'Foreground service start failed'));
    }

    return true;
  } catch (error) {
    await setDataInAsyncStorage(
      'wsStatusMessage',
      '❌ Synchronization recovery failed: ' + String(error),
    );
    throw error;
  } finally {
    recoveryInProgress = false;
  }
}

export function registerRecoveryListener() {
  if (listenerRegistered) {
    return;
  }
  listenerRegistered = true;

  DeviceEventEmitter.addListener(EVENT_NAME, async event => {
    try {
      await recoverSynchronization(event?.reason || 'native_request');
    } catch (error) {
      console.error('ClipCascade recovery request failed:', error);
    }
  });
}
