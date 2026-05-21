package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.PartIssueLineEntity
import javax.`annotation`.processing.Generated
import kotlin.Double
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
public class PartIssueLineDao_Impl(
  __db: RoomDatabase,
) : PartIssueLineDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPartIssueLineEntity: EntityInsertAdapter<PartIssueLineEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPartIssueLineEntity = object : EntityInsertAdapter<PartIssueLineEntity>()
        {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `part_issue_lines` (`line_id`,`issue_id`,`part_id`,`inventory_id`,`quantity`,`unit_cost`,`notes`,`created_at`,`updated_at`,`server_version`) VALUES (?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PartIssueLineEntity) {
        statement.bindText(1, entity.lineId)
        statement.bindText(2, entity.issueId)
        statement.bindText(3, entity.partId)
        val _tmpInventoryId: String? = entity.inventoryId
        if (_tmpInventoryId == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpInventoryId)
        }
        statement.bindDouble(5, entity.quantity)
        val _tmpUnitCost: Double? = entity.unitCost
        if (_tmpUnitCost == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpUnitCost)
        }
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpNotes)
        }
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
        statement.bindLong(10, entity.serverVersion)
      }
    }
  }

  public override suspend fun upsert(entity: PartIssueLineEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfPartIssueLineEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<PartIssueLineEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPartIssueLineEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<PartIssueLineEntity>> {
    val _sql: String =
        "SELECT * FROM part_issue_lines WHERE issue_id IN (SELECT issue_id FROM part_issues WHERE account_id = ?)"
    return createFlow(__db, false, arrayOf("part_issue_lines", "part_issues")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfLineId: Int = getColumnIndexOrThrow(_stmt, "line_id")
        val _columnIndexOfIssueId: Int = getColumnIndexOrThrow(_stmt, "issue_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfInventoryId: Int = getColumnIndexOrThrow(_stmt, "inventory_id")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfUnitCost: Int = getColumnIndexOrThrow(_stmt, "unit_cost")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: MutableList<PartIssueLineEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PartIssueLineEntity
          val _tmpLineId: String
          _tmpLineId = _stmt.getText(_columnIndexOfLineId)
          val _tmpIssueId: String
          _tmpIssueId = _stmt.getText(_columnIndexOfIssueId)
          val _tmpPartId: String
          _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          val _tmpInventoryId: String?
          if (_stmt.isNull(_columnIndexOfInventoryId)) {
            _tmpInventoryId = null
          } else {
            _tmpInventoryId = _stmt.getText(_columnIndexOfInventoryId)
          }
          val _tmpQuantity: Double
          _tmpQuantity = _stmt.getDouble(_columnIndexOfQuantity)
          val _tmpUnitCost: Double?
          if (_stmt.isNull(_columnIndexOfUnitCost)) {
            _tmpUnitCost = null
          } else {
            _tmpUnitCost = _stmt.getDouble(_columnIndexOfUnitCost)
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
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _item =
              PartIssueLineEntity(_tmpLineId,_tmpIssueId,_tmpPartId,_tmpInventoryId,_tmpQuantity,_tmpUnitCost,_tmpNotes,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): PartIssueLineEntity? {
    val _sql: String = "SELECT * FROM part_issue_lines WHERE line_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfLineId: Int = getColumnIndexOrThrow(_stmt, "line_id")
        val _columnIndexOfIssueId: Int = getColumnIndexOrThrow(_stmt, "issue_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfInventoryId: Int = getColumnIndexOrThrow(_stmt, "inventory_id")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfUnitCost: Int = getColumnIndexOrThrow(_stmt, "unit_cost")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: PartIssueLineEntity?
        if (_stmt.step()) {
          val _tmpLineId: String
          _tmpLineId = _stmt.getText(_columnIndexOfLineId)
          val _tmpIssueId: String
          _tmpIssueId = _stmt.getText(_columnIndexOfIssueId)
          val _tmpPartId: String
          _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          val _tmpInventoryId: String?
          if (_stmt.isNull(_columnIndexOfInventoryId)) {
            _tmpInventoryId = null
          } else {
            _tmpInventoryId = _stmt.getText(_columnIndexOfInventoryId)
          }
          val _tmpQuantity: Double
          _tmpQuantity = _stmt.getDouble(_columnIndexOfQuantity)
          val _tmpUnitCost: Double?
          if (_stmt.isNull(_columnIndexOfUnitCost)) {
            _tmpUnitCost = null
          } else {
            _tmpUnitCost = _stmt.getDouble(_columnIndexOfUnitCost)
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
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _result =
              PartIssueLineEntity(_tmpLineId,_tmpIssueId,_tmpPartId,_tmpInventoryId,_tmpQuantity,_tmpUnitCost,_tmpNotes,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
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
    val _sql: String = "DELETE FROM part_issue_lines WHERE line_id = ?"
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
