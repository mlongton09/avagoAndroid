package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.WorkPermitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkPermitDao {
    @Query("SELECT * FROM work_permits WHERE wo_id = :woId AND deleted_at IS NULL")
    fun observeForWo(woId: String): Flow<List<WorkPermitEntity>>

    @Query("SELECT * FROM work_permits WHERE permit_id = :id")
    suspend fun getById(id: String): WorkPermitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkPermitEntity)
}
