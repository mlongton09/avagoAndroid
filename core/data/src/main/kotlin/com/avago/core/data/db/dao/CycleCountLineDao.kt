package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.CycleCountLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleCountLineDao {

    @Query("SELECT * FROM cycle_count_lines WHERE cycle_count_id IN (SELECT cycle_count_id FROM cycle_counts WHERE account_id = :accountId)")
    fun observeAll(accountId: String): Flow<List<CycleCountLineEntity>>

    @Query("SELECT * FROM cycle_count_lines WHERE line_id = :id")
    suspend fun getById(id: String): CycleCountLineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CycleCountLineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CycleCountLineEntity>)

    @Query("DELETE FROM cycle_count_lines WHERE line_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
