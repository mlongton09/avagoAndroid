package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.WoCommentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WoCommentDao {

    @Query("SELECT wc.* FROM wo_comments wc INNER JOIN work_orders wo ON wc.wo_id = wo.wo_id WHERE wo.account_id = :accountId AND wc.deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<WoCommentEntity>>

    @Query("SELECT * FROM wo_comments WHERE comment_id = :id")
    suspend fun getById(id: String): WoCommentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WoCommentEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WoCommentEntity>)

    @Query("UPDATE wo_comments SET deleted_at = :now, updated_at = :now WHERE comment_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
