# ClearGuard UI Configuration

ClearGuard's interface is a custom "liquid glass" design system built on Jetpack Compose
and Material 3. Every visual knob lives in one of three files, so the whole app can be
restyled without touching a screen:

| File | What it controls |
|------|------------------|
| `app/src/main/java/com/clearguard/app/ui/theme/Color.kt` | All colors (light + dark palettes) |
| `app/src/main/java/com/clearguard/app/ui/theme/ClearDesign.kt` | Radii, glass opacity, elevation, motion, spacing |
| `app/src/main/java/com/clearguard/app/ui/theme/Theme.kt` | Theme modes, Material color schemes, system bars |

## Theme modes

Users pick **System / Light / Dark** in Settings → Appearance. The choice is stored in
`SharedPreferences` under `theme_mode` (`PreferenceKeys.KEY_THEME_MODE`, default
`"system"`). `MainActivity` holds the mode as Compose state and feeds it to
`ClearGuardTheme`, so switching is instant — no activity restart.

`ClearGuardTheme` resolves the mode to a `ClearPalette`, publishes it through
`ClearColors`, picks the matching Material `ColorScheme`, and styles the system bars
(icon contrast on all versions; bar background tint on Android 14 and below — Android
15+ is enforced edge-to-edge, where the floating bottom navigation adds
`navigationBarsPadding()` to stay above the gesture bar).

The launch window (before Compose draws) is themed by `res/values/styles.xml` and the
`res/values-night/` overrides, which mirror the dark palette so there is no white flash
when the system is in dark mode.

## Color tokens (`ClearPalette`)

Each palette defines all 16 tokens. Composables read them as `ClearColors.<token>`;
the backing value is Compose state, so reads recompose automatically on theme change.

| Token | Light | Dark | Used for |
|-------|-------|------|----------|
| `bg` | `#F7F8FA` | `#0B1220` | Screen background, scaffold container |
| `panel` | `#FFFFFF` | `#121C2E` | Material surface |
| `text` | `#17202A` | `#E8EDF4` | Primary text |
| `muted` | `#5E6B78` | `#93A2B4` | Captions, descriptions, inactive icons |
| `green` | `#167A52` | `#3DD68C` | Brand accent: protection, buttons, selection |
| `blue` | `#1E5EFF` | `#7BA4FF` | Secondary accent: informational stats |
| `border` | `#D9E0E8` | `#24344D` | Hairlines, outlines |
| `glass` | `#F8FAFC` | `#16233A` | Frosted card base (alpha applied per card) |
| `glassBorder` | white 45% | white 10% | Refraction edge stroke on cards |
| `glassHighlight` | white 32% | white 7% | Specular top shine inside cards |
| `glassShadow` | black 10% | black 45% | Ambient card shadow |
| `success` | = green | = green | Positive states |
| `danger` | `#E53935` | `#FF6B6B` | Errors, destructive actions, "Paused" dot |
| `warning` | `#FFA726` | `#FFB74D` | Warnings, medium threat scores |
| `glassDark` | navy 65% | navy 80% | Dark translucent contrast surfaces |

To add a token: extend `ClearPalette`, give it a value in both `ClearPalettes.light`
and `ClearPalettes.dark`, and expose a getter on `ClearColors`.

## Design tokens (`ClearDesign`)

| Token | Value | Used by |
|-------|-------|---------|
| `cardCorner` | 26dp | `GlassCard` default |
| `compactCorner` | 20dp | `GlassCardCompact` (dense lists) |
| `heroCorner` | 32dp | `GlassCardHero` (big toggle, headline stats) |
| `buttonCorner` | 22dp | `LiquidGlassButton` |
| `navCorner` | 28dp | Floating bottom navigation |
| `cardGlassAlpha` | 0.86 | Standard card frost opacity |
| `compactGlassAlpha` | 0.78 | Compact card frost |
| `heroGlassAlpha` | 0.90 | Hero card frost |
| `cardElevation` | 18dp | Standard card shadow depth |
| `compactElevation` | 8dp | Compact card shadow |
| `heroElevation` | 26dp | Hero card shadow |
| `navElevation` | 14dp | Bottom navigation shadow |
| `buttonElevation` / `buttonPressedElevation` | 12dp / 4dp | Button resting / pressed depth |
| `screenFadeInMs` / `screenFadeOutMs` | 220 / 180 | Screen crossfade |
| `pressFeedbackMs` | 150 | Press scale/glow/elevation animations |
| `buttonPressedScale` | 0.955 | Button shrink while pressed |
| `navSelectedScale` | 1.08 | Selected nav icon pop |
| `screenHPadding` | 20dp | Horizontal screen margin |
| `cardSpacing` | 16dp | Gap between stacked cards |
| `cardPadding` | 18dp | Card content inset |

## Component anatomy

**`GlassCard`** (`ui/components/GlassCard.kt`) — three layers, outermost first:
1. drop shadow (`glassShadow` ambient + black spot), applied before the clip so it
   renders outside the rounded outline;
2. frosted base: `glass` at the card's `glassAlpha`, plus a 1.25dp `glassBorder` stroke;
3. an inner vertical gradient (`glassHighlight` → transparent → soft black) that fakes
   bevel and specular shine.

The card also provides `LocalContentColor = ClearColors.text` to its content (as does
the root scaffold), so text and icons that don't set an explicit color automatically
stay readable in both themes.

Variants `GlassCardCompact` and `GlassCardHero` are just preset token bundles.

**`LiquidGlassButton`** (`ui/components/LiquidGlassButton.kt`) — accent-tinted frosted
gradient body, bright top rim border, a glow layer that brightens while pressed, and
press feedback (scale + elevation drop) driven by `pressFeedbackMs`.
`LiquidGlassIconButton` is the circular variant.

**Navigation** — a five-tab enum (`AppScreen` in `MainActivity.kt`); screens swap with
a crossfade inside `AnimatedContent`. The header (`GlassHeader`) shows the app name,
current screen title, and a live Protected/Paused status pill.

## Customization recipes

- **Change the brand accent:** edit `green` in both palettes in `Color.kt`. Buttons,
  switches, the status pill, and nav selection all follow it.
- **Make the glass frostier/clearer:** raise/lower the `*GlassAlpha` values in
  `ClearDesign.kt` (1.0 = opaque, lower = more transparent).
- **Flatter, calmer look:** reduce the `*Elevation` tokens and set
  `glassShadow` alpha lower.
- **Snappier or softer motion:** adjust `pressFeedbackMs` and the screen fade
  durations; set `buttonPressedScale` closer to 1.0 for subtler presses.
- **Default appearance for new installs:** change `DEFAULT_THEME_MODE` in
  `PreferenceKeys.java` (`"system"`, `"light"`, or `"dark"`).
- **New dark-palette tweaks:** keep `res/values-night/colors.xml` in sync with
  `ClearPalettes.dark` so the launch window matches the in-app theme.
