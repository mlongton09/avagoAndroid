package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.AssetStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetStatusDao {
    @Query("SELECT * FROM asset_statuses WHERE asset_id = :assetId ORDER BY started_at DESC")
    fun observeForAsset(assetId: String): Flow<List<AssetStatusEntity>>

    @Query("SELECT * FROM asset_statuses WHERE asset_status_id = :id")
    suspend fun getById(id: String): AssetStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AssetStatusEntity)
}
