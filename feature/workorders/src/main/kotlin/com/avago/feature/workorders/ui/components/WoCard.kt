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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avago.core.data.db.entity.WorkOrderEntity
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
// — 4dp left priority bar (Critical=Red, High=Orange, Medium=Blue, Low=Gray)
// — hairline border (0.5dp outline color)
// — no elevation shadow (iOS uses very soft shadow, approximated by border)
@Composable
fun WoCard(
    wo: WorkOrderEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val priorityColor = priorityColor(wo.priority)

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
                    .background(priorityColor),
            )

            // ── Card content ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                // Row 1: title + status chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = wo.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    WoStatusChip(status = WoStatus.fromKey(wo.status))
                }

                // Row 2: asset · due date · priority label
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val assetLabel = if (wo.assetId != null)
                        stringResource(R.string.wo_card_asset_label, wo.assetId!!)
                    else
                        stringResource(R.string.wo_card_no_asset)
                    Text(
                        text = assetLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DueDateBadge(dueDateMs = wo.dueDate, status = WoStatus.fromKey(wo.status))
                }

                // Row 3: assignee avatar (optional)
                wo.assignedTo?.takeIf { it.isNotBlank() }?.let { assignedTo ->
                    Spacer(modifier = Modifier.height(8.dp))
                    AssigneeAvatar(initials = assignedTo.take(2).uppercase())
                }
            }
        }
    }
}

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

// Priority left-bar colors matching iOS WorkOrderFormatting.priorityColor()
@Composable
internal fun priorityColor(priority: String?): Color = when (priority?.lowercase()) {
    "critical" -> MaterialTheme.colorScheme.error
    "high"     -> MaterialTheme.colorScheme.tertiary
    "medium"   -> MaterialTheme.colorScheme.primary
    else       -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
fun PriorityBadge(priority: String, modifier: Modifier = Modifier) {
    Text(
        text = priority.replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelSmall,
        color = priorityColor(priority),
        modifier = modifier,
    )
}
