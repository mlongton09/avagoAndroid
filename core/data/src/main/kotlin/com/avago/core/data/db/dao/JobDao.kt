package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.JobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {

    @Query("SELECT * FROM jobs WHERE account_id = :accountId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAll(accountId: String): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE job_id = :id")
    suspend fun getById(id: String): JobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: JobEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<JobEntity>)

    @Query("DELETE FROM jobs WHERE job_id = :id")
    suspend fun softDelete(id: String)

    @Query("SELECT MAX(seq) FROM jobs WHERE account_id = :accountId")
    suspend fun maxSeq(accountId: String): Long?
}
