package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.PartEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartDao {

    @Query("SELECT * FROM parts WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<PartEntity>>

    @Query("SELECT * FROM parts WHERE part_id = :id")
    suspend fun getById(id: String): PartEntity?

    @Query("SELECT * FROM parts WHERE sku = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): PartEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PartEntity>)

    @Query("UPDATE parts SET deleted_at = :now, updated_at = :now WHERE part_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
