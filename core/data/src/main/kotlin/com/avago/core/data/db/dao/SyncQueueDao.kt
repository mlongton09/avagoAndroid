package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue WHERE sync_status IN ('pending', 'error') ORDER BY created_at ASC")
    fun pendingItems(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE queue_id = :id")
    suspend fun getById(id: String): SyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncQueueEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SyncQueueEntity>)

    @Query("UPDATE sync_queue SET sync_status = 'in_flight' WHERE queue_id IN (:queueIds)")
    suspend fun markInFlight(queueIds: List<String>)

    @Query("DELETE FROM sync_queue WHERE queue_id = :queueId")
    suspend fun markSuccess(queueId: String)

    @Query("UPDATE sync_queue SET sync_status = 'error', attempts = attempts + 1, last_error = :error, updated_at = strftime('%s','now') * 1000 WHERE queue_id = :queueId")
    suspend fun markError(queueId: String, error: String)

    @Query("UPDATE sync_queue SET sync_status = 'pending' WHERE sync_status = 'in_flight'")
    suspend fun resetInFlightToPending()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueOrReplace(entity: SyncQueueEntity)

    /** Suspend version that returns a List (not Flow) for use inside the sync engine. */
    @Query("SELECT * FROM sync_queue WHERE sync_status IN ('pending', 'error') ORDER BY created_at ASC")
    suspend fun pendingItemsList(): List<SyncQueueEntity>

    /** Reset a conflicted row back to pending so it will be re-pushed. */
    @Query("UPDATE sync_queue SET sync_status = 'pending', attempts = 0, last_error = NULL, updated_at = strftime('%s','now') * 1000 WHERE queue_id = :queueId")
    suspend fun resetConflictToPending(queueId: String)

    /** Mark a row as conflicted. */
    @Query("UPDATE sync_queue SET sync_status = 'conflict', updated_at = strftime('%s','now') * 1000 WHERE queue_id = :queueId")
    suspend fun markConflict(queueId: String)

    @Query("DELETE FROM sync_queue WHERE queue_id = :id")
    suspend fun softDelete(id: String)

    /**
     * Reset error items back to pending so they are retried on the next push cycle.
     * Items that have exceeded [maxAttempts] are left as errors to avoid infinite loops.
     * Mirrors iOS SyncQueueDAO.resetErrorsToPending(maxAttempts:).
     */
    @Query("UPDATE sync_queue SET sync_status = 'pending' WHERE sync_status = 'error' AND attempts < :maxAttempts")
    suspend fun resetErrorsToPending(maxAttempts: Long = 10L)

    /**
     * Returns non-null if a pending/in-flight/error push exists for the given entity.
     * Used to defer overwriting a locally-edited row with a server pull's older snapshot
     * — letting the push land first ensures the server sees the user's change before we
     * replay an older state on top of it.
     * Mirrors iOS SyncEngine.hasPendingPush(entityType:entityId:).
     */
    @Query("SELECT 1 FROM sync_queue WHERE entity_type = :entityType AND entity_id = :entityId AND sync_status IN ('pending', 'in_flight', 'error') LIMIT 1")
    suspend fun hasPendingPush(entityType: String, entityId: String): Int?

    @Query("SELECT * FROM sync_queue WHERE queue_id = :queueId AND sync_status IN ('pending', 'error')")
    suspend fun getPendingByQueueId(queueId: String): SyncQueueEntity?

    /**
     * Enqueue with iOS-compatible dedup logic:
     *
     * - insert + delete  → cancel both (delete the pending insert, skip enqueueing delete)
     * - insert + update  → keep as "insert" but use the new payload
     * - delete + insert  → treat as a fresh insert (re-create on server)
     * - anything else    → replace the existing row with the new one
     *
     * Mirrors iOS SyncQueueDAO.enqueue() dedup rules.
     */
    @Transaction
    suspend fun enqueueWithDedup(entity: SyncQueueEntity) {
        val existing = getPendingByQueueId(entity.queueId)
        when {
            existing?.operation == "insert" && entity.operation == "delete" -> {
                // Cancel: asset was created locally but never synced; deleting it is a no-op
                softDelete(existing.queueId)
            }
            existing?.operation == "insert" && entity.operation == "update" -> {
                // Merge: keep "insert" so the server creates it, but use the latest payload
                upsert(entity.copy(operation = "insert"))
            }
            else -> {
                // Default: replace with the latest operation + payload
                upsert(entity)
            }
        }
    }
}
