package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.PartIssueEntity
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
public class PartIssueDao_Impl(
  __db: RoomDatabase,
) : PartIssueDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPartIssueEntity: EntityInsertAdapter<PartIssueEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPartIssueEntity = object : EntityInsertAdapter<PartIssueEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `part_issues` (`issue_id`,`account_id`,`location_id`,`from_location_id`,`to_location_id`,`issue_type`,`issued_at`,`issued_by`,`reference_id`,`reference_type`,`notes`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PartIssueEntity) {
        statement.bindText(1, entity.issueId)
        statement.bindText(2, entity.accountId)
        val _tmpLocationId: String? = entity.locationId
        if (_tmpLocationId == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpLocationId)
        }
        val _tmpFromLocationId: String? = entity.fromLocationId
        if (_tmpFromLocationId == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpFromLocationId)
        }
        val _tmpToLocationId: String? = entity.toLocationId
        if (_tmpToLocationId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpToLocationId)
        }
        statement.bindText(6, entity.issueType)
        statement.bindLong(7, entity.issuedAt)
        val _tmpIssuedBy: String? = entity.issuedBy
        if (_tmpIssuedBy == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpIssuedBy)
        }
        val _tmpReferenceId: String? = entity.referenceId
        if (_tmpReferenceId == null) {
          statement.bindNull(9)
        } else {
          statement.bindText(9, _tmpReferenceId)
        }
        val _tmpReferenceType: String? = entity.referenceType
        if (_tmpReferenceType == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpReferenceType)
        }
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpNotes)
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

  public override suspend fun upsert(entity: PartIssueEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfPartIssueEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<PartIssueEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPartIssueEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<PartIssueEntity>> {
    val _sql: String = "SELECT * FROM part_issues WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("part_issues")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfIssueId: Int = getColumnIndexOrThrow(_stmt, "issue_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfFromLocationId: Int = getColumnIndexOrThrow(_stmt, "from_location_id")
        val _columnIndexOfToLocationId: Int = getColumnIndexOrThrow(_stmt, "to_location_id")
        val _columnIndexOfIssueType: Int = getColumnIndexOrThrow(_stmt, "issue_type")
        val _columnIndexOfIssuedAt: Int = getColumnIndexOrThrow(_stmt, "issued_at")
        val _columnIndexOfIssuedBy: Int = getColumnIndexOrThrow(_stmt, "issued_by")
        val _columnIndexOfReferenceId: Int = getColumnIndexOrThrow(_stmt, "reference_id")
        val _columnIndexOfReferenceType: Int = getColumnIndexOrThrow(_stmt, "reference_type")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<PartIssueEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PartIssueEntity
          val _tmpIssueId: String
          _tmpIssueId = _stmt.getText(_columnIndexOfIssueId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpLocationId: String?
          if (_stmt.isNull(_columnIndexOfLocationId)) {
            _tmpLocationId = null
          } else {
            _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          }
          val _tmpFromLocationId: String?
          if (_stmt.isNull(_columnIndexOfFromLocationId)) {
            _tmpFromLocationId = null
          } else {
            _tmpFromLocationId = _stmt.getText(_columnIndexOfFromLocationId)
          }
          val _tmpToLocationId: String?
          if (_stmt.isNull(_columnIndexOfToLocationId)) {
            _tmpToLocationId = null
          } else {
            _tmpToLocationId = _stmt.getText(_columnIndexOfToLocationId)
          }
          val _tmpIssueType: String
          _tmpIssueType = _stmt.getText(_columnIndexOfIssueType)
          val _tmpIssuedAt: Long
          _tmpIssuedAt = _stmt.getLong(_columnIndexOfIssuedAt)
          val _tmpIssuedBy: String?
          if (_stmt.isNull(_columnIndexOfIssuedBy)) {
            _tmpIssuedBy = null
          } else {
            _tmpIssuedBy = _stmt.getText(_columnIndexOfIssuedBy)
          }
          val _tmpReferenceId: String?
          if (_stmt.isNull(_columnIndexOfReferenceId)) {
            _tmpReferenceId = null
          } else {
            _tmpReferenceId = _stmt.getText(_columnIndexOfReferenceId)
          }
          val _tmpReferenceType: String?
          if (_stmt.isNull(_columnIndexOfReferenceType)) {
            _tmpReferenceType = null
          } else {
            _tmpReferenceType = _stmt.getText(_columnIndexOfReferenceType)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
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
              PartIssueEntity(_tmpIssueId,_tmpAccountId,_tmpLocationId,_tmpFromLocationId,_tmpToLocationId,_tmpIssueType,_tmpIssuedAt,_tmpIssuedBy,_tmpReferenceId,_tmpReferenceType,_tmpNotes,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): PartIssueEntity? {
    val _sql: String = "SELECT * FROM part_issues WHERE issue_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfIssueId: Int = getColumnIndexOrThrow(_stmt, "issue_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfFromLocationId: Int = getColumnIndexOrThrow(_stmt, "from_location_id")
        val _columnIndexOfToLocationId: Int = getColumnIndexOrThrow(_stmt, "to_location_id")
        val _columnIndexOfIssueType: Int = getColumnIndexOrThrow(_stmt, "issue_type")
        val _columnIndexOfIssuedAt: Int = getColumnIndexOrThrow(_stmt, "issued_at")
        val _columnIndexOfIssuedBy: Int = getColumnIndexOrThrow(_stmt, "issued_by")
        val _columnIndexOfReferenceId: Int = getColumnIndexOrThrow(_stmt, "reference_id")
        val _columnIndexOfReferenceType: Int = getColumnIndexOrThrow(_stmt, "reference_type")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: PartIssueEntity?
        if (_stmt.step()) {
          val _tmpIssueId: String
          _tmpIssueId = _stmt.getText(_columnIndexOfIssueId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpLocationId: String?
          if (_stmt.isNull(_columnIndexOfLocationId)) {
            _tmpLocationId = null
          } else {
            _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          }
          val _tmpFromLocationId: String?
          if (_stmt.isNull(_columnIndexOfFromLocationId)) {
            _tmpFromLocationId = null
          } else {
            _tmpFromLocationId = _stmt.getText(_columnIndexOfFromLocationId)
          }
          val _tmpToLocationId: String?
          if (_stmt.isNull(_columnIndexOfToLocationId)) {
            _tmpToLocationId = null
          } else {
            _tmpToLocationId = _stmt.getText(_columnIndexOfToLocationId)
          }
          val _tmpIssueType: String
          _tmpIssueType = _stmt.getText(_columnIndexOfIssueType)
          val _tmpIssuedAt: Long
          _tmpIssuedAt = _stmt.getLong(_columnIndexOfIssuedAt)
          val _tmpIssuedBy: String?
          if (_stmt.isNull(_columnIndexOfIssuedBy)) {
            _tmpIssuedBy = null
          } else {
            _tmpIssuedBy = _stmt.getText(_columnIndexOfIssuedBy)
          }
          val _tmpReferenceId: String?
          if (_stmt.isNull(_columnIndexOfReferenceId)) {
            _tmpReferenceId = null
          } else {
            _tmpReferenceId = _stmt.getText(_columnIndexOfReferenceId)
          }
          val _tmpReferenceType: String?
          if (_stmt.isNull(_columnIndexOfReferenceType)) {
            _tmpReferenceType = null
          } else {
            _tmpReferenceType = _stmt.getText(_columnIndexOfReferenceType)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
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
              PartIssueEntity(_tmpIssueId,_tmpAccountId,_tmpLocationId,_tmpFromLocationId,_tmpToLocationId,_tmpIssueType,_tmpIssuedAt,_tmpIssuedBy,_tmpReferenceId,_tmpReferenceType,_tmpNotes,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE part_issues SET deleted_at = ?, updated_at = ? WHERE issue_id = ?"
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
