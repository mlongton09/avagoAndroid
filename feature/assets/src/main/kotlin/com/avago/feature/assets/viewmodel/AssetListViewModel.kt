package com.avago.feature.assets.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.domain.SyncRepository
import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.LocalFleet
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import com.avago.feature.assets.model.AssetTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AssetListViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val sync: SyncRepository,
    private val rentals: RentalRepository,
    private val permissionsManager: PermissionsManager,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    // Use Eagerly so the flow starts immediately on ViewModel creation (not when the
    // first subscriber appears). Combine isRoot directly so any change to _isRoot after
    // launch (e.g. initOnLaunch completing) is reflected without relying on the initial
    // value snapshot. Mirrors iOS: AppPermissions.shared.can("assets.create").
    val canCreateAsset: StateFlow<Boolean> = combine(
        permissionsManager.isRoot,
        permissionsManager.permissions,
    ) { isRoot, perms ->
        Timber.d("[AssetListVM] canCreateAsset: isRoot=$isRoot perms=${perms.contains(Permissions.ASSETS_CREATE)}")
        isRoot || perms.contains(Permissions.ASSETS_CREATE)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = permissionsManager.isRoot.value || permissionsManager.permissions.value.contains(Permissions.ASSETS_CREATE),
    )

    /** Bulk QR label sheets (online action). */
    val canPrintQrLabels: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.ASSET_QR_BULK_GENERATE)
        .stateIn(viewModelScope, SharingStarted.Eagerly, permissionsManager.can(Permissions.ASSET_QR_BULK_GENERATE))

    private val _filterType = MutableStateFlow<String?>(null)
    val filterType: StateFlow<String?> = _filterType.asStateFlow()

    private val _allAssets: StateFlow<List<Asset>> = repository.observeAssets()
        .catch { e ->
            Timber.e(e, "[AssetListViewModel] Asset flow error")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Distinct asset type keys present in the account's assets, ordered by AssetTypes.all. */
    val presentTypes: StateFlow<List<String>> = _allAssets
        .map { all ->
            val typeOrder = AssetTypes.all.map { it.key }
            val present = all.mapNotNull { it.assetTypeCode }.toHashSet()
            typeOrder.filter { it in present } + (present - typeOrder.toHashSet())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _rentalFilter = MutableStateFlow<FleetStatus?>(null)
    /** Rental status filter (spec §11.1); null = all assets. */
    val rentalFilter: StateFlow<FleetStatus?> = _rentalFilter.asStateFlow()

    /**
     * Rental status per rentable asset, computed on the device from the synced rentals
     * store with the server's rule ([LocalFleet]) and re-evaluated every minute so
     * reserved → on rent → overdue transitions show without a sync. Empty when the
     * account has no rentals data (non-rental assets carry no chip).
     */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val rentalStatuses: StateFlow<Map<String, FleetStatus>> = combine(rentals.observeFleet(), ticker) { inputs, _ ->
        LocalFleet.compute(inputs, Instant.now()).mapValues { it.value.status }
    }
        .catch { e ->
            Timber.e(e, "[AssetListViewModel] rental status flow error")
            emit(emptyMap())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    val assets: StateFlow<List<Asset>> = combine(
        _allAssets,
        _searchQuery,
        _filterType,
        _rentalFilter,
        rentalStatuses,
    ) { all, query, type, rental, statuses ->
        all
            .filter { asset -> type == null || asset.assetTypeCode == type }
            .filter { asset -> rental == null || statuses[asset.id] == rental }
            .filter { asset ->
                if (query.isBlank()) true
                else asset.name.contains(query, ignoreCase = true) ||
                    asset.make?.contains(query, ignoreCase = true) == true ||
                    asset.model?.contains(query, ignoreCase = true) == true ||
                    asset.assetTypeCode?.contains(query, ignoreCase = true) == true ||
                    asset.serialNumber?.contains(query, ignoreCase = true) == true
            }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Per-asset open work-order counts. The map contains only assets that have at
     * least one active (non-closed, non-cancelled) work order — a missing key means
     * zero open WOs. Mirrors the `openWOCountsByAsset` dictionary in iOS
     * AssetsListViewController that drives the pill badge on each asset cell.
     */
    val openWoCounts: StateFlow<Map<String, Int>> = _allAssets
        .map { all -> all.filter { it.openWorkOrders > 0 }.associate { it.id to it.openWorkOrders } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    fun onFilterTypeChanged(type: String?) { _filterType.value = type }

    fun onRentalFilterChanged(status: FleetStatus?) { _rentalFilter.value = status }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncError.value = null
            try {
                if (!sync.syncNow()) _syncError.value = "Couldn't sync. Tap to retry."
            } catch (e: Exception) {
                Timber.e(e, "[AssetListViewModel] Sync failed")
                _syncError.value = "Couldn't sync. Tap to retry."
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
