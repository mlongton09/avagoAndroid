package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.ChatThreadLastReadEntity

@Dao
interface ChatThreadLastReadDao {

    @Query("SELECT * FROM thread_last_read WHERE thread_id = :threadId AND user_id = :userId LIMIT 1")
    suspend fun get(threadId: String, userId: String): ChatThreadLastReadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatThreadLastReadEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ChatThreadLastReadEntity>)

    @Query("DELETE FROM thread_last_read WHERE thread_id = :threadId")
    suspend fun deleteForThread(threadId: String)
}
