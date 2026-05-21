package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.SyncQueueEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlin.text.StringBuilder
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class SyncQueueDao_Impl(
  __db: RoomDatabase,
) : SyncQueueDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSyncQueueEntity: EntityInsertAdapter<SyncQueueEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfSyncQueueEntity = object : EntityInsertAdapter<SyncQueueEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `sync_queue` (`queue_id`,`entity_type`,`entity_id`,`operation`,`server_version`,`payload`,`sync_status`,`attempts`,`last_error`,`created_at`,`updated_at`) VALUES (?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SyncQueueEntity) {
        statement.bindText(1, entity.queueId)
        statement.bindText(2, entity.entityType)
        statement.bindText(3, entity.entityId)
        statement.bindText(4, entity.operation)
        val _tmpServerVersion: Long? = entity.serverVersion
        if (_tmpServerVersion == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpServerVersion)
        }
        val _tmpPayload: String? = entity.payload
        if (_tmpPayload == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpPayload)
        }
        statement.bindText(7, entity.syncStatus)
        statement.bindLong(8, entity.attempts)
        val _tmpLastError: String? = entity.lastError
        if (_tmpLastError == null) {
          statement.bindNull(9)
        } else {
          statement.bindText(9, _tmpLastError)
        }
        statement.bindLong(10, entity.createdAt)
        statement.bindLong(11, entity.updatedAt)
      }
    }
  }

  public override suspend fun upsert(entity: SyncQueueEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfSyncQueueEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<SyncQueueEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfSyncQueueEntity.insert(_connection, entities)
  }

  public override suspend fun enqueueOrReplace(entity: SyncQueueEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfSyncQueueEntity.insert(_connection, entity)
  }

  public override fun pendingItems(): Flow<List<SyncQueueEntity>> {
    val _sql: String =
        "SELECT * FROM sync_queue WHERE sync_status IN ('pending', 'error') ORDER BY created_at ASC"
    return createFlow(__db, false, arrayOf("sync_queue")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfQueueId: Int = getColumnIndexOrThrow(_stmt, "queue_id")
        val _columnIndexOfEntityType: Int = getColumnIndexOrThrow(_stmt, "entity_type")
        val _columnIndexOfEntityId: Int = getColumnIndexOrThrow(_stmt, "entity_id")
        val _columnIndexOfOperation: Int = getColumnIndexOrThrow(_stmt, "operation")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfPayload: Int = getColumnIndexOrThrow(_stmt, "payload")
        val _columnIndexOfSyncStatus: Int = getColumnIndexOrThrow(_stmt, "sync_status")
        val _columnIndexOfAttempts: Int = getColumnIndexOrThrow(_stmt, "attempts")
        val _columnIndexOfLastError: Int = getColumnIndexOrThrow(_stmt, "last_error")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<SyncQueueEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SyncQueueEntity
          val _tmpQueueId: String
          _tmpQueueId = _stmt.getText(_columnIndexOfQueueId)
          val _tmpEntityType: String
          _tmpEntityType = _stmt.getText(_columnIndexOfEntityType)
          val _tmpEntityId: String
          _tmpEntityId = _stmt.getText(_columnIndexOfEntityId)
          val _tmpOperation: String
          _tmpOperation = _stmt.getText(_columnIndexOfOperation)
          val _tmpServerVersion: Long?
          if (_stmt.isNull(_columnIndexOfServerVersion)) {
            _tmpServerVersion = null
          } else {
            _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          }
          val _tmpPayload: String?
          if (_stmt.isNull(_columnIndexOfPayload)) {
            _tmpPayload = null
          } else {
            _tmpPayload = _stmt.getText(_columnIndexOfPayload)
          }
          val _tmpSyncStatus: String
          _tmpSyncStatus = _stmt.getText(_columnIndexOfSyncStatus)
          val _tmpAttempts: Long
          _tmpAttempts = _stmt.getLong(_columnIndexOfAttempts)
          val _tmpLastError: String?
          if (_stmt.isNull(_columnIndexOfLastError)) {
            _tmpLastError = null
          } else {
            _tmpLastError = _stmt.getText(_columnIndexOfLastError)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item =
              SyncQueueEntity(_tmpQueueId,_tmpEntityType,_tmpEntityId,_tmpOperation,_tmpServerVersion,_tmpPayload,_tmpSyncStatus,_tmpAttempts,_tmpLastError,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): SyncQueueEntity? {
    val _sql: String = "SELECT * FROM sync_queue WHERE queue_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfQueueId: Int = getColumnIndexOrThrow(_stmt, "queue_id")
        val _columnIndexOfEntityType: Int = getColumnIndexOrThrow(_stmt, "entity_type")
        val _columnIndexOfEntityId: Int = getColumnIndexOrThrow(_stmt, "entity_id")
        val _columnIndexOfOperation: Int = getColumnIndexOrThrow(_stmt, "operation")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfPayload: Int = getColumnIndexOrThrow(_stmt, "payload")
        val _columnIndexOfSyncStatus: Int = getColumnIndexOrThrow(_stmt, "sync_status")
        val _columnIndexOfAttempts: Int = getColumnIndexOrThrow(_stmt, "attempts")
        val _columnIndexOfLastError: Int = getColumnIndexOrThrow(_stmt, "last_error")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: SyncQueueEntity?
        if (_stmt.step()) {
          val _tmpQueueId: String
          _tmpQueueId = _stmt.getText(_columnIndexOfQueueId)
          val _tmpEntityType: String
          _tmpEntityType = _stmt.getText(_columnIndexOfEntityType)
          val _tmpEntityId: String
          _tmpEntityId = _stmt.getText(_columnIndexOfEntityId)
          val _tmpOperation: String
          _tmpOperation = _stmt.getText(_columnIndexOfOperation)
          val _tmpServerVersion: Long?
          if (_stmt.isNull(_columnIndexOfServerVersion)) {
            _tmpServerVersion = null
          } else {
            _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          }
          val _tmpPayload: String?
          if (_stmt.isNull(_columnIndexOfPayload)) {
            _tmpPayload = null
          } else {
            _tmpPayload = _stmt.getText(_columnIndexOfPayload)
          }
          val _tmpSyncStatus: String
          _tmpSyncStatus = _stmt.getText(_columnIndexOfSyncStatus)
          val _tmpAttempts: Long
          _tmpAttempts = _stmt.getLong(_columnIndexOfAttempts)
          val _tmpLastError: String?
          if (_stmt.isNull(_columnIndexOfLastError)) {
            _tmpLastError = null
          } else {
            _tmpLastError = _stmt.getText(_columnIndexOfLastError)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result =
              SyncQueueEntity(_tmpQueueId,_tmpEntityType,_tmpEntityId,_tmpOperation,_tmpServerVersion,_tmpPayload,_tmpSyncStatus,_tmpAttempts,_tmpLastError,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun pendingItemsList(): List<SyncQueueEntity> {
    val _sql: String =
        "SELECT * FROM sync_queue WHERE sync_status IN ('pending', 'error') ORDER BY created_at ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfQueueId: Int = getColumnIndexOrThrow(_stmt, "queue_id")
        val _columnIndexOfEntityType: Int = getColumnIndexOrThrow(_stmt, "entity_type")
        val _columnIndexOfEntityId: Int = getColumnIndexOrThrow(_stmt, "entity_id")
        val _columnIndexOfOperation: Int = getColumnIndexOrThrow(_stmt, "operation")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfPayload: Int = getColumnIndexOrThrow(_stmt, "payload")
        val _columnIndexOfSyncStatus: Int = getColumnIndexOrThrow(_stmt, "sync_status")
        val _columnIndexOfAttempts: Int = getColumnIndexOrThrow(_stmt, "attempts")
        val _columnIndexOfLastError: Int = getColumnIndexOrThrow(_stmt, "last_error")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<SyncQueueEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SyncQueueEntity
          val _tmpQueueId: String
          _tmpQueueId = _stmt.getText(_columnIndexOfQueueId)
          val _tmpEntityType: String
          _tmpEntityType = _stmt.getText(_columnIndexOfEntityType)
          val _tmpEntityId: String
          _tmpEntityId = _stmt.getText(_columnIndexOfEntityId)
          val _tmpOperation: String
          _tmpOperation = _stmt.getText(_columnIndexOfOperation)
          val _tmpServerVersion: Long?
          if (_stmt.isNull(_columnIndexOfServerVersion)) {
            _tmpServerVersion = null
          } else {
            _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          }
          val _tmpPayload: String?
          if (_stmt.isNull(_columnIndexOfPayload)) {
            _tmpPayload = null
          } else {
            _tmpPayload = _stmt.getText(_columnIndexOfPayload)
          }
          val _tmpSyncStatus: String
          _tmpSyncStatus = _stmt.getText(_columnIndexOfSyncStatus)
          val _tmpAttempts: Long
          _tmpAttempts = _stmt.getLong(_columnIndexOfAttempts)
          val _tmpLastError: String?
          if (_stmt.isNull(_columnIndexOfLastError)) {
            _tmpLastError = null
          } else {
            _tmpLastError = _stmt.getText(_columnIndexOfLastError)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item =
              SyncQueueEntity(_tmpQueueId,_tmpEntityType,_tmpEntityId,_tmpOperation,_tmpServerVersion,_tmpPayload,_tmpSyncStatus,_tmpAttempts,_tmpLastError,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markInFlight(queueIds: List<String>) {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("UPDATE sync_queue SET sync_status = 'in_flight' WHERE queue_id IN (")
    val _inputSize: Int = queueIds.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        for (_item: String in queueIds) {
          _stmt.bindText(_argIndex, _item)
          _argIndex++
        }
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markSuccess(queueId: String) {
    val _sql: String = "DELETE FROM sync_queue WHERE queue_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, queueId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markError(queueId: String, error: String) {
    val _sql: String =
        "UPDATE sync_queue SET sync_status = 'error', attempts = attempts + 1, last_error = ?, updated_at = strftime('%s','now') * 1000 WHERE queue_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, error)
        _argIndex = 2
        _stmt.bindText(_argIndex, queueId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun resetInFlightToPending() {
    val _sql: String =
        "UPDATE sync_queue SET sync_status = 'pending' WHERE sync_status = 'in_flight'"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun resetConflictToPending(queueId: String) {
    val _sql: String =
        "UPDATE sync_queue SET sync_status = 'pending', attempts = 0, last_error = NULL, updated_at = strftime('%s','now') * 1000 WHERE queue_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, queueId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markConflict(queueId: String) {
    val _sql: String =
        "UPDATE sync_queue SET sync_status = 'conflict', updated_at = strftime('%s','now') * 1000 WHERE queue_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, queueId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun softDelete(id: String) {
    val _sql: String = "DELETE FROM sync_queue WHERE queue_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
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
