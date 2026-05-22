package com.avago.feature.schedule.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avago.core.data.db.entity.ScheduleEntity
import com.avago.feature.schedule.R
import com.avago.feature.schedule.util.RruleHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleCard(
    schedule: ScheduleEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOverdue = remember(schedule) { RruleHelper.isOverdue(schedule) }
    val isDueSoon = remember(schedule) { RruleHelper.isDueSoon(schedule) }
    val frequencyText = remember(schedule.rrule) { RruleHelper.describe(schedule.rrule) }
    val dueDateText = remember(schedule.nextDueAt) {
        schedule.nextDueAt?.let { epochMs ->
            Instant.ofEpochMilli(epochMs)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        }
    }

    val statusColor = when {
        !schedule.isActive -> Color.Gray
        isOverdue -> MaterialTheme.colorScheme.error
        isDueSoon -> Color(0xFFF59E0B) // amber-500
        else -> Color(0xFF16A34A) // green-600
    }

    val statusLabel = when {
        !schedule.isActive -> stringResource(R.string.schedule_status_inactive)
        isOverdue -> stringResource(R.string.schedule_status_overdue)
        isDueSoon -> stringResource(R.string.schedule_status_due_soon)
        else -> stringResource(R.string.schedule_status_on_track)
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
                // Category
                schedule.category?.takeIf { it.isNotBlank() }?.let { category ->
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Frequency
                Text(
                    text = frequencyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Due date or meter info
                if (schedule.scheduleType == "meter") {
                    schedule.meterDue?.let { due ->
                        Text(
                            text = "Due at ${due.toLong()} ${schedule.meterType ?: ""}".trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (dueDateText != null) {
                    Text(
                        text = "${stringResource(R.string.schedule_due_prefix)} $dueDateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Status chip
            Surface(
                color = statusColor.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
