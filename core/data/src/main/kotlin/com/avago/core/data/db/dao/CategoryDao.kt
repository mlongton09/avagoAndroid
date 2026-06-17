package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE account_id = :accountId AND deleted_at IS NULL ORDER BY name")
    fun observeAll(accountId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE account_id = :accountId AND entity_type = :entityType AND deleted_at IS NULL ORDER BY name")
    fun observeForEntityType(accountId: String, entityType: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE category_id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryEntity)
}
