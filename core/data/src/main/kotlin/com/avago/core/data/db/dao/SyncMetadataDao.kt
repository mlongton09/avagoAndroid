package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata")
    fun observeAll(): Flow<List<SyncMetadataEntity>>

    @Query("SELECT * FROM sync_metadata WHERE entity_type = :id")
    suspend fun getById(id: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncMetadataEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SyncMetadataEntity>)

    @Query("SELECT last_server_seq FROM sync_metadata WHERE entity_type = :entityType")
    suspend fun getWatermark(entityType: String): Long

    @Query("UPDATE sync_metadata SET last_server_seq = :seq, last_sync_at = strftime('%s','now') * 1000 WHERE entity_type = :entityType")
    suspend fun updateWatermark(entityType: String, seq: Long)

    @Query("UPDATE sync_metadata SET last_server_seq = 0, last_sync_at = 0 WHERE entity_type = :entityType")
    suspend fun resetWatermark(entityType: String)

    @Query("DELETE FROM sync_metadata WHERE entity_type = :id")
    suspend fun softDelete(id: String)
}
