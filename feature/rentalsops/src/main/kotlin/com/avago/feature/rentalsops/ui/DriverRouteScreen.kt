package com.avago.feature.rentalsops.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.rememberHaptics
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.data.PendingAction
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.data.RentalOpsRepository.Fetch
import com.avago.feature.rentalsops.data.TaskView
import com.avago.feature.rentalsops.domain.DriverRoute
import com.avago.feature.rentalsops.domain.RouteStep
import com.avago.feature.rentalsops.domain.RouteStop
import com.avago.feature.rentalsops.domain.TaskAction
import com.avago.feature.rentalsops.domain.TaskKind
import com.avago.feature.rentalsops.domain.TaskStatus
import com.avago.feature.rentalsops.domain.TransitionError
import com.avago.feature.rentalsops.domain.TransitionResult
import com.avago.feature.rentalsops.push.RentalOpsRoutes
import com.avago.feature.rentalsops.tracking.RouteTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Where the shown route came from. */
enum class RouteSource { SERVER, CACHED, DEVICE }

data class RouteState(
    val route: DriverRoute? = null,
    val source: RouteSource = RouteSource.DEVICE,
    val fetchedAt: Long? = null,
    /** HTTP status of a failed refresh (null = transport/offline); only meaningful with [failed]. */
    val failure: Int? = null,
    val failed: Boolean = false,
    val loading: Boolean = false,
    val pending: Map<String, List<PendingAction>> = emptyMap(),
    val loaded: Boolean = false,
)

sealed class RouteEvent {
    data object Queued : RouteEvent()
    data class Error(val error: TransitionError) : RouteEvent()
    data class Open(val route: String) : RouteEvent()
}

@HiltViewModel
class DriverRouteViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: RentalOpsRepository,
    private val tracker: RouteTracker,
) : ViewModel() {
    private val fetch = MutableStateFlow<Fetch?>(null)
    private val loading = MutableStateFlow(false)
    private val zone: ZoneId = ZoneId.systemDefault()

    val state: StateFlow<RouteState> = combine(fetch, repo.observeTasks(), loading) { f, tasks, busy ->
        build(f, tasks, busy, repo.userId(), LocalDate.now(zone), zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteState())

    private val _events = MutableSharedFlow<RouteEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RouteEvent> = _events

    /** G5 location sharing: the driver's opt-in, the service state and whether the route is live. */
    val sharing: StateFlow<SharingState> = combine(tracker.sharingEnabled, tracker.running, state) { on, running, s ->
        SharingState(enabled = on, running = running, routeActive = s.route?.tracking == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SharingState())

    fun setSharing(on: Boolean) = viewModelScope.launch {
        tracker.setSharing(on)
        if (on) syncTracking() else tracker.stop()
    }

    /**
     * Starts the service while this screen is visible when the driver opted in and the server
     * route is live (the service itself stops when the route ends). Only a server-confirmed
     * route counts: the device fallback can't tell whether dispatch expects pings.
     */
    fun syncTracking() = viewModelScope.launch {
        val s = state.value
        val live = s.source == RouteSource.SERVER && s.route?.tracking == true
        if (live && !tracker.running.value && tracker.isSharingEnabled()) {
            s.route?.pingIntervalSeconds?.let { if (tracker.baseIntervalSeconds == null) tracker.baseIntervalSeconds = it }
            tracker.start()
        }
    }

    init {
        // §7 "refresh after each check-in": whenever a queued driver update is acknowledged the
        // pending count drops and the server route (with recomputed ETAs) is re-read.
        viewModelScope.launch {
            var last = Int.MAX_VALUE
            repo.observeTasks().map { list -> list.sumOf { it.pending.size } }.distinctUntilChanged().collect { n ->
                if (n < last && last != Int.MAX_VALUE) refresh()
                last = n
            }
        }
    }

    /** Re-reads `my-route` (foreground, pull-to-refresh, after check-ins) and nudges the outbox. */
    fun refresh() {
        if (loading.value) return
        viewModelScope.launch {
            loading.value = true
            try {
                repo.requestSync()
                fetch.value = repo.myRoute()
            } finally {
                loading.value = false
            }
        }
    }

    /** The stop's single next step: start / arrive are queued here; complete opens the stop. */
    fun step(stop: RouteStop) = viewModelScope.launch {
        val open = RouteEvent.Open(RentalOpsRoutes.task(stop.taskId))
        val action = when (RouteStep.of(stop.status)) {
            RouteStep.ON_MY_WAY -> TaskAction.START
            RouteStep.ARRIVED -> TaskAction.ARRIVE
            RouteStep.COMPLETE, RouteStep.NONE -> { _events.tryEmit(open); return@launch }
        }
        // A stop not yet on the device (route fresher than the task feed): open it, which syncs.
        val view: TaskView = repo.observeTask(stop.taskId).first() ?: run { _events.tryEmit(open); return@launch }
        val fix = LastLocation.fix(ctx)
        val r = repo.submit(view, action, null, null, null, fix)
        _events.tryEmit(if (r is TransitionResult.Rejected) RouteEvent.Error(r.error) else RouteEvent.Queued)
    }

    companion object {
        /**
         * Server route when fetched (or cached today), with queued check-ins overlaid; otherwise
         * the driver's tasks in the v2 store for [today].
         */
        fun build(f: Fetch?, tasks: List<TaskView>, busy: Boolean, me: String?, today: LocalDate, zone: ZoneId): RouteState {
            val local = tasks.associate { it.task.id to it.localStatus }
            val pending = tasks.filter { it.pending.isNotEmpty() }.associate { it.task.id to it.pending }
            val fallback = {
                val start = today.atStartOfDay(zone).toInstant()
                val end = today.plusDays(1).atStartOfDay(zone).toInstant()
                DriverRoute.fromTasks(tasks.map { it.task to it.localStatus }, me, start, end)
            }
            fun sameDay(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() == today
            return when (f) {
                null -> RouteState(route = fallback(), loading = busy, pending = pending, loaded = true)
                is Fetch.Ok -> RouteState(
                    route = DriverRoute.withLocalStatus(DriverRoute.parse(f.data.body), local),
                    source = RouteSource.SERVER,
                    fetchedAt = f.data.fetchedAt,
                    loading = busy,
                    pending = pending,
                    loaded = true,
                )
                is Fetch.Failed -> {
                    val cached = f.cached?.takeIf { sameDay(it.fetchedAt) }
                    RouteState(
                        route = cached?.let { DriverRoute.withLocalStatus(DriverRoute.parse(it.body), local) } ?: fallback(),
                        source = if (cached != null) RouteSource.CACHED else RouteSource.DEVICE,
                        fetchedAt = cached?.fetchedAt,
                        failure = f.status,
                        failed = true,
                        loading = busy,
                        pending = pending,
                        loaded = true,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverRouteScreen(onNavigate: (String) -> Unit, onBack: () -> Unit, vm: DriverRouteViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val sharing by vm.sharing.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    // §7 "refresh on foreground".
    LifecycleResumeEffect(vm) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val msgQueued = stringResource(R.string.rentalops_queued)
    val errInvalid = stringResource(R.string.rentalops_err_invalid)
    val errDriver = stringResource(R.string.rentalops_err_driver)
    val errProof = stringResource(R.string.rentalops_err_proof)
    val errReason = stringResource(R.string.rentalops_err_reason)
    LaunchedEffect(s.source, s.route?.tracking, sharing.enabled) { vm.syncTracking() }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is RouteEvent.Open -> onNavigate(e.route)
                RouteEvent.Queued -> { haptics.success(); snackbar.showSnackbar(msgQueued) }
                is RouteEvent.Error -> {
                    haptics.error()
                    snackbar.showSnackbar(
                        when (e.error) {
                            is TransitionError.Invalid -> errInvalid
                            TransitionError.DriverRequired -> errDriver
                            TransitionError.ProofRequired -> errProof
                            TransitionError.ReasonRequired -> errReason
                        },
                    )
                }
            }
        }
    }
    Scaffold(
        topBar = {
            OpsTopBar(stringResource(R.string.rentalops_route_title), onBack) {
                IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.rentalops_refresh)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        PullToRefreshBox(isRefreshing = s.loading && s.loaded, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(pad)) {
            val route = s.route
            when {
                !s.loaded -> SkeletonList(rows = 4)
                route == null || route.stops.isEmpty() -> Column(Modifier.fillMaxSize()) {
                    RouteBanner(s, vm::refresh)
                    if (sharing.enabled) RouteSharingCard(sharing, vm::setSharing)
                    EmptyState(
                        stringResource(R.string.rentalops_route_empty),
                        Modifier.verticalScroll(rememberScrollState()),
                        icon = Icons.Filled.Route,
                    )
                }
                else -> RouteList(route, s, sharing, onShare = vm::setSharing, onRetry = vm::refresh, onStep = vm::step, onOpen = { onNavigate(RentalOpsRoutes.task(it.taskId)) }, onNavigateTo = { openStop(ctx, it) })
            }
        }
    }
}

@Composable
private fun RouteList(
    route: DriverRoute,
    s: RouteState,
    sharing: SharingState,
    onShare: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onStep: (RouteStop) -> Unit,
    onOpen: (RouteStop) -> Unit,
    onNavigateTo: (RouteStop) -> Unit,
) {
    val next = route.next
    val done = route.stops.count { !it.status.isOpen }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = AvagoSpacing.xxl)) {
        item(key = "banner", contentType = "banner") { RouteBanner(s, onRetry) }
        if (route.tracking || sharing.enabled) {
            item(key = "sharing", contentType = "sharing") { RouteSharingCard(sharing, onShare) }
        }
        item(key = "summary", contentType = "header") {
            SectionHeader(stringResource(R.string.rentalops_route_summary, done, route.stops.size))
        }
        items(route.stops, key = { it.taskId }, contentType = { "stop" }) { stop ->
            StopCard(
                stop = stop,
                index = route.stops.indexOf(stop) + 1,
                isNext = stop == next,
                pending = s.pending[stop.taskId].orEmpty(),
                onStep = { onStep(stop) },
                onOpen = { onOpen(stop) },
                onNavigate = { onNavigateTo(stop) },
            )
        }
    }
}

/** Refresh-failed banner: forbidden (red), stale copy (amber) or device-only list (neutral). */
@Composable
private fun RouteBanner(s: RouteState, onRetry: () -> Unit) {
    if (!s.failed) return
    val msg = when {
        s.failure == 403 -> stringResource(R.string.rentalops_err_forbidden)
        s.source == RouteSource.CACHED -> stringResource(R.string.rentalops_offline_cached, Fmt.millis(s.fetchedAt ?: 0))
        else -> stringResource(R.string.rentalops_route_offline)
    }
    ErrorBanner(
        msg,
        modifier = Modifier.padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
        tone = when {
            s.failure == 403 -> StatusTone.Danger
            s.source == RouteSource.CACHED -> StatusTone.Warning
            else -> StatusTone.Neutral
        },
        onRetry = onRetry.takeIf { s.failure != 403 },
        retryLabel = stringResource(R.string.rentalops_retry),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopCard(
    stop: RouteStop,
    index: Int,
    isNext: Boolean,
    pending: List<PendingAction>,
    onStep: () -> Unit,
    onOpen: () -> Unit,
    onNavigate: () -> Unit,
) {
    val status = statusLabel(stop.status)
    val headline = stop.customerName ?: stop.orderNumber?.let { stringResource(R.string.rentalops_order_n, it) } ?: kindLabel(stop.kind)
    val number = stop.seq ?: index
    val stopLabel = stringResource(R.string.rentalops_route_stop, number)
    val ctx = LocalContext.current
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = if (isNext) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isNext) 2.dp else 1.dp, if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs)
            .semantics { stateDescription = status },
    ) {
        Column(Modifier.padding(AvagoSpacing.md), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md), verticalAlignment = Alignment.Top) {
                SeqBadge(number, taskStatusTone(stop.status), Modifier.semantics { contentDescription = stopLabel })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                    if (isNext) {
                        Text(stringResource(R.string.rentalops_route_next), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(headline, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(kindLabel(stop.kind), stop.orderNumber?.takeIf { stop.customerName != null }?.let { stringResource(R.string.rentalops_order_n, it) })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    stop.etaAt != null && stop.status.isOpen -> Text(
                        stringResource(R.string.rentalops_route_eta, Fmt.time(stop.etaAt)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    stop.scheduledAt != null -> Text(Fmt.time(stop.scheduledAt), style = MaterialTheme.typography.titleSmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.xs), verticalAlignment = Alignment.Top) {
                Icon(
                    if (stop.canNavigate) Icons.Filled.Place else Icons.Outlined.LocationOff,
                    null,
                    Modifier.size(16.dp).padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stop.jobSiteName?.let { site -> listOfNotNull(site, stop.address).joinToString(", ") } ?: stop.address ?: stringResource(R.string.rentalops_no_address),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            stop.windowEnd?.takeIf { stop.status.isOpen && stop.scheduledAt != null }?.let { end ->
                Text(
                    stringResource(R.string.rentalops_route_window, Fmt.time(stop.scheduledAt!!) + " \u2013 " + Fmt.time(end)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                TaskStatusPill(stop.status)
                PendingChip(pending)
            }
            if (stop.status.isOpen) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    // One primary action: the stop's next check-in step.
                    when (RouteStep.of(stop.status)) {
                        RouteStep.ON_MY_WAY -> StepButton(Icons.Filled.LocalShipping, stringResource(R.string.rentalops_route_on_my_way), onStep)
                        RouteStep.ARRIVED -> StepButton(Icons.Filled.Place, stringResource(R.string.rentalops_route_arrived), onStep)
                        RouteStep.COMPLETE -> StepButton(Icons.Filled.TaskAlt, stringResource(R.string.rentalops_action_complete), onStep)
                        RouteStep.NONE -> Unit
                    }
                    // §7: hide navigation when the stop has no coordinates.
                    if (stop.canNavigate) {
                        FilledTonalButton(onClick = onNavigate, modifier = Modifier.heightIn(min = MinTouch)) {
                            Icon(Icons.Filled.Directions, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(AvagoSpacing.sm))
                            Text(stringResource(R.string.rentalops_navigate))
                        }
                    }
                    stop.contactPhone?.let { phone ->
                        val callLabel = stringResource(R.string.rentalops_call, stop.contactName ?: phone)
                        FilledTonalButton(
                            onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, ("tel:" + Uri.encode(phone)).toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                            modifier = Modifier.heightIn(min = MinTouch).semantics { contentDescription = callLabel },
                        ) {
                            Icon(Icons.Filled.Call, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(AvagoSpacing.sm))
                            Text(stringResource(R.string.rentalops_call_short))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.heightIn(min = MinTouch)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(AvagoSpacing.sm))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SeqBadge(n: Int, tone: StatusTone, modifier: Modifier = Modifier) {
    val c = com.avago.core.ui.components.toneColors(tone)
    Surface(shape = CircleShape, color = c.container, modifier = modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(n.toString(), style = MaterialTheme.typography.titleMedium, color = c.content)
        }
    }
}

/** §7 Android navigation: the stop's `geo_uri`, then the `maps_url` web link. */
internal fun openStop(ctx: Context, stop: RouteStop) {
    val candidates = listOfNotNull(stop.geoUri, stop.mapsUrl).map { Intent(Intent.ACTION_VIEW, it.toUri()) }
    for (i in candidates) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
    openNavigation(ctx, stop.lat, stop.lng, stop.address)
}

private fun previewStops(): DriverRoute {
    val now = Instant.parse("2026-05-04T08:00:00Z")
    fun stop(i: Int, status: TaskStatus, lat: Double?) = RouteStop(
        taskId = "t$i", seq = i, kind = if (i % 2 == 0) TaskKind.PICKUP else TaskKind.DELIVERY, status = status,
        etaAt = now.plusSeconds(1_800L * i), scheduledAt = now.plusSeconds(1_800L * i), windowEnd = now.plusSeconds(1_800L * i + 3_600),
        lat = lat, lng = lat?.let { 13.4 }, geoFrom = "job_site", orderNumber = "R-10$i",
        customerName = listOf("Bau Müller GmbH", "Northwind Builders", "Kranverleih Süd")[i % 3], jobSiteName = "Site $i",
        address = "Hauptstraße $i, 10115 Berlin", contactName = "Anna", contactPhone = "+49301234567",
        geoUri = lat?.let { "geo:$it,13.4" }, mapsUrl = null,
    )
    return DriverRoute(
        "2026-05-04",
        listOf(stop(1, TaskStatus.COMPLETED, 52.5), stop(2, TaskStatus.EN_ROUTE, 52.52), stop(3, TaskStatus.ASSIGNED, null)),
        tracking = true,
        pingIntervalSeconds = 60,
    )
}

@Composable
private fun RoutePreviewBody() {
    val r = previewStops()
    RouteList(r, RouteState(route = r, source = RouteSource.SERVER, loaded = true), SharingState(enabled = true, running = true, routeActive = true), {}, {}, {}, {}, {})
}

@Preview(name = "Route light", showBackground = true, widthDp = 380)
@Preview(name = "Route dark", showBackground = true, widthDp = 380, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Route 200%", showBackground = true, widthDp = 380, fontScale = 2f)
@Preview(name = "Route de", showBackground = true, widthDp = 380, locale = "de")
@Composable
private fun RoutePreview() {
    com.avago.core.design.theme.AvagoTheme {
        Surface(color = MaterialTheme.colorScheme.background) { RoutePreviewBody() }
    }
}