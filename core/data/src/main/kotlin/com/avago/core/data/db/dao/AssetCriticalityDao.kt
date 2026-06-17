package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.AssetCriticalityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetCriticalityDao {
    @Query("SELECT * FROM asset_criticalities WHERE account_id = :accountId AND deleted_at IS NULL ORDER BY level")
    fun observeAll(accountId: String): Flow<List<AssetCriticalityEntity>>

    @Query("SELECT * FROM asset_criticalities WHERE criticality_id = :id")
    suspend fun getById(id: String): AssetCriticalityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AssetCriticalityEntity)
}
