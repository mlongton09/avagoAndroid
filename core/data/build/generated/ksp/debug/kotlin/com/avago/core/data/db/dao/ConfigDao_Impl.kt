package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.ConfigEntity
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
public class ConfigDao_Impl(
  __db: RoomDatabase,
) : ConfigDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfConfigEntity: EntityInsertAdapter<ConfigEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfConfigEntity = object : EntityInsertAdapter<ConfigEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `configs` (`config_id`,`account_id`,`scope`,`key`,`value`,`version`,`created_at`,`updated_at`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ConfigEntity) {
        statement.bindText(1, entity.configId)
        val _tmpAccountId: String? = entity.accountId
        if (_tmpAccountId == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpAccountId)
        }
        statement.bindText(3, entity.scope)
        statement.bindText(4, entity.key)
        statement.bindText(5, entity.value)
        statement.bindLong(6, entity.version)
        statement.bindLong(7, entity.createdAt)
        statement.bindLong(8, entity.updatedAt)
      }
    }
  }

  public override suspend fun upsert(entity: ConfigEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfConfigEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<ConfigEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfConfigEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<ConfigEntity>> {
    val _sql: String = "SELECT * FROM configs WHERE account_id = ?"
    return createFlow(__db, false, arrayOf("configs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfConfigId: Int = getColumnIndexOrThrow(_stmt, "config_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfScope: Int = getColumnIndexOrThrow(_stmt, "scope")
        val _columnIndexOfKey: Int = getColumnIndexOrThrow(_stmt, "key")
        val _columnIndexOfValue: Int = getColumnIndexOrThrow(_stmt, "value")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<ConfigEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ConfigEntity
          val _tmpConfigId: String
          _tmpConfigId = _stmt.getText(_columnIndexOfConfigId)
          val _tmpAccountId: String?
          if (_stmt.isNull(_columnIndexOfAccountId)) {
            _tmpAccountId = null
          } else {
            _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          }
          val _tmpScope: String
          _tmpScope = _stmt.getText(_columnIndexOfScope)
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfKey)
          val _tmpValue: String
          _tmpValue = _stmt.getText(_columnIndexOfValue)
          val _tmpVersion: Long
          _tmpVersion = _stmt.getLong(_columnIndexOfVersion)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item =
              ConfigEntity(_tmpConfigId,_tmpAccountId,_tmpScope,_tmpKey,_tmpValue,_tmpVersion,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): ConfigEntity? {
    val _sql: String = "SELECT * FROM configs WHERE config_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfConfigId: Int = getColumnIndexOrThrow(_stmt, "config_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfScope: Int = getColumnIndexOrThrow(_stmt, "scope")
        val _columnIndexOfKey: Int = getColumnIndexOrThrow(_stmt, "key")
        val _columnIndexOfValue: Int = getColumnIndexOrThrow(_stmt, "value")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: ConfigEntity?
        if (_stmt.step()) {
          val _tmpConfigId: String
          _tmpConfigId = _stmt.getText(_columnIndexOfConfigId)
          val _tmpAccountId: String?
          if (_stmt.isNull(_columnIndexOfAccountId)) {
            _tmpAccountId = null
          } else {
            _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          }
          val _tmpScope: String
          _tmpScope = _stmt.getText(_columnIndexOfScope)
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfKey)
          val _tmpValue: String
          _tmpValue = _stmt.getText(_columnIndexOfValue)
          val _tmpVersion: Long
          _tmpVersion = _stmt.getLong(_columnIndexOfVersion)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result =
              ConfigEntity(_tmpConfigId,_tmpAccountId,_tmpScope,_tmpKey,_tmpValue,_tmpVersion,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByKey(scope: String, key: String): ConfigEntity? {
    val _sql: String = "SELECT * FROM configs WHERE scope = ? AND key = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, scope)
        _argIndex = 2
        _stmt.bindText(_argIndex, key)
        val _columnIndexOfConfigId: Int = getColumnIndexOrThrow(_stmt, "config_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfScope: Int = getColumnIndexOrThrow(_stmt, "scope")
        val _columnIndexOfKey: Int = getColumnIndexOrThrow(_stmt, "key")
        val _columnIndexOfValue: Int = getColumnIndexOrThrow(_stmt, "value")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: ConfigEntity?
        if (_stmt.step()) {
          val _tmpConfigId: String
          _tmpConfigId = _stmt.getText(_columnIndexOfConfigId)
          val _tmpAccountId: String?
          if (_stmt.isNull(_columnIndexOfAccountId)) {
            _tmpAccountId = null
          } else {
            _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          }
          val _tmpScope: String
          _tmpScope = _stmt.getText(_columnIndexOfScope)
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfKey)
          val _tmpValue: String
          _tmpValue = _stmt.getText(_columnIndexOfValue)
          val _tmpVersion: Long
          _tmpVersion = _stmt.getLong(_columnIndexOfVersion)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result =
              ConfigEntity(_tmpConfigId,_tmpAccountId,_tmpScope,_tmpKey,_tmpValue,_tmpVersion,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: String) {
    val _sql: String = "DELETE FROM configs WHERE config_id = ?"
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
