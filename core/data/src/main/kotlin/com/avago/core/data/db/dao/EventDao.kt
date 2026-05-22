package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events WHERE account_id = :accountId AND deleted_at IS NULL ORDER BY starts_at ASC")
    fun observeAll(accountId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE entity_id = :entityId AND entity_type = :entityType AND deleted_at IS NULL ORDER BY starts_at ASC")
    fun observeByEntity(entityId: String, entityType: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE event_id = :id")
    suspend fun getById(id: String): EventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EventEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<EventEntity>)

    @Query("DELETE FROM events WHERE event_id = :id")
    suspend fun softDelete(id: String)

    @Query("SELECT MAX(seq) FROM events WHERE account_id = :accountId")
    suspend fun maxSeq(accountId: String): Long?
}
