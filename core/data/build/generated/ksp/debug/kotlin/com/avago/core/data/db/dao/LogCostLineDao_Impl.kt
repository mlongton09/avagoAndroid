package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.LogCostLineEntity
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
public class LogCostLineDao_Impl(
  __db: RoomDatabase,
) : LogCostLineDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfLogCostLineEntity: EntityInsertAdapter<LogCostLineEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfLogCostLineEntity = object : EntityInsertAdapter<LogCostLineEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `log_cost_lines` (`line_id`,`account_id`,`log_id`,`kind`,`display_order`,`inventory_id`,`user_id`,`description`,`quantity`,`unit_cost`,`tax_amount`,`gl_code`,`notes`,`wo_id`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LogCostLineEntity) {
        statement.bindText(1, entity.lineId)
        statement.bindText(2, entity.accountId)
        statement.bindText(3, entity.logId)
        statement.bindText(4, entity.kind)
        statement.bindLong(5, entity.displayOrder)
        val _tmpInventoryId: String? = entity.inventoryId
        if (_tmpInventoryId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpInventoryId)
        }
        val _tmpUserId: String? = entity.userId
        if (_tmpUserId == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpUserId)
        }
        val _tmpDescription: String? = entity.description
        if (_tmpDescription == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpDescription)
        }
        statement.bindDouble(9, entity.quantity)
        statement.bindDouble(10, entity.unitCost)
        val _tmpTaxAmount: Double? = entity.taxAmount
        if (_tmpTaxAmount == null) {
          statement.bindNull(11)
        } else {
          statement.bindDouble(11, _tmpTaxAmount)
        }
        val _tmpGlCode: String? = entity.glCode
        if (_tmpGlCode == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpGlCode)
        }
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpNotes)
        }
        val _tmpWoId: String? = entity.woId
        if (_tmpWoId == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpWoId)
        }
        statement.bindLong(15, entity.createdAt)
        statement.bindLong(16, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(17)
        } else {
          statement.bindLong(17, _tmpDeletedAt)
        }
        statement.bindLong(18, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(19)
        } else {
          statement.bindLong(19, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: LogCostLineEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfLogCostLineEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<LogCostLineEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfLogCostLineEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<LogCostLineEntity>> {
    val _sql: String = "SELECT * FROM log_cost_lines WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("log_cost_lines")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfLineId: Int = getColumnIndexOrThrow(_stmt, "line_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfLogId: Int = getColumnIndexOrThrow(_stmt, "log_id")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfDisplayOrder: Int = getColumnIndexOrThrow(_stmt, "display_order")
        val _columnIndexOfInventoryId: Int = getColumnIndexOrThrow(_stmt, "inventory_id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfUnitCost: Int = getColumnIndexOrThrow(_stmt, "unit_cost")
        val _columnIndexOfTaxAmount: Int = getColumnIndexOrThrow(_stmt, "tax_amount")
        val _columnIndexOfGlCode: Int = getColumnIndexOrThrow(_stmt, "gl_code")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfWoId: Int = getColumnIndexOrThrow(_stmt, "wo_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<LogCostLineEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: LogCostLineEntity
          val _tmpLineId: String
          _tmpLineId = _stmt.getText(_columnIndexOfLineId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpLogId: String
          _tmpLogId = _stmt.getText(_columnIndexOfLogId)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpDisplayOrder: Long
          _tmpDisplayOrder = _stmt.getLong(_columnIndexOfDisplayOrder)
          val _tmpInventoryId: String?
          if (_stmt.isNull(_columnIndexOfInventoryId)) {
            _tmpInventoryId = null
          } else {
            _tmpInventoryId = _stmt.getText(_columnIndexOfInventoryId)
          }
          val _tmpUserId: String?
          if (_stmt.isNull(_columnIndexOfUserId)) {
            _tmpUserId = null
          } else {
            _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          }
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpQuantity: Double
          _tmpQuantity = _stmt.getDouble(_columnIndexOfQuantity)
          val _tmpUnitCost: Double
          _tmpUnitCost = _stmt.getDouble(_columnIndexOfUnitCost)
          val _tmpTaxAmount: Double?
          if (_stmt.isNull(_columnIndexOfTaxAmount)) {
            _tmpTaxAmount = null
          } else {
            _tmpTaxAmount = _stmt.getDouble(_columnIndexOfTaxAmount)
          }
          val _tmpGlCode: String?
          if (_stmt.isNull(_columnIndexOfGlCode)) {
            _tmpGlCode = null
          } else {
            _tmpGlCode = _stmt.getText(_columnIndexOfGlCode)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpWoId: String?
          if (_stmt.isNull(_columnIndexOfWoId)) {
            _tmpWoId = null
          } else {
            _tmpWoId = _stmt.getText(_columnIndexOfWoId)
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
              LogCostLineEntity(_tmpLineId,_tmpAccountId,_tmpLogId,_tmpKind,_tmpDisplayOrder,_tmpInventoryId,_tmpUserId,_tmpDescription,_tmpQuantity,_tmpUnitCost,_tmpTaxAmount,_tmpGlCode,_tmpNotes,_tmpWoId,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): LogCostLineEntity? {
    val _sql: String = "SELECT * FROM log_cost_lines WHERE line_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfLineId: Int = getColumnIndexOrThrow(_stmt, "line_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfLogId: Int = getColumnIndexOrThrow(_stmt, "log_id")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfDisplayOrder: Int = getColumnIndexOrThrow(_stmt, "display_order")
        val _columnIndexOfInventoryId: Int = getColumnIndexOrThrow(_stmt, "inventory_id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfUnitCost: Int = getColumnIndexOrThrow(_stmt, "unit_cost")
        val _columnIndexOfTaxAmount: Int = getColumnIndexOrThrow(_stmt, "tax_amount")
        val _columnIndexOfGlCode: Int = getColumnIndexOrThrow(_stmt, "gl_code")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfWoId: Int = getColumnIndexOrThrow(_stmt, "wo_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: LogCostLineEntity?
        if (_stmt.step()) {
          val _tmpLineId: String
          _tmpLineId = _stmt.getText(_columnIndexOfLineId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpLogId: String
          _tmpLogId = _stmt.getText(_columnIndexOfLogId)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpDisplayOrder: Long
          _tmpDisplayOrder = _stmt.getLong(_columnIndexOfDisplayOrder)
          val _tmpInventoryId: String?
          if (_stmt.isNull(_columnIndexOfInventoryId)) {
            _tmpInventoryId = null
          } else {
            _tmpInventoryId = _stmt.getText(_columnIndexOfInventoryId)
          }
          val _tmpUserId: String?
          if (_stmt.isNull(_columnIndexOfUserId)) {
            _tmpUserId = null
          } else {
            _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          }
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpQuantity: Double
          _tmpQuantity = _stmt.getDouble(_columnIndexOfQuantity)
          val _tmpUnitCost: Double
          _tmpUnitCost = _stmt.getDouble(_columnIndexOfUnitCost)
          val _tmpTaxAmount: Double?
          if (_stmt.isNull(_columnIndexOfTaxAmount)) {
            _tmpTaxAmount = null
          } else {
            _tmpTaxAmount = _stmt.getDouble(_columnIndexOfTaxAmount)
          }
          val _tmpGlCode: String?
          if (_stmt.isNull(_columnIndexOfGlCode)) {
            _tmpGlCode = null
          } else {
            _tmpGlCode = _stmt.getText(_columnIndexOfGlCode)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpWoId: String?
          if (_stmt.isNull(_columnIndexOfWoId)) {
            _tmpWoId = null
          } else {
            _tmpWoId = _stmt.getText(_columnIndexOfWoId)
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
              LogCostLineEntity(_tmpLineId,_tmpAccountId,_tmpLogId,_tmpKind,_tmpDisplayOrder,_tmpInventoryId,_tmpUserId,_tmpDescription,_tmpQuantity,_tmpUnitCost,_tmpTaxAmount,_tmpGlCode,_tmpNotes,_tmpWoId,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE log_cost_lines SET deleted_at = ?, updated_at = ? WHERE line_id = ?"
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
