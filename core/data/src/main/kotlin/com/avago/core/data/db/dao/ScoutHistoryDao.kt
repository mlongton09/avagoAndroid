package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.ScoutHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoutHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScoutHistoryEntity)

    @Query("SELECT * FROM scout_history WHERE account_id = :accountId ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(accountId: String, limit: Int = 50): Flow<List<ScoutHistoryEntity>>
}
