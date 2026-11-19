package com.avago.feature.inventory.bins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.Bin
import com.avago.core.data.domain.Location
import com.avago.core.data.domain.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Bin List
// ---------------------------------------------------------------------------

data class BinListUiState(
    val bins: List<Bin> = emptyList(),
    val locations: List<Location> = emptyList(),
    val filterLocationId: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class BinListViewModel @Inject constructor(
    private val locations: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BinListUiState())
    val state: StateFlow<BinListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(locations.observeBins(), locations.observeLocations()) { bins, locs -> bins to locs }
                .collect { (bins, locs) -> _state.update { it.copy(bins = bins, locations = locs, isLoading = false) } }
        }
    }

    fun setFilterLocation(locationId: String?) {
        _state.update { it.copy(filterLocationId = locationId) }
    }

    fun filteredBins(): List<Bin> {
        val s = _state.value
        return if (s.filterLocationId == null) s.bins else s.bins.filter { it.locationId == s.filterLocationId }
    }

    fun locationName(locationId: String): String =
        _state.value.locations.firstOrNull { it.id == locationId }?.name ?: locationId
}

// ---------------------------------------------------------------------------
// Bin Detail
// ---------------------------------------------------------------------------

data class BinDetailUiState(
    val bin: Bin? = null,
    val locationName: String = "",
    val isLoading: Boolean = true,
)

@HiltViewModel
class BinDetailViewModel @Inject constructor(
    private val locations: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BinDetailUiState())
    val state: StateFlow<BinDetailUiState> = _state.asStateFlow()

    fun load(binId: String) {
        viewModelScope.launch {
            val bin = locations.bin(binId)
            val locationName = bin?.let { b -> locations.location(b.locationId)?.name ?: b.locationId }.orEmpty()
            _state.value = BinDetailUiState(bin = bin, locationName = locationName, isLoading = false)
        }
    }
}
