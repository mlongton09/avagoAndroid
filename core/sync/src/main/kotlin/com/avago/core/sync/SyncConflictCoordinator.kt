package com.avago.core.sync

import com.avago.core.data.DatabaseFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SyncConflictCoordinator @Inject constructor(
    private val dbFactory: DatabaseFactory,
    // Provider<> breaks the circular Hilt dependency:
    // SyncConflictCoordinator → SyncEngine → SyncConflictCoordinator
    private val syncEngine: Provider<SyncEngine>,
) {
    private val _conflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    val conflicts: StateFlow<List<SyncConflict>> = _conflicts.asStateFlow()

    /** Add a conflict, deduplicating by queueId. */
    fun addConflict(conflict: SyncConflict) {
        val current = _conflicts.value
        if (current.none { it.queueId == conflict.queueId }) {
            _conflicts.value = current + conflict
            Timber.d("SyncConflictCoordinator: added conflict for ${conflict.entityType}/${conflict.entityId}")
        }
    }

    /** Remove a resolved conflict by queueId. */
    fun removeConflict(queueId: String) {
        _conflicts.value = _conflicts.value.filter { it.queueId != queueId }
    }

    /**
     * "Keep mine" — reset the queue row to pending so it will be re-pushed,
     * then immediately trigger a push-only sync.
     */
    suspend fun keepLocal(conflict: SyncConflict) {
        Timber.d("SyncConflictCoordinator: keepLocal ${conflict.queueId}")
        val accountId = syncEngine.get().activeAccountId() ?: return
        val db = dbFactory.get(accountId)
        db.syncQueueDao().resetConflictToPending(conflict.queueId)
        removeConflict(conflict.queueId)
        syncEngine.get().pushIfNeeded()
    }

    /**
     * "Accept server" — delete the queue row, reset the watermark for that entity type
     * (forces a full re-pull of that type), then run a full sync.
     */
    suspend fun acceptServer(conflict: SyncConflict) {
        Timber.d("SyncConflictCoordinator: acceptServer ${conflict.queueId}")
        val accountId = syncEngine.get().activeAccountId() ?: return
        val db = dbFactory.get(accountId)
        db.syncQueueDao().markSuccess(conflict.queueId)
        db.syncMetadataDao().resetWatermark(conflict.entityType)
        removeConflict(conflict.queueId)
        syncEngine.get().sync()
    }

    /** Keep-mine for every active conflict. */
    suspend fun keepAllLocal() {
        val snapshot = _conflicts.value.toList()
        if (snapshot.isEmpty()) return
        val accountId = syncEngine.get().activeAccountId() ?: return
        val db = dbFactory.get(accountId)
        for (conflict in snapshot) {
            db.syncQueueDao().resetConflictToPending(conflict.queueId)
            removeConflict(conflict.queueId)
        }
        syncEngine.get().pushIfNeeded()
    }

    /** Accept-server for every active conflict. */
    suspend fun acceptAllServer() {
        val snapshot = _conflicts.value.toList()
        if (snapshot.isEmpty()) return
        val accountId = syncEngine.get().activeAccountId() ?: return
        val db = dbFactory.get(accountId)
        for (conflict in snapshot) {
            db.syncQueueDao().markSuccess(conflict.queueId)
            db.syncMetadataDao().resetWatermark(conflict.entityType)
            removeConflict(conflict.queueId)
        }
        syncEngine.get().sync()
    }
}
