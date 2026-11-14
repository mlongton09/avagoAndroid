package com.avago.feature.rentalsops.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avago.core.design.theme.AvagoSpacing
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.data.PendingAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm)
            .semantics { heading() },
    )
}

@Composable
internal fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(AvagoSpacing.xxl), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Waiting-to-sync / rejected marker; colour is never the only signal (icon + words). */
@Composable
internal fun PendingChip(pending: List<PendingAction>, modifier: Modifier = Modifier) {
    if (pending.isEmpty()) return
    val rejected = pending.any { it.rejected || it.parked }
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier.heightIn(min = 32.dp),
        label = {
            Text(stringResource(if (rejected) R.string.rentalops_rejected else R.string.rentalops_waiting_sync))
        },
        leadingIcon = {
            Icon(
                if (rejected) Icons.Filled.ErrorOutline else Icons.Filled.Sync,
                contentDescription = null,
                tint = if (rejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = if (rejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/** Card explaining a server rejection with Retry / Discard (the server state stands until resolved). */
@Composable
internal fun RejectedCard(action: PendingAction, onRetry: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs)) {
        Column(Modifier.padding(AvagoSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(R.string.rentalops_rejected_body),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = AvagoSpacing.sm),
                )
            }
            action.lastError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = AvagoSpacing.xs)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentalops_discard)) }
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentalops_retry)) }
            }
        }
    }
}

@Composable
internal fun OfflineBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = AvagoSpacing.sm))
    }
}

internal object Fmt {
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun dateTime(i: Instant?): String = i?.let { dateTime.format(it.atZone(ZoneId.systemDefault())) } ?: "\u2014"
    fun date(i: Instant?): String = i?.let { date.format(it.atZone(ZoneId.systemDefault())) } ?: "\u2014"
    fun millis(ms: Long): String = dateTime(Instant.ofEpochMilli(ms))
}
