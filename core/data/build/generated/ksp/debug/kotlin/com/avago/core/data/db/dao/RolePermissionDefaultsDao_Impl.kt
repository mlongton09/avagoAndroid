package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.RolePermissionDefaultsEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
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
public class RolePermissionDefaultsDao_Impl(
  __db: RoomDatabase,
) : RolePermissionDefaultsDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfRolePermissionDefaultsEntity:
      EntityInsertAdapter<RolePermissionDefaultsEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfRolePermissionDefaultsEntity = object :
        EntityInsertAdapter<RolePermissionDefaultsEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `role_permission_defaults` (`role_key`,`permissions`) VALUES (?,?)"

      protected override fun bind(statement: SQLiteStatement,
          entity: RolePermissionDefaultsEntity) {
        statement.bindText(1, entity.roleKey)
        statement.bindText(2, entity.permissions)
      }
    }
  }

  public override suspend fun upsert(entity: RolePermissionDefaultsEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfRolePermissionDefaultsEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<RolePermissionDefaultsEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfRolePermissionDefaultsEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<RolePermissionDefaultsEntity>> {
    val _sql: String = "SELECT * FROM role_permission_defaults"
    return createFlow(__db, false, arrayOf("role_permission_defaults")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _columnIndexOfRoleKey: Int = getColumnIndexOrThrow(_stmt, "role_key")
        val _columnIndexOfPermissions: Int = getColumnIndexOrThrow(_stmt, "permissions")
        val _result: MutableList<RolePermissionDefaultsEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: RolePermissionDefaultsEntity
          val _tmpRoleKey: String
          _tmpRoleKey = _stmt.getText(_columnIndexOfRoleKey)
          val _tmpPermissions: String
          _tmpPermissions = _stmt.getText(_columnIndexOfPermissions)
          _item = RolePermissionDefaultsEntity(_tmpRoleKey,_tmpPermissions)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): RolePermissionDefaultsEntity? {
    val _sql: String = "SELECT * FROM role_permission_defaults WHERE role_key = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfRoleKey: Int = getColumnIndexOrThrow(_stmt, "role_key")
        val _columnIndexOfPermissions: Int = getColumnIndexOrThrow(_stmt, "permissions")
        val _result: RolePermissionDefaultsEntity?
        if (_stmt.step()) {
          val _tmpRoleKey: String
          _tmpRoleKey = _stmt.getText(_columnIndexOfRoleKey)
          val _tmpPermissions: String
          _tmpPermissions = _stmt.getText(_columnIndexOfPermissions)
          _result = RolePermissionDefaultsEntity(_tmpRoleKey,_tmpPermissions)
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
    val _sql: String = "DELETE FROM role_permission_defaults WHERE role_key = ?"
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
