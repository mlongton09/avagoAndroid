package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.ScoutPendingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoutPendingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScoutPendingEntity)

    @Query("SELECT * FROM scout_pending ORDER BY created_at ASC")
    suspend fun pendingList(): List<ScoutPendingEntity>

    @Query("SELECT * FROM scout_pending ORDER BY created_at ASC")
    fun observePending(): Flow<List<ScoutPendingEntity>>

    @Query("DELETE FROM scout_pending WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE scout_pending SET attempts = attempts + 1, last_error = :error, updated_at = :updatedAt WHERE id = :id")
    suspend fun markError(id: String, error: String, updatedAt: Long)
}
