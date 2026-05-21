package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.InventoryEntity
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
public class InventoryDao_Impl(
  __db: RoomDatabase,
) : InventoryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfInventoryEntity: EntityInsertAdapter<InventoryEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfInventoryEntity = object : EntityInsertAdapter<InventoryEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `inventory` (`inventory_id`,`account_id`,`part_id`,`location_id`,`quantity_on_hand`,`status`,`last_transaction_id`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: InventoryEntity) {
        statement.bindText(1, entity.inventoryId)
        statement.bindText(2, entity.accountId)
        statement.bindText(3, entity.partId)
        val _tmpLocationId: String? = entity.locationId
        if (_tmpLocationId == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpLocationId)
        }
        statement.bindDouble(5, entity.quantityOnHand)
        statement.bindText(6, entity.status)
        val _tmpLastTransactionId: String? = entity.lastTransactionId
        if (_tmpLastTransactionId == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpLastTransactionId)
        }
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(10)
        } else {
          statement.bindLong(10, _tmpDeletedAt)
        }
        statement.bindLong(11, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(12)
        } else {
          statement.bindLong(12, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: InventoryEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfInventoryEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<InventoryEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfInventoryEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<InventoryEntity>> {
    val _sql: String = "SELECT * FROM inventory WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("inventory")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfInventoryId: Int = getColumnIndexOrThrow(_stmt, "inventory_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfQuantityOnHand: Int = getColumnIndexOrThrow(_stmt, "quantity_on_hand")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfLastTransactionId: Int = getColumnIndexOrThrow(_stmt,
            "last_transaction_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<InventoryEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: InventoryEntity
          val _tmpInventoryId: String
          _tmpInventoryId = _stmt.getText(_columnIndexOfInventoryId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpPartId: String
          _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          val _tmpLocationId: String?
          if (_stmt.isNull(_columnIndexOfLocationId)) {
            _tmpLocationId = null
          } else {
            _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          }
          val _tmpQuantityOnHand: Double
          _tmpQuantityOnHand = _stmt.getDouble(_columnIndexOfQuantityOnHand)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpLastTransactionId: String?
          if (_stmt.isNull(_columnIndexOfLastTransactionId)) {
            _tmpLastTransactionId = null
          } else {
            _tmpLastTransactionId = _stmt.getText(_columnIndexOfLastTransactionId)
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
              InventoryEntity(_tmpInventoryId,_tmpAccountId,_tmpPartId,_tmpLocationId,_tmpQuantityOnHand,_tmpStatus,_tmpLastTransactionId,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): InventoryEntity? {
    val _sql: String = "SELECT * FROM inventory WHERE inventory_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfInventoryId: Int = getColumnIndexOrThrow(_stmt, "inventory_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfQuantityOnHand: Int = getColumnIndexOrThrow(_stmt, "quantity_on_hand")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfLastTransactionId: Int = getColumnIndexOrThrow(_stmt,
            "last_transaction_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: InventoryEntity?
        if (_stmt.step()) {
          val _tmpInventoryId: String
          _tmpInventoryId = _stmt.getText(_columnIndexOfInventoryId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpPartId: String
          _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          val _tmpLocationId: String?
          if (_stmt.isNull(_columnIndexOfLocationId)) {
            _tmpLocationId = null
          } else {
            _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          }
          val _tmpQuantityOnHand: Double
          _tmpQuantityOnHand = _stmt.getDouble(_columnIndexOfQuantityOnHand)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpLastTransactionId: String?
          if (_stmt.isNull(_columnIndexOfLastTransactionId)) {
            _tmpLastTransactionId = null
          } else {
            _tmpLastTransactionId = _stmt.getText(_columnIndexOfLastTransactionId)
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
              InventoryEntity(_tmpInventoryId,_tmpAccountId,_tmpPartId,_tmpLocationId,_tmpQuantityOnHand,_tmpStatus,_tmpLastTransactionId,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE inventory SET deleted_at = ?, updated_at = ? WHERE inventory_id = ?"
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
