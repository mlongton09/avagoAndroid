package com.avago.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EMOJI_CATEGORIES = listOf(
    // Expressions
    "😀", "😂", "😍", "🥺", "😭", "😤", "🤔", "😎",
    "🥳", "😴", "🤗", "😅", "😊", "🙄", "😬", "🤩",
    // Hands / Gestures
    "👍", "👎", "👋", "🙏", "✊", "💪", "👀", "🫡",
    "✌️", "🤝", "👌", "🤌",
    // Status / Symbols
    "✅", "❌", "⚠️", "❤️", "🔥", "💯", "⭐", "🎉",
    "🎊", "🏆", "🚀", "💡", "⚡", "💰", "🔑", "📸",
    "🆘", "🆗", "🔔", "🔕",
    // Work / Maintenance (prominent for field technicians)
    "🔧", "🔨", "🛠️", "📋", "✏️", "📝", "🗂️", "💼",
    "🏭", "🚗", "🚛", "⛽", "🔩", "🔬", "📦", "🏗️",
    "🧰", "🔌", "💧", "🌡️", "🧯", "🪜", "🔦", "🧲",
    // Nature / Weather
    "☀️", "⛅", "🌧️", "❄️", "🌱", "🌊",
    // Food / Break time
    "☕", "🍕", "🍔", "🥤",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "React",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                items(EMOJI_CATEGORIES) { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(4.dp)
                            .clickable {
                                onEmojiSelected(emoji)
                                onDismiss()
                            },
                    )
                }
            }
        }
    }
}
