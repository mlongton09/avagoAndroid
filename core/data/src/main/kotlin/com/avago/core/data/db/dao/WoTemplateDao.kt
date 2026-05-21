package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.WoTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WoTemplateDao {

    @Query("SELECT * FROM wo_templates WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<WoTemplateEntity>>

    @Query("SELECT * FROM wo_templates WHERE template_id = :id")
    suspend fun getById(id: String): WoTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WoTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WoTemplateEntity>)

    @Query("UPDATE wo_templates SET deleted_at = :now, updated_at = :now WHERE template_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
