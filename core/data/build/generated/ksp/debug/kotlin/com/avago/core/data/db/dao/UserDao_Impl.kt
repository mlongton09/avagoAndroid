package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.converters.Converters
import com.avago.core.`data`.db.entity.UserEntity
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
public class UserDao_Impl(
  __db: RoomDatabase,
) : UserDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfUserEntity: EntityInsertAdapter<UserEntity>

  private val __converters: Converters = Converters()
  init {
    this.__db = __db
    this.__insertAdapterOfUserEntity = object : EntityInsertAdapter<UserEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `users` (`user_id`,`account_id`,`display_name`,`email`,`photo_url`,`role`,`is_active`,`created_at`,`updated_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: UserEntity) {
        statement.bindText(1, entity.userId)
        val _tmpAccountId: String? = entity.accountId
        if (_tmpAccountId == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpAccountId)
        }
        val _tmpDisplayName: String? = entity.displayName
        if (_tmpDisplayName == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpDisplayName)
        }
        val _tmpEmail: String? = entity.email
        if (_tmpEmail == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpEmail)
        }
        val _tmpPhotoUrl: String? = entity.photoUrl
        if (_tmpPhotoUrl == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpPhotoUrl)
        }
        val _tmpRole: String? = entity.role
        if (_tmpRole == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpRole)
        }
        val _tmp: Int = __converters.fromBooleanToInt(entity.isActive)
        statement.bindLong(7, _tmp.toLong())
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
        statement.bindLong(10, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(11)
        } else {
          statement.bindLong(11, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: UserEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfUserEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<UserEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfUserEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<UserEntity>> {
    val _sql: String = "SELECT * FROM users WHERE account_id = ?"
    return createFlow(__db, false, arrayOf("users")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfDisplayName: Int = getColumnIndexOrThrow(_stmt, "display_name")
        val _columnIndexOfEmail: Int = getColumnIndexOrThrow(_stmt, "email")
        val _columnIndexOfPhotoUrl: Int = getColumnIndexOrThrow(_stmt, "photo_url")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "is_active")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<UserEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: UserEntity
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpAccountId: String?
          if (_stmt.isNull(_columnIndexOfAccountId)) {
            _tmpAccountId = null
          } else {
            _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          }
          val _tmpDisplayName: String?
          if (_stmt.isNull(_columnIndexOfDisplayName)) {
            _tmpDisplayName = null
          } else {
            _tmpDisplayName = _stmt.getText(_columnIndexOfDisplayName)
          }
          val _tmpEmail: String?
          if (_stmt.isNull(_columnIndexOfEmail)) {
            _tmpEmail = null
          } else {
            _tmpEmail = _stmt.getText(_columnIndexOfEmail)
          }
          val _tmpPhotoUrl: String?
          if (_stmt.isNull(_columnIndexOfPhotoUrl)) {
            _tmpPhotoUrl = null
          } else {
            _tmpPhotoUrl = _stmt.getText(_columnIndexOfPhotoUrl)
          }
          val _tmpRole: String?
          if (_stmt.isNull(_columnIndexOfRole)) {
            _tmpRole = null
          } else {
            _tmpRole = _stmt.getText(_columnIndexOfRole)
          }
          val _tmpIsActive: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIsActive = __converters.fromIntToBoolean(_tmp)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _item =
              UserEntity(_tmpUserId,_tmpAccountId,_tmpDisplayName,_tmpEmail,_tmpPhotoUrl,_tmpRole,_tmpIsActive,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): UserEntity? {
    val _sql: String = "SELECT * FROM users WHERE user_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfDisplayName: Int = getColumnIndexOrThrow(_stmt, "display_name")
        val _columnIndexOfEmail: Int = getColumnIndexOrThrow(_stmt, "email")
        val _columnIndexOfPhotoUrl: Int = getColumnIndexOrThrow(_stmt, "photo_url")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "is_active")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: UserEntity?
        if (_stmt.step()) {
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpAccountId: String?
          if (_stmt.isNull(_columnIndexOfAccountId)) {
            _tmpAccountId = null
          } else {
            _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          }
          val _tmpDisplayName: String?
          if (_stmt.isNull(_columnIndexOfDisplayName)) {
            _tmpDisplayName = null
          } else {
            _tmpDisplayName = _stmt.getText(_columnIndexOfDisplayName)
          }
          val _tmpEmail: String?
          if (_stmt.isNull(_columnIndexOfEmail)) {
            _tmpEmail = null
          } else {
            _tmpEmail = _stmt.getText(_columnIndexOfEmail)
          }
          val _tmpPhotoUrl: String?
          if (_stmt.isNull(_columnIndexOfPhotoUrl)) {
            _tmpPhotoUrl = null
          } else {
            _tmpPhotoUrl = _stmt.getText(_columnIndexOfPhotoUrl)
          }
          val _tmpRole: String?
          if (_stmt.isNull(_columnIndexOfRole)) {
            _tmpRole = null
          } else {
            _tmpRole = _stmt.getText(_columnIndexOfRole)
          }
          val _tmpIsActive: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIsActive = __converters.fromIntToBoolean(_tmp)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _result =
              UserEntity(_tmpUserId,_tmpAccountId,_tmpDisplayName,_tmpEmail,_tmpPhotoUrl,_tmpRole,_tmpIsActive,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "DELETE FROM users WHERE user_id = ?"
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
