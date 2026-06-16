export interface ThemeColors {
  background: string;
  surface: string;
  surfaceHighlight: string;
  card: string;
  cardBorder: string;
  primary: string;
  primaryDim: string;
  secondary: string;
  accent: string;
  accentGlow: string;
  text: string;
  textMuted: string;
  textInverse: string;
  success: string;
  successGlow: string;
  warning: string;
  error: string;
  errorGlow: string;
  divider: string;
  shieldActive: string;
  shieldInactive: string;
  glassBackground: string;
  glassBorder: string;
}

export const lightColors: ThemeColors = {
  background: '#f8fafc',
  surface: '#ffffff',
  surfaceHighlight: '#f1f5f9',
  card: '#ffffff',
  cardBorder: 'rgba(0,0,0,0.05)',
  primary: '#3b82f6',
  primaryDim: 'rgba(59,130,246,0.08)',
  secondary: '#6366f1',
  accent: '#06b6d4',
  accentGlow: 'rgba(6,182,212,0.12)',
  text: '#0f172a',
  textMuted: 'rgba(15,23,42,0.5)',
  textInverse: '#ffffff',
  success: '#10b981',
  successGlow: 'rgba(16,185,129,0.12)',
  warning: '#f59e0b',
  error: '#ef4444',
  errorGlow: 'rgba(239,68,68,0.12)',
  divider: 'rgba(0,0,0,0.05)',
  shieldActive: '#10b981',
  shieldInactive: 'rgba(15,23,42,0.1)',
  glassBackground: 'rgba(255,255,255,0.8)',
  glassBorder: 'rgba(0,0,0,0.04)',
};