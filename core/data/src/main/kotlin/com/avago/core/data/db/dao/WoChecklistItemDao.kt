package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.WoChecklistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WoChecklistItemDao {

    @Query("SELECT wci.* FROM wo_checklist_items wci INNER JOIN work_orders wo ON wci.wo_id = wo.wo_id WHERE wo.account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<WoChecklistItemEntity>>

    @Query("SELECT * FROM wo_checklist_items WHERE item_id = :id")
    suspend fun getById(id: String): WoChecklistItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WoChecklistItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WoChecklistItemEntity>)

    @Query("UPDATE wo_checklist_items SET deleted_at = :now, updated_at = :now WHERE item_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
