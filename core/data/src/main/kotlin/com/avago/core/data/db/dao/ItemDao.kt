package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items WHERE log_id = :logId AND deleted_at IS NULL")
    fun observeByLogId(logId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE item_id = :id")
    suspend fun getById(id: String): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ItemEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ItemEntity>)

    @Query("DELETE FROM items WHERE item_id = :id")
    suspend fun softDelete(id: String)

    @Query("SELECT MAX(seq) FROM items WHERE account_id = :accountId")
    suspend fun maxSeq(accountId: String): Long?
}
