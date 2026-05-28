package com.avago.core.design.theme

import androidx.compose.ui.unit.dp

// Spacing constants mirroring iOS CardStyle and AppTheme measurements.
//
// Usage: AvagoSpacing.lg for 16.dp horizontal screen inset,
//        AvagoSpacing.cardCorner for card shape (use MaterialTheme.shapes.small instead for Compose shapes).
object AvagoSpacing {
    // Base scale
    val xxs = 2.dp
    val xs  = 4.dp   // card-to-card vertical gap, hairline margin
    val sm  = 8.dp   // group spacing, avatar-to-bubble gap
    val md  = 12.dp  // avatar-to-text gap, toolbar inner padding
    val lg  = 16.dp  // standard row/screen inset (iOS rowLeadingInset = 16pt)
    val xl  = 20.dp  // dialog padding
    val xxl = 24.dp  // section vertical spacing

    // Component sizes
    val avatarLg   = 40.dp   // asset/asset-detail avatar (iOS: 40pt)
    val avatarMd   = 28.dp   // chat message avatar (iOS: 28pt)
    val avatarSm   = 24.dp   // category badge (iOS: 24pt)
    val rowHeight  = 56.dp   // minimum tappable row height
    val tabHeight  = 49.dp   // bottom nav bar height (iOS tab bar: 49pt)
    val hairline   = 0.5.dp  // physical 1px separator (iOS: 1.0 / UIScreen.scale)

    // Cards
    val cardHorizontal = 16.dp  // card left/right margin in list (iOS: 16pt)
    val cardVertical   = 4.dp   // card top/bottom margin (iOS: 4pt)
    val priorityBarWidth = 4.dp // WO card left priority stripe (iOS: 4pt)
}
