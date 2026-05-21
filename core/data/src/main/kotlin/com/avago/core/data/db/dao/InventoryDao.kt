package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.InventoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory WHERE inventory_id = :id")
    suspend fun getById(id: String): InventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InventoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<InventoryEntity>)

    @Query("UPDATE inventory SET deleted_at = :now, updated_at = :now WHERE inventory_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
