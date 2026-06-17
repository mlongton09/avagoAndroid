package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.WorkPermitSignatureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkPermitSignatureDao {
    @Query("SELECT * FROM work_permit_signatures WHERE permit_id = :permitId")
    fun observeForPermit(permitId: String): Flow<List<WorkPermitSignatureEntity>>

    @Query("SELECT * FROM work_permit_signatures WHERE signature_id = :id")
    suspend fun getById(id: String): WorkPermitSignatureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkPermitSignatureEntity)
}
