package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.converters.Converters
import com.avago.core.`data`.db.entity.CycleCountLineEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class CycleCountLineDao_Impl(
  __db: RoomDatabase,
) : CycleCountLineDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfCycleCountLineEntity: EntityInsertAdapter<CycleCountLineEntity>

  private val __converters: Converters = Converters()
  init {
    this.__db = __db
    this.__insertAdapterOfCycleCountLineEntity = object :
        EntityInsertAdapter<CycleCountLineEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `cycle_count_lines` (`line_id`,`cycle_count_id`,`inventory_id`,`part_id`,`expected_qty`,`counted_qty`,`variance`,`is_counted`,`counted_at`,`counted_by`,`created_at`,`updated_at`,`server_version`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CycleCountLineEntity) {
        statement.bindText(1, entity.lineId)
        statement.bindText(2, entity.cycleCountId)
        statement.bindText(3, entity.inventoryId)
        val _tmpPartId: String? = entity.partId
        if (_tmpPartId == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpPartId)
        }
        val _tmpExpectedQty: Double? = entity.expectedQty
        if (_tmpExpectedQty == null) {
          statement.bindNull(5)
        } else {
          statement.bindDouble(5, _tmpExpectedQty)
        }
        val _tmpCountedQty: Double? = entity.countedQty
        if (_tmpCountedQty == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpCountedQty)
        }
        val _tmpVariance: Double? = entity.variance
        if (_tmpVariance == null) {
          statement.bindNull(7)
        } else {
          statement.bindDouble(7, _tmpVariance)
        }
        val _tmp: Int = __converters.fromBooleanToInt(entity.isCounted)
        statement.bindLong(8, _tmp.toLong())
        val _tmpCountedAt: Long? = entity.countedAt
        if (_tmpCountedAt == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpCountedAt)
        }
        val _tmpCountedBy: String? = entity.countedBy
        if (_tmpCountedBy == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpCountedBy)
        }
        statement.bindLong(11, entity.createdAt)
        statement.bindLong(12, entity.updatedAt)
        statement.bindLong(13, entity.serverVersion)
      }
    }
  }

  public override suspend fun upsert(entity: CycleCountLineEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfCycleCountLineEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<CycleCountLineEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfCycleCountLineEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<CycleCountLineEntity>> {
    val _sql: String =
        "SELECT * FROM cycle_count_lines WHERE cycle_count_id IN (SELECT cycle_count_id FROM cycle_counts WHERE account_id = ?)"
    return createFlow(__db, false, arrayOf("cycle_count_lines", "cycle_counts")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfLineId: Int = getColumnIndexOrThrow(_stmt, "line_id")
        val _columnIndexOfCycleCountId: Int = getColumnIndexOrThrow(_stmt, "cycle_count_id")
        val _columnIndexOfInventoryId: Int = getColumnIndexOrThrow(_stmt, "inventory_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfExpectedQty: Int = getColumnIndexOrThrow(_stmt, "expected_qty")
        val _columnIndexOfCountedQty: Int = getColumnIndexOrThrow(_stmt, "counted_qty")
        val _columnIndexOfVariance: Int = getColumnIndexOrThrow(_stmt, "variance")
        val _columnIndexOfIsCounted: Int = getColumnIndexOrThrow(_stmt, "is_counted")
        val _columnIndexOfCountedAt: Int = getColumnIndexOrThrow(_stmt, "counted_at")
        val _columnIndexOfCountedBy: Int = getColumnIndexOrThrow(_stmt, "counted_by")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: MutableList<CycleCountLineEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CycleCountLineEntity
          val _tmpLineId: String
          _tmpLineId = _stmt.getText(_columnIndexOfLineId)
          val _tmpCycleCountId: String
          _tmpCycleCountId = _stmt.getText(_columnIndexOfCycleCountId)
          val _tmpInventoryId: String
          _tmpInventoryId = _stmt.getText(_columnIndexOfInventoryId)
          val _tmpPartId: String?
          if (_stmt.isNull(_columnIndexOfPartId)) {
            _tmpPartId = null
          } else {
            _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          }
          val _tmpExpectedQty: Double?
          if (_stmt.isNull(_columnIndexOfExpectedQty)) {
            _tmpExpectedQty = null
          } else {
            _tmpExpectedQty = _stmt.getDouble(_columnIndexOfExpectedQty)
          }
          val _tmpCountedQty: Double?
          if (_stmt.isNull(_columnIndexOfCountedQty)) {
            _tmpCountedQty = null
          } else {
            _tmpCountedQty = _stmt.getDouble(_columnIndexOfCountedQty)
          }
          val _tmpVariance: Double?
          if (_stmt.isNull(_columnIndexOfVariance)) {
            _tmpVariance = null
          } else {
            _tmpVariance = _stmt.getDouble(_columnIndexOfVariance)
          }
          val _tmpIsCounted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCounted).toInt()
          _tmpIsCounted = __converters.fromIntToBoolean(_tmp)
          val _tmpCountedAt: Long?
          if (_stmt.isNull(_columnIndexOfCountedAt)) {
            _tmpCountedAt = null
          } else {
            _tmpCountedAt = _stmt.getLong(_columnIndexOfCountedAt)
          }
          val _tmpCountedBy: String?
          if (_stmt.isNull(_columnIndexOfCountedBy)) {
            _tmpCountedBy = null
          } else {
            _tmpCountedBy = _stmt.getText(_columnIndexOfCountedBy)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _item =
              CycleCountLineEntity(_tmpLineId,_tmpCycleCountId,_tmpInventoryId,_tmpPartId,_tmpExpectedQty,_tmpCountedQty,_tmpVariance,_tmpIsCounted,_tmpCountedAt,_tmpCountedBy,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): CycleCountLineEntity? {
    val _sql: String = "SELECT * FROM cycle_count_lines WHERE line_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfLineId: Int = getColumnIndexOrThrow(_stmt, "line_id")
        val _columnIndexOfCycleCountId: Int = getColumnIndexOrThrow(_stmt, "cycle_count_id")
        val _columnIndexOfInventoryId: Int = getColumnIndexOrThrow(_stmt, "inventory_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfExpectedQty: Int = getColumnIndexOrThrow(_stmt, "expected_qty")
        val _columnIndexOfCountedQty: Int = getColumnIndexOrThrow(_stmt, "counted_qty")
        val _columnIndexOfVariance: Int = getColumnIndexOrThrow(_stmt, "variance")
        val _columnIndexOfIsCounted: Int = getColumnIndexOrThrow(_stmt, "is_counted")
        val _columnIndexOfCountedAt: Int = getColumnIndexOrThrow(_stmt, "counted_at")
        val _columnIndexOfCountedBy: Int = getColumnIndexOrThrow(_stmt, "counted_by")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: CycleCountLineEntity?
        if (_stmt.step()) {
          val _tmpLineId: String
          _tmpLineId = _stmt.getText(_columnIndexOfLineId)
          val _tmpCycleCountId: String
          _tmpCycleCountId = _stmt.getText(_columnIndexOfCycleCountId)
          val _tmpInventoryId: String
          _tmpInventoryId = _stmt.getText(_columnIndexOfInventoryId)
          val _tmpPartId: String?
          if (_stmt.isNull(_columnIndexOfPartId)) {
            _tmpPartId = null
          } else {
            _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          }
          val _tmpExpectedQty: Double?
          if (_stmt.isNull(_columnIndexOfExpectedQty)) {
            _tmpExpectedQty = null
          } else {
            _tmpExpectedQty = _stmt.getDouble(_columnIndexOfExpectedQty)
          }
          val _tmpCountedQty: Double?
          if (_stmt.isNull(_columnIndexOfCountedQty)) {
            _tmpCountedQty = null
          } else {
            _tmpCountedQty = _stmt.getDouble(_columnIndexOfCountedQty)
          }
          val _tmpVariance: Double?
          if (_stmt.isNull(_columnIndexOfVariance)) {
            _tmpVariance = null
          } else {
            _tmpVariance = _stmt.getDouble(_columnIndexOfVariance)
          }
          val _tmpIsCounted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCounted).toInt()
          _tmpIsCounted = __converters.fromIntToBoolean(_tmp)
          val _tmpCountedAt: Long?
          if (_stmt.isNull(_columnIndexOfCountedAt)) {
            _tmpCountedAt = null
          } else {
            _tmpCountedAt = _stmt.getLong(_columnIndexOfCountedAt)
          }
          val _tmpCountedBy: String?
          if (_stmt.isNull(_columnIndexOfCountedBy)) {
            _tmpCountedBy = null
          } else {
            _tmpCountedBy = _stmt.getText(_columnIndexOfCountedBy)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _result =
              CycleCountLineEntity(_tmpLineId,_tmpCycleCountId,_tmpInventoryId,_tmpPartId,_tmpExpectedQty,_tmpCountedQty,_tmpVariance,_tmpIsCounted,_tmpCountedAt,_tmpCountedBy,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
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
    val _sql: String = "DELETE FROM cycle_count_lines WHERE line_id = ?"
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
