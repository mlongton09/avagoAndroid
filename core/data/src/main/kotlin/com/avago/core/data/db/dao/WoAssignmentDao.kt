package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.WoAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WoAssignmentDao {

    @Query("SELECT wa.* FROM wo_assignments wa WHERE wa.account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<WoAssignmentEntity>>

    @Query("SELECT * FROM wo_assignments WHERE assignment_id = :id")
    suspend fun getById(id: String): WoAssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WoAssignmentEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WoAssignmentEntity>)

    @Query("DELETE FROM wo_assignments WHERE assignment_id = :id")
    suspend fun softDelete(id: String)
}
