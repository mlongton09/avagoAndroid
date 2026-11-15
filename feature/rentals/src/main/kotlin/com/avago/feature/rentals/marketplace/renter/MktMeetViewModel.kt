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
import com.avago.feature.rentals.marketplace.domain.MktListing
import com.avago.feature.rentals.marketplace.domain.MktMeetSlots
import com.avago.feature.rentals.marketplace.domain.MktRenterMeetup
import com.avago.feature.rentals.marketplace.domain.MktSlot
import com.avago.feature.rentals.marketplace.domain.MktSlots
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class MktMeetState(
    val listing: MktListing? = null,
    val slots: MktMeetSlots? = null,
    val loading: Boolean = true,
    val loadError: MktError? = null,
    val selected: List<MktSlot> = emptyList(),
    val message: String = "",
    val includePeriod: Boolean = false,
    val firstDay: LocalDate? = null,
    val lastDay: LocalDate? = null,
    val submitting: Boolean = false,
    val error: MktError? = null,
    val done: MktRenterMeetup? = null,
) {
    /** Owner time zone for slot labels (slots response, then listing, then device). */
    val zone: ZoneId
        get() = (slots?.timezone ?: listing?.meetup?.timezone)
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
}

/** "Propose a meet-up" (§6, §10.4): 1–3 owner slots in the next [DAYS] days plus a message. */
@HiltViewModel
class MktMeetViewModel @Inject constructor(
    private val repo: MarketplaceRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    val id: String = saved.get<String>("listingId").orEmpty()
    internal var clock: Clock = Clock.systemDefaultZone()

    private val _state = MutableStateFlow(MktMeetState(listing = repo.cachedListing(id)))
    val state: StateFlow<MktMeetState> = _state.asStateFlow()
    val session: StateFlow<RenterSession?> = repo.session

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val listing = _state.value.listing ?: when (val r = repo.listing(id, null, null, null)) {
                is MktResult.Ok -> r.value.also { l -> _state.update { it.copy(listing = l) } }
                is MktResult.Err -> {
                    _state.update { it.copy(loading = false, loadError = r.error) }
                    return@launch
                }
            }
            when (val r = repo.meetSlots(id, DAYS, listing.locationId)) {
                is MktResult.Ok -> _state.update { s ->
                    // Keep only selections that are still offered.
                    val starts = r.value.slots.map { it.start }.toSet()
                    s.copy(loading = false, slots = r.value, selected = s.selected.filter { it.start in starts })
                }
                is MktResult.Err -> _state.update { it.copy(loading = false, loadError = r.error) }
            }
        }
    }

    fun toggle(slot: MktSlot) = _state.update { it.copy(selected = MktSlots.toggle(it.selected, slot), error = null) }
    fun setMessage(m: String) = _state.update { it.copy(message = m.take(MktSlots.MESSAGE_LIMIT)) }
    fun setIncludePeriod(on: Boolean) = _state.update { it.copy(includePeriod = on, error = null) }
    fun setPeriod(first: LocalDate, last: LocalDate?) = _state.update { it.copy(firstDay = first, lastDay = last ?: first, includePeriod = true, error = null) }

    fun submit() {
        val s = _state.value
        val l = s.listing ?: return
        if (s.submitting || s.done != null) return
        val lead = l.meetup?.leadHours ?: 0
        MktSlots.validate(s.selected, lead, clock.instant())?.let { code ->
            _state.update { it.copy(error = MktError.ofCode(code)) }
            return
        }
        val period = if (s.includePeriod) {
            val a = s.firstDay ?: run {
                _state.update { it.copy(error = MktError.DatesRequired) }
                return
            }
            val b = s.lastDay ?: a
            MktDates.startOfDay(a, s.zone) to MktDates.endExclusive(b, s.zone)
        } else {
            null
        }
        val body = MktBodies.meetup(s.selected, s.message, 1, l.locationId, period?.first, period?.second)
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.requestMeetup(id, body)) {
                is MktResult.Ok -> _state.update { it.copy(submitting = false, done = r.value) }
                is MktResult.Err -> {
                    _state.update { it.copy(submitting = false, error = r.error) }
                    // A slot was taken meanwhile: refresh the offer (selections that vanished are dropped).
                    if (r.error == MktError.SlotUnavailable || r.error == MktError.LeadTime) load()
                }
            }
        }
    }

    companion object {
        const val DAYS = 14
    }
}
