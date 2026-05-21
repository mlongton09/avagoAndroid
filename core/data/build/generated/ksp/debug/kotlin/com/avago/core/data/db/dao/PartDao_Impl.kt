package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.PartEntity
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
public class PartDao_Impl(
  __db: RoomDatabase,
) : PartDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPartEntity: EntityInsertAdapter<PartEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPartEntity = object : EntityInsertAdapter<PartEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `parts` (`part_id`,`account_id`,`sku`,`name`,`description`,`category`,`unit_of_measure`,`default_vendor_id`,`cost`,`currency`,`attributes`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PartEntity) {
        statement.bindText(1, entity.partId)
        statement.bindText(2, entity.accountId)
        val _tmpSku: String? = entity.sku
        if (_tmpSku == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpSku)
        }
        statement.bindText(4, entity.name)
        val _tmpDescription: String? = entity.description
        if (_tmpDescription == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpDescription)
        }
        val _tmpCategory: String? = entity.category
        if (_tmpCategory == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpCategory)
        }
        val _tmpUnitOfMeasure: String? = entity.unitOfMeasure
        if (_tmpUnitOfMeasure == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpUnitOfMeasure)
        }
        val _tmpDefaultVendorId: String? = entity.defaultVendorId
        if (_tmpDefaultVendorId == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpDefaultVendorId)
        }
        val _tmpCost: Double? = entity.cost
        if (_tmpCost == null) {
          statement.bindNull(9)
        } else {
          statement.bindDouble(9, _tmpCost)
        }
        val _tmpCurrency: String? = entity.currency
        if (_tmpCurrency == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpCurrency)
        }
        val _tmpAttributes: String? = entity.attributes
        if (_tmpAttributes == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpAttributes)
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

  public override suspend fun upsert(entity: PartEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfPartEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<PartEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfPartEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<PartEntity>> {
    val _sql: String = "SELECT * FROM parts WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("parts")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfSku: Int = getColumnIndexOrThrow(_stmt, "sku")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfUnitOfMeasure: Int = getColumnIndexOrThrow(_stmt, "unit_of_measure")
        val _columnIndexOfDefaultVendorId: Int = getColumnIndexOrThrow(_stmt, "default_vendor_id")
        val _columnIndexOfCost: Int = getColumnIndexOrThrow(_stmt, "cost")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfAttributes: Int = getColumnIndexOrThrow(_stmt, "attributes")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<PartEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PartEntity
          val _tmpPartId: String
          _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpSku: String?
          if (_stmt.isNull(_columnIndexOfSku)) {
            _tmpSku = null
          } else {
            _tmpSku = _stmt.getText(_columnIndexOfSku)
          }
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpCategory: String?
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _tmpCategory = null
          } else {
            _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          }
          val _tmpUnitOfMeasure: String?
          if (_stmt.isNull(_columnIndexOfUnitOfMeasure)) {
            _tmpUnitOfMeasure = null
          } else {
            _tmpUnitOfMeasure = _stmt.getText(_columnIndexOfUnitOfMeasure)
          }
          val _tmpDefaultVendorId: String?
          if (_stmt.isNull(_columnIndexOfDefaultVendorId)) {
            _tmpDefaultVendorId = null
          } else {
            _tmpDefaultVendorId = _stmt.getText(_columnIndexOfDefaultVendorId)
          }
          val _tmpCost: Double?
          if (_stmt.isNull(_columnIndexOfCost)) {
            _tmpCost = null
          } else {
            _tmpCost = _stmt.getDouble(_columnIndexOfCost)
          }
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpAttributes: String?
          if (_stmt.isNull(_columnIndexOfAttributes)) {
            _tmpAttributes = null
          } else {
            _tmpAttributes = _stmt.getText(_columnIndexOfAttributes)
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
              PartEntity(_tmpPartId,_tmpAccountId,_tmpSku,_tmpName,_tmpDescription,_tmpCategory,_tmpUnitOfMeasure,_tmpDefaultVendorId,_tmpCost,_tmpCurrency,_tmpAttributes,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): PartEntity? {
    val _sql: String = "SELECT * FROM parts WHERE part_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfPartId: Int = getColumnIndexOrThrow(_stmt, "part_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfSku: Int = getColumnIndexOrThrow(_stmt, "sku")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfUnitOfMeasure: Int = getColumnIndexOrThrow(_stmt, "unit_of_measure")
        val _columnIndexOfDefaultVendorId: Int = getColumnIndexOrThrow(_stmt, "default_vendor_id")
        val _columnIndexOfCost: Int = getColumnIndexOrThrow(_stmt, "cost")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfAttributes: Int = getColumnIndexOrThrow(_stmt, "attributes")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: PartEntity?
        if (_stmt.step()) {
          val _tmpPartId: String
          _tmpPartId = _stmt.getText(_columnIndexOfPartId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpSku: String?
          if (_stmt.isNull(_columnIndexOfSku)) {
            _tmpSku = null
          } else {
            _tmpSku = _stmt.getText(_columnIndexOfSku)
          }
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpCategory: String?
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _tmpCategory = null
          } else {
            _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          }
          val _tmpUnitOfMeasure: String?
          if (_stmt.isNull(_columnIndexOfUnitOfMeasure)) {
            _tmpUnitOfMeasure = null
          } else {
            _tmpUnitOfMeasure = _stmt.getText(_columnIndexOfUnitOfMeasure)
          }
          val _tmpDefaultVendorId: String?
          if (_stmt.isNull(_columnIndexOfDefaultVendorId)) {
            _tmpDefaultVendorId = null
          } else {
            _tmpDefaultVendorId = _stmt.getText(_columnIndexOfDefaultVendorId)
          }
          val _tmpCost: Double?
          if (_stmt.isNull(_columnIndexOfCost)) {
            _tmpCost = null
          } else {
            _tmpCost = _stmt.getDouble(_columnIndexOfCost)
          }
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpAttributes: String?
          if (_stmt.isNull(_columnIndexOfAttributes)) {
            _tmpAttributes = null
          } else {
            _tmpAttributes = _stmt.getText(_columnIndexOfAttributes)
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
              PartEntity(_tmpPartId,_tmpAccountId,_tmpSku,_tmpName,_tmpDescription,_tmpCategory,_tmpUnitOfMeasure,_tmpDefaultVendorId,_tmpCost,_tmpCurrency,_tmpAttributes,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE parts SET deleted_at = ?, updated_at = ? WHERE part_id = ?"
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
