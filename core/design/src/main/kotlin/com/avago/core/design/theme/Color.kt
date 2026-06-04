package com.avago.core.design.theme

import androidx.compose.ui.graphics.Color

// ── Dark surface tokens — iOS AppTheme bg0/bg1/bg2/bg3 ─────────────────────
val DarkBackground       = Color(0xFF0D1117)  // bg0: page/window
val DarkSurface          = Color(0xFF161B22)  // bg1: cards/rows
val DarkSurfaceVariant   = Color(0xFF21262D)  // bg2: section headers/toolbars
val DarkSurfaceContainer = Color(0xFF2D333B)  // bg3: active/pressed states
val DarkOnSurface        = Color(0xFFE6EDF3)  // text0: primary content
val DarkOnSurfaceVariant = Color(0xFF8B949E)  // text1: secondary/labels
val DarkOutline          = Color(0xFF30363D)  // border/separator

// ── Light surface tokens — iOS AppTheme bg0/bg1/bg2/bg3 ────────────────────
val LightBackground       = Color(0xFFF0F4F8)  // bg0: page/window
val LightSurface          = Color(0xFFFFFFFF)  // bg1: cards/rows
val LightSurfaceVariant   = Color(0xFFEAEEF2)  // bg2: section headers/toolbars
val LightSurfaceContainer = Color(0xFFD0D7DE)  // bg3: active/pressed states
val LightOnSurface        = Color(0xFF1F2328)  // text0: primary content
val LightOnSurfaceVariant = Color(0xFF57606A)  // text1: secondary/labels
val LightOutline          = Color(0xFFD0D7DE)  // border/separator

// ── Accent colors — dark mode (iOS AppTheme) ────────────────────────────────
val DarkAccentBlue   = Color(0xFF539BF5)  // accentBlue dark
val DarkAccentGreen  = Color(0xFF46954A)  // accentGreen dark
val DarkAccentRed    = Color(0xFFE5534B)  // accentRed dark
val DarkAccentOrange = Color(0xFFC69026)  // accentOrange dark

// ── Accent colors — light mode (iOS AppTheme) ───────────────────────────────
val LightAccentBlue   = Color(0xFF0969DA)  // accentBlue light
val LightAccentGreen  = Color(0xFF1A7F37)  // accentGreen light
val LightAccentRed    = Color(0xFFD1242F)  // accentRed light
val LightAccentOrange = Color(0xFF9A6700)  // accentOrange light

// ── Outline variant — used for low-priority WO bar, subtle separators ───────
val DarkOutlineVariant  = Color(0xFF4D5561)  // darker gray (iOS systemGray dark)
val LightOutlineVariant = Color(0xFFB8C1CA)  // lighter gray (iOS systemGray light)

// ── Primary container tokens (FAB, chips) — green-tinted to match iOS accentGreen CTA ──
val DarkPrimaryContainer   = Color(0xFF1A3020)  // dark green-tinted surface
val DarkOnPrimaryContainer = Color(0xFF46954A)  // accentGreen dark
val LightPrimaryContainer   = Color(0xFFD4EDDA)  // light green tint
val LightOnPrimaryContainer = Color(0xFF1A7F37)  // accentGreen light
