package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.converters.Converters
import com.avago.core.`data`.db.entity.ScheduleEntity
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
public class ScheduleDao_Impl(
  __db: RoomDatabase,
) : ScheduleDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfScheduleEntity: EntityInsertAdapter<ScheduleEntity>

  private val __converters: Converters = Converters()
  init {
    this.__db = __db
    this.__insertAdapterOfScheduleEntity = object : EntityInsertAdapter<ScheduleEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `schedules` (`schedule_id`,`asset_id`,`account_id`,`title`,`category`,`schedule_type`,`rrule`,`end_type`,`end_count`,`end_date`,`meter_type`,`meter_due`,`meter_interval`,`last_completed_at`,`next_due_at`,`is_active`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ScheduleEntity) {
        statement.bindText(1, entity.scheduleId)
        statement.bindText(2, entity.assetId)
        statement.bindText(3, entity.accountId)
        statement.bindText(4, entity.title)
        val _tmpCategory: String? = entity.category
        if (_tmpCategory == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpCategory)
        }
        statement.bindText(6, entity.scheduleType)
        val _tmpRrule: String? = entity.rrule
        if (_tmpRrule == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpRrule)
        }
        val _tmpEndType: String? = entity.endType
        if (_tmpEndType == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpEndType)
        }
        val _tmpEndCount: Long? = entity.endCount
        if (_tmpEndCount == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpEndCount)
        }
        val _tmpEndDate: Long? = entity.endDate
        if (_tmpEndDate == null) {
          statement.bindNull(10)
        } else {
          statement.bindLong(10, _tmpEndDate)
        }
        val _tmpMeterType: String? = entity.meterType
        if (_tmpMeterType == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpMeterType)
        }
        val _tmpMeterDue: Double? = entity.meterDue
        if (_tmpMeterDue == null) {
          statement.bindNull(12)
        } else {
          statement.bindDouble(12, _tmpMeterDue)
        }
        val _tmpMeterInterval: Double? = entity.meterInterval
        if (_tmpMeterInterval == null) {
          statement.bindNull(13)
        } else {
          statement.bindDouble(13, _tmpMeterInterval)
        }
        val _tmpLastCompletedAt: Long? = entity.lastCompletedAt
        if (_tmpLastCompletedAt == null) {
          statement.bindNull(14)
        } else {
          statement.bindLong(14, _tmpLastCompletedAt)
        }
        val _tmpNextDueAt: Long? = entity.nextDueAt
        if (_tmpNextDueAt == null) {
          statement.bindNull(15)
        } else {
          statement.bindLong(15, _tmpNextDueAt)
        }
        val _tmp: Int = __converters.fromBooleanToInt(entity.isActive)
        statement.bindLong(16, _tmp.toLong())
        statement.bindLong(17, entity.createdAt)
        statement.bindLong(18, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(19)
        } else {
          statement.bindLong(19, _tmpDeletedAt)
        }
        statement.bindLong(20, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(21)
        } else {
          statement.bindLong(21, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: ScheduleEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfScheduleEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<ScheduleEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfScheduleEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<ScheduleEntity>> {
    val _sql: String = "SELECT * FROM schedules WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("schedules")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfScheduleId: Int = getColumnIndexOrThrow(_stmt, "schedule_id")
        val _columnIndexOfAssetId: Int = getColumnIndexOrThrow(_stmt, "asset_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfScheduleType: Int = getColumnIndexOrThrow(_stmt, "schedule_type")
        val _columnIndexOfRrule: Int = getColumnIndexOrThrow(_stmt, "rrule")
        val _columnIndexOfEndType: Int = getColumnIndexOrThrow(_stmt, "end_type")
        val _columnIndexOfEndCount: Int = getColumnIndexOrThrow(_stmt, "end_count")
        val _columnIndexOfEndDate: Int = getColumnIndexOrThrow(_stmt, "end_date")
        val _columnIndexOfMeterType: Int = getColumnIndexOrThrow(_stmt, "meter_type")
        val _columnIndexOfMeterDue: Int = getColumnIndexOrThrow(_stmt, "meter_due")
        val _columnIndexOfMeterInterval: Int = getColumnIndexOrThrow(_stmt, "meter_interval")
        val _columnIndexOfLastCompletedAt: Int = getColumnIndexOrThrow(_stmt, "last_completed_at")
        val _columnIndexOfNextDueAt: Int = getColumnIndexOrThrow(_stmt, "next_due_at")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "is_active")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<ScheduleEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ScheduleEntity
          val _tmpScheduleId: String
          _tmpScheduleId = _stmt.getText(_columnIndexOfScheduleId)
          val _tmpAssetId: String
          _tmpAssetId = _stmt.getText(_columnIndexOfAssetId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCategory: String?
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _tmpCategory = null
          } else {
            _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          }
          val _tmpScheduleType: String
          _tmpScheduleType = _stmt.getText(_columnIndexOfScheduleType)
          val _tmpRrule: String?
          if (_stmt.isNull(_columnIndexOfRrule)) {
            _tmpRrule = null
          } else {
            _tmpRrule = _stmt.getText(_columnIndexOfRrule)
          }
          val _tmpEndType: String?
          if (_stmt.isNull(_columnIndexOfEndType)) {
            _tmpEndType = null
          } else {
            _tmpEndType = _stmt.getText(_columnIndexOfEndType)
          }
          val _tmpEndCount: Long?
          if (_stmt.isNull(_columnIndexOfEndCount)) {
            _tmpEndCount = null
          } else {
            _tmpEndCount = _stmt.getLong(_columnIndexOfEndCount)
          }
          val _tmpEndDate: Long?
          if (_stmt.isNull(_columnIndexOfEndDate)) {
            _tmpEndDate = null
          } else {
            _tmpEndDate = _stmt.getLong(_columnIndexOfEndDate)
          }
          val _tmpMeterType: String?
          if (_stmt.isNull(_columnIndexOfMeterType)) {
            _tmpMeterType = null
          } else {
            _tmpMeterType = _stmt.getText(_columnIndexOfMeterType)
          }
          val _tmpMeterDue: Double?
          if (_stmt.isNull(_columnIndexOfMeterDue)) {
            _tmpMeterDue = null
          } else {
            _tmpMeterDue = _stmt.getDouble(_columnIndexOfMeterDue)
          }
          val _tmpMeterInterval: Double?
          if (_stmt.isNull(_columnIndexOfMeterInterval)) {
            _tmpMeterInterval = null
          } else {
            _tmpMeterInterval = _stmt.getDouble(_columnIndexOfMeterInterval)
          }
          val _tmpLastCompletedAt: Long?
          if (_stmt.isNull(_columnIndexOfLastCompletedAt)) {
            _tmpLastCompletedAt = null
          } else {
            _tmpLastCompletedAt = _stmt.getLong(_columnIndexOfLastCompletedAt)
          }
          val _tmpNextDueAt: Long?
          if (_stmt.isNull(_columnIndexOfNextDueAt)) {
            _tmpNextDueAt = null
          } else {
            _tmpNextDueAt = _stmt.getLong(_columnIndexOfNextDueAt)
          }
          val _tmpIsActive: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIsActive = __converters.fromIntToBoolean(_tmp)
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
              ScheduleEntity(_tmpScheduleId,_tmpAssetId,_tmpAccountId,_tmpTitle,_tmpCategory,_tmpScheduleType,_tmpRrule,_tmpEndType,_tmpEndCount,_tmpEndDate,_tmpMeterType,_tmpMeterDue,_tmpMeterInterval,_tmpLastCompletedAt,_tmpNextDueAt,_tmpIsActive,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): ScheduleEntity? {
    val _sql: String = "SELECT * FROM schedules WHERE schedule_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfScheduleId: Int = getColumnIndexOrThrow(_stmt, "schedule_id")
        val _columnIndexOfAssetId: Int = getColumnIndexOrThrow(_stmt, "asset_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfScheduleType: Int = getColumnIndexOrThrow(_stmt, "schedule_type")
        val _columnIndexOfRrule: Int = getColumnIndexOrThrow(_stmt, "rrule")
        val _columnIndexOfEndType: Int = getColumnIndexOrThrow(_stmt, "end_type")
        val _columnIndexOfEndCount: Int = getColumnIndexOrThrow(_stmt, "end_count")
        val _columnIndexOfEndDate: Int = getColumnIndexOrThrow(_stmt, "end_date")
        val _columnIndexOfMeterType: Int = getColumnIndexOrThrow(_stmt, "meter_type")
        val _columnIndexOfMeterDue: Int = getColumnIndexOrThrow(_stmt, "meter_due")
        val _columnIndexOfMeterInterval: Int = getColumnIndexOrThrow(_stmt, "meter_interval")
        val _columnIndexOfLastCompletedAt: Int = getColumnIndexOrThrow(_stmt, "last_completed_at")
        val _columnIndexOfNextDueAt: Int = getColumnIndexOrThrow(_stmt, "next_due_at")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "is_active")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: ScheduleEntity?
        if (_stmt.step()) {
          val _tmpScheduleId: String
          _tmpScheduleId = _stmt.getText(_columnIndexOfScheduleId)
          val _tmpAssetId: String
          _tmpAssetId = _stmt.getText(_columnIndexOfAssetId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCategory: String?
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _tmpCategory = null
          } else {
            _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          }
          val _tmpScheduleType: String
          _tmpScheduleType = _stmt.getText(_columnIndexOfScheduleType)
          val _tmpRrule: String?
          if (_stmt.isNull(_columnIndexOfRrule)) {
            _tmpRrule = null
          } else {
            _tmpRrule = _stmt.getText(_columnIndexOfRrule)
          }
          val _tmpEndType: String?
          if (_stmt.isNull(_columnIndexOfEndType)) {
            _tmpEndType = null
          } else {
            _tmpEndType = _stmt.getText(_columnIndexOfEndType)
          }
          val _tmpEndCount: Long?
          if (_stmt.isNull(_columnIndexOfEndCount)) {
            _tmpEndCount = null
          } else {
            _tmpEndCount = _stmt.getLong(_columnIndexOfEndCount)
          }
          val _tmpEndDate: Long?
          if (_stmt.isNull(_columnIndexOfEndDate)) {
            _tmpEndDate = null
          } else {
            _tmpEndDate = _stmt.getLong(_columnIndexOfEndDate)
          }
          val _tmpMeterType: String?
          if (_stmt.isNull(_columnIndexOfMeterType)) {
            _tmpMeterType = null
          } else {
            _tmpMeterType = _stmt.getText(_columnIndexOfMeterType)
          }
          val _tmpMeterDue: Double?
          if (_stmt.isNull(_columnIndexOfMeterDue)) {
            _tmpMeterDue = null
          } else {
            _tmpMeterDue = _stmt.getDouble(_columnIndexOfMeterDue)
          }
          val _tmpMeterInterval: Double?
          if (_stmt.isNull(_columnIndexOfMeterInterval)) {
            _tmpMeterInterval = null
          } else {
            _tmpMeterInterval = _stmt.getDouble(_columnIndexOfMeterInterval)
          }
          val _tmpLastCompletedAt: Long?
          if (_stmt.isNull(_columnIndexOfLastCompletedAt)) {
            _tmpLastCompletedAt = null
          } else {
            _tmpLastCompletedAt = _stmt.getLong(_columnIndexOfLastCompletedAt)
          }
          val _tmpNextDueAt: Long?
          if (_stmt.isNull(_columnIndexOfNextDueAt)) {
            _tmpNextDueAt = null
          } else {
            _tmpNextDueAt = _stmt.getLong(_columnIndexOfNextDueAt)
          }
          val _tmpIsActive: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIsActive = __converters.fromIntToBoolean(_tmp)
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
              ScheduleEntity(_tmpScheduleId,_tmpAssetId,_tmpAccountId,_tmpTitle,_tmpCategory,_tmpScheduleType,_tmpRrule,_tmpEndType,_tmpEndCount,_tmpEndDate,_tmpMeterType,_tmpMeterDue,_tmpMeterInterval,_tmpLastCompletedAt,_tmpNextDueAt,_tmpIsActive,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE schedules SET deleted_at = ?, updated_at = ? WHERE schedule_id = ?"
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
