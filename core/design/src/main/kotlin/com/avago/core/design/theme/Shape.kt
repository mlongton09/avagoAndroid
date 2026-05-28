package com.avago.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner radii mapped to iOS CardStyle / AppTheme constants.
//
// extraSmall  → 8dp  — buttons, small chips, app icon badge (iOS: 8pt)
// small       → 10dp — cards, list row containers (iOS CardStyle: 10pt continuous)
// medium      → 12dp — dialogs, modal cards
// large       → 18dp — chat bubbles (iOS MessageCell: 18-20pt)
// extraLarge  → 20dp — bottom sheets (iOS sheet corners)
val AvagoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
