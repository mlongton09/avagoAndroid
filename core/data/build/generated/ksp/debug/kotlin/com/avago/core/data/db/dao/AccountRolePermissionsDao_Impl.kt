package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.AccountRolePermissionsEntity
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
public class AccountRolePermissionsDao_Impl(
  __db: RoomDatabase,
) : AccountRolePermissionsDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfAccountRolePermissionsEntity:
      EntityInsertAdapter<AccountRolePermissionsEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfAccountRolePermissionsEntity = object :
        EntityInsertAdapter<AccountRolePermissionsEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `account_role_permissions` (`id`,`account_id`,`role_key`,`permissions`,`updated_at`,`server_version`) VALUES (?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement,
          entity: AccountRolePermissionsEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.accountId)
        statement.bindText(3, entity.roleKey)
        statement.bindText(4, entity.permissions)
        statement.bindLong(5, entity.updatedAt)
        statement.bindLong(6, entity.serverVersion)
      }
    }
  }

  public override suspend fun upsert(entity: AccountRolePermissionsEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfAccountRolePermissionsEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<AccountRolePermissionsEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfAccountRolePermissionsEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<AccountRolePermissionsEntity>> {
    val _sql: String = "SELECT * FROM account_role_permissions WHERE account_id = ?"
    return createFlow(__db, false, arrayOf("account_role_permissions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfRoleKey: Int = getColumnIndexOrThrow(_stmt, "role_key")
        val _columnIndexOfPermissions: Int = getColumnIndexOrThrow(_stmt, "permissions")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: MutableList<AccountRolePermissionsEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: AccountRolePermissionsEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpRoleKey: String
          _tmpRoleKey = _stmt.getText(_columnIndexOfRoleKey)
          val _tmpPermissions: String
          _tmpPermissions = _stmt.getText(_columnIndexOfPermissions)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _item =
              AccountRolePermissionsEntity(_tmpId,_tmpAccountId,_tmpRoleKey,_tmpPermissions,_tmpUpdatedAt,_tmpServerVersion)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): AccountRolePermissionsEntity? {
    val _sql: String = "SELECT * FROM account_role_permissions WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfRoleKey: Int = getColumnIndexOrThrow(_stmt, "role_key")
        val _columnIndexOfPermissions: Int = getColumnIndexOrThrow(_stmt, "permissions")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: AccountRolePermissionsEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpRoleKey: String
          _tmpRoleKey = _stmt.getText(_columnIndexOfRoleKey)
          val _tmpPermissions: String
          _tmpPermissions = _stmt.getText(_columnIndexOfPermissions)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _result =
              AccountRolePermissionsEntity(_tmpId,_tmpAccountId,_tmpRoleKey,_tmpPermissions,_tmpUpdatedAt,_tmpServerVersion)
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
    val _sql: String = "DELETE FROM account_role_permissions WHERE id = ?"
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
