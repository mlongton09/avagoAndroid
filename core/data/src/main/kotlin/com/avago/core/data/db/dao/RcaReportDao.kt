package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.RcaReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RcaReportDao {
    @Query("SELECT * FROM rca_reports WHERE wo_id = :woId AND deleted_at IS NULL")
    fun observeForWo(woId: String): Flow<List<RcaReportEntity>>

    @Query("SELECT * FROM rca_reports WHERE report_id = :id")
    suspend fun getById(id: String): RcaReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RcaReportEntity)
}
