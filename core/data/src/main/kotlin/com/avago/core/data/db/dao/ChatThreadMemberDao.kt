package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.ChatThreadMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatThreadMemberDao {

    @Query("SELECT * FROM thread_members WHERE thread_id = :threadId")
    fun observeByThread(threadId: String): Flow<List<ChatThreadMemberEntity>>

    @Query("SELECT * FROM thread_members WHERE thread_id = :threadId AND left_at IS NULL")
    suspend fun getActiveMembers(threadId: String): List<ChatThreadMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatThreadMemberEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ChatThreadMemberEntity>)

    @Query("DELETE FROM thread_members WHERE thread_id = :threadId AND user_id = :userId")
    suspend fun delete(threadId: String, userId: String)

    @Query("DELETE FROM thread_members WHERE thread_id = :threadId")
    suspend fun deleteAllForThread(threadId: String)
}
