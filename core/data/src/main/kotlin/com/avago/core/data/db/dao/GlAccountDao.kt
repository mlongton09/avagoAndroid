package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.GlAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GlAccountDao {

    @Query("SELECT * FROM gl_accounts WHERE account_id = :accountId AND deleted_at IS NULL ORDER BY gl_code ASC")
    fun observeAll(accountId: String): Flow<List<GlAccountEntity>>

    @Query("SELECT * FROM gl_accounts WHERE gl_account_id = :id")
    suspend fun getById(id: String): GlAccountEntity?

    @Query("SELECT * FROM gl_accounts WHERE account_id = :accountId AND gl_code = :code LIMIT 1")
    suspend fun getByCode(accountId: String, code: String): GlAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GlAccountEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<GlAccountEntity>)

    @Query("DELETE FROM gl_accounts WHERE gl_account_id = :id")
    suspend fun softDelete(id: String)

    @Query("SELECT MAX(seq) FROM gl_accounts WHERE account_id = :accountId")
    suspend fun maxSeq(accountId: String): Long?
}
