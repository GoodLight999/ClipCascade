import {
  setDataInAsyncStorage,
  getDataFromAsyncStorage,
} from './AsyncStorageManagement';
import StartForegroundService from './StartForegroundService';

module.exports = async data => {
  try {
    const wsIsEnabled = async () => {
      const value = await getDataFromAsyncStorage('wsIsRunning');
      return value === 'true';
    };

    const startSyncService = async () => {
      if (!(await wsIsEnabled())) {
        return;
      }
      await setDataInAsyncStorage('wsStatusMessage', '');
      await setDataInAsyncStorage('wsForegroundServiceTerminated', 'false');
      const result = await StartForegroundService();
      if (result[0] === false) {
        throw result[1];
      }
    };

    if (!data || !data.event) {
      return;
    }

    if (data.event === 'BOOT_COMPLETED') {
      const relaunchOnBoot = await getDataFromAsyncStorage('relaunch_on_boot');
      if (relaunchOnBoot === 'true') {
        await startSyncService();
      }
      return;
    }

    if (data.event === 'HEALTH_CHECK_FAILED') {
      await startSyncService();
    }
  } catch (error) {
    await setDataInAsyncStorage(
      'wsStatusMessage',
      'Service restart failed: ' + String(error),
    );
    console.error('Error in Headless JS Task:', error);
  }
};
