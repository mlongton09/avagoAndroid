package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.AssetCustomStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetCustomStatusDao {
    @Query("SELECT * FROM asset_custom_statuses WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<AssetCustomStatusEntity>>

    @Query("SELECT * FROM asset_custom_statuses WHERE custom_status_id = :id")
    suspend fun getById(id: String): AssetCustomStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AssetCustomStatusEntity)
}
