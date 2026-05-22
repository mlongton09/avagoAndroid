package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices WHERE account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE device_id = :id")
    suspend fun getById(id: String): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeviceEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DeviceEntity>)

    @Query("DELETE FROM devices WHERE device_id = :id")
    suspend fun softDelete(id: String)
}
