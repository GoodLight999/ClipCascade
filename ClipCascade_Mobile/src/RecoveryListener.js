import {DeviceEventEmitter} from 'react-native';
import notifee from '@notifee/react-native';

import {
  getDataFromAsyncStorage,
  setDataInAsyncStorage,
} from './AsyncStorageManagement';
import StartForegroundService from './StartForegroundService';

const EVENT_NAME = 'CLIPCASCADE_RECOVERY_REQUEST';
const MIN_RECOVERY_INTERVAL_MS = 60_000;
const SERVICE_STOP_TIMEOUT_MS = 8_000;
const SERVICE_STOP_POLL_MS = 100;
const FORCED_STOP_SETTLE_MS = 500;

let listenerRegistered = false;
let recoveryInProgress = false;
let lastRecoveryStartedAt = 0;

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

async function stopPreviousServiceGeneration() {
  await setDataInAsyncStorage('wsForegroundServiceTerminated', 'false');
  await setDataInAsyncStorage('wsIsRunning', 'false');

  const deadline = Date.now() + SERVICE_STOP_TIMEOUT_MS;
  while (Date.now() < deadline) {
    const terminated = await getDataFromAsyncStorage(
      'wsForegroundServiceTerminated',
    );
    if (terminated === 'true') {
      return true;
    }
    await sleep(SERVICE_STOP_POLL_MS);
  }

  // A dead JavaScript service cannot acknowledge its own teardown. Stop the
  // native foreground-service shell and continue with a fresh generation.
  try {
    await notifee.stopForegroundService();
  } catch (stopError) {
    // Missing service is an expected health-recovery condition.
  }
  await sleep(FORCED_STOP_SETTLE_MS);
  return false;
}

export async function recoverSynchronization(reason = 'unspecified') {
  if (recoveryInProgress) {
    return false;
  }

  const wsWasEnabled = await getDataFromAsyncStorage('wsIsRunning');
  if (wsWasEnabled !== 'true') {
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

    await stopPreviousServiceGeneration();

    await setDataInAsyncStorage('wsIsRunning', 'true');
    await setDataInAsyncStorage('wsForegroundServiceTerminated', 'false');
    await setDataInAsyncStorage('wsStatusMessage', '');
    await setDataInAsyncStorage('p2pStatusMessage', '');

    const result = await StartForegroundService();
    if (!Array.isArray(result) || result[0] !== true) {
      const detail = Array.isArray(result) ? result[1] : result;
      throw new Error(String(detail || 'Foreground service start failed'));
    }

    return true;
  } catch (error) {
    // Preserve the user's enabled intent so a later network/health/user action
    // can retry, even if Android rejected this background start attempt.
    await setDataInAsyncStorage('wsIsRunning', 'true');
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
