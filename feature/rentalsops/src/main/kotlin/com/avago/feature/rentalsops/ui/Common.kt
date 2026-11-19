package com.avago.feature.rentalsops.ui

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Instant
import java.time.ZoneId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.EmptyStateView
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.toneColors
import com.avago.feature.rentalsops.data.PendingAction
import com.avago.feature.rentalsops.domain.TaskStatus
import com.avago.feature.rentalsops.R

/** Minimum touch target (Material / WCAG): every tappable element here is at least 48dp. */
internal val MinTouch = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpsTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.rentalops_back))
                }
            }
        },
        actions = { actions() },
    )
}

/** Section title above a group of rows (TalkBack heading); same role as the rentals section cards. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = AvagoSpacing.lg, top = AvagoSpacing.md, bottom = AvagoSpacing.xs)
            .semantics { heading() },
    )
}

/** Empty / no-access state: tonal icon + message (+ optional next step), centred in the space available. */
@Composable
internal fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyStateView(icon = icon, title = text, message = message, actionLabel = actionLabel, onAction = onAction)
    }
}

/** Waiting-to-sync / rejected marker; colour is never the only signal (icon + words). */
@Composable
internal fun PendingChip(pending: List<PendingAction>, modifier: Modifier = Modifier) {
    if (pending.isEmpty()) return
    val rejected = pending.any { it.rejected }
    StatusPill(
        stringResource(if (rejected) R.string.rentalops_rejected else R.string.rentalops_waiting_sync),
        if (rejected) StatusTone.Danger else StatusTone.Neutral,
        modifier = modifier,
        icon = if (rejected) Icons.Filled.ErrorOutline else Icons.Filled.Sync,
    )
}

/** Card explaining a server rejection; the optimistic change was already rolled back, so only Dismiss. */
@Composable
internal fun RejectedCard(action: PendingAction, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = toneColors(StatusTone.Danger)
    Surface(
        color = c.container,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = AvagoSpacing.lg, end = AvagoSpacing.xs, top = AvagoSpacing.md)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md)) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = c.content)
                Column(Modifier.weight(1f).padding(end = AvagoSpacing.md), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                    Text(stringResource(R.string.rentalops_rejected_body), style = MaterialTheme.typography.titleSmall, color = c.content)
                    action.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.content) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentalops_discard), color = c.content)
                }
            }
        }
    }
}

/** Offline / limited-mode notice: neutral tonal banner with an icon (not an error). */
@Composable
internal fun OfflineBanner(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Filled.CloudOff) {
    ErrorBanner(text, modifier = modifier, tone = StatusTone.Neutral, icon = icon)
}

/** Grouped card with rows separated by inset dividers. */
@Composable
internal fun OpsCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(modifier = modifier, title = title, content = content)
}

internal fun taskStatusTone(s: TaskStatus): StatusTone = when (s) {
    TaskStatus.FAILED -> StatusTone.Danger
    TaskStatus.COMPLETED -> StatusTone.Success
    TaskStatus.EN_ROUTE, TaskStatus.ARRIVED -> StatusTone.Info
    TaskStatus.CANCELLED -> StatusTone.Neutral
    TaskStatus.PLANNED, TaskStatus.ASSIGNED -> StatusTone.Warning
}

internal fun taskStatusIcon(s: TaskStatus): ImageVector = when (s) {
    TaskStatus.FAILED -> Icons.Filled.ErrorOutline
    TaskStatus.COMPLETED -> Icons.Filled.CheckCircle
    TaskStatus.EN_ROUTE -> Icons.Filled.LocalShipping
    TaskStatus.ARRIVED -> Icons.Filled.Place
    TaskStatus.CANCELLED -> Icons.Filled.Block
    TaskStatus.PLANNED, TaskStatus.ASSIGNED -> Icons.Filled.Schedule
}

/** Task status as a pill (icon + words, tone from the shared status palette). */
@Composable
internal fun TaskStatusPill(s: TaskStatus, modifier: Modifier = Modifier) {
    StatusPill(statusLabel(s), taskStatusTone(s), modifier = modifier, icon = taskStatusIcon(s))
}
internal object Fmt {
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun dateTime(i: Instant?): String = i?.let { dateTime.format(it.atZone(ZoneId.systemDefault())) } ?: "\u2014"
    fun date(i: Instant?): String = i?.let { date.format(it.atZone(ZoneId.systemDefault())) } ?: "\u2014"
    fun time(i: Instant): String = time.format(i.atZone(ZoneId.systemDefault()))
    fun millis(ms: Long): String = dateTime(Instant.ofEpochMilli(ms))
}
