package com.avago.feature.rentals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avago.core.ui.components.EmptyStateView
import com.avago.feature.rentals.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RentalsTopBar(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.rentals_back))
                }
            }
        },
        actions = actions,
    )
}

/** Section header (TalkBack heading), matching the grouped-card section titles. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .padding(start = 20.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** Two-column label/value row that wraps for large fonts; values are end-aligned for scanning. */
@Composable
internal fun LabelValue(label: String, value: String, modifier: Modifier = Modifier, emphasise: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = if (emphasise) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Empty state: icon + message (+ optional next step). */
@Composable
internal fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    EmptyStateView(icon = icon, title = text, modifier = modifier, message = message, actionLabel = actionLabel, onAction = onAction)
}

/** Tablet / expanded layout (WindowSizeClass width ≥ 600dp). */
@Composable
internal fun isWide(): Boolean = currentWindowAdaptiveInfo().windowSizeClass.minWidthDp >= 600

/**
 * Pull-to-refresh state for screens backed by the background sync: [onSync] only enqueues work,
 * so the indicator stays up until [lastSyncedAt] changes or 8 s pass — never spins forever.
 */
internal class SyncRefresh(val refreshing: Boolean, val onRefresh: () -> Unit)

@Composable
internal fun rememberSyncRefresh(lastSyncedAt: Long?, onSync: () -> Unit): SyncRefresh {
    var refreshing by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(lastSyncedAt)
    val sync by rememberUpdatedState(onSync)
    LaunchedEffect(refreshing) {
        if (!refreshing) return@LaunchedEffect
        val start = latest
        withTimeoutOrNull(8_000) { snapshotFlow { latest }.first { it != start } }
        refreshing = false
    }
    return SyncRefresh(refreshing) {
        refreshing = true
        sync()
    }
}

/**
 * True while the very first sync is plausibly in flight (no data, never synced) — bounded to
 * [timeoutMs] so an empty account falls through to its empty state instead of a skeleton.
 */
@Composable
internal fun rememberFirstLoad(empty: Boolean, lastSyncedAt: Long?, timeoutMs: Long = 6_000): Boolean {
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(timeoutMs)
        timedOut = true
    }
    return empty && lastSyncedAt == null && !timedOut
}

/**
 * Search field in the app's iOS-style pill look, but accessible: ≥ 48dp tall (grows with font
 * scale), the placeholder doubles as the TalkBack label, a 48dp clear button and IME Search
 * dismisses the keyboard.
 */
@Composable
internal fun RentalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Row(
                Modifier
                    .heightIn(min = MinTouch)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp)) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(MinTouch)) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.rentals_clear_search), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    )
}

/** Single-line, horizontally scrollable row of filter chips (keeps lists high on phones). */
@Composable
internal fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Sticky-header label for a calendar day: Today / Tomorrow / Yesterday, else "Mon, 3 Jun 2025". */
@Composable
internal fun dayLabel(date: java.time.LocalDate, zone: java.time.ZoneId): String {
    val today = java.time.LocalDate.now(zone)
    return when (date) {
        today -> stringResource(R.string.rentals_today)
        today.plusDays(1) -> stringResource(R.string.rentals_tomorrow)
        today.minusDays(1) -> stringResource(R.string.rentals_yesterday)
        else -> java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy", java.util.Locale.getDefault()).format(date)
    }
}