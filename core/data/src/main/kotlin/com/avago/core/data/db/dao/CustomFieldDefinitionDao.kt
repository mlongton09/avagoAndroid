package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.CustomFieldDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFieldDefinitionDao {
    @Query("SELECT * FROM custom_field_definitions WHERE account_id = :accountId AND entity_type = :entityType AND deleted_at IS NULL ORDER BY display_order")
    fun observeForEntityType(accountId: String, entityType: String): Flow<List<CustomFieldDefinitionEntity>>

    @Query("SELECT * FROM custom_field_definitions WHERE definition_id = :id")
    suspend fun getById(id: String): CustomFieldDefinitionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CustomFieldDefinitionEntity)
}
