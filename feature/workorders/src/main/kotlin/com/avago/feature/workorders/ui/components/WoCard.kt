package com.avago.feature.workorders.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun WoCard(
    wo: WorkOrderEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = wo.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                WoStatusChip(status = WoStatus.fromKey(wo.status))
            }

            // Asset name / no-asset label
            Spacer(modifier = Modifier.height(4.dp))
            val assetLabel = if (wo.assetId != null) stringResource(R.string.wo_card_asset_label, wo.assetId) else stringResource(R.string.wo_card_no_asset)
            Text(
                text = assetLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Due date + priority row
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DueDateBadge(dueDateMs = wo.dueDate, status = WoStatus.fromKey(wo.status))
                wo.priority?.takeIf { it.isNotBlank() }?.let { priority ->
                    PriorityBadge(priority = priority)
                }
            }

            // Assignee initials
            wo.assignedTo?.takeIf { it.isNotBlank() }?.let { assignedTo ->
                Spacer(modifier = Modifier.height(6.dp))
                AssigneeAvatar(initials = assignedTo.take(2).uppercase())
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
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
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
    val isOverdue = dueDate.isBefore(today) && status != WoStatus.COMPLETE && status != WoStatus.CANCELLED

    val formatter = DateTimeFormatter.ofPattern("MMM d")
    val label = if (isOverdue) stringResource(R.string.wo_card_overdue_format, dueDate.format(formatter)) else stringResource(R.string.wo_card_due_format, dueDate.format(formatter))
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun PriorityBadge(priority: String, modifier: Modifier = Modifier) {
    val color = when (priority) {
        "critical" -> MaterialTheme.colorScheme.error
        "high" -> com.avago.core.design.theme.AvagoAmber
        "medium" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = priority.replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelSmall,
        color = color,
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
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
