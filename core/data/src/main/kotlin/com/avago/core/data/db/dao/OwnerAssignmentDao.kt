package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avago.core.data.db.entity.OwnerAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnerAssignmentDao {
    @Query("SELECT * FROM owner_assignments WHERE resource_type = :resourceType AND resource_id = :resourceId AND deleted_at IS NULL")
    fun observeForResource(resourceType: String, resourceId: String): Flow<List<OwnerAssignmentEntity>>

    @Query("SELECT * FROM owner_assignments WHERE assignment_id = :id")
    suspend fun getById(id: String): OwnerAssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OwnerAssignmentEntity)
}
