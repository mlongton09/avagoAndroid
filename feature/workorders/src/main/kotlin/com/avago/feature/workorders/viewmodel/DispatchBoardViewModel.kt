package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.sync.SyncEngine
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.repository.WorkOrderRepository
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
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Ordered status columns shown on the dispatch board (excludes CANCELLED). */
val DISPATCH_COLUMNS = listOf(
    WoStatus.OPEN,
    WoStatus.ASSIGNED,
    WoStatus.IN_PROGRESS,
    WoStatus.ON_HOLD,
    WoStatus.PENDING_PARTS,
    WoStatus.COMPLETE,
)

@HiltViewModel
class DispatchBoardViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val columns: StateFlow<Map<WoStatus, List<WorkOrderEntity>>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) flowOf(emptyList())
                else repository.observeAll(accountId)
                    .catch { e -> Timber.e(e, "[DispatchBoardVM] flow error"); emit(emptyList()) }
            }
            .map { wos ->
                // Only active WOs on the board — exclude cancelled
                val active = wos.filter { it.status != WoStatus.CANCELLED.key }
                DISPATCH_COLUMNS.associateWith { status ->
                    active.filter { it.status == status.key }
                        .sortedBy { it.dueDate ?: Long.MAX_VALUE }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DISPATCH_COLUMNS.associateWith { emptyList() },
            )

    /**
     * Called when a card is dragged to a new column.
     * RBAC is checked in the UI before calling this.
     */
    fun moveToStatus(wo: WorkOrderEntity, targetStatus: WoStatus) {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val updated = wo.copy(
                    status = targetStatus.key,
                    updatedAt = now,
                    startedAt = if (targetStatus == WoStatus.IN_PROGRESS && wo.startedAt == null)
                        now else wo.startedAt,
                    completedAt = if (targetStatus == WoStatus.COMPLETE) now else wo.completedAt,
                )
                repository.upsert(accountId, updated)
            } catch (e: Exception) {
                Timber.e(e, "[DispatchBoardVM] moveToStatus failed for ${wo.woId}")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try { syncEngine.sync() } catch (e: Exception) {
                Timber.e(e, "[DispatchBoardVM] sync failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
