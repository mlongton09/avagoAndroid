package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.ChatSyncStateEntity

@Dao
interface ChatSyncStateDao {

    @Query("SELECT value FROM sync_state WHERE key = :key")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(entity: ChatSyncStateEntity)

    @Query("DELETE FROM sync_state WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM sync_state")
    suspend fun clear()
}
