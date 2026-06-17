package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.AssetModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetModelDao {
    @Query("SELECT * FROM asset_models WHERE account_id = :accountId AND deleted_at IS NULL ORDER BY name")
    fun observeAll(accountId: String): Flow<List<AssetModelEntity>>

    @Query("SELECT * FROM asset_models WHERE model_id = :id")
    suspend fun getById(id: String): AssetModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AssetModelEntity)
}
