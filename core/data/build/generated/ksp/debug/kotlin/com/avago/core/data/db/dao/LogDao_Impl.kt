package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.LogEntity
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
public class LogDao_Impl(
  __db: RoomDatabase,
) : LogDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfLogEntity: EntityInsertAdapter<LogEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfLogEntity = object : EntityInsertAdapter<LogEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `log` (`entry_id`,`asset_id`,`account_id`,`title`,`entry_date`,`odometer_value`,`category`,`cost`,`performed_by`,`performed_by_user_id`,`notes`,`data`,`attributes`,`cost_mode`,`cost_items`,`cost_labor`,`cost_tax`,`currency`,`base_amount`,`exchange_rate_used`,`config_id`,`config_version`,`parent_id`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LogEntity) {
        statement.bindText(1, entity.entryId)
        statement.bindText(2, entity.assetId)
        statement.bindText(3, entity.accountId)
        statement.bindText(4, entity.title)
        statement.bindLong(5, entity.entryDate)
        val _tmpOdometerValue: Double? = entity.odometerValue
        if (_tmpOdometerValue == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpOdometerValue)
        }
        val _tmpCategory: String? = entity.category
        if (_tmpCategory == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpCategory)
        }
        val _tmpCost: Double? = entity.cost
        if (_tmpCost == null) {
          statement.bindNull(8)
        } else {
          statement.bindDouble(8, _tmpCost)
        }
        val _tmpPerformedBy: String? = entity.performedBy
        if (_tmpPerformedBy == null) {
          statement.bindNull(9)
        } else {
          statement.bindText(9, _tmpPerformedBy)
        }
        val _tmpPerformedByUserId: String? = entity.performedByUserId
        if (_tmpPerformedByUserId == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpPerformedByUserId)
        }
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpNotes)
        }
        val _tmpData: String? = entity.data
        if (_tmpData == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpData)
        }
        val _tmpAttributes: String? = entity.attributes
        if (_tmpAttributes == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpAttributes)
        }
        val _tmpCostMode: String? = entity.costMode
        if (_tmpCostMode == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpCostMode)
        }
        val _tmpCostItems: Double? = entity.costItems
        if (_tmpCostItems == null) {
          statement.bindNull(15)
        } else {
          statement.bindDouble(15, _tmpCostItems)
        }
        val _tmpCostLabor: Double? = entity.costLabor
        if (_tmpCostLabor == null) {
          statement.bindNull(16)
        } else {
          statement.bindDouble(16, _tmpCostLabor)
        }
        val _tmpCostTax: Double? = entity.costTax
        if (_tmpCostTax == null) {
          statement.bindNull(17)
        } else {
          statement.bindDouble(17, _tmpCostTax)
        }
        val _tmpCurrency: String? = entity.currency
        if (_tmpCurrency == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpCurrency)
        }
        val _tmpBaseAmount: Double? = entity.baseAmount
        if (_tmpBaseAmount == null) {
          statement.bindNull(19)
        } else {
          statement.bindDouble(19, _tmpBaseAmount)
        }
        val _tmpExchangeRateUsed: Double? = entity.exchangeRateUsed
        if (_tmpExchangeRateUsed == null) {
          statement.bindNull(20)
        } else {
          statement.bindDouble(20, _tmpExchangeRateUsed)
        }
        val _tmpConfigId: String? = entity.configId
        if (_tmpConfigId == null) {
          statement.bindNull(21)
        } else {
          statement.bindText(21, _tmpConfigId)
        }
        val _tmpConfigVersion: Long? = entity.configVersion
        if (_tmpConfigVersion == null) {
          statement.bindNull(22)
        } else {
          statement.bindLong(22, _tmpConfigVersion)
        }
        val _tmpParentId: String? = entity.parentId
        if (_tmpParentId == null) {
          statement.bindNull(23)
        } else {
          statement.bindText(23, _tmpParentId)
        }
        statement.bindLong(24, entity.createdAt)
        statement.bindLong(25, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(26)
        } else {
          statement.bindLong(26, _tmpDeletedAt)
        }
        statement.bindLong(27, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(28)
        } else {
          statement.bindLong(28, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: LogEntity): Unit = performSuspending(__db, false, true)
      { _connection ->
    __insertAdapterOfLogEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<LogEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfLogEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<LogEntity>> {
    val _sql: String = "SELECT * FROM log WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("log")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfEntryId: Int = getColumnIndexOrThrow(_stmt, "entry_id")
        val _columnIndexOfAssetId: Int = getColumnIndexOrThrow(_stmt, "asset_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfEntryDate: Int = getColumnIndexOrThrow(_stmt, "entry_date")
        val _columnIndexOfOdometerValue: Int = getColumnIndexOrThrow(_stmt, "odometer_value")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfCost: Int = getColumnIndexOrThrow(_stmt, "cost")
        val _columnIndexOfPerformedBy: Int = getColumnIndexOrThrow(_stmt, "performed_by")
        val _columnIndexOfPerformedByUserId: Int = getColumnIndexOrThrow(_stmt,
            "performed_by_user_id")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfData: Int = getColumnIndexOrThrow(_stmt, "data")
        val _columnIndexOfAttributes: Int = getColumnIndexOrThrow(_stmt, "attributes")
        val _columnIndexOfCostMode: Int = getColumnIndexOrThrow(_stmt, "cost_mode")
        val _columnIndexOfCostItems: Int = getColumnIndexOrThrow(_stmt, "cost_items")
        val _columnIndexOfCostLabor: Int = getColumnIndexOrThrow(_stmt, "cost_labor")
        val _columnIndexOfCostTax: Int = getColumnIndexOrThrow(_stmt, "cost_tax")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfBaseAmount: Int = getColumnIndexOrThrow(_stmt, "base_amount")
        val _columnIndexOfExchangeRateUsed: Int = getColumnIndexOrThrow(_stmt, "exchange_rate_used")
        val _columnIndexOfConfigId: Int = getColumnIndexOrThrow(_stmt, "config_id")
        val _columnIndexOfConfigVersion: Int = getColumnIndexOrThrow(_stmt, "config_version")
        val _columnIndexOfParentId: Int = getColumnIndexOrThrow(_stmt, "parent_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<LogEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: LogEntity
          val _tmpEntryId: String
          _tmpEntryId = _stmt.getText(_columnIndexOfEntryId)
          val _tmpAssetId: String
          _tmpAssetId = _stmt.getText(_columnIndexOfAssetId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpEntryDate: Long
          _tmpEntryDate = _stmt.getLong(_columnIndexOfEntryDate)
          val _tmpOdometerValue: Double?
          if (_stmt.isNull(_columnIndexOfOdometerValue)) {
            _tmpOdometerValue = null
          } else {
            _tmpOdometerValue = _stmt.getDouble(_columnIndexOfOdometerValue)
          }
          val _tmpCategory: String?
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _tmpCategory = null
          } else {
            _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          }
          val _tmpCost: Double?
          if (_stmt.isNull(_columnIndexOfCost)) {
            _tmpCost = null
          } else {
            _tmpCost = _stmt.getDouble(_columnIndexOfCost)
          }
          val _tmpPerformedBy: String?
          if (_stmt.isNull(_columnIndexOfPerformedBy)) {
            _tmpPerformedBy = null
          } else {
            _tmpPerformedBy = _stmt.getText(_columnIndexOfPerformedBy)
          }
          val _tmpPerformedByUserId: String?
          if (_stmt.isNull(_columnIndexOfPerformedByUserId)) {
            _tmpPerformedByUserId = null
          } else {
            _tmpPerformedByUserId = _stmt.getText(_columnIndexOfPerformedByUserId)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpData: String?
          if (_stmt.isNull(_columnIndexOfData)) {
            _tmpData = null
          } else {
            _tmpData = _stmt.getText(_columnIndexOfData)
          }
          val _tmpAttributes: String?
          if (_stmt.isNull(_columnIndexOfAttributes)) {
            _tmpAttributes = null
          } else {
            _tmpAttributes = _stmt.getText(_columnIndexOfAttributes)
          }
          val _tmpCostMode: String?
          if (_stmt.isNull(_columnIndexOfCostMode)) {
            _tmpCostMode = null
          } else {
            _tmpCostMode = _stmt.getText(_columnIndexOfCostMode)
          }
          val _tmpCostItems: Double?
          if (_stmt.isNull(_columnIndexOfCostItems)) {
            _tmpCostItems = null
          } else {
            _tmpCostItems = _stmt.getDouble(_columnIndexOfCostItems)
          }
          val _tmpCostLabor: Double?
          if (_stmt.isNull(_columnIndexOfCostLabor)) {
            _tmpCostLabor = null
          } else {
            _tmpCostLabor = _stmt.getDouble(_columnIndexOfCostLabor)
          }
          val _tmpCostTax: Double?
          if (_stmt.isNull(_columnIndexOfCostTax)) {
            _tmpCostTax = null
          } else {
            _tmpCostTax = _stmt.getDouble(_columnIndexOfCostTax)
          }
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpBaseAmount: Double?
          if (_stmt.isNull(_columnIndexOfBaseAmount)) {
            _tmpBaseAmount = null
          } else {
            _tmpBaseAmount = _stmt.getDouble(_columnIndexOfBaseAmount)
          }
          val _tmpExchangeRateUsed: Double?
          if (_stmt.isNull(_columnIndexOfExchangeRateUsed)) {
            _tmpExchangeRateUsed = null
          } else {
            _tmpExchangeRateUsed = _stmt.getDouble(_columnIndexOfExchangeRateUsed)
          }
          val _tmpConfigId: String?
          if (_stmt.isNull(_columnIndexOfConfigId)) {
            _tmpConfigId = null
          } else {
            _tmpConfigId = _stmt.getText(_columnIndexOfConfigId)
          }
          val _tmpConfigVersion: Long?
          if (_stmt.isNull(_columnIndexOfConfigVersion)) {
            _tmpConfigVersion = null
          } else {
            _tmpConfigVersion = _stmt.getLong(_columnIndexOfConfigVersion)
          }
          val _tmpParentId: String?
          if (_stmt.isNull(_columnIndexOfParentId)) {
            _tmpParentId = null
          } else {
            _tmpParentId = _stmt.getText(_columnIndexOfParentId)
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
              LogEntity(_tmpEntryId,_tmpAssetId,_tmpAccountId,_tmpTitle,_tmpEntryDate,_tmpOdometerValue,_tmpCategory,_tmpCost,_tmpPerformedBy,_tmpPerformedByUserId,_tmpNotes,_tmpData,_tmpAttributes,_tmpCostMode,_tmpCostItems,_tmpCostLabor,_tmpCostTax,_tmpCurrency,_tmpBaseAmount,_tmpExchangeRateUsed,_tmpConfigId,_tmpConfigVersion,_tmpParentId,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): LogEntity? {
    val _sql: String = "SELECT * FROM log WHERE entry_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfEntryId: Int = getColumnIndexOrThrow(_stmt, "entry_id")
        val _columnIndexOfAssetId: Int = getColumnIndexOrThrow(_stmt, "asset_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfEntryDate: Int = getColumnIndexOrThrow(_stmt, "entry_date")
        val _columnIndexOfOdometerValue: Int = getColumnIndexOrThrow(_stmt, "odometer_value")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfCost: Int = getColumnIndexOrThrow(_stmt, "cost")
        val _columnIndexOfPerformedBy: Int = getColumnIndexOrThrow(_stmt, "performed_by")
        val _columnIndexOfPerformedByUserId: Int = getColumnIndexOrThrow(_stmt,
            "performed_by_user_id")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfData: Int = getColumnIndexOrThrow(_stmt, "data")
        val _columnIndexOfAttributes: Int = getColumnIndexOrThrow(_stmt, "attributes")
        val _columnIndexOfCostMode: Int = getColumnIndexOrThrow(_stmt, "cost_mode")
        val _columnIndexOfCostItems: Int = getColumnIndexOrThrow(_stmt, "cost_items")
        val _columnIndexOfCostLabor: Int = getColumnIndexOrThrow(_stmt, "cost_labor")
        val _columnIndexOfCostTax: Int = getColumnIndexOrThrow(_stmt, "cost_tax")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfBaseAmount: Int = getColumnIndexOrThrow(_stmt, "base_amount")
        val _columnIndexOfExchangeRateUsed: Int = getColumnIndexOrThrow(_stmt, "exchange_rate_used")
        val _columnIndexOfConfigId: Int = getColumnIndexOrThrow(_stmt, "config_id")
        val _columnIndexOfConfigVersion: Int = getColumnIndexOrThrow(_stmt, "config_version")
        val _columnIndexOfParentId: Int = getColumnIndexOrThrow(_stmt, "parent_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: LogEntity?
        if (_stmt.step()) {
          val _tmpEntryId: String
          _tmpEntryId = _stmt.getText(_columnIndexOfEntryId)
          val _tmpAssetId: String
          _tmpAssetId = _stmt.getText(_columnIndexOfAssetId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpEntryDate: Long
          _tmpEntryDate = _stmt.getLong(_columnIndexOfEntryDate)
          val _tmpOdometerValue: Double?
          if (_stmt.isNull(_columnIndexOfOdometerValue)) {
            _tmpOdometerValue = null
          } else {
            _tmpOdometerValue = _stmt.getDouble(_columnIndexOfOdometerValue)
          }
          val _tmpCategory: String?
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _tmpCategory = null
          } else {
            _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          }
          val _tmpCost: Double?
          if (_stmt.isNull(_columnIndexOfCost)) {
            _tmpCost = null
          } else {
            _tmpCost = _stmt.getDouble(_columnIndexOfCost)
          }
          val _tmpPerformedBy: String?
          if (_stmt.isNull(_columnIndexOfPerformedBy)) {
            _tmpPerformedBy = null
          } else {
            _tmpPerformedBy = _stmt.getText(_columnIndexOfPerformedBy)
          }
          val _tmpPerformedByUserId: String?
          if (_stmt.isNull(_columnIndexOfPerformedByUserId)) {
            _tmpPerformedByUserId = null
          } else {
            _tmpPerformedByUserId = _stmt.getText(_columnIndexOfPerformedByUserId)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpData: String?
          if (_stmt.isNull(_columnIndexOfData)) {
            _tmpData = null
          } else {
            _tmpData = _stmt.getText(_columnIndexOfData)
          }
          val _tmpAttributes: String?
          if (_stmt.isNull(_columnIndexOfAttributes)) {
            _tmpAttributes = null
          } else {
            _tmpAttributes = _stmt.getText(_columnIndexOfAttributes)
          }
          val _tmpCostMode: String?
          if (_stmt.isNull(_columnIndexOfCostMode)) {
            _tmpCostMode = null
          } else {
            _tmpCostMode = _stmt.getText(_columnIndexOfCostMode)
          }
          val _tmpCostItems: Double?
          if (_stmt.isNull(_columnIndexOfCostItems)) {
            _tmpCostItems = null
          } else {
            _tmpCostItems = _stmt.getDouble(_columnIndexOfCostItems)
          }
          val _tmpCostLabor: Double?
          if (_stmt.isNull(_columnIndexOfCostLabor)) {
            _tmpCostLabor = null
          } else {
            _tmpCostLabor = _stmt.getDouble(_columnIndexOfCostLabor)
          }
          val _tmpCostTax: Double?
          if (_stmt.isNull(_columnIndexOfCostTax)) {
            _tmpCostTax = null
          } else {
            _tmpCostTax = _stmt.getDouble(_columnIndexOfCostTax)
          }
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpBaseAmount: Double?
          if (_stmt.isNull(_columnIndexOfBaseAmount)) {
            _tmpBaseAmount = null
          } else {
            _tmpBaseAmount = _stmt.getDouble(_columnIndexOfBaseAmount)
          }
          val _tmpExchangeRateUsed: Double?
          if (_stmt.isNull(_columnIndexOfExchangeRateUsed)) {
            _tmpExchangeRateUsed = null
          } else {
            _tmpExchangeRateUsed = _stmt.getDouble(_columnIndexOfExchangeRateUsed)
          }
          val _tmpConfigId: String?
          if (_stmt.isNull(_columnIndexOfConfigId)) {
            _tmpConfigId = null
          } else {
            _tmpConfigId = _stmt.getText(_columnIndexOfConfigId)
          }
          val _tmpConfigVersion: Long?
          if (_stmt.isNull(_columnIndexOfConfigVersion)) {
            _tmpConfigVersion = null
          } else {
            _tmpConfigVersion = _stmt.getLong(_columnIndexOfConfigVersion)
          }
          val _tmpParentId: String?
          if (_stmt.isNull(_columnIndexOfParentId)) {
            _tmpParentId = null
          } else {
            _tmpParentId = _stmt.getText(_columnIndexOfParentId)
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
              LogEntity(_tmpEntryId,_tmpAssetId,_tmpAccountId,_tmpTitle,_tmpEntryDate,_tmpOdometerValue,_tmpCategory,_tmpCost,_tmpPerformedBy,_tmpPerformedByUserId,_tmpNotes,_tmpData,_tmpAttributes,_tmpCostMode,_tmpCostItems,_tmpCostLabor,_tmpCostTax,_tmpCurrency,_tmpBaseAmount,_tmpExchangeRateUsed,_tmpConfigId,_tmpConfigVersion,_tmpParentId,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE log SET deleted_at = ?, updated_at = ? WHERE entry_id = ?"
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
