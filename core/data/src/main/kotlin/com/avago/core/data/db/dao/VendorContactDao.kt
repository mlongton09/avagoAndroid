package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.VendorContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorContactDao {

    @Query("SELECT * FROM vendor_contacts WHERE vendor_id = :vendorId ORDER BY is_primary DESC, name ASC")
    fun observeByVendor(vendorId: String): Flow<List<VendorContactEntity>>

    @Query("SELECT * FROM vendor_contacts WHERE contact_id = :id")
    suspend fun getById(id: String): VendorContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VendorContactEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<VendorContactEntity>)

    @Query("DELETE FROM vendor_contacts WHERE contact_id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM vendor_contacts WHERE vendor_id = :vendorId")
    suspend fun deleteByVendor(vendorId: String)
}
