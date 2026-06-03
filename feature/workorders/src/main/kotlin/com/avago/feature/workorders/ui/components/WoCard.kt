package com.avago.feature.workorders.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.ui.CategoryBadge
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.model.statusColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Card layout mirrors iOS UnifiedWorkOrderCell:
// — Surface bg1 card on bg0 page background
// — 10dp corner radius (iOS CardStyle: 10pt continuous)
// — 4dp left priority bar (Critical=Red, High=Orange, Medium=Blue, Low=Gray) — Tailwind 500-stops
// — 24dp category badge circle (after priority bar)
// — Row 1: title (1 line) + due date (right, short "MMM d") — no status chip
// — Row 2: P1 · asset · effort | tech · Repeats
// Status is communicated via section bucket headers, not a chip.
@Composable
fun WoCard(
    wo: WorkOrderEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val priorityBarColor = priorityBarColor(wo.priority)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row {
            // ── Priority bar (4dp left stripe, full height) ───────────────
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(priorityBarColor),
            )

            // ── Category badge (24dp circle) ──────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 10.dp)
                    .size(24.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CategoryBadge(categoryId = wo.category)
            }

            // ── Card content ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            ) {
                // Row 1: title (left, 1 line) + due date (right, "MMM d")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = wo.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    wo.dueDate?.let { ms ->
                        Spacer(modifier = Modifier.width(8.dp))
                        val date = Instant.ofEpochMilli(ms)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("MMM d")),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Row 2: P1 · asset · effort | tech · Repeats
                Spacer(modifier = Modifier.height(2.dp))
                WoSubtitleLine(wo = wo)
            }
        }
    }
}

@Composable
private fun WoSubtitleLine(wo: WorkOrderEntity) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sepColor = mutedColor.copy(alpha = 0.5f)
    val priorityTextColor = priorityTextColor(wo.priority)
    val repeatsColor = Color(0xFF7C3AED)

    val dot  = "  ·  "   // "  ·  "
    val pipe = "  |  "

    val text = buildAnnotatedString {
        var first = true
        fun sep(separator: String) {
            if (!first) withStyle(SpanStyle(color = sepColor)) { append(separator) }
            first = false
        }

        // 1. Priority code P1/P2/P3/P4 (always present, colored)
        sep(dot)
        withStyle(SpanStyle(color = priorityTextColor, fontWeight = FontWeight.Bold)) {
            append(priorityCode(wo.priority))
        }

        // 2. Asset subtitle (dot separator)
        if (!wo.assetId.isNullOrBlank()) {
            sep(dot)
            withStyle(SpanStyle(color = mutedColor)) { append(wo.assetId!!) }
        }

        // 3. Estimated effort (pipe separator — visual split between "what" and "how much")
        val mins = wo.estimatedEffortMinutes
        if (mins != null && mins > 0L) {
            sep(pipe)
            val h = (mins / 60).toInt()
            val m = (mins % 60).toInt()
            val effortStr = when {
                h > 0 && m > 0 -> "${h}h ${m}m"
                h > 0           -> "${h}h"
                else            -> "${m}m"
            }
            withStyle(SpanStyle(color = mutedColor)) { append(effortStr) }
        }

        // 4. Assignee (dot separator)
        if (!wo.assignedTo.isNullOrBlank()) {
            sep(dot)
            withStyle(SpanStyle(color = mutedColor)) { append(wo.assignedTo!!) }
        }

        // 5. Repeats badge (purple, bold) — present when rrule is set
        if (!wo.rrule.isNullOrBlank()) {
            sep(dot)
            withStyle(SpanStyle(color = repeatsColor, fontWeight = FontWeight.Bold)) {
                append("Repeats")
            }
        }
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ── Sub-components used by other screens (WoStatusChip → Detail, AssigneeAvatar → TechPicker/Detail, etc.) ──

@Composable
fun WoStatusChip(status: WoStatus, modifier: Modifier = Modifier) {
    val color = status.statusColor()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun DueDateBadge(dueDateMs: Long?, status: WoStatus, modifier: Modifier = Modifier) {
    if (dueDateMs == null) {
        Text(
            text = stringResource(R.string.wo_card_no_due_date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val zone = ZoneId.systemDefault()
    val dueDate = Instant.ofEpochMilli(dueDateMs).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val isOverdue = dueDate.isBefore(today) &&
        status != WoStatus.COMPLETE &&
        status != WoStatus.CANCELLED
    val formatter = DateTimeFormatter.ofPattern("MMM d")
    val label = if (isOverdue)
        stringResource(R.string.wo_card_overdue_format, dueDate.format(formatter))
    else
        stringResource(R.string.wo_card_due_format, dueDate.format(formatter))
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = if (isOverdue) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun AssigneeAvatar(initials: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun PriorityBadge(priority: String, modifier: Modifier = Modifier) {
    Text(
        text = priority.replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelSmall,
        color = priorityBarColor(priority),
        modifier = modifier,
    )
}

// ── Priority helpers ──────────────────────────────────────────────────────────

// Left-bar fill colors — Tailwind 500-stops, matching iOS priorityBarColor()
@Composable
internal fun priorityBarColor(priority: String?): Color = when (priority?.lowercase()) {
    "critical" -> Color(0xFFEF4444)
    "high"     -> Color(0xFFF97316)
    "medium"   -> Color(0xFF3B82F6)
    else       -> Color(0xFF9CA3AF)
}

// Darker Tailwind 700-stop for inline priority code text, matching iOS priorityTextColor()
@Composable
internal fun priorityTextColor(priority: String?): Color = when (priority?.lowercase()) {
    "critical" -> Color(0xFFB91C1C)
    "high"     -> Color(0xFFC2410C)
    "medium"   -> Color(0xFF1D4ED8)
    else       -> Color(0xFF6B7280)
}

internal fun priorityCode(priority: String?): String = when (priority?.lowercase()) {
    "critical" -> "P1"
    "high"     -> "P2"
    "medium"   -> "P3"
    "low"      -> "P4"
    else       -> "P3"
}
