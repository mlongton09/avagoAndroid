package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.ChatThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatThreadDao {

    @Query(
        "SELECT * FROM chat_threads " +
            "WHERE account_id = :accountId AND deleted_at IS NULL " +
            "ORDER BY last_message_at DESC"
    )
    fun observeAll(accountId: String): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE thread_id = :threadId")
    suspend fun getById(threadId: String): ChatThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(thread: ChatThreadEntity)

    @Query("UPDATE chat_threads SET unread_count = :count WHERE thread_id = :threadId")
    suspend fun updateUnreadCount(threadId: String, count: Int)
}
