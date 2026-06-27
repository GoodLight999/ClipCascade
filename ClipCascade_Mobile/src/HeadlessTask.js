import {getDataFromAsyncStorage, setDataInAsyncStorage} from './AsyncStorageManagement';
import {recoverSynchronization} from './RecoveryListener';

module.exports = async data => {
  try {
    if (!data?.event) {
      return;
    }

    if (data.event === 'BOOT_COMPLETED') {
      const relaunchOnBoot = await getDataFromAsyncStorage('relaunch_on_boot');
      if (relaunchOnBoot === 'true') {
        await recoverSynchronization('boot_completed');
      }
      return;
    }

    if (data.event === 'HEALTH_CHECK_FAILED') {
      await recoverSynchronization(data.reason || 'health_check_failed');
    }
  } catch (error) {
    await setDataInAsyncStorage(
      'wsStatusMessage',
      'Service restart failed: ' + String(error),
    );
    console.error('Error in Headless JS Task:', error);
  }
};
