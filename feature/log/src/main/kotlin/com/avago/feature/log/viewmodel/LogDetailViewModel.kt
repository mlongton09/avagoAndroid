package com.avago.feature.log.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class LogDetailUiState(
    val log: LogEntity? = null,
    val costLines: List<LogCostLineEntity> = emptyList(),
    val photos: List<PhotoEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val error: String? = null,
) {
    val partLines: List<LogCostLineEntity> get() = costLines.filter { it.kind == "part" }
    val laborLines: List<LogCostLineEntity> get() = costLines.filter { it.kind == "labor" }
    val totalCost: Double get() = costLines.sumOf { it.quantity * it.unitCost + (it.taxAmount ?: 0.0) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogDetailViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val _entryId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LogDetailUiState> = combine(
        identity.activeAccountId.filterNotNull(),
        _entryId.filterNotNull(),
    ) { accountId, entryId ->
        Pair(accountId, entryId)
    }.flatMapLatest { (accountId, entryId) ->
        try {
            val db = dbFactory.get(accountId)
            combine(
                flowOf(Unit).map { db.logDao().getById(entryId) },
                db.logCostLineDao().observeAll(accountId).map { lines ->
                    lines.filter { it.logId == entryId }.sortedBy { it.displayOrder }
                },
                db.photoDao().observeAll(accountId).map { photos ->
                    photos.filter { it.entityId == entryId && it.entityType == "log" }
                        .sortedWith(compareByDescending<PhotoEntity> { it.isPrimary }.thenBy { it.sortOrder })
                },
            ) { log, costLines, photos ->
                LogDetailUiState(
                    log = log,
                    costLines = costLines,
                    photos = photos,
                    isLoading = false,
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "[LogDetailViewModel] Failed to observe log detail")
            flowOf(LogDetailUiState(isLoading = false, error = e.message))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogDetailUiState())

    fun setEntryId(entryId: String) {
        _entryId.value = entryId
    }

    fun deleteLog(onDeleted: () -> Unit) {
        val accountId = identity.getActiveAccountId() ?: return
        val entryId = _entryId.value ?: return
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val now = System.currentTimeMillis()
                db.logDao().softDelete(entryId, now)
                enqueueSyncDelete(accountId, entryId, now)
                Timber.d("[LogDetailViewModel] Soft-deleted log $entryId")
                onDeleted()
            } catch (e: Exception) {
                Timber.e(e, "[LogDetailViewModel] Delete failed for $entryId")
            }
        }
    }

    fun setPrimaryPhoto(photoId: String) {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val now = System.currentTimeMillis()
                val targetPhoto = db.photoDao().getById(photoId) ?: return@launch
                db.photoDao().upsert(targetPhoto.copy(isPrimary = true, updatedAt = now))
                Timber.d("[LogDetailViewModel] Set primary photo $photoId")
            } catch (e: Exception) {
                Timber.e(e, "[LogDetailViewModel] setPrimaryPhoto failed")
            }
        }
    }

    private suspend fun enqueueSyncDelete(accountId: String, entryId: String, now: Long) {
        try {
            val db = dbFactory.get(accountId)
            val entity = com.avago.core.data.db.entity.SyncQueueEntity(
                queueId = "log:$entryId:delete",
                entityType = "log",
                entityId = entryId,
                operation = "delete",
                serverVersion = null,
                payload = null,
                syncStatus = "pending",
                attempts = 0L,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            )
            db.syncQueueDao().enqueueWithDedup(entity)
            syncEngine.pushIfNeeded()
        } catch (e: Exception) {
            Timber.e(e, "[LogDetailViewModel] Failed to enqueue delete for $entryId")
        }
    }
}
