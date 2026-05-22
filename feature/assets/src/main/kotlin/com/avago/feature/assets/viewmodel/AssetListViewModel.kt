package com.avago.feature.assets.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.repository.AssetRepository
import com.avago.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AssetListViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _filterType = MutableStateFlow<String?>(null)
    val filterType: StateFlow<String?> = _filterType.asStateFlow()

    /**
     * The full unfiltered asset list, reactive to the active account.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _allAssets: StateFlow<List<AssetEntity>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) {
                    flowOf(emptyList())
                } else {
                    try {
                        repository.observeAssets(accountId)
                    } catch (e: Exception) {
                        Timber.e(e, "[AssetListViewModel] Failed to observe assets for $accountId")
                        flowOf(emptyList())
                    }
                }
            }
            .catch { e ->
                Timber.e(e, "[AssetListViewModel] Asset flow error")
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /**
     * Filtered and searched list presented to the UI.
     */
    val assets: StateFlow<List<AssetEntity>> = combine(
        _allAssets,
        _searchQuery,
        _filterType,
    ) { all, query, type ->
        all
            .filter { asset ->
                type == null || asset.assetType == type
            }
            .filter { asset ->
                if (query.isBlank()) true
                else {
                    asset.name.contains(query, ignoreCase = true) ||
                        asset.make?.contains(query, ignoreCase = true) == true ||
                        asset.model?.contains(query, ignoreCase = true) == true ||
                        asset.assetType?.contains(query, ignoreCase = true) == true
                }
            }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterTypeChanged(type: String?) {
        _filterType.value = type
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncError.value = null
            try {
                syncEngine.sync()
            } catch (e: Exception) {
                Timber.e(e, "[AssetListViewModel] Sync failed")
                _syncError.value = "Couldn't sync. Tap to retry."
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
