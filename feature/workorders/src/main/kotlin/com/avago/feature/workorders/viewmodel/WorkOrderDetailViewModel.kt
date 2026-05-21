package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.WoAssignmentEntity
import com.avago.core.data.db.entity.WoChecklistItemEntity
import com.avago.core.data.db.entity.WoCommentEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.network.AvagoServiceClient
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
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WorkOrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
    private val serviceClient: AvagoServiceClient,
) : ViewModel() {

    private val woId: String = requireNotNull(savedStateHandle["woId"]) {
        "WorkOrderDetailViewModel requires woId in SavedStateHandle"
    }

    private val _accountId: StateFlow<String?> = identityManager.activeAccountId
        .stateIn(viewModelScope, SharingStarted.Eagerly, identityManager.getActiveAccountId())

    @OptIn(ExperimentalCoroutinesApi::class)
    val workOrder: StateFlow<WorkOrderEntity?> = _accountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(null)
            else repository.observeAll(accountId)
                .map { list -> list.firstOrNull { it.woId == woId } }
                .catch { e -> Timber.e(e, "[WoDetailVM] wo flow error"); emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val assignments: StateFlow<List<WoAssignmentEntity>> = _accountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList())
            else repository.observeAssignmentsForWo(accountId, woId)
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val checklistItems: StateFlow<List<WoChecklistItemEntity>> = _accountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList())
            else repository.observeChecklistForWo(accountId, woId)
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val comments: StateFlow<List<WoCommentEntity>> = _accountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList())
            else repository.observeCommentsForWo(accountId, woId)
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ---------------------------------------------------------------------------
    // Status transition
    // ---------------------------------------------------------------------------

    fun transitionStatus(targetStatus: WoStatus) {
        val accountId = _accountId.value ?: return
        val wo = workOrder.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val now = System.currentTimeMillis()
                val updated = wo.copy(
                    status = targetStatus.key,
                    updatedAt = now,
                    startedAt = if (targetStatus == WoStatus.IN_PROGRESS && wo.startedAt == null) now else wo.startedAt,
                    completedAt = if (targetStatus == WoStatus.COMPLETE) now else wo.completedAt,
                )
                repository.upsert(accountId, updated)
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] transitionStatus failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Dispatcher notes auto-save
    // ---------------------------------------------------------------------------

    fun saveDispatcherNotes(notes: String) {
        val accountId = _accountId.value ?: return
        val wo = workOrder.value ?: return
        viewModelScope.launch {
            try {
                repository.upsert(accountId, wo.copy(
                    dispatcherNotes = notes.ifBlank { null },
                    updatedAt = System.currentTimeMillis(),
                ))
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] saveDispatcherNotes failed")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Checklist toggle
    // ---------------------------------------------------------------------------

    fun toggleChecklistItem(item: WoChecklistItemEntity) {
        val accountId = _accountId.value ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val updated = item.copy(
                isCompleted = !item.isCompleted,
                completedAt = if (!item.isCompleted) now else null,
            )
            repository.upsertChecklistItem(accountId, updated)
        }
    }

    // ---------------------------------------------------------------------------
    // Comments
    // ---------------------------------------------------------------------------

    fun addComment(body: String) {
        val accountId = _accountId.value ?: return
        val authorId = identityManager.getActiveAccountId() ?: return
        if (body.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val comment = WoCommentEntity(
                commentId = UUID.randomUUID().toString(),
                woId = woId,
                authorId = authorId,
                body = body.trim(),
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                serverVersion = 0L,
                seq = null,
            )
            repository.upsertComment(accountId, comment)
        }
    }

    // ---------------------------------------------------------------------------
    // Delete WO
    // ---------------------------------------------------------------------------

    fun deleteWorkOrder(onDeleted: () -> Unit) {
        val accountId = _accountId.value ?: return
        viewModelScope.launch {
            try {
                repository.softDelete(accountId, woId)
                onDeleted()
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] deleteWorkOrder failed")
                _error.value = e.message
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Recurrence
    // ---------------------------------------------------------------------------

    fun saveRecurrence(rrule: String) {
        val accountId = _accountId.value ?: return
        val wo = workOrder.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val updated = wo.copy(
                    rrule = rrule,
                    woKind = "recurring_parent",
                    updatedAt = System.currentTimeMillis(),
                )
                repository.upsert(accountId, updated)
                // POST to server asynchronously
                try {
                    serviceClient.updateWorkOrderRecurrence(accountId, woId, rrule)
                } catch (e: Exception) {
                    Timber.w(e, "[WoDetailVM] recurrence server POST failed — will retry via sync")
                }
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] saveRecurrence failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    fun refresh() {
        viewModelScope.launch {
            try { syncEngine.sync() } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] sync failed")
            }
        }
    }
}
