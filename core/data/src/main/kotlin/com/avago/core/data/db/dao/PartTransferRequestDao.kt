package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.PartTransferRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartTransferRequestDao {
    @Query("SELECT * FROM part_transfer_requests WHERE account_id = :accountId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAll(accountId: String): Flow<List<PartTransferRequestEntity>>

    @Query("SELECT * FROM part_transfer_requests WHERE request_id = :id")
    suspend fun getById(id: String): PartTransferRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartTransferRequestEntity)
}
