package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.TechProfileEntity
import com.avago.feature.workorders.repository.WorkOrderRepository
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
class TechPickerViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _allProfiles: StateFlow<List<TechProfileEntity>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) flowOf(emptyList())
                else repository.observeTechProfiles(accountId)
                    .catch { e -> Timber.e(e, "[TechPickerVM] profiles error"); emit(emptyList()) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Sorted by composite dispatch score:
     *   skill_score × 0.40 + proximity × 0.30 + availability × 0.30
     *
     * In the absence of GPS and live workload data we approximate:
     * - skill_score: always 1.0 (we don't know required skills here)
     * - proximity: 0 (no location on client)
     * - availability: based on speedFactor (lower = more available / faster)
     */
    val techProfiles: StateFlow<List<TechProfileEntity>> = combine(
        _allProfiles,
        _searchQuery,
    ) { all, query ->
        all
            .filter { tech ->
                if (query.isBlank()) true
                else tech.userId.contains(query, ignoreCase = true) ||
                    tech.skills?.contains(query, ignoreCase = true) == true
            }
            .sortedByDescending { tech ->
                val speedScore = 1.0 - (tech.speedFactor ?: 1.0).coerceIn(0.5, 2.0) / 2.0
                // skill × 0.40 + proximity 0.0 × 0.30 + speed as availability proxy × 0.30
                1.0 * 0.40 + 0.0 * 0.30 + speedScore * 0.30
            }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchChanged(query: String) {
        _searchQuery.value = query
    }
}
