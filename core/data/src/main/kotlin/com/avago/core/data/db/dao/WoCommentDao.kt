package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.WoCommentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WoCommentDao {

    @Query("SELECT * FROM wo_comments WHERE wo_id IN (SELECT wo_id FROM work_orders WHERE account_id = :accountId) AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<WoCommentEntity>>

    @Query("SELECT * FROM wo_comments WHERE comment_id = :id")
    suspend fun getById(id: String): WoCommentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WoCommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WoCommentEntity>)

    @Query("UPDATE wo_comments SET deleted_at = :now, updated_at = :now WHERE comment_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
