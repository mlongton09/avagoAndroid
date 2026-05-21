package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.InventoryTransactionEntity
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
public class InventoryTransactionDao_Impl(
  __db: RoomDatabase,
) : InventoryTransactionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfInventoryTransactionEntity:
      EntityInsertAdapter<InventoryTransactionEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfInventoryTransactionEntity = object :
        EntityInsertAdapter<InventoryTransactionEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `inventory_transactions` (`transaction_id`,`account_id`,`inventory_id`,`part_id`,`location_id`,`transaction_type`,`quantity`,`unit_cost`,`currency`,`reference_id`,`reference_type`,`performed_by`,`notes`,`transfer_id`,`from_location_id`,`to_location_id`,`created_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: InventoryTransactionEntity) {
        statement.bindText(1, entity.transactionId)
        statement.bindText(2, entity.accountId)
        statement.bindText(3, entity.inventoryId)
        statement.bindText(4, entity.partId)
        val _tmpLocationId: String? = entity.locationId
        if (_tmpLocationId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpLocationId)
        }
        statement.bindText(6, entity.transactionType)
        statement.bindDouble(7, entity.quantity)
        val _tmpUnitCost: Double? = entity.unitCost
        if (_tmpUnitCost == null) {
          statement.bindNull(8)
        } else {
          statement.bindDouble(8, _tmpUnitCost)
        }
        val _tmpCurrency: String? = entity.currency
        if (_tmpCurrency == null) {
          statement.bindNull(9)
        } else {
          statement.bindText(9, _tmpCurrency)
        }
        val _tmpReferenceId: String? = entity.referenceId
        if (_tmpReferenceId == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpReferenceId)
        }
        val _tmpReferenceType: String? = entity.referenceType
        if (_tmpReferenceType == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpReferenceType)
        }
        val _tmpPerformedBy: String? = entity.performedBy
        if (_tmpPerformedBy == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpPerformedBy)
        }
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpNotes)
        }
        val _tmpTransferId: String? = entity.transferId
        if (_tmpTransferId == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpTransferId)
        }
        val _tmpFromLocationId: String? = entity.fromLocationId
        if (_tmpFromLocationId == null) {
          statement.bindNull(15)
        } else {
          statement.bindText(15, _tmpFromLocationId)
        }
        val _tmpToLocationId: String? = entity.toLocationId
        if (_tmpToLocationId == null) {
          statement.bindNull(16)
        } else {
          statement.bindText(16, _tmpToLocationId)
        }
        statement.bindLong(17, entity.createdAt)
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

  public override suspend fun upsert(entity: InventoryTransactionEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfInventoryTransactionEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<InventoryTransactionEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfInventoryTransactionEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<InventoryTransactionEntity>> {
    val _sql: String = "SELECT * FROM inventory_transactions WHERE account_id = ?"
    return createFlow(__db, false, arrayOf("inventory_transactions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfTransactionId: Int = getColumnIndexOrThrow(_stmt, "transaction_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfInventoryId: Int = getColumnIndexOrThrow(_stmt, "inventory_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfTransactionType: Int = getColumnIndexOrThrow(_stmt, "transaction_type")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfUnitCost: Int = getColumnIndexOrThrow(_stmt, "unit_cost")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfReferenceId: Int = getColumnIndexOrThrow(_stmt, "reference_id")
        val _columnIndexOfReferenceType: Int = getColumnIndexOrThrow(_stmt, "reference_type")
        val _columnIndexOfPerformedBy: Int = getColumnIndexOrThrow(_stmt, "performed_by")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfTransferId: Int = getColumnIndexOrThrow(_stmt, "transfer_id")
        val _columnIndexOfFromLocationId: Int = getColumnIndexOrThrow(_stmt, "from_location_id")
        val _columnIndexOfToLocationId: Int = getColumnIndexOrThrow(_stmt, "to_location_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<InventoryTransactionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: InventoryTransactionEntity
          val _tmpTransactionId: String
          _tmpTransactionId = _stmt.getText(_columnIndexOfTransactionId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpInventoryId: String
          _tmpInventoryId = _stmt.getText(_columnIndexOfInventoryId)
          val _tmpPartId: String
          _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          val _tmpLocationId: String?
          if (_stmt.isNull(_columnIndexOfLocationId)) {
            _tmpLocationId = null
          } else {
            _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          }
          val _tmpTransactionType: String
          _tmpTransactionType = _stmt.getText(_columnIndexOfTransactionType)
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
          val _tmpPerformedBy: String?
          if (_stmt.isNull(_columnIndexOfPerformedBy)) {
            _tmpPerformedBy = null
          } else {
            _tmpPerformedBy = _stmt.getText(_columnIndexOfPerformedBy)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpTransferId: String?
          if (_stmt.isNull(_columnIndexOfTransferId)) {
            _tmpTransferId = null
          } else {
            _tmpTransferId = _stmt.getText(_columnIndexOfTransferId)
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
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _item =
              InventoryTransactionEntity(_tmpTransactionId,_tmpAccountId,_tmpInventoryId,_tmpPartId,_tmpLocationId,_tmpTransactionType,_tmpQuantity,_tmpUnitCost,_tmpCurrency,_tmpReferenceId,_tmpReferenceType,_tmpPerformedBy,_tmpNotes,_tmpTransferId,_tmpFromLocationId,_tmpToLocationId,_tmpCreatedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): InventoryTransactionEntity? {
    val _sql: String = "SELECT * FROM inventory_transactions WHERE transaction_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfTransactionId: Int = getColumnIndexOrThrow(_stmt, "transaction_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfInventoryId: Int = getColumnIndexOrThrow(_stmt, "inventory_id")
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfTransactionType: Int = getColumnIndexOrThrow(_stmt, "transaction_type")
        val _columnIndexOfQuantity: Int = getColumnIndexOrThrow(_stmt, "quantity")
        val _columnIndexOfUnitCost: Int = getColumnIndexOrThrow(_stmt, "unit_cost")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfReferenceId: Int = getColumnIndexOrThrow(_stmt, "reference_id")
        val _columnIndexOfReferenceType: Int = getColumnIndexOrThrow(_stmt, "reference_type")
        val _columnIndexOfPerformedBy: Int = getColumnIndexOrThrow(_stmt, "performed_by")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfTransferId: Int = getColumnIndexOrThrow(_stmt, "transfer_id")
        val _columnIndexOfFromLocationId: Int = getColumnIndexOrThrow(_stmt, "from_location_id")
        val _columnIndexOfToLocationId: Int = getColumnIndexOrThrow(_stmt, "to_location_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: InventoryTransactionEntity?
        if (_stmt.step()) {
          val _tmpTransactionId: String
          _tmpTransactionId = _stmt.getText(_columnIndexOfTransactionId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpInventoryId: String
          _tmpInventoryId = _stmt.getText(_columnIndexOfInventoryId)
          val _tmpPartId: String
          _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          val _tmpLocationId: String?
          if (_stmt.isNull(_columnIndexOfLocationId)) {
            _tmpLocationId = null
          } else {
            _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          }
          val _tmpTransactionType: String
          _tmpTransactionType = _stmt.getText(_columnIndexOfTransactionType)
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
          val _tmpPerformedBy: String?
          if (_stmt.isNull(_columnIndexOfPerformedBy)) {
            _tmpPerformedBy = null
          } else {
            _tmpPerformedBy = _stmt.getText(_columnIndexOfPerformedBy)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpTransferId: String?
          if (_stmt.isNull(_columnIndexOfTransferId)) {
            _tmpTransferId = null
          } else {
            _tmpTransferId = _stmt.getText(_columnIndexOfTransferId)
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
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _result =
              InventoryTransactionEntity(_tmpTransactionId,_tmpAccountId,_tmpInventoryId,_tmpPartId,_tmpLocationId,_tmpTransactionType,_tmpQuantity,_tmpUnitCost,_tmpCurrency,_tmpReferenceId,_tmpReferenceType,_tmpPerformedBy,_tmpNotes,_tmpTransferId,_tmpFromLocationId,_tmpToLocationId,_tmpCreatedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "DELETE FROM inventory_transactions WHERE transaction_id = ?"
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
