package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.converters.Converters
import com.avago.core.`data`.db.entity.PhotoEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class PhotoDao_Impl(
  __db: RoomDatabase,
) : PhotoDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPhotoEntity: EntityInsertAdapter<PhotoEntity>

  private val __converters: Converters = Converters()
  init {
    this.__db = __db
    this.__insertAdapterOfPhotoEntity = object : EntityInsertAdapter<PhotoEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `photos` (`photo_id`,`entity_id`,`entity_type`,`account_id`,`storage_key`,`download_url`,`sort_order`,`is_primary`,`created_at`,`updated_at`,`deleted_at`,`server_version`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PhotoEntity) {
        statement.bindText(1, entity.photoId)
        statement.bindText(2, entity.entityId)
        statement.bindText(3, entity.entityType)
        statement.bindText(4, entity.accountId)
        val _tmpStorageKey: String? = entity.storageKey
        if (_tmpStorageKey == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpStorageKey)
        }
        val _tmpDownloadUrl: String? = entity.downloadUrl
        if (_tmpDownloadUrl == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpDownloadUrl)
        }
        statement.bindLong(7, entity.sortOrder)
        val _tmp: Int = __converters.fromBooleanToInt(entity.isPrimary)
        statement.bindLong(8, _tmp.toLong())
        statement.bindLong(9, entity.createdAt)
        statement.bindLong(10, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(11)
        } else {
          statement.bindLong(11, _tmpDeletedAt)
        }
        statement.bindLong(12, entity.serverVersion)
      }
    }
  }

  public override suspend fun upsert(entity: PhotoEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfPhotoEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<PhotoEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfPhotoEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<PhotoEntity>> {
    val _sql: String = "SELECT * FROM photos WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("photos")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfPhotoId: Int = getColumnIndexOrThrow(_stmt, "photo_id")
        val _columnIndexOfEntityId: Int = getColumnIndexOrThrow(_stmt, "entity_id")
        val _columnIndexOfEntityType: Int = getColumnIndexOrThrow(_stmt, "entity_type")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfStorageKey: Int = getColumnIndexOrThrow(_stmt, "storage_key")
        val _columnIndexOfDownloadUrl: Int = getColumnIndexOrThrow(_stmt, "download_url")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfIsPrimary: Int = getColumnIndexOrThrow(_stmt, "is_primary")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: MutableList<PhotoEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PhotoEntity
          val _tmpPhotoId: String
          _tmpPhotoId = _stmt.getText(_columnIndexOfPhotoId)
          val _tmpEntityId: String
          _tmpEntityId = _stmt.getText(_columnIndexOfEntityId)
          val _tmpEntityType: String
          _tmpEntityType = _stmt.getText(_columnIndexOfEntityType)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpStorageKey: String?
          if (_stmt.isNull(_columnIndexOfStorageKey)) {
            _tmpStorageKey = null
          } else {
            _tmpStorageKey = _stmt.getText(_columnIndexOfStorageKey)
          }
          val _tmpDownloadUrl: String?
          if (_stmt.isNull(_columnIndexOfDownloadUrl)) {
            _tmpDownloadUrl = null
          } else {
            _tmpDownloadUrl = _stmt.getText(_columnIndexOfDownloadUrl)
          }
          val _tmpSortOrder: Long
          _tmpSortOrder = _stmt.getLong(_columnIndexOfSortOrder)
          val _tmpIsPrimary: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsPrimary).toInt()
          _tmpIsPrimary = __converters.fromIntToBoolean(_tmp)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _item =
              PhotoEntity(_tmpPhotoId,_tmpEntityId,_tmpEntityType,_tmpAccountId,_tmpStorageKey,_tmpDownloadUrl,_tmpSortOrder,_tmpIsPrimary,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): PhotoEntity? {
    val _sql: String = "SELECT * FROM photos WHERE photo_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfPhotoId: Int = getColumnIndexOrThrow(_stmt, "photo_id")
        val _columnIndexOfEntityId: Int = getColumnIndexOrThrow(_stmt, "entity_id")
        val _columnIndexOfEntityType: Int = getColumnIndexOrThrow(_stmt, "entity_type")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfStorageKey: Int = getColumnIndexOrThrow(_stmt, "storage_key")
        val _columnIndexOfDownloadUrl: Int = getColumnIndexOrThrow(_stmt, "download_url")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfIsPrimary: Int = getColumnIndexOrThrow(_stmt, "is_primary")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: PhotoEntity?
        if (_stmt.step()) {
          val _tmpPhotoId: String
          _tmpPhotoId = _stmt.getText(_columnIndexOfPhotoId)
          val _tmpEntityId: String
          _tmpEntityId = _stmt.getText(_columnIndexOfEntityId)
          val _tmpEntityType: String
          _tmpEntityType = _stmt.getText(_columnIndexOfEntityType)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpStorageKey: String?
          if (_stmt.isNull(_columnIndexOfStorageKey)) {
            _tmpStorageKey = null
          } else {
            _tmpStorageKey = _stmt.getText(_columnIndexOfStorageKey)
          }
          val _tmpDownloadUrl: String?
          if (_stmt.isNull(_columnIndexOfDownloadUrl)) {
            _tmpDownloadUrl = null
          } else {
            _tmpDownloadUrl = _stmt.getText(_columnIndexOfDownloadUrl)
          }
          val _tmpSortOrder: Long
          _tmpSortOrder = _stmt.getLong(_columnIndexOfSortOrder)
          val _tmpIsPrimary: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsPrimary).toInt()
          _tmpIsPrimary = __converters.fromIntToBoolean(_tmp)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _result =
              PhotoEntity(_tmpPhotoId,_tmpEntityId,_tmpEntityType,_tmpAccountId,_tmpStorageKey,_tmpDownloadUrl,_tmpSortOrder,_tmpIsPrimary,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun softDelete(id: String, now: Long) {
    val _sql: String = "UPDATE photos SET deleted_at = ?, updated_at = ? WHERE photo_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, now)
        _argIndex = 2
        _stmt.bindLong(_argIndex, now)
        _argIndex = 3
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
