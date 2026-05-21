package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.DeviceEntity
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
public class DeviceDao_Impl(
  __db: RoomDatabase,
) : DeviceDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfDeviceEntity: EntityInsertAdapter<DeviceEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfDeviceEntity = object : EntityInsertAdapter<DeviceEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `devices` (`device_id`,`account_id`,`platform`,`push_token`,`app_version`,`os_version`,`created_at`,`updated_at`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: DeviceEntity) {
        statement.bindText(1, entity.deviceId)
        val _tmpAccountId: String? = entity.accountId
        if (_tmpAccountId == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpAccountId)
        }
        statement.bindText(3, entity.platform)
        val _tmpPushToken: String? = entity.pushToken
        if (_tmpPushToken == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpPushToken)
        }
        val _tmpAppVersion: String? = entity.appVersion
        if (_tmpAppVersion == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpAppVersion)
        }
        val _tmpOsVersion: String? = entity.osVersion
        if (_tmpOsVersion == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpOsVersion)
        }
        statement.bindLong(7, entity.createdAt)
        statement.bindLong(8, entity.updatedAt)
      }
    }
  }

  public override suspend fun upsert(entity: DeviceEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfDeviceEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<DeviceEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfDeviceEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<DeviceEntity>> {
    val _sql: String = "SELECT * FROM devices WHERE account_id = ?"
    return createFlow(__db, false, arrayOf("devices")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfPlatform: Int = getColumnIndexOrThrow(_stmt, "platform")
        val _columnIndexOfPushToken: Int = getColumnIndexOrThrow(_stmt, "push_token")
        val _columnIndexOfAppVersion: Int = getColumnIndexOrThrow(_stmt, "app_version")
        val _columnIndexOfOsVersion: Int = getColumnIndexOrThrow(_stmt, "os_version")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<DeviceEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: DeviceEntity
          val _tmpDeviceId: String
          _tmpDeviceId = _stmt.getText(_columnIndexOfDeviceId)
          val _tmpAccountId: String?
          if (_stmt.isNull(_columnIndexOfAccountId)) {
            _tmpAccountId = null
          } else {
            _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          }
          val _tmpPlatform: String
          _tmpPlatform = _stmt.getText(_columnIndexOfPlatform)
          val _tmpPushToken: String?
          if (_stmt.isNull(_columnIndexOfPushToken)) {
            _tmpPushToken = null
          } else {
            _tmpPushToken = _stmt.getText(_columnIndexOfPushToken)
          }
          val _tmpAppVersion: String?
          if (_stmt.isNull(_columnIndexOfAppVersion)) {
            _tmpAppVersion = null
          } else {
            _tmpAppVersion = _stmt.getText(_columnIndexOfAppVersion)
          }
          val _tmpOsVersion: String?
          if (_stmt.isNull(_columnIndexOfOsVersion)) {
            _tmpOsVersion = null
          } else {
            _tmpOsVersion = _stmt.getText(_columnIndexOfOsVersion)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item =
              DeviceEntity(_tmpDeviceId,_tmpAccountId,_tmpPlatform,_tmpPushToken,_tmpAppVersion,_tmpOsVersion,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): DeviceEntity? {
    val _sql: String = "SELECT * FROM devices WHERE device_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfPlatform: Int = getColumnIndexOrThrow(_stmt, "platform")
        val _columnIndexOfPushToken: Int = getColumnIndexOrThrow(_stmt, "push_token")
        val _columnIndexOfAppVersion: Int = getColumnIndexOrThrow(_stmt, "app_version")
        val _columnIndexOfOsVersion: Int = getColumnIndexOrThrow(_stmt, "os_version")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: DeviceEntity?
        if (_stmt.step()) {
          val _tmpDeviceId: String
          _tmpDeviceId = _stmt.getText(_columnIndexOfDeviceId)
          val _tmpAccountId: String?
          if (_stmt.isNull(_columnIndexOfAccountId)) {
            _tmpAccountId = null
          } else {
            _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          }
          val _tmpPlatform: String
          _tmpPlatform = _stmt.getText(_columnIndexOfPlatform)
          val _tmpPushToken: String?
          if (_stmt.isNull(_columnIndexOfPushToken)) {
            _tmpPushToken = null
          } else {
            _tmpPushToken = _stmt.getText(_columnIndexOfPushToken)
          }
          val _tmpAppVersion: String?
          if (_stmt.isNull(_columnIndexOfAppVersion)) {
            _tmpAppVersion = null
          } else {
            _tmpAppVersion = _stmt.getText(_columnIndexOfAppVersion)
          }
          val _tmpOsVersion: String?
          if (_stmt.isNull(_columnIndexOfOsVersion)) {
            _tmpOsVersion = null
          } else {
            _tmpOsVersion = _stmt.getText(_columnIndexOfOsVersion)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result =
              DeviceEntity(_tmpDeviceId,_tmpAccountId,_tmpPlatform,_tmpPushToken,_tmpAppVersion,_tmpOsVersion,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun softDelete(id: String) {
    val _sql: String = "DELETE FROM devices WHERE device_id = ?"
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
