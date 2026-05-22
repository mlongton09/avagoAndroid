package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.PoLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PoLineDao {

    @Query("SELECT pl.* FROM po_lines pl INNER JOIN purchase_orders po ON pl.po_id = po.po_id WHERE po.account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<PoLineEntity>>

    @Query("SELECT * FROM po_lines WHERE po_line_id = :id")
    suspend fun getById(id: String): PoLineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PoLineEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PoLineEntity>)

    @Query("DELETE FROM po_lines WHERE po_line_id = :id")
    suspend fun softDelete(id: String)
}
