package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.ChatOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatOutboxDao {

    @Query("SELECT * FROM outbox WHERE account_id = :accountId ORDER BY created_at ASC")
    fun observeAll(accountId: String): Flow<List<ChatOutboxEntity>>

    @Query("SELECT * FROM outbox WHERE status = 'pending' OR status = 'failed' ORDER BY created_at ASC")
    suspend fun getPendingAndFailed(): List<ChatOutboxEntity>

    @Query("SELECT * FROM outbox WHERE local_id = :localId")
    suspend fun getById(localId: String): ChatOutboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatOutboxEntity)

    @Query("UPDATE outbox SET status = :status, attempts = attempts + 1, last_error = :error, updated_at = :updatedAt WHERE local_id = :localId")
    suspend fun updateStatus(localId: String, status: String, error: String?, updatedAt: Long)

    @Query("DELETE FROM outbox WHERE local_id = :localId")
    suspend fun delete(localId: String)

    @Query("DELETE FROM outbox WHERE thread_id = :threadId")
    suspend fun deleteForThread(threadId: String)
}
