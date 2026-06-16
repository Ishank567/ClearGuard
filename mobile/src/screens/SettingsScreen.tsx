import React, { useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  Alert,
  Switch,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useTheme } from '../theme/ThemeProvider';
import { useVPNStatus } from '../hooks/useVPNStatus';
import { useProtectionMode } from '../hooks/useProtectionMode';
import { useBatteryOptimization } from '../hooks/useBatteryOptimization';
import { useAppUpdater } from '../hooks/useAppUpdater';
import UpdateModal from '../components/UpdateModal';
import { PROTECTION_MODE_LABELS, ProtectionMode } from '../types/navigation';
import GlassCard from '../components/GlassCard';
import VPNModule from '../native/VPNModule';

const MODES: ProtectionMode[] = ['default', 'study', 'work', 'kids', 'elder', 'shopping', 'spiritual', 'battery'];

export default function SettingsScreen() {
  const { colors } = useTheme();
  const { stats } = useVPNStatus();
  const { mode, setMode } = useProtectionMode();
  const battery = useBatteryOptimization();
  const updater = useAppUpdater();
  const [showUpdateModal, setShowUpdateModal] = useState(false);
  const [autoUpdate, setAutoUpdate] = useState(true);
  const [resumeOnBoot, setResumeOnBoot] = useState(true);
  const [backgroundSync, setBackgroundSync] = useState(true);
  const [accessibilityEnabled, setAccessibilityEnabled] = useState(false);

  const exportSettings = async () => {
    try {
      const json = await VPNModule.exportSettings();
      Alert.alert('Export', 'Settings JSON copied to clipboard (stub)');
    } catch {
      Alert.alert('Error', 'Export failed');
    }
  };

  const importSettings = async () => {
    Alert.alert('Import', 'File picker not implemented in stub');
  };

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      {/* Protection Mode */}
      <Text style={[styles.sectionTitle, { color: colors.textMuted }]}>Protection Mode</Text>
      <GlassCard padding={12}>
        <View style={styles.modesWrap}>
          {MODES.map((m) => (
            <TouchableOpacity
              key={m}
              activeOpacity={0.7}
              onPress={() => setMode(m)}
              style={[
                styles.modePill,
                {
                  backgroundColor: mode === m ? colors.primaryDim : colors.glassBackground,
                  borderColor: mode === m ? colors.primary : colors.glassBorder,
                },
              ]}
            >
              <Text
                style={{
                  color: mode === m ? colors.primary : colors.textMuted,
                  fontSize: 12,
                  fontWeight: '600',
                }}
              >
                {PROTECTION_MODE_LABELS[m]}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </GlassCard>

      {/* Battery Optimization */}
      <Text style={[styles.sectionTitle, { color: colors.textMuted }]}>Battery Optimization</Text>
      <GlassCard padding={0}>
        <ToggleRow
          label="Battery Saver Mode"
          value={battery.saverEnabled}
          onValueChange={battery.setSaverEnabled}
        />
        <View style={[styles.divider, { backgroundColor: colors.divider }]} />
        <ToggleRow label="Background blocklist sync" value={backgroundSync} onValueChange={setBackgroundSync} />
        <View style={[styles.divider, { backgroundColor: colors.divider }]} />
        <View style={styles.optionRow}>
          <View style={{ flex: 1 }}>
            <Text style={{ color: colors.text, fontSize: 14, fontWeight: '500' }}>Poll Interval</Text>
            <Text style={{ color: colors.textMuted, fontSize: 11, marginTop: 2 }}>
              {battery.saverEnabled ? 'Reduced (8-15s) to save battery' : 'Normal (3-8s) for real-time stats'}
            </Text>
          </View>
        </View>
      </GlassCard>

      {/* ReVanced Shield — Accessibility Service */}
      <Text style={[styles.sectionTitle, { color: colors.textMuted }]}>ReVanced Shield (No Root)</Text>
      <GlassCard padding={12}>
        <Text style={{ color: colors.textMuted, fontSize: 12, marginBottom: 10 }}>
          Uses Android Accessibility to auto-click ad elements in Instagram, YouTube, Facebook, TikTok, and Snapchat. The closest non-root alternative to ReVanced patching.
        </Text>
        <ToggleRow
          label="Enable ReVanced Shield"
          value={accessibilityEnabled}
          onValueChange={async (v) => {
            if (v) {
              const enabled = await VPNModule.isAccessibilityServiceEnabled();
              if (!enabled) {
                Alert.alert(
                  'Enable Accessibility Service',
                  'ClearGuard needs Accessibility permission to detect and dismiss ad UI elements. Please enable "ClearGuard" in the next screen.',
                  [
                    { text: 'Cancel', style: 'cancel' },
                    {
                      text: 'Open Settings',
                      onPress: () => {
                        setAccessibilityEnabled(true);
                        VPNModule.openAccessibilitySettings();
                      },
                    },
                  ]
                );
              } else {
                setAccessibilityEnabled(true);
                VPNModule.toggleAccessibilityService(true);
              }
            } else {
              setAccessibilityEnabled(false);
              VPNModule.toggleAccessibilityService(false);
            }
          }}
        />
        {accessibilityEnabled && (
          <View style={{ marginTop: 8, padding: 10, borderRadius: 8, backgroundColor: colors.successGlow }}>
            <Text style={{ color: colors.success, fontSize: 12, fontWeight: '600' }}>
              Active: Auto-clicking ads in Instagram, YouTube, Facebook, TikTok, Snapchat
            </Text>
          </View>
        )}
      </GlassCard>

      {/* Toggles */}
      <Text style={[styles.sectionTitle, { color: colors.textMuted }]}>General</Text>
      <GlassCard padding={0}>
        <ToggleRow label="Auto-update blocklists" value={autoUpdate} onValueChange={setAutoUpdate} />
        <View style={[styles.divider, { backgroundColor: colors.divider }]} />
        <ToggleRow label="Resume on boot" value={resumeOnBoot} onValueChange={setResumeOnBoot} />
        <View style={[styles.divider, { backgroundColor: colors.divider }]} />
        <ToggleRow label="Scam Shield" value={stats.scamShieldEnabled} onValueChange={() => {}} />
        <View style={[styles.divider, { backgroundColor: colors.divider }]} />
        <ToggleRow label="Indian Scam Shield" value={stats.indianScamShieldEnabled} onValueChange={() => {}} />
        <View style={[styles.divider, { backgroundColor: colors.divider }]} />
        <ToggleRow label="DoH (DNS over HTTPS)" value={stats.dohEnabled} onValueChange={() => {}} />
      </GlassCard>

      {/* App Update */}
      <Text style={[styles.sectionTitle, { color: colors.textMuted }]}>App Update</Text>
      <GlassCard padding={12}>
        <TouchableOpacity
          onPress={() => {
            setShowUpdateModal(true);
            updater.check();
          }}
          style={styles.actionBtn}
        >
          <Icon name="system-update" size={20} color={colors.primary} />
          <Text style={{ color: colors.primary, fontSize: 14, fontWeight: '600', marginLeft: 10 }}>
            Check for Updates
          </Text>
        </TouchableOpacity>
      </GlassCard>

      <UpdateModal
        visible={showUpdateModal}
        state={updater}
        onCheck={updater.check}
        onDownload={updater.download}
        onInstall={updater.install}
        onDismiss={() => {
          setShowUpdateModal(false);
          updater.reset();
        }}
        onCancelDownload={updater.cancelDownload}
      />

      {/* Backup */}
      <Text style={[styles.sectionTitle, { color: colors.textMuted }]}>Backup & Restore</Text>
      <GlassCard padding={12}>
        <TouchableOpacity onPress={exportSettings} style={styles.actionBtn}>
          <Icon name="file-download" size={20} color={colors.primary} />
          <Text style={{ color: colors.primary, fontSize: 14, fontWeight: '600', marginLeft: 10 }}>
            Export Settings
          </Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={importSettings} style={[styles.actionBtn, { marginTop: 10 }]}>
          <Icon name="file-upload" size={20} color={colors.success} />
          <Text style={{ color: colors.success, fontSize: 14, fontWeight: '600', marginLeft: 10 }}>
            Import Settings
          </Text>
        </TouchableOpacity>
      </GlassCard>

      {/* About */}
      <View style={{ alignItems: 'center', marginTop: 24, marginBottom: 16 }}>
        <Text style={{ color: colors.textMuted, fontSize: 12 }}>
          ClearGuard Mobile v1.0.0
        </Text>
        <Text style={{ color: colors.textMuted, fontSize: 11, marginTop: 4 }}>
          100% on-device. No data leaves your phone.
        </Text>
      </View>
    </ScrollView>
  );
}

function ToggleRow({
  label,
  value,
  onValueChange,
}: {
  label: string;
  value: boolean;
  onValueChange: (v: boolean) => void;
}) {
  const { colors } = useTheme();
  return (
    <View style={styles.optionRow}>
      <Text style={{ color: colors.text, fontSize: 14, fontWeight: '500' }}>{label}</Text>
      <Switch
        value={value}
        onValueChange={onValueChange}
        thumbColor={value ? colors.primary : colors.textMuted}
        trackColor={{ false: colors.divider, true: colors.primaryDim }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 20, paddingBottom: 40 },
  sectionTitle: {
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
    marginTop: 20,
    marginBottom: 10,
    marginLeft: 4,
  },
  modesWrap: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  modePill: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1,
  },
  optionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
  },
  divider: { height: 1, marginHorizontal: 16 },
  radio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioInner: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#fff',
  },
  actionBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
  },
});
