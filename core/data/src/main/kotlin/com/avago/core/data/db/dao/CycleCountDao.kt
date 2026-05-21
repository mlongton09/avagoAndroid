package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.CycleCountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleCountDao {

    @Query("SELECT * FROM cycle_counts WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<CycleCountEntity>>

    @Query("SELECT * FROM cycle_counts WHERE cycle_count_id = :id")
    suspend fun getById(id: String): CycleCountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CycleCountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CycleCountEntity>)

    @Query("UPDATE cycle_counts SET deleted_at = :now, updated_at = :now WHERE cycle_count_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
