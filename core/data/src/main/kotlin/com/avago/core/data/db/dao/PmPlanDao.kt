package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.PmPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PmPlanDao {
    @Query("SELECT * FROM pm_plans WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<PmPlanEntity>>

    @Query("SELECT * FROM pm_plans WHERE pm_plan_id = :id")
    suspend fun getById(id: String): PmPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PmPlanEntity)
}
