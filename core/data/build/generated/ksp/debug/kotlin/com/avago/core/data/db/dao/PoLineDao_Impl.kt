package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.PoLineEntity
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
public class PoLineDao_Impl(
  __db: RoomDatabase,
) : PoLineDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPoLineEntity: EntityInsertAdapter<PoLineEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPoLineEntity = object : EntityInsertAdapter<PoLineEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `po_lines` (`po_line_id`,`po_id`,`part_id`,`description`,`quantity`,`unit_cost`,`currency`,`gl_code`,`received_qty`,`display_order`,`created_at`,`updated_at`,`server_version`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PoLineEntity) {
        statement.bindText(1, entity.poLineId)
        statement.bindText(2, entity.poId)
        val _tmpPartId: String? = entity.partId
        if (_tmpPartId == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpPartId)
        }
        val _tmpDescription: String? = entity.description
        if (_tmpDescription == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpDescription)
        }
        statement.bindDouble(5, entity.quantity)
        val _tmpUnitCost: Double? = entity.unitCost
        if (_tmpUnitCost == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpUnitCost)
        }
        val _tmpCurrency: String? = entity.currency
        if (_tmpCurrency == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpCurrency)
        }
        val _tmpGlCode: String? = entity.glCode
        if (_tmpGlCode == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpGlCode)
        }
        val _tmpReceivedQty: Double? = entity.receivedQty
        if (_tmpReceivedQty == null) {
          statement.bindNull(9)
        } else {
          statement.bindDouble(9, _tmpReceivedQty)
        }
        statement.bindLong(10, entity.displayOrder)
        statement.bindLong(11, entity.createdAt)
        statement.bindLong(12, entity.updatedAt)
        statement.bindLong(13, entity.serverVersion)
      }
    }
  }

  public override suspend fun upsert(entity: PoLineEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfPoLineEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<PoLineEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPoLineEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<PoLineEntity>> {
    val _sql: String =
        "SELECT * FROM po_lines WHERE po_id IN (SELECT po_id FROM purchase_orders WHERE account_id = ?)"
    return createFlow(__db, false, arrayOf("po_lines", "purchase_orders")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfPoLineId: Int = getColumnIndexOrThrow(_stmt, "po_line_id")
        val _columnIndexOfPoId: Int = getColumnIndexOrThrow(_stmt, "po_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfUnitCost: Int = getColumnIndexOrThrow(_stmt, "unit_cost")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfGlCode: Int = getColumnIndexOrThrow(_stmt, "gl_code")
        val _columnIndexOfReceivedQty: Int = getColumnIndexOrThrow(_stmt, "received_qty")
        val _columnIndexOfDisplayOrder: Int = getColumnIndexOrThrow(_stmt, "display_order")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: MutableList<PoLineEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PoLineEntity
          val _tmpPoLineId: String
          _tmpPoLineId = _stmt.getText(_columnIndexOfPoLineId)
          val _tmpPoId: String
          _tmpPoId = _stmt.getText(_columnIndexOfPoId)
          val _tmpPartId: String?
          if (_stmt.isNull(_columnIndexOfPartId)) {
            _tmpPartId = null
          } else {
            _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          }
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpQuantity: Double
          _tmpQuantity = _stmt.getDouble(_columnIndexOfQuantity)
          val _tmpUnitCost: Double?
          if (_stmt.isNull(_columnIndexOfUnitCost)) {
            _tmpUnitCost = null
          } else {
            _tmpUnitCost = _stmt.getDouble(_columnIndexOfUnitCost)
          }
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpGlCode: String?
          if (_stmt.isNull(_columnIndexOfGlCode)) {
            _tmpGlCode = null
          } else {
            _tmpGlCode = _stmt.getText(_columnIndexOfGlCode)
          }
          val _tmpReceivedQty: Double?
          if (_stmt.isNull(_columnIndexOfReceivedQty)) {
            _tmpReceivedQty = null
          } else {
            _tmpReceivedQty = _stmt.getDouble(_columnIndexOfReceivedQty)
          }
          val _tmpDisplayOrder: Long
          _tmpDisplayOrder = _stmt.getLong(_columnIndexOfDisplayOrder)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _item =
              PoLineEntity(_tmpPoLineId,_tmpPoId,_tmpPartId,_tmpDescription,_tmpQuantity,_tmpUnitCost,_tmpCurrency,_tmpGlCode,_tmpReceivedQty,_tmpDisplayOrder,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): PoLineEntity? {
    val _sql: String = "SELECT * FROM po_lines WHERE po_line_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfPoLineId: Int = getColumnIndexOrThrow(_stmt, "po_line_id")
        val _columnIndexOfPoId: Int = getColumnIndexOrThrow(_stmt, "po_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfUnitCost: Int = getColumnIndexOrThrow(_stmt, "unit_cost")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfGlCode: Int = getColumnIndexOrThrow(_stmt, "gl_code")
        val _columnIndexOfReceivedQty: Int = getColumnIndexOrThrow(_stmt, "received_qty")
        val _columnIndexOfDisplayOrder: Int = getColumnIndexOrThrow(_stmt, "display_order")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: PoLineEntity?
        if (_stmt.step()) {
          val _tmpPoLineId: String
          _tmpPoLineId = _stmt.getText(_columnIndexOfPoLineId)
          val _tmpPoId: String
          _tmpPoId = _stmt.getText(_columnIndexOfPoId)
          val _tmpPartId: String?
          if (_stmt.isNull(_columnIndexOfPartId)) {
            _tmpPartId = null
          } else {
            _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          }
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpQuantity: Double
          _tmpQuantity = _stmt.getDouble(_columnIndexOfQuantity)
          val _tmpUnitCost: Double?
          if (_stmt.isNull(_columnIndexOfUnitCost)) {
            _tmpUnitCost = null
          } else {
            _tmpUnitCost = _stmt.getDouble(_columnIndexOfUnitCost)
          }
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpGlCode: String?
          if (_stmt.isNull(_columnIndexOfGlCode)) {
            _tmpGlCode = null
          } else {
            _tmpGlCode = _stmt.getText(_columnIndexOfGlCode)
          }
          val _tmpReceivedQty: Double?
          if (_stmt.isNull(_columnIndexOfReceivedQty)) {
            _tmpReceivedQty = null
          } else {
            _tmpReceivedQty = _stmt.getDouble(_columnIndexOfReceivedQty)
          }
          val _tmpDisplayOrder: Long
          _tmpDisplayOrder = _stmt.getLong(_columnIndexOfDisplayOrder)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _result =
              PoLineEntity(_tmpPoLineId,_tmpPoId,_tmpPartId,_tmpDescription,_tmpQuantity,_tmpUnitCost,_tmpCurrency,_tmpGlCode,_tmpReceivedQty,_tmpDisplayOrder,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
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
    val _sql: String = "DELETE FROM po_lines WHERE po_line_id = ?"
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
