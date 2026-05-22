package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.ServiceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDao {

    @Query("SELECT * FROM service WHERE asset_id = :assetId AND deleted_at IS NULL ORDER BY scheduled_at DESC")
    fun observeByAsset(assetId: String): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM service WHERE account_id = :accountId AND deleted_at IS NULL ORDER BY scheduled_at DESC")
    fun observeAll(accountId: String): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM service WHERE service_id = :id")
    suspend fun getById(id: String): ServiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ServiceEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ServiceEntity>)

    @Query("DELETE FROM service WHERE service_id = :id")
    suspend fun softDelete(id: String)

    @Query("SELECT MAX(seq) FROM service WHERE account_id = :accountId")
    suspend fun maxSeq(accountId: String): Long?
}
