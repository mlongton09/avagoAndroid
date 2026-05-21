package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.LogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    @Query("SELECT * FROM log WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<LogEntity>>

    @Query("SELECT * FROM log WHERE entry_id = :id")
    suspend fun getById(id: String): LogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LogEntity>)

    @Query("UPDATE log SET deleted_at = :now, updated_at = :now WHERE entry_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
