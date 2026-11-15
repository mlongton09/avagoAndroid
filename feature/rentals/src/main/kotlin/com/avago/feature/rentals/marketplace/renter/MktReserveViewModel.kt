package com.avago.feature.rentals.marketplace.renter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.feature.rentals.marketplace.data.MarketplaceRepository
import com.avago.feature.rentals.marketplace.data.MktResult
import com.avago.feature.rentals.marketplace.data.RenterSession
import com.avago.feature.rentals.marketplace.domain.MktBodies
import com.avago.feature.rentals.marketplace.domain.MktDates
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktFulfilment
import com.avago.feature.rentals.marketplace.domain.MktListing
import com.avago.feature.rentals.marketplace.domain.MktReservation
import com.avago.feature.rentals.marketplace.domain.MktReserve
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class MktReserveState(
    val listing: MktListing? = null,
    val loadError: MktError? = null,
    val firstDay: LocalDate? = null,
    val lastDay: LocalDate? = null,
    val time: LocalTime = LocalTime.of(9, 0),
    val qty: Int = 1,
    val fulfilment: String = MktFulfilment.PICKUP,
    val address: String = "",
    val notes: String = "",
    val submitting: Boolean = false,
    val error: MktError? = null,
    val done: MktReservation? = null,
) {
    val days: Long? get() = if (firstDay != null && lastDay != null && !lastDay.isBefore(firstDay)) MktDates.days(firstDay, lastDay) else null
}

/**
 * Reserve / request a quote (§5, §10.3). The rental runs from the pickup time on the first day
 * to the same time after the last day. One client_uuid per screen (kept across process death)
 * makes a retried submit idempotent.
 */
@HiltViewModel
class MktReserveViewModel @Inject constructor(
    private val repo: MarketplaceRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val id: String = saved.get<String>("listingId").orEmpty()
    internal var clock: Clock = Clock.systemDefaultZone()
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val clientUuid: String = saved.get<String>(KEY_UUID) ?: UUID.randomUUID().toString().also { saved[KEY_UUID] = it }

    private val _state = MutableStateFlow(MktReserveState(listing = repo.cachedListing(id)).withDefaultFulfilment())
    val state: StateFlow<MktReserveState> = _state.asStateFlow()
    val session: StateFlow<RenterSession?> = repo.session

    init { if (_state.value.listing == null) load() }

    fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            when (val r = repo.listing(id, null, null, null)) {
                is MktResult.Ok -> _state.update { it.copy(listing = r.value).withDefaultFulfilment() }
                is MktResult.Err -> _state.update { it.copy(loadError = r.error) }
            }
        }
    }

    fun setDates(first: LocalDate, last: LocalDate?) = _state.update { it.copy(firstDay = first, lastDay = last ?: first, error = null) }
    fun setTime(t: LocalTime) = _state.update { it.copy(time = t, error = null) }
    fun setQty(q: Int) = _state.update { it.copy(qty = q.coerceIn(1, 99), error = null) }
    fun setFulfilment(f: String) = _state.update { it.copy(fulfilment = f, error = null) }
    fun setAddress(a: String) = _state.update { it.copy(address = a.take(500), error = null) }
    fun setNotes(n: String) = _state.update { it.copy(notes = n.take(2000)) }

    fun submit() {
        val s = _state.value
        val l = s.listing ?: return
        if (s.submitting || s.done != null) return
        val code = MktReserve.validate(
            s.firstDay, s.lastDay, s.time, zone, l.minLeadMinutes, l.maxDays, s.fulfilment, s.address, s.qty, clock.instant(),
        )
        if (code != null) {
            _state.update { it.copy(error = MktError.ofCode(code)) }
            return
        }
        val (start, end) = MktDates.rentalWindow(s.firstDay!!, s.lastDay!!, s.time, zone)
        val body = MktBodies.reserve(start, end, l.locationId, s.qty, s.fulfilment, s.address, s.notes, clientUuid)
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.reserve(id, body)) {
                is MktResult.Ok -> _state.update { it.copy(submitting = false, done = r.value) }
                is MktResult.Err -> _state.update { it.copy(submitting = false, error = r.error) }
            }
        }
    }

    private companion object {
        const val KEY_UUID = "mkt_reserve_client_uuid"
    }
}

private fun MktReserveState.withDefaultFulfilment(): MktReserveState {
    val f = listing?.fulfilment ?: return this
    val ok = (fulfilment == MktFulfilment.PICKUP && f.pickup) || (fulfilment == MktFulfilment.DELIVERY && f.delivery)
    return if (ok) this else copy(fulfilment = if (f.pickup) MktFulfilment.PICKUP else MktFulfilment.DELIVERY)
}
