package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.ChatMentionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMentionDao {

    @Query("SELECT * FROM mentions_of_me WHERE account_id = :accountId AND is_read = 0 ORDER BY created_at DESC")
    fun observeUnread(accountId: String): Flow<List<ChatMentionEntity>>

    @Query("SELECT COUNT(*) FROM mentions_of_me WHERE account_id = :accountId AND is_read = 0")
    fun observeUnreadCount(accountId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatMentionEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ChatMentionEntity>)

    @Query("UPDATE mentions_of_me SET is_read = 1 WHERE mention_id = :mentionId")
    suspend fun markRead(mentionId: String)

    @Query("UPDATE mentions_of_me SET is_read = 1 WHERE thread_id = :threadId")
    suspend fun markAllReadForThread(threadId: String)
}
