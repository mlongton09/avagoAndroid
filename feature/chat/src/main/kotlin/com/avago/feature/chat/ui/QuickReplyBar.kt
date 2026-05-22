package com.avago.feature.chat.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val QUICK_REPLIES = listOf("On my way", "Done ✅", "Need parts", "Need help", "Acknowledged")

/**
 * A horizontally scrollable row of quick-reply chips shown above the composer.
 * Clicking a chip calls [onQuickReply] with the chip text, equivalent to sending that message.
 */
@Composable
fun QuickReplyBar(onQuickReply: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        items(QUICK_REPLIES) { text ->
            AssistChip(
                onClick = { onQuickReply(text) },
                label = { Text(text) },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
