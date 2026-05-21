package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.WoChecklistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WoChecklistItemDao {

    @Query("SELECT * FROM wo_checklist_items WHERE wo_id IN (SELECT wo_id FROM work_orders WHERE account_id = :accountId)")
    fun observeAll(accountId: String): Flow<List<WoChecklistItemEntity>>

    @Query("SELECT * FROM wo_checklist_items WHERE item_id = :id")
    suspend fun getById(id: String): WoChecklistItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WoChecklistItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WoChecklistItemEntity>)

    @Query("DELETE FROM wo_checklist_items WHERE item_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
