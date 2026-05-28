package com.avago.core.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Typography mapped 1-to-1 with iOS AppTheme font methods.
// iOS pt ≈ Android sp at 1x density.
//
// Slot → iOS equivalent
// headlineMedium  → largeTitleFont   (22pt Bold)   — screen/page headers
// headlineLarge   → statFont         (18pt Regular) — stat numbers in strip
// titleLarge      → titleFont        (17pt Semibold)— list row titles, nav titles
// bodyLarge       → bodyFont         (17pt Regular) — primary body text
// titleMedium     → subheadFont      (15pt Regular) — second-line details
// bodyMedium      → smallFont        (13pt Regular) — secondary labels
// labelLarge      → smallBoldFont    (13pt Semibold)— badges, priority/status labels
// bodySmall       → captionFont      (12pt Regular) — timestamps, captions
// labelMedium     → captionBoldFont  (12pt Semibold)— section header labels (uppercase)
// labelSmall      → formFont         (11pt Regular) — form field fine print
val AvagoTypography = Typography(
    headlineLarge  = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 18.sp, lineHeight = 24.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge     = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleMedium    = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall      = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge     = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium    = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall     = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 11.sp, lineHeight = 14.sp),
)
