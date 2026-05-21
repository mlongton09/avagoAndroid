package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.CycleCountEntity
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
public class CycleCountDao_Impl(
  __db: RoomDatabase,
) : CycleCountDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfCycleCountEntity: EntityInsertAdapter<CycleCountEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfCycleCountEntity = object : EntityInsertAdapter<CycleCountEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `cycle_counts` (`cycle_count_id`,`account_id`,`location_id`,`status`,`scope_type`,`scope_value`,`started_at`,`locked_at`,`completed_at`,`started_by`,`locked_by`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CycleCountEntity) {
        statement.bindText(1, entity.cycleCountId)
        statement.bindText(2, entity.accountId)
        statement.bindText(3, entity.locationId)
        statement.bindText(4, entity.status)
        val _tmpScopeType: String? = entity.scopeType
        if (_tmpScopeType == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpScopeType)
        }
        val _tmpScopeValue: String? = entity.scopeValue
        if (_tmpScopeValue == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpScopeValue)
        }
        val _tmpStartedAt: Long? = entity.startedAt
        if (_tmpStartedAt == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpStartedAt)
        }
        val _tmpLockedAt: Long? = entity.lockedAt
        if (_tmpLockedAt == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpLockedAt)
        }
        val _tmpCompletedAt: Long? = entity.completedAt
        if (_tmpCompletedAt == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpCompletedAt)
        }
        val _tmpStartedBy: String? = entity.startedBy
        if (_tmpStartedBy == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpStartedBy)
        }
        val _tmpLockedBy: String? = entity.lockedBy
        if (_tmpLockedBy == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpLockedBy)
        }
        statement.bindLong(12, entity.createdAt)
        statement.bindLong(13, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(14)
        } else {
          statement.bindLong(14, _tmpDeletedAt)
        }
        statement.bindLong(15, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(16)
        } else {
          statement.bindLong(16, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: CycleCountEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfCycleCountEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<CycleCountEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfCycleCountEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<CycleCountEntity>> {
    val _sql: String = "SELECT * FROM cycle_counts WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("cycle_counts")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfCycleCountId: Int = getColumnIndexOrThrow(_stmt, "cycle_count_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfScopeType: Int = getColumnIndexOrThrow(_stmt, "scope_type")
        val _columnIndexOfScopeValue: Int = getColumnIndexOrThrow(_stmt, "scope_value")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "started_at")
        val _columnIndexOfLockedAt: Int = getColumnIndexOrThrow(_stmt, "locked_at")
        val _columnIndexOfCompletedAt: Int = getColumnIndexOrThrow(_stmt, "completed_at")
        val _columnIndexOfStartedBy: Int = getColumnIndexOrThrow(_stmt, "started_by")
        val _columnIndexOfLockedBy: Int = getColumnIndexOrThrow(_stmt, "locked_by")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<CycleCountEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CycleCountEntity
          val _tmpCycleCountId: String
          _tmpCycleCountId = _stmt.getText(_columnIndexOfCycleCountId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpLocationId: String
          _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpScopeType: String?
          if (_stmt.isNull(_columnIndexOfScopeType)) {
            _tmpScopeType = null
          } else {
            _tmpScopeType = _stmt.getText(_columnIndexOfScopeType)
          }
          val _tmpScopeValue: String?
          if (_stmt.isNull(_columnIndexOfScopeValue)) {
            _tmpScopeValue = null
          } else {
            _tmpScopeValue = _stmt.getText(_columnIndexOfScopeValue)
          }
          val _tmpStartedAt: Long?
          if (_stmt.isNull(_columnIndexOfStartedAt)) {
            _tmpStartedAt = null
          } else {
            _tmpStartedAt = _stmt.getLong(_columnIndexOfStartedAt)
          }
          val _tmpLockedAt: Long?
          if (_stmt.isNull(_columnIndexOfLockedAt)) {
            _tmpLockedAt = null
          } else {
            _tmpLockedAt = _stmt.getLong(_columnIndexOfLockedAt)
          }
          val _tmpCompletedAt: Long?
          if (_stmt.isNull(_columnIndexOfCompletedAt)) {
            _tmpCompletedAt = null
          } else {
            _tmpCompletedAt = _stmt.getLong(_columnIndexOfCompletedAt)
          }
          val _tmpStartedBy: String?
          if (_stmt.isNull(_columnIndexOfStartedBy)) {
            _tmpStartedBy = null
          } else {
            _tmpStartedBy = _stmt.getText(_columnIndexOfStartedBy)
          }
          val _tmpLockedBy: String?
          if (_stmt.isNull(_columnIndexOfLockedBy)) {
            _tmpLockedBy = null
          } else {
            _tmpLockedBy = _stmt.getText(_columnIndexOfLockedBy)
          }
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
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _item =
              CycleCountEntity(_tmpCycleCountId,_tmpAccountId,_tmpLocationId,_tmpStatus,_tmpScopeType,_tmpScopeValue,_tmpStartedAt,_tmpLockedAt,_tmpCompletedAt,_tmpStartedBy,_tmpLockedBy,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): CycleCountEntity? {
    val _sql: String = "SELECT * FROM cycle_counts WHERE cycle_count_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfCycleCountId: Int = getColumnIndexOrThrow(_stmt, "cycle_count_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfScopeType: Int = getColumnIndexOrThrow(_stmt, "scope_type")
        val _columnIndexOfScopeValue: Int = getColumnIndexOrThrow(_stmt, "scope_value")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "started_at")
        val _columnIndexOfLockedAt: Int = getColumnIndexOrThrow(_stmt, "locked_at")
        val _columnIndexOfCompletedAt: Int = getColumnIndexOrThrow(_stmt, "completed_at")
        val _columnIndexOfStartedBy: Int = getColumnIndexOrThrow(_stmt, "started_by")
        val _columnIndexOfLockedBy: Int = getColumnIndexOrThrow(_stmt, "locked_by")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: CycleCountEntity?
        if (_stmt.step()) {
          val _tmpCycleCountId: String
          _tmpCycleCountId = _stmt.getText(_columnIndexOfCycleCountId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpLocationId: String
          _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpScopeType: String?
          if (_stmt.isNull(_columnIndexOfScopeType)) {
            _tmpScopeType = null
          } else {
            _tmpScopeType = _stmt.getText(_columnIndexOfScopeType)
          }
          val _tmpScopeValue: String?
          if (_stmt.isNull(_columnIndexOfScopeValue)) {
            _tmpScopeValue = null
          } else {
            _tmpScopeValue = _stmt.getText(_columnIndexOfScopeValue)
          }
          val _tmpStartedAt: Long?
          if (_stmt.isNull(_columnIndexOfStartedAt)) {
            _tmpStartedAt = null
          } else {
            _tmpStartedAt = _stmt.getLong(_columnIndexOfStartedAt)
          }
          val _tmpLockedAt: Long?
          if (_stmt.isNull(_columnIndexOfLockedAt)) {
            _tmpLockedAt = null
          } else {
            _tmpLockedAt = _stmt.getLong(_columnIndexOfLockedAt)
          }
          val _tmpCompletedAt: Long?
          if (_stmt.isNull(_columnIndexOfCompletedAt)) {
            _tmpCompletedAt = null
          } else {
            _tmpCompletedAt = _stmt.getLong(_columnIndexOfCompletedAt)
          }
          val _tmpStartedBy: String?
          if (_stmt.isNull(_columnIndexOfStartedBy)) {
            _tmpStartedBy = null
          } else {
            _tmpStartedBy = _stmt.getText(_columnIndexOfStartedBy)
          }
          val _tmpLockedBy: String?
          if (_stmt.isNull(_columnIndexOfLockedBy)) {
            _tmpLockedBy = null
          } else {
            _tmpLockedBy = _stmt.getText(_columnIndexOfLockedBy)
          }
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
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _result =
              CycleCountEntity(_tmpCycleCountId,_tmpAccountId,_tmpLocationId,_tmpStatus,_tmpScopeType,_tmpScopeValue,_tmpStartedAt,_tmpLockedAt,_tmpCompletedAt,_tmpStartedBy,_tmpLockedBy,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String =
        "UPDATE cycle_counts SET deleted_at = ?, updated_at = ? WHERE cycle_count_id = ?"
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
