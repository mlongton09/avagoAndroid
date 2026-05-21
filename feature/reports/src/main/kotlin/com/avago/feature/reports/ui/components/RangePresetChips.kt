package com.avago.feature.reports.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avago.core.reports.model.ReportRangePreset

private val PRESET_LABELS = mapOf(
    ReportRangePreset.THIS_MONTH to "This Month",
    ReportRangePreset.LAST_MONTH to "Last Month",
    ReportRangePreset.THIS_QUARTER to "This Quarter",
    ReportRangePreset.LAST_QUARTER to "Last Quarter",
    ReportRangePreset.YTD to "YTD",
    ReportRangePreset.LAST_12_MONTHS to "Last 12 Months",
    ReportRangePreset.CUSTOM to "Custom",
)

@Composable
fun RangePresetChips(
    selected: ReportRangePreset,
    onSelect: (ReportRangePreset) -> Unit,
    modifier: Modifier = Modifier,
    includeCustom: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReportRangePreset.entries
            .filter { it != ReportRangePreset.CUSTOM || includeCustom }
            .forEach { preset ->
                FilterChip(
                    selected = selected == preset,
                    onClick = { onSelect(preset) },
                    label = { Text(PRESET_LABELS[preset] ?: preset.name) },
                )
            }
    }
}
