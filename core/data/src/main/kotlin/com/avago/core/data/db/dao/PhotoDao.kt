package com.avago.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avago.core.data.db.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos WHERE account_id = :accountId AND deleted_at IS NULL")
    fun observeAll(accountId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE entity_id = :entityId AND entity_type = :entityType AND deleted_at IS NULL ORDER BY sort_order ASC")
    fun observeByEntity(entityId: String, entityType: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE photo_id = :id")
    suspend fun getById(id: String): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PhotoEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PhotoEntity>)

    @Query("UPDATE photos SET deleted_at = :now, updated_at = :now WHERE photo_id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** Photos that have a local file but no storage_key yet — need to be uploaded. */
    @Query("SELECT * FROM photos WHERE account_id = :accountId AND storage_key IS NULL AND local_path IS NOT NULL AND deleted_at IS NULL")
    suspend fun pendingUpload(accountId: String): List<PhotoEntity>

    /** Photos that have already been uploaded (storage_key set) but still have a local copy — evictable. */
    @Query("SELECT * FROM photos WHERE account_id = :accountId AND storage_key IS NOT NULL AND local_path IS NOT NULL AND deleted_at IS NULL")
    suspend fun uploadedWithLocalPath(accountId: String): List<PhotoEntity>

    @Query("UPDATE photos SET storage_key = :storageKey, updated_at = :now WHERE photo_id = :photoId")
    suspend fun updateStorageKey(photoId: String, storageKey: String, now: Long)

    @Query("UPDATE photos SET local_path = NULL, updated_at = :now WHERE photo_id = :photoId")
    suspend fun clearLocalPath(photoId: String, now: Long)
}
