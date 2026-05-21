package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.PartIssueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartIssueDao {

    @Query("SELECT * FROM part_issues WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<PartIssueEntity>>

    @Query("SELECT * FROM part_issues WHERE issue_id = :id")
    suspend fun getById(id: String): PartIssueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartIssueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PartIssueEntity>)

    @Query("UPDATE part_issues SET deleted_at = :now, updated_at = :now WHERE issue_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
