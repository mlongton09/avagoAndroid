package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.ChatPresenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatPresenceDao {

    @Query("SELECT * FROM presence WHERE account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<ChatPresenceEntity>>

    @Query("SELECT * FROM presence WHERE user_id = :userId")
    suspend fun getByUser(userId: String): ChatPresenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatPresenceEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ChatPresenceEntity>)

    @Query("DELETE FROM presence WHERE user_id = :userId")
    suspend fun delete(userId: String)
}
