package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.GrnLineEntity
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
public class GrnLineDao_Impl(
  __db: RoomDatabase,
) : GrnLineDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfGrnLineEntity: EntityInsertAdapter<GrnLineEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfGrnLineEntity = object : EntityInsertAdapter<GrnLineEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `grn_lines` (`grn_line_id`,`grn_id`,`po_line_id`,`part_id`,`quantity_received`,`quantity_expected`,`variance_reason`,`notes`,`created_at`,`updated_at`,`server_version`) VALUES (?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: GrnLineEntity) {
        statement.bindText(1, entity.grnLineId)
        statement.bindText(2, entity.grnId)
        val _tmpPoLineId: String? = entity.poLineId
        if (_tmpPoLineId == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpPoLineId)
        }
        val _tmpPartId: String? = entity.partId
        if (_tmpPartId == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpPartId)
        }
        statement.bindDouble(5, entity.quantityReceived)
        val _tmpQuantityExpected: Double? = entity.quantityExpected
        if (_tmpQuantityExpected == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpQuantityExpected)
        }
        val _tmpVarianceReason: String? = entity.varianceReason
        if (_tmpVarianceReason == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpVarianceReason)
        }
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpNotes)
        }
        statement.bindLong(9, entity.createdAt)
        statement.bindLong(10, entity.updatedAt)
        statement.bindLong(11, entity.serverVersion)
      }
    }
  }

  public override suspend fun upsert(entity: GrnLineEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfGrnLineEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<GrnLineEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfGrnLineEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<GrnLineEntity>> {
    val _sql: String =
        "SELECT * FROM grn_lines WHERE grn_id IN (SELECT grn_id FROM grns WHERE account_id = ?)"
    return createFlow(__db, false, arrayOf("grn_lines", "grns")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfGrnLineId: Int = getColumnIndexOrThrow(_stmt, "grn_line_id")
        val _columnIndexOfGrnId: Int = getColumnIndexOrThrow(_stmt, "grn_id")
        val _columnIndexOfPoLineId: Int = getColumnIndexOrThrow(_stmt, "po_line_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfQuantityReceived: Int = getColumnIndexOrThrow(_stmt, "quantity_received")
        val _columnIndexOfQuantityExpected: Int = getColumnIndexOrThrow(_stmt, "quantity_expected")
        val _columnIndexOfVarianceReason: Int = getColumnIndexOrThrow(_stmt, "variance_reason")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: MutableList<GrnLineEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: GrnLineEntity
          val _tmpGrnLineId: String
          _tmpGrnLineId = _stmt.getText(_columnIndexOfGrnLineId)
          val _tmpGrnId: String
          _tmpGrnId = _stmt.getText(_columnIndexOfGrnId)
          val _tmpPoLineId: String?
          if (_stmt.isNull(_columnIndexOfPoLineId)) {
            _tmpPoLineId = null
          } else {
            _tmpPoLineId = _stmt.getText(_columnIndexOfPoLineId)
          }
          val _tmpPartId: String?
          if (_stmt.isNull(_columnIndexOfPartId)) {
            _tmpPartId = null
          } else {
            _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          }
          val _tmpQuantityReceived: Double
          _tmpQuantityReceived = _stmt.getDouble(_columnIndexOfQuantityReceived)
          val _tmpQuantityExpected: Double?
          if (_stmt.isNull(_columnIndexOfQuantityExpected)) {
            _tmpQuantityExpected = null
          } else {
            _tmpQuantityExpected = _stmt.getDouble(_columnIndexOfQuantityExpected)
          }
          val _tmpVarianceReason: String?
          if (_stmt.isNull(_columnIndexOfVarianceReason)) {
            _tmpVarianceReason = null
          } else {
            _tmpVarianceReason = _stmt.getText(_columnIndexOfVarianceReason)
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
              GrnLineEntity(_tmpGrnLineId,_tmpGrnId,_tmpPoLineId,_tmpPartId,_tmpQuantityReceived,_tmpQuantityExpected,_tmpVarianceReason,_tmpNotes,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): GrnLineEntity? {
    val _sql: String = "SELECT * FROM grn_lines WHERE grn_line_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfGrnLineId: Int = getColumnIndexOrThrow(_stmt, "grn_line_id")
        val _columnIndexOfGrnId: Int = getColumnIndexOrThrow(_stmt, "grn_id")
        val _columnIndexOfPoLineId: Int = getColumnIndexOrThrow(_stmt, "po_line_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfQuantityReceived: Int = getColumnIndexOrThrow(_stmt, "quantity_received")
        val _columnIndexOfQuantityExpected: Int = getColumnIndexOrThrow(_stmt, "quantity_expected")
        val _columnIndexOfVarianceReason: Int = getColumnIndexOrThrow(_stmt, "variance_reason")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: GrnLineEntity?
        if (_stmt.step()) {
          val _tmpGrnLineId: String
          _tmpGrnLineId = _stmt.getText(_columnIndexOfGrnLineId)
          val _tmpGrnId: String
          _tmpGrnId = _stmt.getText(_columnIndexOfGrnId)
          val _tmpPoLineId: String?
          if (_stmt.isNull(_columnIndexOfPoLineId)) {
            _tmpPoLineId = null
          } else {
            _tmpPoLineId = _stmt.getText(_columnIndexOfPoLineId)
          }
          val _tmpPartId: String?
          if (_stmt.isNull(_columnIndexOfPartId)) {
            _tmpPartId = null
          } else {
            _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          }
          val _tmpQuantityReceived: Double
          _tmpQuantityReceived = _stmt.getDouble(_columnIndexOfQuantityReceived)
          val _tmpQuantityExpected: Double?
          if (_stmt.isNull(_columnIndexOfQuantityExpected)) {
            _tmpQuantityExpected = null
          } else {
            _tmpQuantityExpected = _stmt.getDouble(_columnIndexOfQuantityExpected)
          }
          val _tmpVarianceReason: String?
          if (_stmt.isNull(_columnIndexOfVarianceReason)) {
            _tmpVarianceReason = null
          } else {
            _tmpVarianceReason = _stmt.getText(_columnIndexOfVarianceReason)
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
              GrnLineEntity(_tmpGrnLineId,_tmpGrnId,_tmpPoLineId,_tmpPartId,_tmpQuantityReceived,_tmpQuantityExpected,_tmpVarianceReason,_tmpNotes,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
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
    val _sql: String = "DELETE FROM grn_lines WHERE grn_line_id = ?"
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
