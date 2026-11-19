package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.Worker
import com.avago.core.data.domain.WorkerRepository
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
class TechPickerViewModel @Inject constructor(
    workers: WorkerRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Assignable workers (signed-up users), available first, then by free capacity
     * (`max_active_work_orders` minus open work), then by name.
     */
    val techProfiles: StateFlow<List<Worker>> = combine(
        workers.observeWorkers().catch { e -> Timber.e(e, "[TechPickerVM] workers error"); emit(emptyList()) },
        _searchQuery,
    ) { all, query ->
        all.filter { it.userId != null }
            .filter { w ->
                query.isBlank() || w.displayName.contains(query, ignoreCase = true) ||
                    w.skills.any { it.contains(query, ignoreCase = true) }
            }
            .sortedWith(
                compareByDescending<Worker> { it.available }
                    .thenByDescending { w -> w.maxActiveWorkOrders?.let { it - w.activeWorkOrders } ?: Int.MAX_VALUE }
                    .thenBy { it.displayName.lowercase() },
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchChanged(query: String) {
        _searchQuery.value = query
    }
}
