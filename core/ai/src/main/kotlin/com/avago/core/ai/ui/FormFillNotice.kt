package com.avago.core.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Inline banner shown inside a form screen when Scout has pre-populated
 * one or more fields.  Gives the user a single-tap "Undo" to clear the
 * AI-filled values and return to a blank form.
 *
 * The banner is invisible when [filledFields] is empty so callers can
 * unconditionally include it in their layouts.
 *
 * Mirrors iOS ScoutSheetView's `formReadyBanner` surface.
 *
 * @param filledFields Human-readable field names Scout populated
 *                     (e.g. listOf("Title", "Asset", "Priority")).
 * @param onUndo       Called when the user taps "Undo"; the host form
 *                     should clear the Scout-supplied values.
 */
@Composable
fun FormFillNotice(
    filledFields: List<String>,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (filledFields.isEmpty()) return

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Scout filled:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = filledFields.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(onClick = onUndo) {
                Text("Undo")
            }
        }
    }
}
