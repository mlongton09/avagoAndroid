package com.avago.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.LocationEntity
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
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LocationPickerViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _allLocations: StateFlow<List<LocationEntity>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) {
                    flowOf(emptyList())
                } else {
                    try {
                        dbFactory.get(accountId).locationDao().observeAll(accountId)
                    } catch (e: Exception) {
                        Timber.e(e, "[LocationPickerViewModel] Failed to observe locations")
                        flowOf(emptyList())
                    }
                }
            }
            .catch { e ->
                Timber.e(e, "[LocationPickerViewModel] Location flow error")
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val locations: StateFlow<List<LocationEntity>> = combine(
        _allLocations,
        _searchQuery,
    ) { all, query ->
        if (query.isBlank()) all
        else all.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
