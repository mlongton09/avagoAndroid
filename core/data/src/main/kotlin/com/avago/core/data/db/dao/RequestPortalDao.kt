package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.RequestPortalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RequestPortalDao {
    @Query("SELECT * FROM request_portals WHERE account_id = :accountId AND deleted_at IS NULL ORDER BY name")
    fun observeAll(accountId: String): Flow<List<RequestPortalEntity>>

    @Query("SELECT * FROM request_portals WHERE portal_id = :id")
    suspend fun getById(id: String): RequestPortalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RequestPortalEntity)
}
