package com.avago.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Grouped content card ("inset grouped" list section, iOS-parity): an optional
 * heading above a rounded surface. Use [HorizontalDivider] between rows inside.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    contentPadding: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null || trailing != null) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(start = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                trailing?.invoke(this)
            }
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(if (contentPadding) Modifier.padding(16.dp) else Modifier, content = content)
        }
    }
}

/** Inset divider for rows inside a [SectionCard]. */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
}

@Immutable
data class KpiItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val tone: StatusTone,
    val supporting: String? = null,
    val onClick: (() -> Unit)? = null,
)

/** One headline metric: tonal icon badge, large value and label. */
@Composable
fun KpiTile(item: KpiItem, modifier: Modifier = Modifier) {
    val c = toneColors(item.tone)
    val description = listOfNotNull(item.label, item.value, item.supporting).joinToString(", ")
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = c.container, modifier = Modifier.size(32.dp)) {}
                    Icon(item.icon, contentDescription = null, tint = c.content, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
            }
            Text(
                item.value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = if (item.tone == StatusTone.Neutral) MaterialTheme.colorScheme.onSurface else c.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.supporting?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    val m = modifier.heightIn(min = 96.dp)
    val onClick = item.onClick
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = border,
            modifier = m.clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
            content = body,
        )
    } else {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = border,
            modifier = m.clearAndSetSemantics { contentDescription = description },
            content = body,
        )
    }
}

/**
 * Responsive KPI grid: 2 columns on phones, 4 on tablets / unfolded foldables.
 * Tiles in a row share the tallest height so values align at large font scales.
 */
@Composable
fun KpiGrid(items: List<KpiItem>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val columns = if (maxWidth >= 560.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { KpiTile(it, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Sticky bottom bar for the next step on a detail screen (Check out, Collect
 * payment …). Pads for the gesture/nav bar so buttons stay thumb-reachable.
 */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** Day / group header for `stickyHeader {}` — opaque so rows scroll under it. */
@Composable
fun StickyDayHeader(text: String, modifier: Modifier = Modifier, count: Int? = null, tone: StatusTone = StatusTone.Neutral) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (tone == StatusTone.Neutral) MaterialTheme.colorScheme.onSurfaceVariant else toneColors(tone).content,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            if (count != null) {
                StatusPill(count.toString(), tone)
            }
        }
    }
}
