package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.PoCommentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PoCommentDao {
    @Query("SELECT * FROM po_comments WHERE po_id = :poId AND deleted_at IS NULL ORDER BY created_at ASC")
    fun observeForPo(poId: String): Flow<List<PoCommentEntity>>

    @Query("SELECT * FROM po_comments WHERE comment_id = :id")
    suspend fun getById(id: String): PoCommentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PoCommentEntity)
}
