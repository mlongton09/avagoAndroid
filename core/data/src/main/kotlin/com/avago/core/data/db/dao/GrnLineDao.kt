package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.GrnLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GrnLineDao {

    @Query("SELECT gl.* FROM grn_lines gl INNER JOIN grns g ON gl.grn_id = g.grn_id WHERE g.account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<GrnLineEntity>>

    @Query("SELECT * FROM grn_lines WHERE grn_line_id = :id")
    suspend fun getById(id: String): GrnLineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GrnLineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<GrnLineEntity>)

    @Query("UPDATE grn_lines SET deleted_at = :now, updated_at = :now WHERE grn_line_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
