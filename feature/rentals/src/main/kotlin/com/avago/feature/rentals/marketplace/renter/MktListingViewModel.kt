package com.avago.feature.rentals.marketplace.renter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.feature.rentals.marketplace.data.MarketplaceRepository
import com.avago.feature.rentals.marketplace.data.MktResult
import com.avago.feature.rentals.marketplace.data.MktUnits
import com.avago.feature.rentals.marketplace.data.RenterSession
import com.avago.feature.rentals.marketplace.domain.DistanceUnit
import com.avago.feature.rentals.marketplace.domain.MktAvailabilityDay
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktListing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class MktListingState(
    val loading: Boolean = true,
    val listing: MktListing? = null,
    val error: MktError? = null,
    val availability: List<MktAvailabilityDay>? = null,
    val availabilityFailed: Boolean = false,
    val unit: DistanceUnit = DistanceUnit.KM,
)

/** Listing detail (§5): public, distance relative to the search centre passed in the route. */
@HiltViewModel
class MktListingViewModel @Inject constructor(
    private val repo: MarketplaceRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    val id: String = saved.get<String>("listingId").orEmpty()
    private val lat: Double? = saved.get<String>("lat")?.toDoubleOrNull()
    private val lng: Double? = saved.get<String>("lng")?.toDoubleOrNull()
    private val loc: String? = saved.get<String>("loc")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(MktListingState(listing = repo.cachedListing(id), unit = MktUnits.device()))
    val state: StateFlow<MktListingState> = _state.asStateFlow()
    val session: StateFlow<RenterSession?> = repo.session

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.listing(id, lat, lng, loc)) {
                is MktResult.Ok -> {
                    _state.update { it.copy(loading = false, listing = r.value) }
                    loadAvailability(r.value)
                }
                is MktResult.Err -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    private suspend fun loadAvailability(l: MktListing) {
        val zone = ZoneId.systemDefault()
        when (val r = repo.availability(l.id, LocalDate.now(zone), AVAILABILITY_DAYS, l.locationId, zone)) {
            is MktResult.Ok -> _state.update { it.copy(availability = r.value, availabilityFailed = false) }
            is MktResult.Err -> _state.update { it.copy(availabilityFailed = true) }
        }
    }

    companion object {
        const val AVAILABILITY_DAYS = 30
    }
}
