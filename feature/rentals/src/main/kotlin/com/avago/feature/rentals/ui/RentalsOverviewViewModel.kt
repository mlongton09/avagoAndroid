package com.avago.feature.rentals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.domain.ChangeQueueRepository
import com.avago.core.data.domain.RentalFailure
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.domain.SyncRepository
import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.LocalFleet
import com.avago.core.network.rentals.RentalsApi
import com.avago.core.network.rentals.RentalsApiException
import com.avago.core.sync.ConnectivityMonitor
import com.avago.feature.rentals.domain.Availability
import com.avago.feature.rentals.domain.ClassView
import com.avago.feature.rentals.domain.LocalSchedule
import com.avago.feature.rentals.domain.ScheduleEvent
import com.avago.feature.rentals.domain.ServerAvailability
import com.avago.feature.rentals.domain.TodayAgenda
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Snapshot of the offline fleet (spec §11.1), recomputed whenever the store changes and every minute. */
data class FleetSnapshot(
    val now: Instant = Instant.now(),
    val units: List<LocalFleet.Result> = emptyList(),
    val counts: Map<FleetStatus, Int> = FleetStatus.entries.associateWith { 0 },
    val dueToday: Int = 0,
    val today: List<ScheduleEvent> = emptyList(),
    val inputs: LocalFleet.Inputs? = null,
    val agenda: TodayAgenda = TodayAgenda(),
    /** Number and customer of every order, for schedule rows. */
    val orderRefs: Map<String, LocalSchedule.OrderRef> = emptyMap(),
) {
    val isEmpty: Boolean get() = units.isEmpty()
    val availableNow: Int get() = counts[FleetStatus.AVAILABLE] ?: 0
}

data class AvailabilityQuery(
    val from: Instant,
    val to: Instant,
    val classId: String?,
    val qty: Int,
)

data class AvailabilityState(
    val query: AvailabilityQuery? = null,
    /** Offline result from synced allocations ("not confirmed"). */
    val local: List<Pair<Availability.ClassResult, List<Availability.UnitResult>>> = emptyList(),
    /** Server answer (classes) when online. */
    val server: List<ServerAvailability.ServerClass> = emptyList(),
    val serverUnits: List<ServerAvailability.ServerUnit> = emptyList(),
    val loading: Boolean = false,
    val offline: Boolean = false,
)

@HiltViewModel
class RentalsOverviewViewModel @Inject constructor(
    private val repo: RentalRepository,
    changes: ChangeQueueRepository,
    private val sync: SyncRepository,
    private val identity: IdentityManager,
    private val api: RentalsApi,
    connectivity: ConnectivityMonitor,
) : ViewModel() {

    val fleet: StateFlow<FleetSnapshot> = combine(repo.observeFleet(), repo.observeOrders()) { inputs, orders ->
        val now = Instant.now()
        val results = LocalFleet.compute(inputs, now).values.sortedWith(
            compareBy<LocalFleet.Result> { it.status.ordinal }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.unit.name },
        )
        val (counts, due) = LocalFleet.counts(results)
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone)
        val refs = orders.associate { it.id to LocalSchedule.OrderRef(it.number, it.customerName) }
        val today = LocalSchedule.events(
            inputs,
            refs,
            day.atStartOfDay(zone).toInstant(),
            day.plusDays(1).atStartOfDay(zone).toInstant(),
            now,
        )
        FleetSnapshot(now, results, counts, due, today, inputs, TodayAgenda.of(today), refs)
    }
        // Status / agenda computation walks every rental row: keep it off the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FleetSnapshot())

    /** Device connectivity, for the offline hint next to the sync row. */
    val online: StateFlow<Boolean> = connectivity.networkStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val classes: StateFlow<List<ClassView>> = repo.observeClasses()
        .map { rows -> rows.map(ClassView::of) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> = changes.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Rental changes the server refused, newest first (a hand-over gate offers its fix on the order). */
    val failures: StateFlow<List<RentalFailure>> = repo.observeFailures()
        .map { list -> list.sortedByDescending { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _statusFilter = MutableStateFlow<FleetStatus?>(null)
    val statusFilter: StateFlow<FleetStatus?> = _statusFilter.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val visibleUnits: StateFlow<List<LocalFleet.Result>> = combine(fleet, _statusFilter, _query) { f, s, q ->
        f.units.filter { r ->
            (s == null || r.status == s) &&
                (q.isBlank() || r.unit.name.contains(q, true) || r.unit.className?.contains(q, true) == true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStatusFilter(s: FleetStatus?) { _statusFilter.value = s }
    fun setQuery(q: String) { _query.value = q }

    suspend fun syncNow(): Boolean = sync.syncNow()

    fun dismiss(commandId: String) = viewModelScope.launch {
        runCatching { repo.dismissFailure(commandId) }.onFailure { Timber.w(it, "dismiss failed") }
    }

    // ── Availability search (§11.1) ──

    private val _availability = MutableStateFlow(AvailabilityState())
    val availability: StateFlow<AvailabilityState> = _availability.asStateFlow()

    fun searchAvailability(q: AvailabilityQuery) {
        val snap = fleet.value
        val now = Instant.now()
        val busy = snap.inputs?.let(LocalSchedule::busy).orEmpty()
        val byClass = snap.units.groupBy { it.unit.classId }
            .filterKeys { q.classId == null || it == q.classId }
        val local = byClass.map { (cid, units) ->
            Availability.forClass(cid, units.map { it.unit.assetId }, busy, q.from, q.to, q.qty, now)
        }
        _availability.value = AvailabilityState(query = q, local = local, loading = true)
        viewModelScope.launch {
            val acc = identity.getActiveAccountId() ?: run {
                _availability.value = _availability.value.copy(loading = false, offline = true)
                return@launch
            }
            try {
                val res = api.availabilitySearch(acc, q.from.toString(), q.to.toString(), classId = q.classId, qty = q.qty)
                val parsed = ServerAvailability.parse(res)
                _availability.value = _availability.value.copy(server = parsed.classes, serverUnits = parsed.units, loading = false)
            } catch (e: RentalsApiException) {
                _availability.value = _availability.value.copy(loading = false, offline = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.d(e, "availability search offline")
                _availability.value = _availability.value.copy(loading = false, offline = true)
            }
        }
    }

    fun clearAvailability() { _availability.value = AvailabilityState() }
}
