package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.TechLaborRateEntity
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
public class TechLaborRateDao_Impl(
  __db: RoomDatabase,
) : TechLaborRateDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfTechLaborRateEntity: EntityInsertAdapter<TechLaborRateEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfTechLaborRateEntity = object : EntityInsertAdapter<TechLaborRateEntity>()
        {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `tech_labor_rates` (`rate_id`,`tech_id`,`account_id`,`role_key`,`hourly_rate`,`currency`,`effective_date`,`created_at`,`updated_at`,`server_version`) VALUES (?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: TechLaborRateEntity) {
        statement.bindText(1, entity.rateId)
        statement.bindText(2, entity.techId)
        statement.bindText(3, entity.accountId)
        val _tmpRoleKey: String? = entity.roleKey
        if (_tmpRoleKey == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpRoleKey)
        }
        statement.bindDouble(5, entity.hourlyRate)
        statement.bindText(6, entity.currency)
        val _tmpEffectiveDate: Long? = entity.effectiveDate
        if (_tmpEffectiveDate == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpEffectiveDate)
        }
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
        statement.bindLong(10, entity.serverVersion)
      }
    }
  }

  public override suspend fun upsert(entity: TechLaborRateEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfTechLaborRateEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<TechLaborRateEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfTechLaborRateEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<TechLaborRateEntity>> {
    val _sql: String = "SELECT * FROM tech_labor_rates WHERE account_id = ?"
    return createFlow(__db, false, arrayOf("tech_labor_rates")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfRateId: Int = getColumnIndexOrThrow(_stmt, "rate_id")
        val _columnIndexOfTechId: Int = getColumnIndexOrThrow(_stmt, "tech_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfRoleKey: Int = getColumnIndexOrThrow(_stmt, "role_key")
        val _columnIndexOfHourlyRate: Int = getColumnIndexOrThrow(_stmt, "hourly_rate")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfEffectiveDate: Int = getColumnIndexOrThrow(_stmt, "effective_date")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: MutableList<TechLaborRateEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TechLaborRateEntity
          val _tmpRateId: String
          _tmpRateId = _stmt.getText(_columnIndexOfRateId)
          val _tmpTechId: String
          _tmpTechId = _stmt.getText(_columnIndexOfTechId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpRoleKey: String?
          if (_stmt.isNull(_columnIndexOfRoleKey)) {
            _tmpRoleKey = null
          } else {
            _tmpRoleKey = _stmt.getText(_columnIndexOfRoleKey)
          }
          val _tmpHourlyRate: Double
          _tmpHourlyRate = _stmt.getDouble(_columnIndexOfHourlyRate)
          val _tmpCurrency: String
          _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          val _tmpEffectiveDate: Long?
          if (_stmt.isNull(_columnIndexOfEffectiveDate)) {
            _tmpEffectiveDate = null
          } else {
            _tmpEffectiveDate = _stmt.getLong(_columnIndexOfEffectiveDate)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _item =
              TechLaborRateEntity(_tmpRateId,_tmpTechId,_tmpAccountId,_tmpRoleKey,_tmpHourlyRate,_tmpCurrency,_tmpEffectiveDate,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): TechLaborRateEntity? {
    val _sql: String = "SELECT * FROM tech_labor_rates WHERE rate_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfRateId: Int = getColumnIndexOrThrow(_stmt, "rate_id")
        val _columnIndexOfTechId: Int = getColumnIndexOrThrow(_stmt, "tech_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfRoleKey: Int = getColumnIndexOrThrow(_stmt, "role_key")
        val _columnIndexOfHourlyRate: Int = getColumnIndexOrThrow(_stmt, "hourly_rate")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfEffectiveDate: Int = getColumnIndexOrThrow(_stmt, "effective_date")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: TechLaborRateEntity?
        if (_stmt.step()) {
          val _tmpRateId: String
          _tmpRateId = _stmt.getText(_columnIndexOfRateId)
          val _tmpTechId: String
          _tmpTechId = _stmt.getText(_columnIndexOfTechId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpRoleKey: String?
          if (_stmt.isNull(_columnIndexOfRoleKey)) {
            _tmpRoleKey = null
          } else {
            _tmpRoleKey = _stmt.getText(_columnIndexOfRoleKey)
          }
          val _tmpHourlyRate: Double
          _tmpHourlyRate = _stmt.getDouble(_columnIndexOfHourlyRate)
          val _tmpCurrency: String
          _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          val _tmpEffectiveDate: Long?
          if (_stmt.isNull(_columnIndexOfEffectiveDate)) {
            _tmpEffectiveDate = null
          } else {
            _tmpEffectiveDate = _stmt.getLong(_columnIndexOfEffectiveDate)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _result =
              TechLaborRateEntity(_tmpRateId,_tmpTechId,_tmpAccountId,_tmpRoleKey,_tmpHourlyRate,_tmpCurrency,_tmpEffectiveDate,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
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
    val _sql: String = "DELETE FROM tech_labor_rates WHERE rate_id = ?"
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
