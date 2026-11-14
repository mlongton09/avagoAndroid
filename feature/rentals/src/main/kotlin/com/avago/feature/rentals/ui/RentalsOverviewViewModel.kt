package com.avago.feature.rentals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.LocalFleet
import com.avago.core.network.rentals.RentalsApi
import com.avago.core.network.rentals.RentalsApiException
import com.avago.core.sync.ConnectivityMonitor
import com.avago.feature.rentals.domain.TodayAgenda
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import com.avago.feature.rentals.data.RentalRepository
import com.avago.feature.rentals.domain.Availability
import com.avago.feature.rentals.domain.ClassView
import com.avago.feature.rentals.domain.LocalSchedule
import com.avago.feature.rentals.domain.OutboxPlanner
import com.avago.feature.rentals.domain.ServerAvailability
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.ScheduleEvent
import com.avago.feature.rentals.domain.objs
import com.avago.feature.rentals.domain.str
import com.avago.feature.rentals.domain.long
import com.avago.feature.rentals.domain.bool
import com.avago.feature.rentals.domain.instant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Snapshot of the offline fleet (spec §11.1) recomputed every minute. */
data class FleetSnapshot(
    val now: Instant = Instant.now(),
    val units: List<LocalFleet.Result> = emptyList(),
    val counts: Map<FleetStatus, Int> = FleetStatus.entries.associateWith { 0 },
    val dueToday: Int = 0,
    val today: List<ScheduleEvent> = emptyList(),
    val records: List<RentalRecordEntity> = emptyList(),
    val agenda: TodayAgenda = TodayAgenda(),
) {
    val isEmpty: Boolean get() = units.isEmpty()
    val availableNow: Int get() = counts[FleetStatus.AVAILABLE] ?: 0
}

/** A record needing attention plus the queue row that drives its Retry / Discard actions. */
data class RentalNotice(val record: RentalRecordEntity, val queueId: String?)

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
    private val api: RentalsApi,
    connectivity: ConnectivityMonitor,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(Instant.now())
            delay(60_000)
        }
    }

    val fleet: StateFlow<FleetSnapshot> = combine(repo.fleetInputs(), ticker) { records, now ->
        val results = LocalFleet.compute(records, now).values.sortedWith(
            compareBy<LocalFleet.Result> { it.status.ordinal }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.unit.name },
        )
        val (counts, due) = LocalFleet.counts(results)
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone)
        val today = LocalSchedule.events(
            records,
            day.atStartOfDay(zone).toInstant(),
            day.plusDays(1).atStartOfDay(zone).toInstant(),
            now,
        )
        FleetSnapshot(now, results, counts, due, today, records, TodayAgenda.of(today))
    }
        // Status / agenda computation walks every rental row: keep it off the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FleetSnapshot())

    /** Device connectivity, for the offline hint next to "last synced". */
    val online: StateFlow<Boolean> = connectivity.networkStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val classes: StateFlow<List<ClassView>> = repo.records(RentalEntity.CLASS)
        .map { rows -> rows.map(ClassView::of).sortedBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastSyncedAt: StateFlow<Long?> = repo.lastSyncedAt()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val queue: StateFlow<List<SyncQueueEntity>> = repo.queue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Records needing attention (conflicts first, then drafts), each paired with its queue row.
     * Matching parses every queue payload, so it runs once per change off the main thread
     * instead of per row on every recomposition.
     */
    val notices: StateFlow<List<RentalNotice>> = combine(repo.unsynced(), repo.queue()) { rows, queue ->
        val parsed = queue.map { it to com.avago.feature.rentals.domain.parseObj(it.payload) }
        rows.filter { it.localState == RentalRecordEntity.STATE_CONFLICT || it.notice != null }
            .sortedBy { if (it.localState == RentalRecordEntity.STATE_CONFLICT) 0 else 1 }
            .map { rec -> RentalNotice(rec, queueFor(rec, parsed)?.queueId) }
    }
        .flowOn(Dispatchers.Default)
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
    fun syncNow() = repo.requestSync()

    fun retry(queueId: String) = viewModelScope.launch { runCatching { repo.retry(queueId) }.onFailure { Timber.w(it) } }
    fun discard(queueId: String) = viewModelScope.launch { runCatching { repo.discard(queueId) }.onFailure { Timber.w(it) } }

    /** Queue row for a record (by `local_id` / order id in the payload) to drive Retry / Discard. */
    private fun queueFor(record: RentalRecordEntity, queue: List<Pair<SyncQueueEntity, kotlinx.serialization.json.JsonObject?>>): SyncQueueEntity? =
        queue.filter { (q, p) ->
            p?.str("local_id") == record.id || p?.str("order_id") == record.id || q.entityId == record.clientUuid
        }.map { it.first }
            .minByOrNull { if (it.syncStatus == "conflict" || it.syncStatus == OutboxPlanner.STATUS_FAILED) 0 else 1 }

    // ── Availability search (§11.1) ───────────────────────────────────────────────

    private val _availability = MutableStateFlow(AvailabilityState())
    val availability: StateFlow<AvailabilityState> = _availability.asStateFlow()

    fun searchAvailability(q: AvailabilityQuery) {
        val snap = fleet.value
        val now = Instant.now()
        val busy = LocalSchedule.busy(snap.records)
        val byClass = snap.units.groupBy { it.unit.classId }
            .filterKeys { q.classId == null || it == q.classId }
        val local = byClass.map { (cid, units) ->
            Availability.forClass(cid, units.map { it.unit.assetId }, busy, q.from, q.to, q.qty, now)
        }
        _availability.value = AvailabilityState(query = q, local = local, loading = true)
        viewModelScope.launch {
            val acc = repo.accountId.first() ?: return@launch
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
