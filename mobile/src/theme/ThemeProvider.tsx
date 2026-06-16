import React, { createContext, useContext } from 'react';
import { ThemeColors, lightColors } from './colors';

interface ThemeContextType {
  colors: ThemeColors;
  isDark: boolean;
}

const ThemeContext = createContext<ThemeContextType>({
  colors: lightColors,
  isDark: false,
});

export const useTheme = () => useContext(ThemeContext);

/** ShieldDNS uses a single light theme. */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  return (
    <ThemeContext.Provider value={{ colors: lightColors, isDark: false }}>
      {children}
    </ThemeContext.Provider>
  );
}