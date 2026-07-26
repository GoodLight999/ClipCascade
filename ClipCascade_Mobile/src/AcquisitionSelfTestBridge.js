import { NativeEventEmitter, NativeModules } from 'react-native';

let selfTestSubscription = null;

/**
 * Registers one process-lifetime acknowledgement listener for the sanitized
 * native -> React Native acquisition self-test.
 *
 * The event contains only a diagnostic test ID. It never enters clipboard or
 * outbound transport handling.
 */
export const startAcquisitionSelfTestBridge = () => {
  if (selfTestSubscription != null) {
    return;
  }

  const { ClipboardListener } = NativeModules;
  if (ClipboardListener == null) {
    return;
  }

  const emitter = new NativeEventEmitter(ClipboardListener);
  selfTestSubscription = emitter.addListener(
    'onAcquisitionSelfTest',
    event => {
      const testId = event?.testId;
      if (typeof testId === 'string' && testId.length > 0) {
        ClipboardListener.acknowledgeAcquisitionSelfTest(testId);
      }
    },
  );
};
