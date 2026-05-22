package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.ChatAccountRosterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatAccountRosterDao {

    @Query("SELECT * FROM account_roster WHERE account_id = :accountId AND is_active = 1 ORDER BY display_name ASC")
    fun observeAll(accountId: String): Flow<List<ChatAccountRosterEntity>>

    @Query("SELECT * FROM account_roster WHERE user_id = :userId AND account_id = :accountId LIMIT 1")
    suspend fun getByUser(userId: String, accountId: String): ChatAccountRosterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatAccountRosterEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ChatAccountRosterEntity>)

    @Query("DELETE FROM account_roster WHERE roster_id = :rosterId")
    suspend fun delete(rosterId: String)
}
