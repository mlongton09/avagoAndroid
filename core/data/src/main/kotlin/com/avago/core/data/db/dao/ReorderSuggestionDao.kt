package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.ReorderSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReorderSuggestionDao {

    @Query("SELECT * FROM reorder_suggestions WHERE account_id = :accountId AND status = 'active' ORDER BY created_at DESC")
    fun observeActive(accountId: String): Flow<List<ReorderSuggestionEntity>>

    @Query("SELECT * FROM reorder_suggestions WHERE account_id = :accountId ORDER BY created_at DESC")
    fun observeAll(accountId: String): Flow<List<ReorderSuggestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReorderSuggestionEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ReorderSuggestionEntity>)

    @Query("UPDATE reorder_suggestions SET status = :status, updated_at = :now WHERE suggestion_id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)

    @Query("DELETE FROM reorder_suggestions WHERE account_id = :accountId")
    suspend fun deleteAll(accountId: String)
}
