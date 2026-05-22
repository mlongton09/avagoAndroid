package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.LogCostLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogCostLineDao {

    @Query("SELECT * FROM log_cost_lines WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<LogCostLineEntity>>

    @Query("SELECT * FROM log_cost_lines WHERE account_id = :accountId AND wo_id = :woId AND deleted_at IS NULL ORDER BY display_order ASC")
    fun observeForWo(accountId: String, woId: String): Flow<List<LogCostLineEntity>>

    @Query("SELECT * FROM log_cost_lines WHERE line_id = :id")
    suspend fun getById(id: String): LogCostLineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LogCostLineEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LogCostLineEntity>)

    @Query("UPDATE log_cost_lines SET deleted_at = :now, updated_at = :now WHERE line_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
