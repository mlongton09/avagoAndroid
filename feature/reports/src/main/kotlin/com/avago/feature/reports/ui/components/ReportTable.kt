package com.avago.feature.reports.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Horizontally-scrollable table with a header row.
 *
 * Must NOT use an internal LazyColumn — callers place this inside a LazyColumn
 * item already, and nested lazy lists with unbounded height crash at runtime.
 *
 * @param headers  Column header labels.
 * @param rows     Data rows; each inner list must have the same size as [headers].
 * @param colWidth Width of each column.
 */
@Composable
fun ReportTable(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    colWidth: Dp = 140.dp,
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            headers.forEach { header ->
                Box(
                    modifier = Modifier
                        .width(colWidth)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider()

        // Data rows
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .background(
                        if (index % 2 == 0) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
            ) {
                row.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .width(colWidth)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}
