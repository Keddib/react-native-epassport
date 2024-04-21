import * as React from 'react';

import {
  StyleSheet,
  View,
  Text,
  ScrollView,
  TouchableOpacity,
} from 'react-native';
import NfcPassportReader from 'react-native-nfc-passport-reader';
import type { NfcResult } from 'react-native-nfc-passport-reader';

export default function App() {
  const [result, setResult] = React.useState<NfcResult>();
  const [tagDiscovered, setTagDiscovered] = React.useState<boolean>(false);

  React.useEffect(() => {
    NfcPassportReader.addOnSuccessListener((data: NfcResult) => {
      setTagDiscovered(false);
      setResult(data);
    });

    NfcPassportReader.addOnErrorListener((error: string) => {
      setTagDiscovered(false);
      console.error(error);
    });

    NfcPassportReader.addOnTagDiscoveredListener(() => {
      console.log('Tag Discovered');
      setTagDiscovered(true);
    });

    NfcPassportReader.addOnNfcStateChangedListener((state) => {
      console.log('NFC State Changed:', state);
    });

    return () => {
      NfcPassportReader.stopReading();
      NfcPassportReader.removeListeners();
    };
  }, []);

  const startReading = () => {
    NfcPassportReader.startReading({
      mrz: 'P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<1234567890',
      includeImages: true,
    });
  };

  const stopReading = () => {
    NfcPassportReader.stopReading();
  };

  const openNfcSettings = async () => {
    try {
      const result = await NfcPassportReader.openNfcSettings();
      console.log(result);
    } catch (e) {
      console.log(e);
    }
  };

  const isNfcSupported = async () => {
    try {
      const result = await NfcPassportReader.isNfcSupported();
      console.log(result);
    } catch (e) {
      console.log(e);
    }
  };

  const isNfcEnabled = async () => {
    try {
      const result = await NfcPassportReader.isNfcEnabled();
      console.log(result);
    } catch (e) {
      console.log(e);
    }
  };

  return (
    <>
      <ScrollView style={styles.container}>
        <View style={styles.box}>
          <View style={styles.buttonContainer}>
            <TouchableOpacity onPress={startReading} style={styles.button}>
              <Text style={styles.buttonText}>Start Reading</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={stopReading} style={styles.button}>
              <Text style={styles.buttonText}>Stop Reading</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.buttonContainer}>
            <TouchableOpacity onPress={isNfcSupported} style={styles.button}>
              <Text style={styles.buttonText}>Is NFC Supported</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={isNfcEnabled} style={styles.button}>
              <Text style={styles.buttonText}>Is NFC Enabled</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={openNfcSettings} style={styles.button}>
              <Text style={styles.buttonText}>Open NFC Settings</Text>
            </TouchableOpacity>
          </View>

          <Text style={styles.text}>{JSON.stringify(result, null, 2)}</Text>
        </View>
      </ScrollView>
      {tagDiscovered && (
        <View style={styles.overlayBox}>
          <View style={styles.infoBox}>
            <Text style={styles.infoText}>
              NFC reading. Please wait for a moment...
            </Text>
          </View>
        </View>
      )}
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#252526',
  },
  buttonContainer: {
    flex: 1,
    flexDirection: 'row',
    gap: 16,
  },
  button: {
    flex: 1,
    backgroundColor: '#fff',
    padding: 16,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 4,
  },
  buttonText: {
    color: '#252526',
    textAlign: 'center',
  },
  text: {
    color: '#fff',
  },
  box: {
    flex: 1,
    padding: 16,
    gap: 8,
  },
  overlayBox: {
    position: 'absolute',
    width: '100%',
    height: '100%',
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 100,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'flex-end',
  },
  infoBox: {
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 16,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fff',
    minHeight: 200,
  },
  infoText: {
    color: '#252526',
    textAlign: 'center',
    fontSize: 22,
  },
});
