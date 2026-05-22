package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.AssetLocationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetLocationHistoryDao {

    @Query("SELECT * FROM asset_location_history WHERE asset_id = :assetId ORDER BY moved_at DESC")
    fun observeByAsset(assetId: String): Flow<List<AssetLocationHistoryEntity>>

    @Query("SELECT * FROM asset_location_history WHERE history_id = :id")
    suspend fun getById(id: String): AssetLocationHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AssetLocationHistoryEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AssetLocationHistoryEntity>)

    @Query("DELETE FROM asset_location_history WHERE history_id = :id")
    suspend fun delete(id: String)

    @Query("SELECT MAX(seq) FROM asset_location_history WHERE account_id = :accountId")
    suspend fun maxSeq(accountId: String): Long?
}
