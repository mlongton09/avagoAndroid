package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.StockingLevelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockingLevelDao {

    @Query("SELECT * FROM stocking_levels WHERE part_id IN (SELECT part_id FROM parts WHERE account_id = :accountId)")
    fun observeAll(accountId: String): Flow<List<StockingLevelEntity>>

    @Query("SELECT * FROM stocking_levels WHERE stocking_level_id = :id")
    suspend fun getById(id: String): StockingLevelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StockingLevelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StockingLevelEntity>)

    @Query("DELETE FROM stocking_levels WHERE stocking_level_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
