package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.WoAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WoAssignmentDao {

    @Query("SELECT * FROM wo_assignments WHERE wo_id IN (SELECT wo_id FROM work_orders WHERE account_id = :accountId)")
    fun observeAll(accountId: String): Flow<List<WoAssignmentEntity>>

    @Query("SELECT * FROM wo_assignments WHERE assignment_id = :id")
    suspend fun getById(id: String): WoAssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WoAssignmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WoAssignmentEntity>)

    @Query("DELETE FROM wo_assignments WHERE assignment_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
