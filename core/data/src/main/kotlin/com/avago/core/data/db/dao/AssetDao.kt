package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    @Query("SELECT * FROM assets WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE asset_id = :id")
    suspend fun getById(id: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE asset_id = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): AssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AssetEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AssetEntity>)

    @Query("UPDATE assets SET deleted_at = :now, updated_at = :now WHERE asset_id = :id")
    suspend fun softDelete(id: String, now: Long)

    // ---------------------------------------------------------------------------
    // Hierarchy queries — mirrors iOS AssetDAO hierarchy methods
    // ---------------------------------------------------------------------------

    @Query("SELECT * FROM assets WHERE parent_asset_id = :assetId AND deleted_at IS NULL ORDER BY name ASC")
    suspend fun directChildren(assetId: String): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE path LIKE :pathPrefix AND asset_id != :assetId AND deleted_at IS NULL ORDER BY depth ASC, name ASC")
    suspend fun descendants(assetId: String, pathPrefix: String): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE account_id = :accountId AND asset_id NOT IN (:excludeIds) AND deleted_at IS NULL ORDER BY name ASC")
    suspend fun eligibleParents(accountId: String, excludeIds: List<String>): List<AssetEntity>

    // ---------------------------------------------------------------------------
    // FRE sample data — mirrors iOS AssetDAO.deleteAllFreSampleAssets()
    // ---------------------------------------------------------------------------

    @Query("SELECT COUNT(*) FROM assets WHERE account_id = :accountId AND is_fre_sample = 0 AND deleted_at IS NULL")
    suspend fun countRealAssets(accountId: String): Int

    @Query("UPDATE assets SET deleted_at = :now, updated_at = :now WHERE account_id = :accountId AND is_fre_sample = 1 AND deleted_at IS NULL")
    suspend fun softDeleteAllFreSamples(accountId: String, now: Long)

    @Query("SELECT asset_id FROM assets WHERE account_id = :accountId AND is_fre_sample = 1 AND deleted_at IS NULL")
    suspend fun freeSampleAssetIds(accountId: String): List<String>
}
