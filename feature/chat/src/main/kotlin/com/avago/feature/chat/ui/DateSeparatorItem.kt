package com.avago.feature.chat.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Centered date divider rendered between message groups in the LazyColumn when the date changes.
 * Shows labels like "Today", "Yesterday", "Monday", or "Jan 15".
 */
@Composable
fun DateSeparatorItem(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        )
    }
}
