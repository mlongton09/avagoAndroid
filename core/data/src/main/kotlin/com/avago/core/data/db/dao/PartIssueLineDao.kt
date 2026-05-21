package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.PartIssueLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartIssueLineDao {

    @Query("SELECT * FROM part_issue_lines WHERE issue_id IN (SELECT issue_id FROM part_issues WHERE account_id = :accountId)")
    fun observeAll(accountId: String): Flow<List<PartIssueLineEntity>>

    @Query("SELECT * FROM part_issue_lines WHERE line_id = :id")
    suspend fun getById(id: String): PartIssueLineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartIssueLineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PartIssueLineEntity>)

    @Query("DELETE FROM part_issue_lines WHERE line_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
