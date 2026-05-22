package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.StockingLevelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockingLevelDao {

    @Query("SELECT sl.* FROM stocking_levels sl INNER JOIN parts p ON sl.part_id = p.part_id WHERE p.account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<StockingLevelEntity>>

    @Query("SELECT * FROM stocking_levels WHERE stocking_level_id = :id")
    suspend fun getById(id: String): StockingLevelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StockingLevelEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StockingLevelEntity>)

    @Query("DELETE FROM stocking_levels WHERE stocking_level_id = :id")
    suspend fun softDelete(id: String)
}
