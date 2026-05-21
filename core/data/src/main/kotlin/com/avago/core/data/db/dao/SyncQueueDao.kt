package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
    suspend fun softDelete(id: String, now: Long)
}
