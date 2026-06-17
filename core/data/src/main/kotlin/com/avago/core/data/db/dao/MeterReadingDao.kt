package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.MeterReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterReadingDao {
    @Query("SELECT * FROM meter_readings WHERE asset_id = :assetId AND deleted_at IS NULL ORDER BY read_at DESC")
    fun observeForAsset(assetId: String): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings WHERE meter_reading_id = :id")
    suspend fun getById(id: String): MeterReadingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MeterReadingEntity)
}
