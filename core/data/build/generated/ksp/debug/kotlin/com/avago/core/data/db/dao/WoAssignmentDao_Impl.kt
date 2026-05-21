package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.WoAssignmentEntity
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
public class WoAssignmentDao_Impl(
  __db: RoomDatabase,
) : WoAssignmentDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfWoAssignmentEntity: EntityInsertAdapter<WoAssignmentEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfWoAssignmentEntity = object : EntityInsertAdapter<WoAssignmentEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `wo_assignments` (`assignment_id`,`wo_id`,`technician_id`,`assigned_at`,`unassigned_at`,`status`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: WoAssignmentEntity) {
        statement.bindText(1, entity.assignmentId)
        statement.bindText(2, entity.woId)
        statement.bindText(3, entity.technicianId)
        statement.bindLong(4, entity.assignedAt)
        val _tmpUnassignedAt: Long? = entity.unassignedAt
        if (_tmpUnassignedAt == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpUnassignedAt)
        }
        statement.bindText(6, entity.status)
        statement.bindLong(7, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: WoAssignmentEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfWoAssignmentEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<WoAssignmentEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfWoAssignmentEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<WoAssignmentEntity>> {
    val _sql: String =
        "SELECT * FROM wo_assignments WHERE wo_id IN (SELECT wo_id FROM work_orders WHERE account_id = ?)"
    return createFlow(__db, false, arrayOf("wo_assignments", "work_orders")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfAssignmentId: Int = getColumnIndexOrThrow(_stmt, "assignment_id")
        val _columnIndexOfWoId: Int = getColumnIndexOrThrow(_stmt, "wo_id")
        val _columnIndexOfTechnicianId: Int = getColumnIndexOrThrow(_stmt, "technician_id")
        val _columnIndexOfAssignedAt: Int = getColumnIndexOrThrow(_stmt, "assigned_at")
        val _columnIndexOfUnassignedAt: Int = getColumnIndexOrThrow(_stmt, "unassigned_at")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<WoAssignmentEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: WoAssignmentEntity
          val _tmpAssignmentId: String
          _tmpAssignmentId = _stmt.getText(_columnIndexOfAssignmentId)
          val _tmpWoId: String
          _tmpWoId = _stmt.getText(_columnIndexOfWoId)
          val _tmpTechnicianId: String
          _tmpTechnicianId = _stmt.getText(_columnIndexOfTechnicianId)
          val _tmpAssignedAt: Long
          _tmpAssignedAt = _stmt.getLong(_columnIndexOfAssignedAt)
          val _tmpUnassignedAt: Long?
          if (_stmt.isNull(_columnIndexOfUnassignedAt)) {
            _tmpUnassignedAt = null
          } else {
            _tmpUnassignedAt = _stmt.getLong(_columnIndexOfUnassignedAt)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _item =
              WoAssignmentEntity(_tmpAssignmentId,_tmpWoId,_tmpTechnicianId,_tmpAssignedAt,_tmpUnassignedAt,_tmpStatus,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): WoAssignmentEntity? {
    val _sql: String = "SELECT * FROM wo_assignments WHERE assignment_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfAssignmentId: Int = getColumnIndexOrThrow(_stmt, "assignment_id")
        val _columnIndexOfWoId: Int = getColumnIndexOrThrow(_stmt, "wo_id")
        val _columnIndexOfTechnicianId: Int = getColumnIndexOrThrow(_stmt, "technician_id")
        val _columnIndexOfAssignedAt: Int = getColumnIndexOrThrow(_stmt, "assigned_at")
        val _columnIndexOfUnassignedAt: Int = getColumnIndexOrThrow(_stmt, "unassigned_at")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: WoAssignmentEntity?
        if (_stmt.step()) {
          val _tmpAssignmentId: String
          _tmpAssignmentId = _stmt.getText(_columnIndexOfAssignmentId)
          val _tmpWoId: String
          _tmpWoId = _stmt.getText(_columnIndexOfWoId)
          val _tmpTechnicianId: String
          _tmpTechnicianId = _stmt.getText(_columnIndexOfTechnicianId)
          val _tmpAssignedAt: Long
          _tmpAssignedAt = _stmt.getLong(_columnIndexOfAssignedAt)
          val _tmpUnassignedAt: Long?
          if (_stmt.isNull(_columnIndexOfUnassignedAt)) {
            _tmpUnassignedAt = null
          } else {
            _tmpUnassignedAt = _stmt.getLong(_columnIndexOfUnassignedAt)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _result =
              WoAssignmentEntity(_tmpAssignmentId,_tmpWoId,_tmpTechnicianId,_tmpAssignedAt,_tmpUnassignedAt,_tmpStatus,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "DELETE FROM wo_assignments WHERE assignment_id = ?"
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
