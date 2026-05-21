package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.InventoryTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryTransactionDao {

    @Query("SELECT * FROM inventory_transactions WHERE account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<InventoryTransactionEntity>>

    @Query("SELECT * FROM inventory_transactions WHERE transaction_id = :id")
    suspend fun getById(id: String): InventoryTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InventoryTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<InventoryTransactionEntity>)

    @Query("UPDATE inventory_transactions SET deleted_at = :now, updated_at = :now WHERE transaction_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
