package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.ChatReactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatReactionDao {

    @Query("SELECT * FROM reactions WHERE message_id = :messageId")
    fun observeByMessage(messageId: String): Flow<List<ChatReactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatReactionEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ChatReactionEntity>)

    @Query("DELETE FROM reactions WHERE message_id = :messageId AND user_id = :userId AND emoji = :emoji")
    suspend fun delete(messageId: String, userId: String, emoji: String)

    @Query("DELETE FROM reactions WHERE message_id = :messageId")
    suspend fun deleteAllForMessage(messageId: String)
}
