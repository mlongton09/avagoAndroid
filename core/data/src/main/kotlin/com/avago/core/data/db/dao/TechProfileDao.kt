package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.TechProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TechProfileDao {

    @Query("SELECT * FROM tech_profiles WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<TechProfileEntity>>

    @Query("SELECT * FROM tech_profiles WHERE tech_id = :id")
    suspend fun getById(id: String): TechProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TechProfileEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TechProfileEntity>)

    @Query("UPDATE tech_profiles SET deleted_at = :now, updated_at = :now WHERE tech_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
