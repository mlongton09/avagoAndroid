package com.avago.feature.chat.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 3-dot animated typing indicator, shown when a remote user is typing.
 * Matches the iOS TypingIndicatorView bounce animation.
 *
 * @param typingUserNames Optional list of names currently typing.
 *   - Empty      → dots only (anonymous / unknown typers)
 *   - 1 name     → "{Name} is typing…" + dots
 *   - 2 names    → "{A} and {B} are typing…" + dots
 *   - 3+ names   → "{count} people are typing…" + dots
 */
@Composable
fun TypingIndicator(
    typingUserNames: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1",
    )
    val phase2 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 133, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2",
    )
    val phase3 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 266, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3",
    )

    val labelText: String? = when (typingUserNames.size) {
        0 -> null
        1 -> "${typingUserNames[0]} is typing…"
        2 -> "${typingUserNames[0]} and ${typingUserNames[1]} are typing…"
        else -> "${typingUserNames.size} people are typing…"
    }

    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.large,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Animated dots
        listOf(phase1, phase2, phase3).forEach { phase ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { translationY = -6f * phase }
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f + 0.5f * phase),
                        CircleShape,
                    ),
            )
        }

        // Named typing label, shown to the right of the dots
        if (labelText != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = labelText,
                // iOS TypingIndicatorView: italicSystemFont(ofSize: 13) → bodyMedium italic.
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
