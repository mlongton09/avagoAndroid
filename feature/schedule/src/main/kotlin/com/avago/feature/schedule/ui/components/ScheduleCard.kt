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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avago.core.data.domain.PmOccurrence
import com.avago.feature.schedule.R
import com.avago.feature.schedule.util.PmDueStatus
import com.avago.feature.schedule.util.PmSchedule
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val AMBER = Color(0xFFF59E0B)
private val GREEN = Color(0xFF16A34A)

@Composable
fun pmStatusColor(status: PmDueStatus): Color = when (status) {
    PmDueStatus.OVERDUE -> MaterialTheme.colorScheme.error
    PmDueStatus.DUE_SOON -> AMBER
    PmDueStatus.ON_TRACK, PmDueStatus.COMPLETED -> GREEN
    PmDueStatus.SKIPPED, PmDueStatus.CANCELLED -> MaterialTheme.colorScheme.outline
}

@Composable
fun pmStatusLabel(status: PmDueStatus): String = stringResource(
    when (status) {
        PmDueStatus.OVERDUE -> R.string.schedule_status_overdue
        PmDueStatus.DUE_SOON -> R.string.schedule_status_due_soon
        PmDueStatus.ON_TRACK -> R.string.schedule_status_on_track
        PmDueStatus.COMPLETED -> R.string.schedule_status_completed
        PmDueStatus.SKIPPED -> R.string.schedule_status_skipped
        PmDueStatus.CANCELLED -> R.string.schedule_status_cancelled
    },
)

@Composable
fun PmStatusBadge(status: PmDueStatus, modifier: Modifier = Modifier) {
    val color = pmStatusColor(status)
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(
            text = pmStatusLabel(status),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** "Due Mar 3, 2026" and/or "Due at 15000" for an occurrence. */
@Composable
fun pmDueText(o: PmOccurrence): String? {
    val parts = buildList {
        o.dueAt?.let { add(PmSchedule.localDate(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
        o.dueValue?.let { add(stringResource(R.string.schedule_due_at_value, PmSchedule.formatValue(it))) }
    }
    return if (parts.isEmpty()) null else stringResource(R.string.schedule_due_format, parts.joinToString(" ? "))
}

@Composable
fun ScheduleCard(
    schedule: PmOccurrence,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    val status = PmSchedule.status(schedule, now)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.planTitle,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                schedule.assetName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                pmDueText(schedule)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            PmStatusBadge(status)
        }
    }
}
