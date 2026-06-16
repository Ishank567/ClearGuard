import React, { useState, useEffect } from 'react';
import { StatusBar } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ThemeProvider } from './src/theme/ThemeProvider';
import AppNavigator from './src/navigation/AppNavigator';
import UpdateModal from './src/components/UpdateModal';
import { useAppUpdater } from './src/hooks/useAppUpdater';

const ONBOARDING_KEY = '@clearguard/onboarding_seen';
const LAST_CHECK_KEY = '@clearguard/last_update_check';
const CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000; // 24 hours

function AppContent() {
  const [onboardingSeen, setOnboardingSeen] = useState<boolean | null>(null);
  const updater = useAppUpdater();
  const [showModal, setShowModal] = useState(false);

  useEffect(() => {
    AsyncStorage.getItem(ONBOARDING_KEY).then((val: string | null) => {
      setOnboardingSeen(val === 'true');
    });
  }, []);

  useEffect(() => {
    if (onboardingSeen === null) return;
    // Auto-check for updates once per day after onboarding
    AsyncStorage.getItem(LAST_CHECK_KEY).then((last: string | null) => {
      const lastTime = last ? parseInt(last, 10) : 0;
      const now = Date.now();
      if (now - lastTime > CHECK_INTERVAL_MS) {
        updater.check().then(() => {
          AsyncStorage.setItem(LAST_CHECK_KEY, String(now));
          if (updater.available) setShowModal(true);
        });
      }
    });
  }, [onboardingSeen]);

  if (onboardingSeen === null) return null;

  return (
    <>
      <StatusBar barStyle="dark-content" backgroundColor="transparent" translucent />
      <AppNavigator onboardingSeen={onboardingSeen} />
      <UpdateModal
        visible={showModal}
        state={updater}
        onCheck={updater.check}
        onDownload={updater.download}
        onInstall={updater.install}
        onDismiss={() => {
          setShowModal(false);
          updater.reset();
        }}
        onCancelDownload={updater.cancelDownload}
      />
    </>
  );
}

export default function App() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <ThemeProvider>
          <AppContent />
        </ThemeProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
