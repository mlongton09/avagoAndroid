package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.WorkOrderAssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkOrderAssetDao {
    @Query("SELECT * FROM work_order_assets WHERE wo_id = :woId ORDER BY seq_order")
    fun observeForWo(woId: String): Flow<List<WorkOrderAssetEntity>>

    @Query("SELECT * FROM work_order_assets WHERE wo_asset_id = :id")
    suspend fun getById(id: String): WorkOrderAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkOrderAssetEntity)
}
