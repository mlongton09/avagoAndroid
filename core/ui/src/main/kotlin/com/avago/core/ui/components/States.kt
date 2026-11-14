package com.avago.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avago.core.ui.R

/**
 * Empty / no-results state: tonal icon, title, optional message and action.
 * Prefer a concrete next step ("Quick book", "Clear filters") over a dead end.
 */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp).semantics { heading() },
        )
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
        }
    }
}

/**
 * Inline error / warning banner with an optional Retry. Announced politely by
 * TalkBack when it appears; the icon plus text means colour is not the only cue.
 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = stringResource(R.string.core_retry),
    tone: StatusTone = StatusTone.Danger,
    icon: ImageVector = Icons.Filled.ErrorOutline,
) {
    val c = toneColors(tone)
    Surface(
        color = c.container,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = c.content, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = c.content, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            if (onRetry != null) {
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(retryLabel, color = c.content) }
            }
        }
    }
}

/** Animated shimmer fill for skeleton placeholders. */
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    background(Brush.linearGradient(listOf(base, highlight.copy(alpha = 0.7f), base), start = Offset(x, 0f), end = Offset(x + 300f, 0f)))
}

@Composable
fun SkeletonBlock(width: Dp?, height: Dp, modifier: Modifier = Modifier) {
    val m = if (width != null) modifier.size(width, height) else modifier.fillMaxWidth().height(height)
    Box(m.clip(RoundedCornerShape(6.dp)).shimmer())
}

/** Placeholder for a two-line list row with a leading thumbnail. */
@Composable
fun SkeletonListItem(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).shimmer())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(null, 14.dp, Modifier.padding(end = 48.dp))
            SkeletonBlock(160.dp, 12.dp)
        }
        SkeletonBlock(56.dp, 20.dp)
    }
}

/** A short list of skeleton rows announced once as "Loading". */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = 6) {
    val loading = stringResource(R.string.core_loading)
    Column(modifier.clearAndSetSemantics { contentDescription = loading }) {
        repeat(rows) { SkeletonListItem() }
    }
}

/** Success / failure haptics for confirmations (no-op when the user disabled touch feedback). */
class Haptics internal constructor(private val perform: (HapticFeedbackType) -> Unit) {
    fun success() = perform(HapticFeedbackType.Confirm)
    fun error() = perform(HapticFeedbackType.Reject)
    fun tick() = perform(HapticFeedbackType.SegmentTick)
}

@Composable
fun rememberHaptics(): Haptics {
    val h = LocalHapticFeedback.current
    return remember(h) { Haptics { h.performHapticFeedback(it) } }
}
