package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.MeterTriggerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterTriggerDao {
    @Query("SELECT * FROM meter_triggers WHERE asset_id = :assetId AND deleted_at IS NULL")
    fun observeForAsset(assetId: String): Flow<List<MeterTriggerEntity>>

    @Query("SELECT * FROM meter_triggers WHERE trigger_id = :id")
    suspend fun getById(id: String): MeterTriggerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MeterTriggerEntity)
}
