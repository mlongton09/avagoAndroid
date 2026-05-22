package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.VendorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorDao {

    @Query("SELECT * FROM vendors WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<VendorEntity>>

    @Query("SELECT * FROM vendors WHERE vendor_id = :id")
    suspend fun getById(id: String): VendorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VendorEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<VendorEntity>)

    @Query("UPDATE vendors SET deleted_at = :now, updated_at = :now WHERE vendor_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
