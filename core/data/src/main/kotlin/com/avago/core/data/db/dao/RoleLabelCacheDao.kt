package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.RoleLabelCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoleLabelCacheDao {

    @Query("SELECT * FROM role_label_cache ORDER BY label ASC")
    fun observeAll(): Flow<List<RoleLabelCacheEntity>>

    @Query("SELECT * FROM role_label_cache WHERE role_key = :roleKey")
    suspend fun getByKey(roleKey: String): RoleLabelCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoleLabelCacheEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RoleLabelCacheEntity>)

    @Query("DELETE FROM role_label_cache WHERE role_key = :roleKey")
    suspend fun delete(roleKey: String)

    @Query("DELETE FROM role_label_cache")
    suspend fun deleteAll()
}
