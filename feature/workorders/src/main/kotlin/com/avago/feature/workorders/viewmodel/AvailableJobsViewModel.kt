package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.sync.SyncEngine
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

@HiltViewModel
class AvailableJobsViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
    private val serviceClient: AvagoServiceClient,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Set of WO IDs currently being claimed. */
    private val _claimingIds = MutableStateFlow<Set<String>>(emptySet())
    val claimingIds: StateFlow<Set<String>> = _claimingIds.asStateFlow()

    /**
     * Unassigned, open work orders visible for self-assignment.
     * Requires dispatch_mode == hybrid or self_assign (checked at the nav layer;
     * screen is not shown in central mode).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val availableJobs: StateFlow<List<WorkOrderEntity>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) flowOf(emptyList())
                else repository.observeAll(accountId)
                    .map { wos ->
                        wos.filter { wo ->
                            wo.status == "open" && wo.assignedTo.isNullOrBlank()
                        }.sortedByDescending { it.priority?.let { p -> priorityWeight(p) } ?: 0 }
                    }
                    .catch { e -> Timber.e(e, "[AvailableJobsVM] flow error"); emit(emptyList()) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun claimJob(wo: WorkOrderEntity) {
        val accountId = identityManager.getActiveAccountId() ?: return
        val userId = accountId // on Android the accountId is used as the userId placeholder
        viewModelScope.launch {
            _claimingIds.value = _claimingIds.value + wo.woId
            try {
                // Optimistic local update
                val now = System.currentTimeMillis()
                repository.upsert(
                    accountId,
                    wo.copy(
                        assignedTo = userId,
                        status = "assigned",
                        updatedAt = now,
                    )
                )
                // Fire-and-forget to server — sync will reconcile
                try {
                    serviceClient.selfAssignWorkOrder(accountId, wo.woId, userId)
                } catch (e: Exception) {
                    Timber.w(e, "[AvailableJobsVM] selfAssign server call failed — local already updated")
                }
            } catch (e: Exception) {
                Timber.e(e, "[AvailableJobsVM] claimJob failed for ${wo.woId}")
            } finally {
                _claimingIds.value = _claimingIds.value - wo.woId
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try { syncEngine.sync() } catch (e: Exception) {
                Timber.e(e, "[AvailableJobsVM] sync failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun priorityWeight(priority: String): Int = when (priority) {
        "critical" -> 4
        "high" -> 3
        "medium" -> 2
        "low" -> 1
        else -> 0
    }
}
