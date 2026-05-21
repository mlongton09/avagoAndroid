package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.SyncMetadataEntity
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
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class SyncMetadataDao_Impl(
  __db: RoomDatabase,
) : SyncMetadataDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSyncMetadataEntity: EntityInsertAdapter<SyncMetadataEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfSyncMetadataEntity = object : EntityInsertAdapter<SyncMetadataEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `sync_metadata` (`entity_type`,`last_server_seq`,`last_sync_at`) VALUES (?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SyncMetadataEntity) {
        statement.bindText(1, entity.entityType)
        statement.bindLong(2, entity.lastServerSeq)
        statement.bindLong(3, entity.lastSyncAt)
      }
    }
  }

  public override suspend fun upsert(entity: SyncMetadataEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfSyncMetadataEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<SyncMetadataEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfSyncMetadataEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<SyncMetadataEntity>> {
    val _sql: String = "SELECT * FROM sync_metadata"
    return createFlow(__db, false, arrayOf("sync_metadata")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _columnIndexOfEntityType: Int = getColumnIndexOrThrow(_stmt, "entity_type")
        val _columnIndexOfLastServerSeq: Int = getColumnIndexOrThrow(_stmt, "last_server_seq")
        val _columnIndexOfLastSyncAt: Int = getColumnIndexOrThrow(_stmt, "last_sync_at")
        val _result: MutableList<SyncMetadataEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SyncMetadataEntity
          val _tmpEntityType: String
          _tmpEntityType = _stmt.getText(_columnIndexOfEntityType)
          val _tmpLastServerSeq: Long
          _tmpLastServerSeq = _stmt.getLong(_columnIndexOfLastServerSeq)
          val _tmpLastSyncAt: Long
          _tmpLastSyncAt = _stmt.getLong(_columnIndexOfLastSyncAt)
          _item = SyncMetadataEntity(_tmpEntityType,_tmpLastServerSeq,_tmpLastSyncAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): SyncMetadataEntity? {
    val _sql: String = "SELECT * FROM sync_metadata WHERE entity_type = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfEntityType: Int = getColumnIndexOrThrow(_stmt, "entity_type")
        val _columnIndexOfLastServerSeq: Int = getColumnIndexOrThrow(_stmt, "last_server_seq")
        val _columnIndexOfLastSyncAt: Int = getColumnIndexOrThrow(_stmt, "last_sync_at")
        val _result: SyncMetadataEntity?
        if (_stmt.step()) {
          val _tmpEntityType: String
          _tmpEntityType = _stmt.getText(_columnIndexOfEntityType)
          val _tmpLastServerSeq: Long
          _tmpLastServerSeq = _stmt.getLong(_columnIndexOfLastServerSeq)
          val _tmpLastSyncAt: Long
          _tmpLastSyncAt = _stmt.getLong(_columnIndexOfLastSyncAt)
          _result = SyncMetadataEntity(_tmpEntityType,_tmpLastServerSeq,_tmpLastSyncAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getWatermark(entityType: String): Long {
    val _sql: String = "SELECT last_server_seq FROM sync_metadata WHERE entity_type = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, entityType)
        val _result: Long
        if (_stmt.step()) {
          _result = _stmt.getLong(0)
        } else {
          _result = 0L
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateWatermark(entityType: String, seq: Long) {
    val _sql: String =
        "UPDATE sync_metadata SET last_server_seq = ?, last_sync_at = strftime('%s','now') * 1000 WHERE entity_type = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, seq)
        _argIndex = 2
        _stmt.bindText(_argIndex, entityType)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun resetWatermark(entityType: String) {
    val _sql: String =
        "UPDATE sync_metadata SET last_server_seq = 0, last_sync_at = 0 WHERE entity_type = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, entityType)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun softDelete(id: String) {
    val _sql: String = "DELETE FROM sync_metadata WHERE entity_type = ?"
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
