package com.avago.feature.rentals.marketplace.owner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.permissions.PermissionsManager
import com.avago.feature.rentals.marketplace.data.MarketplaceOwnerRepository
import com.avago.feature.rentals.marketplace.data.MktResult
import com.avago.feature.rentals.marketplace.domain.MktBodies
import com.avago.feature.rentals.marketplace.domain.MktCounter
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktMeetupStatus
import com.avago.feature.rentals.marketplace.domain.MktOwnerMeetup
import com.avago.feature.rentals.marketplace.ui.PERMISSION_MEETUP_RESPOND
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

enum class MktMeetupNotice { Accepted, Countered, Declined, Blocked, Reloaded }

data class MktMeetupDetailState(
    val allowed: Boolean = true,
    val loading: Boolean = true,
    val meetup: MktOwnerMeetup? = null,
    val loadError: MktError? = null,
    val busy: Boolean = false,
    val error: MktError? = null,
    val notice: MktMeetupNotice? = null,
) {
    val canAnswer: Boolean get() = meetup?.let { MktMeetupStatus.ownerCanAnswer(it.status) } == true
    val canDecline: Boolean get() = meetup?.status.let { it == MktMeetupStatus.PENDING || it == MktMeetupStatus.COUNTERED }
    val canBlock: Boolean get() = meetup?.let { it.renter.id != null && !it.renter.blocked } == true
}

/**
 * One meet-up request for the owner (§7, §10.6): accept a slot, propose another time, decline or
 * block the renter. Every action sends `server_version`; a 409 `version_conflict` /
 * `invalid_state` reloads the request so the owner sees what changed.
 */
@HiltViewModel
class MktMeetupDetailViewModel @Inject constructor(
    private val repo: MarketplaceOwnerRepository,
    permissions: PermissionsManager,
    saved: SavedStateHandle,
) : ViewModel() {
    val id: String = saved.get<String>("meetupId").orEmpty()
    internal var clock: Clock = Clock.systemDefaultZone()

    private val _state = MutableStateFlow(MktMeetupDetailState())
    val state: StateFlow<MktMeetupDetailState> = _state.asStateFlow()
    private var started = false

    init {
        viewModelScope.launch {
            permissions.observeCan(PERMISSION_MEETUP_RESPOND).collect { can ->
                _state.update { it.copy(allowed = can) }
                if (can && !started) {
                    started = true
                    load()
                }
            }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val r = repo.meetup(id)) {
                is MktResult.Ok -> _state.update { it.copy(loading = false, meetup = r.value) }
                is MktResult.Err -> _state.update {
                    it.copy(loading = false, loadError = r.error, allowed = it.allowed && r.error != MktError.Forbidden)
                }
            }
        }
    }

    /** True when [index] is a proposed slot that has not started yet. */
    fun slotOpen(index: Int): Boolean = _state.value.meetup?.proposed?.getOrNull(index)?.start?.isAfter(clock.instant()) == true

    fun accept(slotIndex: Int, message: String, createReservation: Boolean) {
        val m = _state.value.meetup ?: return
        if (!slotOpen(slotIndex)) {
            _state.update { it.copy(error = MktError.SlotPassed) }
            return
        }
        act("accept", MktBodies.accept(slotIndex, message, createReservation && m.hasRentalPeriod, m.serverVersion), MktMeetupNotice.Accepted)
    }

    /** Validates locally first (start in the future, end after start, ≤ 4 h); the server checks the windows. */
    fun counter(start: Instant?, end: Instant?, message: String): Boolean {
        val m = _state.value.meetup ?: return false
        MktCounter.validate(start, end, clock.instant())?.let { code ->
            _state.update { it.copy(error = MktError.ofCode(code)) }
            return false
        }
        act("counter", MktBodies.counter(start!!, end!!, message, m.serverVersion), MktMeetupNotice.Countered)
        return true
    }

    fun decline(message: String) {
        val m = _state.value.meetup ?: return
        act("decline", MktBodies.decline(message, m.serverVersion), MktMeetupNotice.Declined)
    }

    fun block(reason: String) = act("block-renter", MktBodies.block(reason), MktMeetupNotice.Blocked)

    fun clearError() = _state.update { it.copy(error = null) }
    fun noticeShown() = _state.update { it.copy(notice = null) }

    private fun act(action: String, body: JsonObject, notice: MktMeetupNotice) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.meetupAction(id, action, body)) {
                is MktResult.Ok -> {
                    _state.update { s -> s.copy(busy = false, notice = notice, meetup = r.value ?: s.meetup) }
                    // Block answers without the request; accept may have linked an order. Re-read for both.
                    if (r.value == null || action == "accept") load()
                    // Keep the inbox list and drawer badge (cached "open" page) current.
                    launch { repo.refreshInbox(MktInboxFilter.Open.status) }
                }
                is MktResult.Err -> {
                    _state.update { it.copy(busy = false, error = r.error) }
                    if (r.error == MktError.VersionConflict || r.error == MktError.InvalidState || r.error == MktError.SlotPassed) load()
                }
            }
        }
    }
}
