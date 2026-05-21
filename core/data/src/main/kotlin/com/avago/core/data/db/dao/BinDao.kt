package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.BinEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BinDao {

    @Query("SELECT b.* FROM bins b INNER JOIN locations l ON b.location_id = l.location_id WHERE l.account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<BinEntity>>

    @Query("SELECT * FROM bins WHERE bin_id = :id")
    suspend fun getById(id: String): BinEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BinEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BinEntity>)

    @Query("DELETE FROM bins WHERE bin_id = :id")
    suspend fun softDelete(id: String, now: Long)
}
