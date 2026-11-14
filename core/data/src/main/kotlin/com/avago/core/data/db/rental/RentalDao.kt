package com.avago.core.data.db.rental

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RentalDao {

    /**
     * Rentals outbox rows in the shared `sync_queue` (any status, conflicts included). Kept here
     * rather than on SyncQueueDao so the shared DAO (and its test fakes) stay untouched.
     */
    @Query("SELECT * FROM sync_queue WHERE entity_type IN (:entityTypes) ORDER BY created_at ASC")
    suspend fun queueItemsOfTypes(entityTypes: List<String>): List<SyncQueueEntity>

    /** Live view of [queueItemsOfTypes] (pending-sync badges). */
    @Query("SELECT * FROM sync_queue WHERE entity_type IN (:entityTypes) ORDER BY created_at ASC")
    fun observeQueueItemsOfTypes(entityTypes: List<String>): Flow<List<SyncQueueEntity>>

    /**
     * Parks a rentals outbox row as `failed` (route missing: 404/405) with a visible error. It is
     * not picked up by the core push (`pending`/`error` only) or the rentals planner until the
     * user retries it (`SyncQueueDao.resetConflictToPending`).
     */
    @Query("UPDATE sync_queue SET sync_status = 'failed', attempts = attempts + 1, last_error = :error, updated_at = strftime('%s','now') * 1000 WHERE queue_id = :queueId")
    suspend fun markQueueFailed(queueId: String, error: String)

    // ── Records ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: RentalRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<RentalRecordEntity>)

    @Query("SELECT * FROM rental_records WHERE entity = :entity AND id = :id")
    suspend fun get(entity: String, id: String): RentalRecordEntity?

    @Query("SELECT * FROM rental_records WHERE entity = :entity AND id = :id")
    fun observe(entity: String, id: String): Flow<RentalRecordEntity?>

    @Query("SELECT * FROM rental_records WHERE entity = :entity ORDER BY start_at, name, id")
    fun observeAll(entity: String): Flow<List<RentalRecordEntity>>

    @Query("SELECT * FROM rental_records WHERE entity = :entity ORDER BY start_at, name, id")
    suspend fun all(entity: String): List<RentalRecordEntity>

    @Query("SELECT * FROM rental_records WHERE entity IN (:entities) ORDER BY start_at, id")
    fun observeEntities(entities: List<String>): Flow<List<RentalRecordEntity>>

    @Query("SELECT * FROM rental_records WHERE entity = :entity AND asset_id = :assetId ORDER BY start_at DESC")
    fun observeByAsset(entity: String, assetId: String): Flow<List<RentalRecordEntity>>

    @Query("SELECT * FROM rental_records WHERE entity = :entity AND order_id = :orderId ORDER BY start_at, id")
    fun observeByOrder(entity: String, orderId: String): Flow<List<RentalRecordEntity>>

    @Query("SELECT * FROM rental_records WHERE entity = :entity AND order_id = :orderId ORDER BY start_at, id")
    suspend fun byOrder(entity: String, orderId: String): List<RentalRecordEntity>

    @Query("SELECT * FROM rental_records WHERE entity = :entity AND customer_id = :customerId ORDER BY start_at DESC")
    fun observeByCustomer(entity: String, customerId: String): Flow<List<RentalRecordEntity>>

    /** Records overlapping [from, to) (epoch ms; null end = open). */
    @Query(
        "SELECT * FROM rental_records WHERE entity = :entity AND start_at < :to " +
            "AND (end_at IS NULL OR end_at > :from) ORDER BY start_at",
    )
    fun observeOverlapping(entity: String, from: Long, to: Long): Flow<List<RentalRecordEntity>>

    @Query("SELECT * FROM rental_records WHERE local_state != 'synced' ORDER BY updated_at DESC")
    fun observeUnsynced(): Flow<List<RentalRecordEntity>>

    @Query("SELECT * FROM rental_records WHERE entity = :entity AND client_uuid = :clientUuid LIMIT 1")
    suspend fun byClientUuid(entity: String, clientUuid: String): RentalRecordEntity?

    @Query("UPDATE rental_records SET local_state = :state, notice = :notice WHERE entity = :entity AND id = :id")
    suspend fun setState(entity: String, id: String, state: String, notice: String?)

    @Query("DELETE FROM rental_records WHERE entity = :entity AND id = :id")
    suspend fun delete(entity: String, id: String)

    @Query("DELETE FROM rental_records WHERE entity = :entity AND id IN (:ids) AND local_state = 'synced'")
    suspend fun deleteSynced(entity: String, ids: List<String>)

    @Query("SELECT COUNT(*) FROM rental_records WHERE entity = :entity")
    suspend fun count(entity: String): Int

    // ── Sync state ───────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: RentalSyncStateEntity)

    @Query("SELECT * FROM rental_sync_state WHERE entity = :entity")
    suspend fun syncState(entity: String): RentalSyncStateEntity?

    @Query("SELECT * FROM rental_sync_state")
    fun observeSyncStates(): Flow<List<RentalSyncStateEntity>>

    @Query("SELECT MAX(last_synced_at) FROM rental_sync_state")
    fun observeLastSyncedAt(): Flow<Long?>

    /** Applies one feed page: upserts, tombstone deletes and the new cursor atomically. */
    @Transaction
    suspend fun applyPage(
        entity: String,
        upserts: List<RentalRecordEntity>,
        tombstoneIds: List<String>,
        state: RentalSyncStateEntity,
    ) {
        if (upserts.isNotEmpty()) upsertAll(upserts)
        if (tombstoneIds.isNotEmpty()) tombstoneIds.chunked(500).forEach { deleteSynced(entity, it) }
        upsertSyncState(state)
    }
}
