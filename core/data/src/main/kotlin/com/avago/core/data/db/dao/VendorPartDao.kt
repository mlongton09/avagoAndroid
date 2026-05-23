package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.VendorPartEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorPartDao {

    @Query("SELECT * FROM vendor_parts WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<VendorPartEntity>>

    @Query("SELECT * FROM vendor_parts WHERE vendor_id = :vendorId AND deleted_at IS NULL")
    fun observeByVendor(vendorId: String): Flow<List<VendorPartEntity>>

    @Query("SELECT * FROM vendor_parts WHERE part_id = :partId AND deleted_at IS NULL")
    fun observeByPart(partId: String): Flow<List<VendorPartEntity>>

    @Query("SELECT * FROM vendor_parts WHERE vendor_part_id = :id")
    suspend fun getById(id: String): VendorPartEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VendorPartEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<VendorPartEntity>)

    @Query("UPDATE vendor_parts SET deleted_at = :now, updated_at = :now WHERE vendor_part_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
