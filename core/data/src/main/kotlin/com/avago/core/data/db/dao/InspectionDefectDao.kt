package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.InspectionDefectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDefectDao {

    @Query("SELECT * FROM inspection_defects WHERE asset_id = :assetId")
    fun observeForAsset(assetId: String): Flow<List<InspectionDefectEntity>>

    @Query("SELECT * FROM inspection_defects WHERE defect_id = :id")
    suspend fun getById(id: String): InspectionDefectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InspectionDefectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<InspectionDefectEntity>)

    @Query("DELETE FROM inspection_defects WHERE defect_id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM inspection_defects WHERE defect_id NOT IN (:keepIds)")
    suspend fun deleteAllExcept(keepIds: List<String>)

    /**
     * Replace the cached register with an authoritative server snapshot. Rows in [keepIds]
     * (defects with a queued local change) are left untouched so a pull never overwrites an
     * edit that has not been pushed yet (same rule as `SyncEngine.pendingPushGuardTypes`).
     */
    @Transaction
    suspend fun replaceSnapshot(rows: List<InspectionDefectEntity>, keepIds: Set<String>) {
        deleteAllExcept(keepIds.toList())
        upsertAll(rows.filterNot { it.defectId in keepIds })
    }
}
