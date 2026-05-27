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
            "WHERE account_id = :accountId AND deleted_at IS NULL AND is_archived = 0 " +
            "ORDER BY is_favorite DESC, last_message_at DESC"
    )
    fun observeAll(accountId: String): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE thread_id = :threadId")
    suspend fun getById(threadId: String): ChatThreadEntity?

    @Query("SELECT * FROM chat_threads WHERE thread_id = :threadId")
    fun observeById(threadId: String): Flow<ChatThreadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(thread: ChatThreadEntity)

    @Query("UPDATE chat_threads SET unread_count = :count WHERE thread_id = :threadId")
    suspend fun updateUnreadCount(threadId: String, count: Int)

    @Query("UPDATE chat_threads SET is_favorite = :isFavorite, updated_at = :updatedAt WHERE thread_id = :threadId")
    suspend fun updateFavorite(threadId: String, isFavorite: Boolean, updatedAt: Long)

    @Query("UPDATE chat_threads SET is_archived = :isArchived, updated_at = :updatedAt WHERE thread_id = :threadId")
    suspend fun updateArchived(threadId: String, isArchived: Boolean, updatedAt: Long)

    @Query("UPDATE chat_threads SET display_name = :displayName, updated_at = :updatedAt WHERE thread_id = :threadId")
    suspend fun updateDisplayName(threadId: String, displayName: String, updatedAt: Long)

    @Query("UPDATE chat_threads SET notification_pref = :pref, updated_at = :updatedAt WHERE thread_id = :threadId")
    suspend fun updateNotificationPref(threadId: String, pref: String, updatedAt: Long)
}
