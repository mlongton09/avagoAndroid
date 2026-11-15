package com.avago.feature.rentals.marketplace.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.permissions.PermissionsManager
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.data.MarketplaceOwnerRepository
import com.avago.feature.rentals.marketplace.data.MktResult
import com.avago.feature.rentals.marketplace.domain.MktBlockedRenter
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.ui.MktPreviews
import com.avago.feature.rentals.marketplace.ui.PERMISSION_MARKETPLACE_MANAGE
import com.avago.feature.rentals.marketplace.ui.PERMISSION_MEETUP_RESPOND
import com.avago.feature.rentals.marketplace.ui.mktErrorText
import com.avago.feature.rentals.ui.EmptyState
import com.avago.feature.rentals.ui.MinTouch
import com.avago.feature.rentals.ui.RentalsTopBar
import com.avago.feature.rentals.ui.readableWidth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class MktBlocksState(
    val allowed: Boolean = true,
    val loading: Boolean = true,
    val items: List<MktBlockedRenter>? = null,
    val loadError: MktError? = null,
    val refreshFailed: Boolean = false,
    val busyId: String? = null,
    val error: MktError? = null,
    val unblocked: Boolean = false,
)

/** Blocked renters (§7 blocks, §10.6) with unblock. Visible to marketplace managers and meet-up responders. */
@HiltViewModel
class MktBlocksViewModel @Inject constructor(
    private val repo: MarketplaceOwnerRepository,
    permissions: PermissionsManager,
) : ViewModel() {
    private val _state = MutableStateFlow(MktBlocksState())
    val state: StateFlow<MktBlocksState> = _state.asStateFlow()
    private var started = false

    init {
        viewModelScope.launch {
            combine(permissions.observeCan(PERMISSION_MARKETPLACE_MANAGE), permissions.observeCan(PERMISSION_MEETUP_RESPOND)) { a, b -> a || b }
                .collect { can ->
                    _state.update { it.copy(allowed = can) }
                    if (can && !started) {
                        started = true
                        load()
                    }
                }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null, refreshFailed = false) }
        viewModelScope.launch {
            when (val r = repo.blocks()) {
                is MktResult.Ok -> _state.update { it.copy(loading = false, items = r.value) }
                is MktResult.Err -> _state.update {
                    when {
                        r.error == MktError.Forbidden -> it.copy(loading = false, allowed = false, items = null)
                        it.items != null -> it.copy(loading = false, refreshFailed = true)
                        else -> it.copy(loading = false, loadError = r.error)
                    }
                }
            }
        }
    }

    fun unblock(renterId: String) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = renterId, error = null) }
        viewModelScope.launch {
            when (val r = repo.unblock(renterId)) {
                is MktResult.Ok -> _state.update { s -> s.copy(busyId = null, unblocked = true, items = s.items?.filterNot { it.renterId == renterId }) }
                // 404: no longer blocked — drop it from the list as well.
                is MktResult.Err -> _state.update { s ->
                    if (r.error == MktError.NotFound) s.copy(busyId = null, items = s.items?.filterNot { it.renterId == renterId })
                    else s.copy(busyId = null, error = r.error)
                }
            }
        }
    }

    fun unblockedShown() = _state.update { it.copy(unblocked = false) }
}

@Composable
fun MktBlocksScreen(onBack: () -> Unit, vm: MktBlocksViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MktBlocksContent(state, onBack, vm::load, vm::unblock, vm::unblockedShown)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MktBlocksContent(
    state: MktBlocksState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUnblock: (String) -> Unit,
    onUnblockedShown: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val toast = stringResource(R.string.mkt_inbox_unblocked_toast)
    LaunchedEffect(state.unblocked) {
        if (state.unblocked) {
            snackbar.showSnackbar(toast)
            onUnblockedShown()
        }
    }
    Scaffold(topBar = { RentalsTopBar(stringResource(R.string.mkt_inbox_blocks_title), onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val items = state.items
            when {
                !state.allowed -> NoPermission(stringResource(R.string.mkt_inbox_no_permission))
                items == null && state.loading -> SkeletonList(rows = 3)
                items == null -> EmptyState(
                    mktErrorText(state.loadError ?: MktError.Generic, owner = true), icon = Icons.Outlined.Block,
                    actionLabel = stringResource(R.string.mkt_retry), onAction = onRefresh,
                )
                else -> PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "intro") {
                            Text(
                                stringResource(R.string.mkt_inbox_blocks_body), style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
                            )
                        }
                        if (state.refreshFailed) item(key = "refresh") {
                            ErrorBanner(
                                stringResource(R.string.rentals_refresh_failed), Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg),
                                onRetry = onRefresh, retryLabel = stringResource(R.string.rentals_retry), tone = StatusTone.Warning,
                            )
                        }
                        state.error?.let { e -> item(key = "err") { ErrorBanner(mktErrorText(e, owner = true), Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg)) } }
                        if (items.isEmpty()) item(key = "empty") { EmptyState(stringResource(R.string.mkt_no_blocks), icon = Icons.Outlined.Block) }
                        items(items, key = { it.renterId }) { b ->
                            Card(Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs)) {
                                Row(Modifier.padding(AvagoSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(b.name.ifBlank { stringResource(R.string.mkt_inbox_renter) }, style = MaterialTheme.typography.titleMedium)
                                        b.createdAt?.let { Text(stringResource(R.string.mkt_inbox_blocked_since, day(it)), style = MaterialTheme.typography.bodySmall) }
                                        b.reason?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    TextButton(onClick = { confirm = b.renterId }, enabled = state.busyId == null, modifier = Modifier.heightIn(min = MinTouch)) {
                                        Text(stringResource(R.string.mkt_inbox_unblock))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.items?.firstOrNull { it.renterId == confirm }?.let { b ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.mkt_unblock_title, b.name.ifBlank { stringResource(R.string.mkt_inbox_renter) })) },
            confirmButton = {
                TextButton(onClick = { confirm = null; onUnblock(b.renterId) }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.mkt_inbox_unblock))
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_inbox_cancel)) } },
        )
    }
}

private fun day(i: Instant): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(i)

@MktPreviews
@Composable
private fun MktBlocksPreview() {
    AvagoTheme {
        MktBlocksContent(
            MktBlocksState(loading = false, items = listOf(MktBlockedRenter("r1", "Pat Nuisance", "No-show twice", Instant.parse("2026-01-10T10:00:00Z")))),
            {}, {}, {}, {},
        )
    }
}
