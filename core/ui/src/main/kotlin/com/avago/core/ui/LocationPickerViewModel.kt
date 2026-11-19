package com.avago.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.Location
import com.avago.core.data.domain.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LocationPickerViewModel @Inject constructor(
    locationRepository: LocationRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val locations: StateFlow<List<Location>> = combine(
        locationRepository.observeLocations()
            .catch { e ->
                Timber.e(e, "[LocationPickerViewModel] Location flow error")
                emit(emptyList())
            },
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
