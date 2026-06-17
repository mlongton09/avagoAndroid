package com.avago.feature.inventory.bins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.BinEntity
import com.avago.core.data.db.entity.LocationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Bin List
// ---------------------------------------------------------------------------

data class BinListUiState(
    val bins: List<BinEntity> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val filterLocationId: String? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BinListViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(BinListUiState())
    val state: StateFlow<BinListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            identityManager.activeAccountId
                .filterNotNull()
                .flatMapLatest { accountId -> dbFactory.get(accountId).binDao().observeAll(accountId) }
                .collect { bins ->
                    _state.value = _state.value.copy(bins = bins, isLoading = false)
                }
        }
        viewModelScope.launch {
            identityManager.activeAccountId
                .filterNotNull()
                .flatMapLatest { accountId -> dbFactory.get(accountId).locationDao().observeAll(accountId) }
                .collect { locs -> _state.value = _state.value.copy(locations = locs) }
        }
    }

    fun setFilterLocation(locationId: String?) {
        _state.value = _state.value.copy(filterLocationId = locationId)
    }

    fun filteredBins(): List<BinEntity> {
        val s = _state.value
        return if (s.filterLocationId == null) s.bins
        else s.bins.filter { it.locationId == s.filterLocationId }
    }

    fun locationName(locationId: String): String =
        _state.value.locations.firstOrNull { it.locationId == locationId }?.name ?: locationId
}

// ---------------------------------------------------------------------------
// Bin Detail
// ---------------------------------------------------------------------------

data class BinDetailUiState(
    val bin: BinEntity? = null,
    val locationName: String = "",
    val isLoading: Boolean = true,
)

@HiltViewModel
class BinDetailViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(BinDetailUiState())
    val state: StateFlow<BinDetailUiState> = _state.asStateFlow()

    fun load(binId: String) {
        viewModelScope.launch {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            val db = dbFactory.get(accountId)
            val bin = db.binDao().getById(binId)
            val locationName = bin?.let { b ->
                db.locationDao().getById(b.locationId)?.name ?: b.locationId
            } ?: ""
            _state.value = BinDetailUiState(bin = bin, locationName = locationName, isLoading = false)
        }
    }
}
