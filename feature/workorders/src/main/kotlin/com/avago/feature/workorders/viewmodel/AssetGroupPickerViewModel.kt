package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.AssetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AssetGroupPickerViewModel @Inject constructor(
    repository: AssetRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Asset types in the account with how many assets each holds. */
    private val allGroups = repository.observeAssets()
        .map { assets -> assetGroupsOf(assets.mapNotNull { it.assetTypeCode }) }
        .catch { e ->
            Timber.e(e, "[AssetGroupPickerViewModel] Error loading asset types")
            emit(emptyList())
        }

    val filteredGroups: StateFlow<List<AssetGroup>> = combine(allGroups, _searchQuery) { groups, query ->
        if (query.isBlank()) groups else groups.filter { it.label.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}

/** All assets of one type; picking a group covers every one of them on the work order. */
data class AssetGroup(val typeCode: String, val label: String, val assetCount: Int)

internal fun assetGroupsOf(typeCodes: List<String>): List<AssetGroup> =
    typeCodes.filter { it.isNotBlank() }
        .groupingBy { it }.eachCount()
        .map { (code, count) -> AssetGroup(code, code.replace("_", " ").replaceFirstChar { it.uppercase() }, count) }
        .sortedBy { it.label }
