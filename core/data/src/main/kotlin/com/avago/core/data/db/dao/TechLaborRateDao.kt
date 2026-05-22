package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.TechLaborRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TechLaborRateDao {

    @Query("SELECT * FROM tech_labor_rates WHERE account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<TechLaborRateEntity>>

    @Query("SELECT * FROM tech_labor_rates WHERE rate_id = :id")
    suspend fun getById(id: String): TechLaborRateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TechLaborRateEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TechLaborRateEntity>)

    @Query("DELETE FROM tech_labor_rates WHERE rate_id = :id")
    suspend fun softDelete(id: String)
}
