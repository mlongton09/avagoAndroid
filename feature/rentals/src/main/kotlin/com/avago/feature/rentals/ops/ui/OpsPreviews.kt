package com.avago.feature.rentals.ops.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.ui.components.StickyDayHeader
import com.avago.feature.rentals.ops.domain.TaskStatus

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Ops status + states – light", showBackground = true, widthDp = 380)
@Preview(name = "Ops status + states – dark", showBackground = true, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Ops status – 200% font", showBackground = true, widthDp = 380, fontScale = 2f)
@Composable
private fun OpsStatusPreview() {
    AvagoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(vertical = AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                StickyDayHeader("Today", count = TaskStatus.entries.size)
                FlowRow(
                    Modifier.padding(horizontal = AvagoSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
                ) {
                    TaskStatus.entries.forEach { TaskStatusPill(it) }
                }
                OfflineBanner("Offline – showing saved tasks.")
                EmptyState("No tasks for today", icon = Icons.Outlined.LocalShipping, message = "New deliveries appear here.", actionLabel = "Show all tasks", onAction = {})
            }
        }
    }
}
