package com.avago.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 40dp tonal square used as a list-row thumbnail when there is no photo. */
@Composable
fun LeadingBadge(icon: ImageVector, tone: StatusTone, modifier: Modifier = Modifier) {
    val c = toneColors(tone)
    Box(modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = c.content, modifier = Modifier.size(22.dp))
    }
}

/** Transparent ListItem colours so rows sit on a [SectionCard] / list surface. */
@Composable
fun cardListItemColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)

// ── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "Kit – light", showBackground = true, widthDp = 380)
@Preview(name = "Kit – dark", showBackground = true, widthDp = 380, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun KitPreview() {
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                KpiGrid(
                    listOf(
                        KpiItem("On rent", "12", Icons.Filled.DirectionsCar, StatusTone.Accent),
                        KpiItem("Available", "34", Icons.Filled.CheckCircle, StatusTone.Success),
                        KpiItem("Overdue", "2", Icons.Filled.Warning, StatusTone.Danger, onClick = {}),
                        KpiItem("Due today", "5", Icons.Filled.Schedule, StatusTone.Warning),
                    ),
                )
                ErrorBanner("Couldn't refresh. Showing saved data.", onRetry = {}, retryLabel = "Retry")
                StepIndicator(listOf("Units", "Condition", "Sign", "Review"), current = 1)
                StickyDayHeader("Today", count = 3)
                SectionCard(title = "Order") {
                    ListItem(
                        headlineContent = { Text("Excavator 3t · EX-104") },
                        supportingContent = { Text("ACME Builders · 12 Mar – 19 Mar") },
                        leadingContent = { LeadingBadge(Icons.Filled.Inventory2, StatusTone.Info) },
                        trailingContent = { StatusPill("On rent", StatusTone.Accent, icon = Icons.Filled.DirectionsCar) },
                        colors = cardListItemColors(),
                    )
                    CardDivider()
                    TimelineEntry("Checked out", Icons.Filled.CalendarMonth, tone = StatusTone.Success, supporting = "by Alex", time = "12 Mar 09:14", isFirst = true)
                    TimelineEntry("Payment", Icons.Filled.CheckCircle, tone = StatusTone.Info, trailing = "€ 240.00", isLast = true)
                }
                BottomActionBar {
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Extend") }
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Check in") }
                }
            }
        }
    }
}

@Preview(name = "Empty + skeleton", showBackground = true, widthDp = 380)
@Composable
private fun StatesPreview() {
    MaterialTheme {
        Column(Modifier.fillMaxWidth()) {
            EmptyStateView(Icons.Filled.Inventory2, "No orders yet", message = "Book a unit to see it here.", actionLabel = "Quick book", onAction = {})
            SkeletonList(rows = 3)
        }
    }
}
