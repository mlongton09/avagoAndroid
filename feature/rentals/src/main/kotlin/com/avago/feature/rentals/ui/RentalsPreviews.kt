package com.avago.feature.rentals.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.ui.components.StatusTone

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Rental status pills – light", showBackground = true, widthDp = 380)
@Preview(name = "Rental status pills – dark", showBackground = true, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Rental status pills – 200% font", showBackground = true, widthDp = 380, fontScale = 2f)
@Composable
private fun RentalStatusPreview() {
    AvagoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(AvagoSpacing.lg), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.md)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    com.avago.core.data.rental.FleetStatus.entries.forEach { FleetStatusChip(it) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    listOf("quoted", "reserved", "on_rent", "off_rent_requested", "returned", "closed").forEach { OrderStatusPill(it) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    InitialsAvatar("ACME Builders", StatusTone.Info)
                    InitialsAvatar("Jane Doe", StatusTone.Accent)
                }
            }
        }
    }
}

/** Pass-2 kit: all-clear state, money stats, thumbnail fallback, show-all and swipe rows. */
@Preview(name = "Rentals kit pass 2 – light", showBackground = true, widthDp = 380)
@Preview(name = "Rentals kit pass 2 – dark", showBackground = true, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Rentals kit pass 2 – 200% font", showBackground = true, widthDp = 380, fontScale = 2f)
@Preview(name = "Rentals kit pass 2 – German", showBackground = true, widthDp = 380, locale = "de")
@Composable
private fun RentalsKitPass2Preview() {
    AvagoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(vertical = AvagoSpacing.lg), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.md)) {
                AllClearCard()
                MoneyStats(
                    listOf(
                        Triple("Total", "€1,240.00", null),
                        Triple("Paid", "€400.00", StatusTone.Success),
                        Triple("Balance", "€840.00", StatusTone.Warning),
                    ),
                )
                Row(
                    Modifier.padding(horizontal = AvagoSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UnitThumbnail(null, Icons.Default.Construction, StatusTone.Info)
                    UnitThumbnail(null, Icons.Default.Construction, StatusTone.Danger, size = AvagoSpacing.thumbnailLg)
                    OverflowMenu(listOf(MenuAction("History", Icons.Default.History) {}, MenuAction("Stop renting", Icons.Default.Block, destructive = true) {}))
                }
                SwipeActionsRow(
                    start = SwipeAction("Book", Icons.Default.Add, StatusTone.Accent) {},
                    end = SwipeAction("Call", Icons.Default.Call, StatusTone.Success) {},
                ) { a11y ->
                    ListItem(headlineContent = { Text("ACME Builders") }, supportingContent = { Text("Swipe for Book / Call") }, modifier = a11y)
                }
                ShowAllRow(total = 7, expanded = false, onToggle = {})
            }
        }
    }
}