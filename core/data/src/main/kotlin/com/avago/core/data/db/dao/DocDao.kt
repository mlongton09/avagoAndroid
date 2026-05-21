package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.DocEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocDao {

    @Query("SELECT * FROM docs WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<DocEntity>>

    @Query("SELECT * FROM docs WHERE doc_id = :id")
    suspend fun getById(id: String): DocEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DocEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DocEntity>)

    @Query("UPDATE docs SET deleted_at = :now, updated_at = :now WHERE doc_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
