import { NativeModules, DeviceEventEmitter, Platform } from 'react-native';
import {
  type MRZKey,
  type NfcResult,
  NfcPassportReaderEvent,
  type DocumentReadingProgress,
  type CustomMessages,
} from './types';
const LINKING_ERROR =
  `The package '@didit-sdk/react-native-nfc-passport-reader' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const NfcPassportReaderNativeModule = NativeModules.NfcPassportReader
  ? NativeModules.NfcPassportReader
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export * from './types';

export default class NfcPassportReader {
  static startReading(
    mrzKey: MRZKey,
    customMessages?: CustomMessages
  ): Promise<NfcResult> {
    console.log('startReading: ', mrzKey);
    if (Platform.OS === 'android') {
      return NfcPassportReaderNativeModule.startReading(mrzKey);
    }
    return NfcPassportReaderNativeModule.startReading(mrzKey, customMessages);
  }

  static stopReading() {
    if (Platform.OS === 'android') {
      NfcPassportReaderNativeModule.stopReading();
    } else {
      throw new Error('Unsupported platform');
    }
  }

  static addOnDocumentReadingProgressListener(
    callback: (progress: DocumentReadingProgress) => void
  ) {
    if (Platform.OS === 'android') {
      this.addListener(
        NfcPassportReaderEvent.DOCUMENT_READING_PROGRESS,
        callback
      );
    }
  }

  static addOnNfcStateChangedListener(callback: (state: 'off' | 'on') => void) {
    if (Platform.OS === 'android') {
      this.addListener(NfcPassportReaderEvent.NFC_STATE_CHANGED, callback);
    }
  }

  static isNfcEnabled(): Promise<boolean> {
    if (Platform.OS === 'android') {
      return NfcPassportReaderNativeModule.isNfcEnabled();
    } else if (Platform.OS === 'ios') {
      return NfcPassportReaderNativeModule.isNfcSupported();
    } else {
      throw new Error('Unsupported platform');
    }
  }

  static isNfcSupported(): Promise<boolean> {
    return NfcPassportReaderNativeModule.isNfcSupported();
  }

  static openNfcSettings(): Promise<boolean> {
    if (Platform.OS === 'android') {
      return NfcPassportReaderNativeModule.openNfcSettings();
    } else {
      throw new Error('Unsupported platform');
    }
  }

  private static addListener(
    event: NfcPassportReaderEvent,
    callback: (data: any) => void
  ) {
    DeviceEventEmitter.addListener(event, callback);
  }

  static removeListeners() {
    if (Platform.OS === 'android') {
      DeviceEventEmitter.removeAllListeners(
        NfcPassportReaderEvent.DOCUMENT_READING_PROGRESS
      );
      DeviceEventEmitter.removeAllListeners(
        NfcPassportReaderEvent.NFC_STATE_CHANGED
      );
    }
  }
}
