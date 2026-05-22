package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.repository.AssetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AssetGroupPickerViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _allGroups: StateFlow<List<String>> = identityManager.activeAccountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList())
            else {
                repository.observeAssets(accountId)
                    .map { assets ->
                        assets.mapNotNull { it.assetType }
                            .distinct()
                            .sorted()
                            .map { key -> key.replace("_", " ").replaceFirstChar { it.uppercase() } }
                    }
                    .catch { e ->
                        Timber.e(e, "[AssetGroupPickerViewModel] Error loading asset types")
                        emit(emptyList())
                    }
            }
        }
        .catch { e ->
            Timber.e(e, "[AssetGroupPickerViewModel] Flow error")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredGroups: StateFlow<List<String>> = _searchQuery
        .flatMapLatest { query ->
            _allGroups.map { groups ->
                if (query.isBlank()) groups
                else groups.filter { it.contains(query, ignoreCase = true) }
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
}
