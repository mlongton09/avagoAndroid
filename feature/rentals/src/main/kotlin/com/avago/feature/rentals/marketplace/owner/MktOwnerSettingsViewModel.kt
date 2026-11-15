package com.avago.feature.rentals.marketplace.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.permissions.PermissionsManager
import com.avago.feature.rentals.marketplace.data.MarketplaceOwnerRepository
import com.avago.feature.rentals.marketplace.data.MktLocator
import com.avago.feature.rentals.marketplace.data.MktResult
import com.avago.feature.rentals.marketplace.domain.MktBodies
import com.avago.feature.rentals.marketplace.domain.MktCoords
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktOwnerLocation
import com.avago.feature.rentals.marketplace.domain.MktOwnerMarketplace
import com.avago.feature.rentals.marketplace.domain.MktOwnerSettings
import com.avago.feature.rentals.marketplace.domain.MktWindows
import com.avago.feature.rentals.marketplace.ui.PERMISSION_MARKETPLACE_MANAGE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** Editable copy of the settings as typed text (validated on save). */
data class MktSettingsDraft(
    val enabled: Boolean = false,
    val displayName: String = "",
    val about: String = "",
    val offerPickup: Boolean = true,
    val offerDelivery: Boolean = false,
    val deliveryRadiusKm: String = "",
    val offerMeetup: Boolean = false,
    /** Day key (mon…sun) → "09:00-12:00, 13:00-17:00". */
    val windows: Map<String, String> = emptyMap(),
    val blackouts: String = "",
    val slotMinutes: Int = 30,
    val leadHours: String = "24",
    val timezone: String = "",
) {
    companion object {
        fun of(s: MktOwnerSettings) = MktSettingsDraft(
            enabled = s.enabled,
            displayName = s.displayName.orEmpty(),
            about = s.about.orEmpty(),
            offerPickup = s.offerPickup,
            offerDelivery = s.offerDelivery,
            deliveryRadiusKm = s.deliveryRadiusKm?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty(),
            offerMeetup = s.offerMeetup,
            windows = MktWindows.DAYS.associateWith { MktWindows.format(s.meetAvailability[it].orEmpty()) },
            blackouts = MktWindows.formatBlackouts(s.meetBlackouts),
            slotMinutes = s.slotMinutes,
            leadHours = s.leadHours.toString(),
            timezone = s.timezone.orEmpty(),
        )
    }
}

/** Field-level validation result: [error] code + optional day / range args for the message. */
data class MktDraftError(val code: String, val day: String? = null)

/** Pure draft → settings conversion (unit-tested); returns the settings or the first error. */
object MktSettingsForm {
    val SLOT_CHOICES = listOf(15, 30, 45, 60, 90, 120)
    const val LEAD_MAX = 168
    const val RADIUS_MAX = 500

    fun build(d: MktSettingsDraft, base: MktOwnerSettings): Pair<MktOwnerSettings?, MktDraftError?> {
        val windows = mutableMapOf<String, List<Pair<String, String>>>()
        for (day in MktWindows.DAYS) {
            val text = d.windows[day].orEmpty()
            windows[day] = MktWindows.parse(text) ?: return null to MktDraftError("windows", day)
        }
        val blackouts = MktWindows.parseBlackouts(d.blackouts) ?: return null to MktDraftError("blackouts")
        val lead = d.leadHours.trim().toIntOrNull()?.takeIf { it in 0..LEAD_MAX } ?: return null to MktDraftError("lead")
        val radius = if (d.deliveryRadiusKm.isBlank()) null
        else d.deliveryRadiusKm.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..RADIUS_MAX.toDouble() } ?: return null to MktDraftError("radius")
        val tz = d.timezone.trim()
        if (tz.isNotEmpty() && runCatching { ZoneId.of(tz) }.isFailure) return null to MktDraftError("timezone")
        if (d.offerMeetup && windows.values.all { it.isEmpty() }) return null to MktDraftError("no_windows")
        if (d.slotMinutes !in 5..240) return null to MktDraftError("slot")
        return base.copy(
            enabled = d.enabled,
            displayName = d.displayName.trim().ifEmpty { null },
            about = d.about.trim().ifEmpty { null },
            offerPickup = d.offerPickup,
            offerDelivery = d.offerDelivery,
            deliveryRadiusKm = radius,
            offerMeetup = d.offerMeetup,
            meetAvailability = windows.filterValues { it.isNotEmpty() },
            meetBlackouts = blackouts,
            slotMinutes = d.slotMinutes,
            leadHours = lead,
            timezone = tz.ifEmpty { null },
        ) to null
    }
}

enum class MktSettingsNotice { Saved, LocationSaved, Reloaded }

data class MktSettingsState(
    val allowed: Boolean = true,
    val loading: Boolean = true,
    val data: MktOwnerMarketplace? = null,
    val draft: MktSettingsDraft = MktSettingsDraft(),
    val loadError: MktError? = null,
    val saving: Boolean = false,
    val error: MktError? = null,
    val draftError: MktDraftError? = null,
    val notice: MktSettingsNotice? = null,
    /** Location being edited (coordinates dialog). */
    val editing: MktOwnerLocation? = null,
    val lat: String = "",
    val lng: String = "",
    val coordsError: Boolean = false,
    val locating: Boolean = false,
    val savingLocation: Boolean = false,
    val locationError: MktError? = null,
) {
    val dirty: Boolean get() = data != null && draft != MktSettingsDraft.of(data.settings)
}

/** Owner "Marketplace" settings (§7, §10.6): opt-in, profile, fulfilment, meet-up hours, location coordinates. */
@HiltViewModel
class MktOwnerSettingsViewModel @Inject constructor(
    private val repo: MarketplaceOwnerRepository,
    private val locator: MktLocator,
    permissions: PermissionsManager,
) : ViewModel() {
    private val _state = MutableStateFlow(MktSettingsState())
    val state: StateFlow<MktSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            permissions.observeCan(PERMISSION_MARKETPLACE_MANAGE).collect { can ->
                val was = _state.value.allowed && _state.value.data != null
                _state.update { it.copy(allowed = can) }
                if (can && !was) load()
            }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val r = repo.settings()) {
                is MktResult.Ok -> _state.update { it.copy(loading = false, data = r.value, draft = MktSettingsDraft.of(r.value.settings)) }
                is MktResult.Err -> _state.update {
                    it.copy(loading = false, loadError = r.error, allowed = it.allowed && r.error != MktError.Forbidden)
                }
            }
        }
    }

    fun edit(f: (MktSettingsDraft) -> MktSettingsDraft) = _state.update { it.copy(draft = f(it.draft), draftError = null, error = null) }

    fun discard() = _state.update { s -> s.data?.let { s.copy(draft = MktSettingsDraft.of(it.settings), draftError = null, error = null) } ?: s }

    fun save() {
        val s = _state.value
        val data = s.data ?: return
        if (s.saving) return
        val (settings, err) = MktSettingsForm.build(s.draft, data.settings)
        if (settings == null) {
            _state.update { it.copy(draftError = err) }
            return
        }
        if (settings.enabled && !data.settings.enabled && !data.readiness.canEnable) {
            _state.update { it.copy(error = MktError.LocationCoordinatesRequired) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.saveSettings(MktBodies.settings(settings))) {
                is MktResult.Ok -> _state.update {
                    it.copy(saving = false, data = r.value, draft = MktSettingsDraft.of(r.value.settings), notice = MktSettingsNotice.Saved)
                }
                is MktResult.Err -> {
                    _state.update { it.copy(saving = false, error = r.error) }
                    // Someone else saved first: reload the server copy (the message explains it).
                    if (r.error == MktError.VersionConflict) reloadAfterConflict()
                }
            }
        }
    }

    private fun reloadAfterConflict() {
        viewModelScope.launch {
            val r = repo.settings()
            if (r is MktResult.Ok) _state.update { it.copy(data = r.value, draft = MktSettingsDraft.of(r.value.settings), notice = MktSettingsNotice.Reloaded) }
        }
    }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    // ---- Location coordinates ----

    fun editLocation(l: MktOwnerLocation) = _state.update {
        it.copy(editing = l, lat = l.latitude?.toString().orEmpty(), lng = l.longitude?.toString().orEmpty(), coordsError = false, locationError = null)
    }

    fun closeLocation() = _state.update { it.copy(editing = null, locating = false) }
    fun setLat(v: String) = _state.update { it.copy(lat = v.take(20), coordsError = false) }
    fun setLng(v: String) = _state.update { it.copy(lng = v.take(20), coordsError = false) }

    /** After the precise-location permission is granted. */
    fun useCurrentLocation() {
        if (_state.value.locating) return
        _state.update { it.copy(locating = true, locationError = null) }
        viewModelScope.launch {
            val p = locator.current(precise = true)
            _state.update {
                if (p == null) it.copy(locating = false, locationError = MktError.Generic)
                else it.copy(locating = false, lat = "%.6f".format(java.util.Locale.ROOT, p.first), lng = "%.6f".format(java.util.Locale.ROOT, p.second), coordsError = false)
            }
        }
    }

    fun saveLocation() {
        val s = _state.value
        val loc = s.editing ?: return
        if (s.savingLocation) return
        val (lat, lng) = MktCoords.parse(s.lat, s.lng) ?: run {
            _state.update { it.copy(coordsError = true) }
            return
        }
        _state.update { it.copy(savingLocation = true, locationError = null) }
        viewModelScope.launch {
            when (val r = repo.saveLocation(loc.id, MktBodies.location(lat, lng))) {
                is MktResult.Ok -> {
                    _state.update { it.copy(savingLocation = false, editing = null, notice = MktSettingsNotice.LocationSaved) }
                    // Readiness (can enable?) depends on the coordinates: refresh it, keeping unsaved edits.
                    when (val fresh = repo.settings()) {
                        is MktResult.Ok -> _state.update { st ->
                            val keep = st.dirty
                            st.copy(data = fresh.value, draft = if (keep) st.draft else MktSettingsDraft.of(fresh.value.settings))
                        }
                        is MktResult.Err -> Unit
                    }
                }
                is MktResult.Err -> _state.update { it.copy(savingLocation = false, locationError = r.error) }
            }
        }
    }
}
