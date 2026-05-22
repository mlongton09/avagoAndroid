package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.LabelTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelTemplateDao {

    @Query("SELECT * FROM label_templates WHERE account_id = :accountId AND deleted_at IS NULL ORDER BY name ASC")
    fun observeAll(accountId: String): Flow<List<LabelTemplateEntity>>

    @Query("SELECT * FROM label_templates WHERE id = :id")
    suspend fun getById(id: String): LabelTemplateEntity?

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LabelTemplateEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LabelTemplateEntity>)

    @Query("DELETE FROM label_templates WHERE id = :id")
    suspend fun softDelete(id: String)
}
