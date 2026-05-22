package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.RolePermissionDefaultsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RolePermissionDefaultsDao {

    @Query("SELECT * FROM role_permission_defaults")
    fun observeAll(): Flow<List<RolePermissionDefaultsEntity>>

    @Query("SELECT * FROM role_permission_defaults WHERE role_key = :id")
    suspend fun getById(id: String): RolePermissionDefaultsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RolePermissionDefaultsEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RolePermissionDefaultsEntity>)

    @Query("DELETE FROM role_permission_defaults WHERE role_key = :id")
    suspend fun softDelete(id: String)
}
