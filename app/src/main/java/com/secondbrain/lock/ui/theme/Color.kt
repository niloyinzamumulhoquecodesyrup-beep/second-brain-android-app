package com.secondbrain.lock.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Every token below has a dark and light value lifted directly from the web app's CSS
 * variables, so native chrome tracks the web app's manual theme toggle exactly rather than
 * the OS dark-mode setting. Read [SbThemeState.mode] to pick a branch — plain property reads
 * done during composition/draw are tracked by Compose's snapshot system, so consumers
 * recompose live when the theme flips.
 */
private val isLight: Boolean get() = SbThemeState.mode == ThemeMode.LIGHT

// Ink — background scale, lifted from the Streak Section redesign's v2 bg/cardAlt/border/track
// tokens. 700/600/500 are the design's translucent border/track values pre-composited to opaque
// hex over cardAlt, since call sites here use them as solid fills (buttons, dots, progress
// tracks), not just card-edge borders like the source design does.
val Ink950: Color get() = if (isLight) Color(0xFFF4F5F7) else Color(0xFF08090B)
val Ink900: Color get() = if (isLight) Color(0xFFFFFFFF) else Color(0xFF24343A)
val Ink800: Color get() = if (isLight) Color(0xFFFAFAFB) else Color(0xFF1B282C)
val Ink700: Color get() = if (isLight) Color(0xFFECEDED) else Color(0xFF3D4B51)
val Ink600: Color get() = if (isLight) Color(0xFFCBD0D4) else Color(0xFF39474D)
val Ink500: Color get() = if (isLight) Color(0xFFB8BCBF) else Color(0xFF455358)

// Mist — text scale, lifted from the same design's textPrimary/Secondary/Tertiary tokens.
val Mist100: Color get() = if (isLight) Color(0xFF121B1E) else Color(0xFFF4F5F7)
val Mist300: Color get() = if (isLight) Color(0xFF6D7476) else Color(0x9EF4F5F7)
val Mist500: Color get() = if (isLight) Color(0x6B121B1E) else Color(0x66F4F5F7)
/** Legacy alias kept for existing call sites — maps to the dimmest text tier. */
val Mist400: Color get() = Mist500

// Bottom nav bar surface — deliberately its own token rather than reusing Ink900, since neither
// Ink900 branch matches these exact requested values.
val NavBarSurface: Color get() = if (isLight) Color(0xFFD9E6E9) else Color(0xFF121B1E)

// Streak — the reward/streak feature's palette. accentRed/silver/callisto are flat across light
// and dark (the design keeps them fixed); StreakCard is the design's tinted `card` surface,
// which the streak feature's cards use in place of the app's plain Ink900/cardAlt.
val StreakCard: Color get() = if (isLight) Color(0xFFD9E6E9) else Color(0xFF1C2A2E)
val StreakAccent: Color = Color(0xFFFB4F40)
val StreakSilver: Color = Color(0xFF8C8E8F)
val StreakCallisto: Color = Color(0xFFCBD0D4)

// Emerald — primary accent
val Emerald400: Color get() = if (isLight) Color(0xFF6D57BC) else Color(0xFF5EEAD4)
val Emerald500: Color get() = if (isLight) Color(0xFF5843A7) else Color(0xFF2DD4BF)
val Emerald600: Color get() = if (isLight) Color(0xFF49378A) else Color(0xFF14B8A6)

// Violet — secondary accent
val Violet400: Color get() = if (isLight) Color(0xFF506B82) else Color(0xFFB7A6F7)
val Violet500: Color get() = if (isLight) Color(0xFF405669) else Color(0xFFA78BFA)
val Violet600: Color get() = if (isLight) Color(0xFF354756) else Color(0xFF8B6EF2)

// Gold — tertiary accent
val Gold400: Color get() = if (isLight) Color(0xFFBD382C) else Color(0xFFF0D9A3)
val Gold500: Color get() = if (isLight) Color(0xFF992D24) else Color(0xFFE0C07E)

// Status
val Rose400: Color get() = if (isLight) Color(0xFFD1264F) else Color(0xFFFB7185)
val Rose500: Color get() = if (isLight) Color(0xFF881337) else Color(0xFFF43F5E)
val Red400: Color get() = if (isLight) Color(0xFFB91C1C) else Color(0xFFF87171)
/** Used by RewardPanel's "Focus sessions" gauge (web's orange-400). */
val Orange400: Color get() = if (isLight) Color(0xFFC2410C) else Color(0xFFFB923C)
/** Used by RewardPanel's "Focus time" gauge — matches web RewardPanel.js's #7dd3fc exactly. */
val Sky400: Color get() = if (isLight) Color(0xFF0369A1) else Color(0xFF7DD3FC)

// Aura — `.bg-aura` radial-glow backdrop
val Aura1: Color get() = if (isLight) Color(0xFF506B82) else Color(0xFFA78BFA)
val Aura2: Color get() = if (isLight) Color(0xFFBD382C) else Color(0xFFE0C07E)
val Aura3: Color get() = if (isLight) Color(0xFF6D57BC) else Color(0xFF2DD4BF)
val AuraOpacity: Float get() = if (isLight) 0.35f else 1f
