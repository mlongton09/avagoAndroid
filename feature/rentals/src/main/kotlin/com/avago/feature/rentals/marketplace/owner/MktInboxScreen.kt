package com.avago.feature.rentals.marketplace.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.permissions.PermissionsManager
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.data.MarketplaceOwnerRepository
import com.avago.feature.rentals.marketplace.data.MktResult
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktMeetupStatus
import com.avago.feature.rentals.marketplace.domain.MktOwnerMeetup
import com.avago.feature.rentals.marketplace.domain.MktOwnerRenter
import com.avago.feature.rentals.marketplace.domain.MktSlot
import com.avago.feature.rentals.marketplace.renter.periodText
import com.avago.feature.rentals.marketplace.ui.MktPreviews
import com.avago.feature.rentals.marketplace.ui.PERMISSION_MEETUP_RESPOND
import com.avago.feature.rentals.marketplace.ui.meetupIcon
import com.avago.feature.rentals.marketplace.ui.meetupTone
import com.avago.feature.rentals.marketplace.ui.mktErrorText
import com.avago.feature.rentals.marketplace.ui.ownerStatusRes
import com.avago.feature.rentals.marketplace.ui.slotLabel
import com.avago.feature.rentals.ui.EmptyState
import com.avago.feature.rentals.ui.MinTouch
import com.avago.feature.rentals.ui.RentalsTopBar
import com.avago.feature.rentals.ui.readableWidth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject

/** Segmented inbox filters (§10.6) → `status` query value. */
enum class MktInboxFilter(val status: String) { Open("open"), Accepted("accepted"), All("all") }

data class MktInboxState(
    val allowed: Boolean = true,
    val filter: MktInboxFilter = MktInboxFilter.Open,
    val loading: Boolean = true,
    /** First page from the display cache plus pages loaded with "Load more". */
    val items: List<MktOwnerMeetup>? = null,
    val nextCursor: String? = null,
    val pendingCount: Int = 0,
    /** Device time of the cached first page; set while showing data the last refresh could not replace. */
    val cachedAt: Long? = null,
    val refreshFailed: Boolean = false,
    val error: MktError? = null,
    val loadingMore: Boolean = false,
)

/** Owner meet-up inbox (§7 respond, §10.6). Cached first page per filter; actions happen on the detail screen. */
@HiltViewModel
class MktInboxViewModel @Inject constructor(
    private val repo: MarketplaceOwnerRepository,
    permissions: PermissionsManager,
) : ViewModel() {
    private val _state = MutableStateFlow(MktInboxState())
    val state: StateFlow<MktInboxState> = _state.asStateFlow()
    private var cacheJob: Job? = null
    private var extra: List<MktOwnerMeetup> = emptyList()
    private var extraCursor: String? = null
    private var started = false

    init {
        viewModelScope.launch {
            permissions.observeCan(PERMISSION_MEETUP_RESPOND).collect { can ->
                _state.update { it.copy(allowed = can, loading = can && it.loading) }
                if (can && !started) {
                    started = true
                    select(_state.value.filter, force = true)
                }
            }
        }
    }

    fun select(filter: MktInboxFilter, force: Boolean = false) {
        if (!force && filter == _state.value.filter) return
        extra = emptyList()
        extraCursor = null
        _state.update { MktInboxState(allowed = it.allowed, filter = filter) }
        cacheJob?.cancel()
        cacheJob = viewModelScope.launch {
            repo.cachedInbox(filter.status).collect { c ->
                if (c == null) return@collect
                _state.update {
                    it.copy(
                        items = c.page.items + extra.filter { e -> c.page.items.none { i -> i.id == e.id } },
                        nextCursor = if (extra.isEmpty()) c.page.nextCursor else extraCursor,
                        pendingCount = c.page.pendingCount,
                        cachedAt = c.at,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        val filter = _state.value.filter
        _state.update { it.copy(loading = true, error = null, refreshFailed = false) }
        viewModelScope.launch {
            val r = repo.refreshInbox(filter.status)
            if (filter != _state.value.filter) return@launch
            when (r) {
                is MktResult.Ok -> {
                    extra = emptyList()
                    extraCursor = null
                    // The cache flow emits too; set directly so the screen never waits for Room.
                    _state.update { it.copy(loading = false, items = r.value.items, nextCursor = r.value.nextCursor, pendingCount = r.value.pendingCount, cachedAt = null) }
                }
                is MktResult.Err -> _state.update {
                    when {
                        r.error == MktError.Forbidden -> it.copy(loading = false, allowed = false, items = null)
                        it.items != null -> it.copy(loading = false, refreshFailed = true)
                        else -> it.copy(loading = false, error = r.error)
                    }
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || s.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val r = repo.inboxPage(s.filter.status, cursor)) {
                is MktResult.Ok -> {
                    val fresh = r.value.items.filter { n -> _state.value.items.orEmpty().none { it.id == n.id } }
                    extra = extra + fresh
                    extraCursor = r.value.nextCursor
                    _state.update { it.copy(loadingMore = false, items = it.items.orEmpty() + fresh, nextCursor = r.value.nextCursor) }
                }
                is MktResult.Err -> _state.update { it.copy(loadingMore = false, refreshFailed = it.items != null) }
            }
        }
    }
}

@Composable
fun MktInboxScreen(
    onBack: (() -> Unit)?,
    onOpen: (String) -> Unit,
    onBlocks: () -> Unit,
    vm: MktInboxViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    MktInboxContent(state, onBack, onOpen, onBlocks, vm::select, vm::refresh, vm::loadMore)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MktInboxContent(
    state: MktInboxState,
    onBack: (() -> Unit)?,
    onOpen: (String) -> Unit,
    onBlocks: () -> Unit,
    onFilter: (MktInboxFilter) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Scaffold(
        topBar = {
            RentalsTopBar(stringResource(R.string.mkt_inbox_title), onBack) {
                if (state.allowed) {
                    IconButton(onClick = onBlocks) { Icon(Icons.Outlined.Block, contentDescription = stringResource(R.string.mkt_inbox_blocks_title)) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val items = state.items
            when {
                !state.allowed -> NoPermission(stringResource(R.string.mkt_inbox_no_permission))
                else -> PullToRefreshBox(isRefreshing = state.loading && items != null, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "head") {
                            Column(Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                                Text(stringResource(R.string.mkt_inbox_subtitle), style = MaterialTheme.typography.bodyMedium)
                                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                    val labels = listOf(R.string.mkt_inbox_filter_open, R.string.mkt_inbox_filter_accepted, R.string.mkt_inbox_filter_all)
                                    MktInboxFilter.entries.forEachIndexed { i, f ->
                                        SegmentedButton(
                                            selected = state.filter == f, onClick = { onFilter(f) },
                                            shape = SegmentedButtonDefaults.itemShape(i, MktInboxFilter.entries.size),
                                            modifier = Modifier.heightIn(min = MinTouch),
                                        ) { Text(stringResource(labels[i])) }
                                    }
                                }
                                if (state.pendingCount > 0) {
                                    StatusPill(stringResource(R.string.mkt_inbox_pending_count, state.pendingCount.toString()), StatusTone.Warning)
                                }
                            }
                        }
                        if (state.refreshFailed) item(key = "refresh") {
                            ErrorBanner(
                                state.cachedAt?.let { stringResource(R.string.mkt_inbox_cached, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))) }
                                    ?: stringResource(R.string.rentals_refresh_failed),
                                Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg),
                                onRetry = onRefresh, retryLabel = stringResource(R.string.rentals_retry), tone = StatusTone.Warning,
                            )
                        }
                        when {
                            items == null && state.loading -> item(key = "skeleton") { SkeletonList(rows = 4) }
                            items == null -> item(key = "error") {
                                EmptyState(
                                    mktErrorText(state.error ?: MktError.Generic, owner = true), icon = Icons.Outlined.EventNote,
                                    actionLabel = stringResource(R.string.mkt_retry), onAction = onRefresh,
                                )
                            }
                            items.isEmpty() -> item(key = "empty") {
                                EmptyState(
                                    stringResource(if (state.filter == MktInboxFilter.Open) R.string.mkt_inbox_empty_open else R.string.mkt_inbox_empty),
                                    icon = Icons.Outlined.EventNote, message = stringResource(R.string.mkt_inbox_empty_hint),
                                )
                            }
                            else -> {
                                items(items, key = { it.id }) { m -> InboxCard(m) { onOpen(m.id) } }
                                if (state.nextCursor != null) item(key = "more") {
                                    Box(Modifier.fillMaxWidth().padding(AvagoSpacing.md), contentAlignment = Alignment.Center) {
                                        TextButton(onClick = onLoadMore, enabled = !state.loadingMore, modifier = Modifier.heightIn(min = MinTouch)) {
                                            Text(stringResource(if (state.loadingMore) R.string.mkt_loading else R.string.mkt_inbox_load_more))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxCard(m: MktOwnerMeetup, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    Card(onClick = onClick, modifier = Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs)) {
        Column(Modifier.padding(AvagoSpacing.md), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                Text(
                    m.renter.name.ifBlank { stringResource(R.string.mkt_inbox_renter) }, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                StatusPill(stringResource(ownerStatusRes(m.status)), meetupTone(m.status), icon = meetupIcon(m.status))
            }
            Text(
                listOfNotNull(m.className.ifBlank { stringResource(R.string.mkt_inbox_unknown_item) }, m.qty.takeIf { it > 1 }?.let { "× $it" }, m.locationName)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            val slot = m.accepted ?: m.counter ?: m.proposed.firstOrNull()
            slot?.let {
                val text = if (m.accepted != null) stringResource(R.string.mkt_inbox_meeting_at, slotLabel(it, zone, locale)) else slotLabel(it, zone, locale)
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
            if (m.rentalStart != null && m.rentalEnd != null) {
                Text(stringResource(R.string.mkt_inbox_rental_period, periodText(m.rentalStart, m.rentalEnd, zone)), style = MaterialTheme.typography.bodySmall)
            }
            m.message?.takeIf { it.isNotBlank() }?.let {
                Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

internal fun previewMeetup(status: String = MktMeetupStatus.PENDING): MktOwnerMeetup {
    val start = Instant.now().plus(Duration.ofDays(2))
    val slot = MktSlot(start, start.plus(Duration.ofMinutes(30)))
    return MktOwnerMeetup(
        "m1", status, "c1", "Utility trailer", "l1", "Main yard", 1,
        MktOwnerRenter("r1", "Sam Renter", true, null, null, false),
        listOf(slot, slot.copy(start = slot.start.plusSeconds(86_400), end = slot.end.plusSeconds(86_400))),
        null, null, start.plus(Duration.ofDays(3)), start.plus(Duration.ofDays(5)),
        "Need it for a weekend move.", null, null, null, start, Instant.now(), 2,
    )
}

@MktPreviews
@Composable
private fun MktInboxPreview() {
    AvagoTheme {
        MktInboxContent(
            MktInboxState(loading = false, items = listOf(previewMeetup(), previewMeetup(MktMeetupStatus.COUNTERED).copy(id = "m2")), pendingCount = 1),
            {}, {}, {}, {}, {}, {},
        )
    }
}
