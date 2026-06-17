package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.SyncConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncConflictDao {
    @Query("SELECT * FROM sync_conflicts WHERE resolution_status = 'PENDING' ORDER BY created_at DESC")
    fun observePending(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE conflict_id = :id")
    suspend fun getById(id: String): SyncConflictEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncConflictEntity)

    @Query("UPDATE sync_conflicts SET resolution_status = :status WHERE conflict_id = :conflictId")
    suspend fun updateResolutionStatus(conflictId: String, status: String)
}
