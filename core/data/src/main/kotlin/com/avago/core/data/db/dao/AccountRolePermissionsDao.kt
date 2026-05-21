package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.AccountRolePermissionsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountRolePermissionsDao {

    @Query("SELECT * FROM account_role_permissions WHERE account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<AccountRolePermissionsEntity>>

    @Query("SELECT * FROM account_role_permissions WHERE id = :id")
    suspend fun getById(id: String): AccountRolePermissionsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AccountRolePermissionsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AccountRolePermissionsEntity>)

    @Query("DELETE FROM account_role_permissions WHERE id = :id")
    suspend fun softDelete(id: String)
}
