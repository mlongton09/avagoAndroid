package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.StockingLevelEntity
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
public class StockingLevelDao_Impl(
  __db: RoomDatabase,
) : StockingLevelDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfStockingLevelEntity: EntityInsertAdapter<StockingLevelEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfStockingLevelEntity = object : EntityInsertAdapter<StockingLevelEntity>()
        {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `stocking_levels` (`stocking_level_id`,`part_id`,`location_id`,`min_qty`,`max_qty`,`reorder_qty`,`safety_stock`,`created_at`,`updated_at`,`server_version`) VALUES (?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: StockingLevelEntity) {
        statement.bindText(1, entity.stockingLevelId)
        statement.bindText(2, entity.partId)
        statement.bindText(3, entity.locationId)
        val _tmpMinQty: Double? = entity.minQty
        if (_tmpMinQty == null) {
          statement.bindNull(4)
        } else {
          statement.bindDouble(4, _tmpMinQty)
        }
        val _tmpMaxQty: Double? = entity.maxQty
        if (_tmpMaxQty == null) {
          statement.bindNull(5)
        } else {
          statement.bindDouble(5, _tmpMaxQty)
        }
        val _tmpReorderQty: Double? = entity.reorderQty
        if (_tmpReorderQty == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpReorderQty)
        }
        val _tmpSafetyStock: Double? = entity.safetyStock
        if (_tmpSafetyStock == null) {
          statement.bindNull(7)
        } else {
          statement.bindDouble(7, _tmpSafetyStock)
        }
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
        statement.bindLong(10, entity.serverVersion)
      }
    }
  }

  public override suspend fun upsert(entity: StockingLevelEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfStockingLevelEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<StockingLevelEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfStockingLevelEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<StockingLevelEntity>> {
    val _sql: String =
        "SELECT * FROM stocking_levels WHERE part_id IN (SELECT part_id FROM parts WHERE account_id = ?)"
    return createFlow(__db, false, arrayOf("stocking_levels", "parts")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfStockingLevelId: Int = getColumnIndexOrThrow(_stmt, "stocking_level_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfMinQty: Int = getColumnIndexOrThrow(_stmt, "min_qty")
        val _columnIndexOfMaxQty: Int = getColumnIndexOrThrow(_stmt, "max_qty")
        val _columnIndexOfReorderQty: Int = getColumnIndexOrThrow(_stmt, "reorder_qty")
        val _columnIndexOfSafetyStock: Int = getColumnIndexOrThrow(_stmt, "safety_stock")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: MutableList<StockingLevelEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: StockingLevelEntity
          val _tmpStockingLevelId: String
          _tmpStockingLevelId = _stmt.getText(_columnIndexOfStockingLevelId)
          val _tmpPartId: String
          _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          val _tmpLocationId: String
          _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          val _tmpMinQty: Double?
          if (_stmt.isNull(_columnIndexOfMinQty)) {
            _tmpMinQty = null
          } else {
            _tmpMinQty = _stmt.getDouble(_columnIndexOfMinQty)
          }
          val _tmpMaxQty: Double?
          if (_stmt.isNull(_columnIndexOfMaxQty)) {
            _tmpMaxQty = null
          } else {
            _tmpMaxQty = _stmt.getDouble(_columnIndexOfMaxQty)
          }
          val _tmpReorderQty: Double?
          if (_stmt.isNull(_columnIndexOfReorderQty)) {
            _tmpReorderQty = null
          } else {
            _tmpReorderQty = _stmt.getDouble(_columnIndexOfReorderQty)
          }
          val _tmpSafetyStock: Double?
          if (_stmt.isNull(_columnIndexOfSafetyStock)) {
            _tmpSafetyStock = null
          } else {
            _tmpSafetyStock = _stmt.getDouble(_columnIndexOfSafetyStock)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _item =
              StockingLevelEntity(_tmpStockingLevelId,_tmpPartId,_tmpLocationId,_tmpMinQty,_tmpMaxQty,_tmpReorderQty,_tmpSafetyStock,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): StockingLevelEntity? {
    val _sql: String = "SELECT * FROM stocking_levels WHERE stocking_level_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfStockingLevelId: Int = getColumnIndexOrThrow(_stmt, "stocking_level_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfMinQty: Int = getColumnIndexOrThrow(_stmt, "min_qty")
        val _columnIndexOfMaxQty: Int = getColumnIndexOrThrow(_stmt, "max_qty")
        val _columnIndexOfReorderQty: Int = getColumnIndexOrThrow(_stmt, "reorder_qty")
        val _columnIndexOfSafetyStock: Int = getColumnIndexOrThrow(_stmt, "safety_stock")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: StockingLevelEntity?
        if (_stmt.step()) {
          val _tmpStockingLevelId: String
          _tmpStockingLevelId = _stmt.getText(_columnIndexOfStockingLevelId)
          val _tmpPartId: String
          _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          val _tmpLocationId: String
          _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          val _tmpMinQty: Double?
          if (_stmt.isNull(_columnIndexOfMinQty)) {
            _tmpMinQty = null
          } else {
            _tmpMinQty = _stmt.getDouble(_columnIndexOfMinQty)
          }
          val _tmpMaxQty: Double?
          if (_stmt.isNull(_columnIndexOfMaxQty)) {
            _tmpMaxQty = null
          } else {
            _tmpMaxQty = _stmt.getDouble(_columnIndexOfMaxQty)
          }
          val _tmpReorderQty: Double?
          if (_stmt.isNull(_columnIndexOfReorderQty)) {
            _tmpReorderQty = null
          } else {
            _tmpReorderQty = _stmt.getDouble(_columnIndexOfReorderQty)
          }
          val _tmpSafetyStock: Double?
          if (_stmt.isNull(_columnIndexOfSafetyStock)) {
            _tmpSafetyStock = null
          } else {
            _tmpSafetyStock = _stmt.getDouble(_columnIndexOfSafetyStock)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _result =
              StockingLevelEntity(_tmpStockingLevelId,_tmpPartId,_tmpLocationId,_tmpMinQty,_tmpMaxQty,_tmpReorderQty,_tmpSafetyStock,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
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
    val _sql: String = "DELETE FROM stocking_levels WHERE stocking_level_id = ?"
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
