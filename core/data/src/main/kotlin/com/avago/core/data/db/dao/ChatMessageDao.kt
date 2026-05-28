package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query(
        "SELECT * FROM chat_messages " +
            "WHERE thread_id = :threadId AND deleted_at IS NULL " +
            "ORDER BY created_at ASC"
    )
    fun observeByThread(threadId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE message_id = :messageId")
    suspend fun getById(messageId: String): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: ChatMessageEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<ChatMessageEntity>)

    @Query(
        "SELECT * FROM chat_messages " +
            "WHERE thread_id = :threadId AND created_at < :beforeCreatedAt AND deleted_at IS NULL " +
            "ORDER BY created_at DESC LIMIT :limit"
    )
    suspend fun getPage(threadId: String, beforeCreatedAt: Long, limit: Int): List<ChatMessageEntity>

    @Query("UPDATE chat_messages SET outbox_status = :status WHERE message_id = :messageId")
    suspend fun updateOutboxStatus(messageId: String, status: String?)

    @Query("DELETE FROM chat_messages WHERE message_id = :messageId")
    suspend fun deleteById(messageId: String)

    @Query(
        "UPDATE chat_messages " +
            "SET body_md = :bodyMd, edited_at = :editedAt, updated_at = :editedAt " +
            "WHERE message_id = :messageId"
    )
    suspend fun updateEdited(messageId: String, bodyMd: String, editedAt: Long)

    @Query(
        "SELECT * FROM chat_messages " +
            "WHERE outbox_status = 'failed' AND deleted_at IS NULL"
    )
    fun observeFailedOutbox(): Flow<List<ChatMessageEntity>>

    /** One-shot query used by the periodic flush timer. */
    @Query(
        "SELECT * FROM chat_messages " +
            "WHERE outbox_status = 'failed' AND deleted_at IS NULL"
    )
    suspend fun failedOutboxList(): List<ChatMessageEntity>

    /** Observe all replies to a specific parent message, ordered oldest-first. */
    @Query(
        "SELECT * FROM chat_messages " +
            "WHERE thread_id = :threadId AND parent_message_id = :parentMessageId AND deleted_at IS NULL " +
            "ORDER BY created_at ASC"
    )
    fun observeByThreadAndParent(threadId: String, parentMessageId: String): Flow<List<ChatMessageEntity>>

    /** Observe the single pinned message for a thread (if any). */
    @Query(
        "SELECT * FROM chat_messages " +
            "WHERE thread_id = :threadId AND is_pinned = 1 AND deleted_at IS NULL " +
            "LIMIT 1"
    )
    fun observePinnedMessage(threadId: String): Flow<ChatMessageEntity?>

    @Query("UPDATE chat_messages SET is_pinned = :pinned WHERE message_id = :messageId")
    suspend fun updatePinned(messageId: String, pinned: Boolean)

    @Query(
        "SELECT * FROM chat_messages " +
            "WHERE thread_id = :threadId AND deleted_at IS NULL " +
            "ORDER BY created_at DESC LIMIT 1"
    )
    suspend fun getLastMessage(threadId: String): ChatMessageEntity?

    @Query(
        "SELECT * FROM chat_messages " +
            "WHERE body_md LIKE '%@' || :username || '%' AND deleted_at IS NULL " +
            "ORDER BY created_at DESC"
    )
    fun observeMentions(username: String): Flow<List<ChatMessageEntity>>
}
