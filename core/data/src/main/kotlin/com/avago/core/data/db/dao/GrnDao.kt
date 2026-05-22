package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.GrnEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GrnDao {

    @Query("SELECT * FROM grns WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<GrnEntity>>

    @Query("SELECT * FROM grns WHERE grn_id = :id")
    suspend fun getById(id: String): GrnEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GrnEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<GrnEntity>)

    @Query("UPDATE grns SET deleted_at = :now, updated_at = :now WHERE grn_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
