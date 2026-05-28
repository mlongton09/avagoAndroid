package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.ConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {

    @Query("SELECT * FROM configs WHERE account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM configs WHERE config_id = :id")
    suspend fun getById(id: String): ConfigEntity?

    @Query("SELECT * FROM configs WHERE scope = :scope AND key = :key LIMIT 1")
    suspend fun getByKey(scope: String, key: String): ConfigEntity?

    @Query("SELECT * FROM configs WHERE scope = :scope AND key = :key LIMIT 1")
    fun observeByKey(scope: String, key: String): Flow<ConfigEntity?>

    @Query("SELECT * FROM configs WHERE scope = :scope AND key LIKE :keyPattern ORDER BY version DESC")
    suspend fun getByPattern(scope: String, keyPattern: String): List<ConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConfigEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ConfigEntity>)

    @Query("DELETE FROM configs WHERE config_id = :id")
    suspend fun deleteById(id: String)
}
