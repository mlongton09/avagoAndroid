package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.PmPlanIntervalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PmPlanIntervalDao {
    @Query("SELECT * FROM pm_plan_intervals WHERE pm_plan_id = :pmPlanId ORDER BY cycle_number")
    fun observeForPlan(pmPlanId: String): Flow<List<PmPlanIntervalEntity>>

    @Query("SELECT * FROM pm_plan_intervals WHERE interval_id = :id")
    suspend fun getById(id: String): PmPlanIntervalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PmPlanIntervalEntity)
}
