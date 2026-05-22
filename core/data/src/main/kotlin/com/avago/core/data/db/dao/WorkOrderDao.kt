package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.WorkOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkOrderDao {

    @Query("SELECT * FROM work_orders WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<WorkOrderEntity>>

    @Query("SELECT * FROM work_orders WHERE schedule_id = :scheduleId AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20")
    fun observeBySchedule(scheduleId: String): Flow<List<WorkOrderEntity>>

    @Query("SELECT * FROM work_orders WHERE wo_id = :id")
    suspend fun getById(id: String): WorkOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WorkOrderEntity>)

    @Query("UPDATE work_orders SET deleted_at = :now, updated_at = :now WHERE wo_id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM work_orders WHERE assigned_to = :techId AND deleted_at IS NULL ORDER BY due_date ASC")
    fun observeByAssignee(techId: String): Flow<List<WorkOrderEntity>>

    @Query("SELECT * FROM work_orders WHERE asset_id = :assetId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByAsset(assetId: String): Flow<List<WorkOrderEntity>>
}
