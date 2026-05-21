package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.sync.SyncEngine
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
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

enum class WoListFilter { ALL, OPEN, MINE, OVERDUE }

@HiltViewModel
class WorkOrderListViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(WoListFilter.ALL)
    val filter: StateFlow<WoListFilter> = _filter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _allWos: StateFlow<List<WorkOrderEntity>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) {
                    flowOf(emptyList())
                } else {
                    try {
                        repository.observeAll(accountId)
                    } catch (e: Exception) {
                        Timber.e(e, "[WoListVM] Failed to observe WOs for $accountId")
                        flowOf(emptyList())
                    }
                }
            }
            .catch { e ->
                Timber.e(e, "[WoListVM] WO flow error")
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val workOrders: StateFlow<List<WorkOrderEntity>> = combine(
        _allWos,
        _searchQuery,
        _filter,
    ) { all, query, filter ->
        val myUserId = identityManager.getActiveUserId() ?: identityManager.getActiveAccountId()
        val nowMs = System.currentTimeMillis()
        all
            .filter { wo ->
                when (filter) {
                    WoListFilter.ALL -> true
                    WoListFilter.OPEN -> wo.status !in listOf("complete", "cancelled")
                    WoListFilter.MINE -> wo.assignedTo == myUserId ||
                        wo.createdBy == myUserId
                    WoListFilter.OVERDUE -> {
                        val due = wo.dueDate ?: return@filter false
                        due < nowMs && wo.status !in listOf("complete", "cancelled")
                    }
                }
            }
            .filter { wo ->
                if (query.isBlank()) true
                else wo.title.contains(query, ignoreCase = true) ||
                    wo.description?.contains(query, ignoreCase = true) == true
            }
            .sortedByDescending { it.updatedAt }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }

    fun onFilterChanged(f: WoListFilter) { _filter.value = f }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncEngine.sync()
            } catch (e: Exception) {
                Timber.e(e, "[WoListVM] Sync failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
