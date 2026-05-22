package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.PurchaseOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseOrderDao {

    @Query("SELECT * FROM purchase_orders WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_orders WHERE po_id = :id")
    suspend fun getById(id: String): PurchaseOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PurchaseOrderEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PurchaseOrderEntity>)

    @Query("UPDATE purchase_orders SET deleted_at = :now, updated_at = :now WHERE po_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
