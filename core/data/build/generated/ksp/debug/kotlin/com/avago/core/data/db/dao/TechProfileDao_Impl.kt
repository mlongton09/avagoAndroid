package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.TechProfileEntity
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
public class TechProfileDao_Impl(
  __db: RoomDatabase,
) : TechProfileDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfTechProfileEntity: EntityInsertAdapter<TechProfileEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfTechProfileEntity = object : EntityInsertAdapter<TechProfileEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `tech_profiles` (`tech_id`,`account_id`,`user_id`,`skills`,`certifications`,`hourly_rate`,`currency`,`availability`,`speed_factor`,`current_location_lat`,`current_location_lng`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: TechProfileEntity) {
        statement.bindText(1, entity.techId)
        statement.bindText(2, entity.accountId)
        statement.bindText(3, entity.userId)
        val _tmpSkills: String? = entity.skills
        if (_tmpSkills == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpSkills)
        }
        val _tmpCertifications: String? = entity.certifications
        if (_tmpCertifications == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpCertifications)
        }
        val _tmpHourlyRate: Double? = entity.hourlyRate
        if (_tmpHourlyRate == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpHourlyRate)
        }
        val _tmpCurrency: String? = entity.currency
        if (_tmpCurrency == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpCurrency)
        }
        val _tmpAvailability: String? = entity.availability
        if (_tmpAvailability == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpAvailability)
        }
        val _tmpSpeedFactor: Double? = entity.speedFactor
        if (_tmpSpeedFactor == null) {
          statement.bindNull(9)
        } else {
          statement.bindDouble(9, _tmpSpeedFactor)
        }
        val _tmpCurrentLocationLat: Double? = entity.currentLocationLat
        if (_tmpCurrentLocationLat == null) {
          statement.bindNull(10)
        } else {
          statement.bindDouble(10, _tmpCurrentLocationLat)
        }
        val _tmpCurrentLocationLng: Double? = entity.currentLocationLng
        if (_tmpCurrentLocationLng == null) {
          statement.bindNull(11)
        } else {
          statement.bindDouble(11, _tmpCurrentLocationLng)
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

  public override suspend fun upsert(entity: TechProfileEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfTechProfileEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<TechProfileEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfTechProfileEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<TechProfileEntity>> {
    val _sql: String = "SELECT * FROM tech_profiles WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("tech_profiles")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfTechId: Int = getColumnIndexOrThrow(_stmt, "tech_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfSkills: Int = getColumnIndexOrThrow(_stmt, "skills")
        val _columnIndexOfCertifications: Int = getColumnIndexOrThrow(_stmt, "certifications")
        val _columnIndexOfHourlyRate: Int = getColumnIndexOrThrow(_stmt, "hourly_rate")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfAvailability: Int = getColumnIndexOrThrow(_stmt, "availability")
        val _columnIndexOfSpeedFactor: Int = getColumnIndexOrThrow(_stmt, "speed_factor")
        val _columnIndexOfCurrentLocationLat: Int = getColumnIndexOrThrow(_stmt,
            "current_location_lat")
        val _columnIndexOfCurrentLocationLng: Int = getColumnIndexOrThrow(_stmt,
            "current_location_lng")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<TechProfileEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TechProfileEntity
          val _tmpTechId: String
          _tmpTechId = _stmt.getText(_columnIndexOfTechId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpSkills: String?
          if (_stmt.isNull(_columnIndexOfSkills)) {
            _tmpSkills = null
          } else {
            _tmpSkills = _stmt.getText(_columnIndexOfSkills)
          }
          val _tmpCertifications: String?
          if (_stmt.isNull(_columnIndexOfCertifications)) {
            _tmpCertifications = null
          } else {
            _tmpCertifications = _stmt.getText(_columnIndexOfCertifications)
          }
          val _tmpHourlyRate: Double?
          if (_stmt.isNull(_columnIndexOfHourlyRate)) {
            _tmpHourlyRate = null
          } else {
            _tmpHourlyRate = _stmt.getDouble(_columnIndexOfHourlyRate)
          }
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpAvailability: String?
          if (_stmt.isNull(_columnIndexOfAvailability)) {
            _tmpAvailability = null
          } else {
            _tmpAvailability = _stmt.getText(_columnIndexOfAvailability)
          }
          val _tmpSpeedFactor: Double?
          if (_stmt.isNull(_columnIndexOfSpeedFactor)) {
            _tmpSpeedFactor = null
          } else {
            _tmpSpeedFactor = _stmt.getDouble(_columnIndexOfSpeedFactor)
          }
          val _tmpCurrentLocationLat: Double?
          if (_stmt.isNull(_columnIndexOfCurrentLocationLat)) {
            _tmpCurrentLocationLat = null
          } else {
            _tmpCurrentLocationLat = _stmt.getDouble(_columnIndexOfCurrentLocationLat)
          }
          val _tmpCurrentLocationLng: Double?
          if (_stmt.isNull(_columnIndexOfCurrentLocationLng)) {
            _tmpCurrentLocationLng = null
          } else {
            _tmpCurrentLocationLng = _stmt.getDouble(_columnIndexOfCurrentLocationLng)
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
              TechProfileEntity(_tmpTechId,_tmpAccountId,_tmpUserId,_tmpSkills,_tmpCertifications,_tmpHourlyRate,_tmpCurrency,_tmpAvailability,_tmpSpeedFactor,_tmpCurrentLocationLat,_tmpCurrentLocationLng,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): TechProfileEntity? {
    val _sql: String = "SELECT * FROM tech_profiles WHERE tech_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfTechId: Int = getColumnIndexOrThrow(_stmt, "tech_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfSkills: Int = getColumnIndexOrThrow(_stmt, "skills")
        val _columnIndexOfCertifications: Int = getColumnIndexOrThrow(_stmt, "certifications")
        val _columnIndexOfHourlyRate: Int = getColumnIndexOrThrow(_stmt, "hourly_rate")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfAvailability: Int = getColumnIndexOrThrow(_stmt, "availability")
        val _columnIndexOfSpeedFactor: Int = getColumnIndexOrThrow(_stmt, "speed_factor")
        val _columnIndexOfCurrentLocationLat: Int = getColumnIndexOrThrow(_stmt,
            "current_location_lat")
        val _columnIndexOfCurrentLocationLng: Int = getColumnIndexOrThrow(_stmt,
            "current_location_lng")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: TechProfileEntity?
        if (_stmt.step()) {
          val _tmpTechId: String
          _tmpTechId = _stmt.getText(_columnIndexOfTechId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpSkills: String?
          if (_stmt.isNull(_columnIndexOfSkills)) {
            _tmpSkills = null
          } else {
            _tmpSkills = _stmt.getText(_columnIndexOfSkills)
          }
          val _tmpCertifications: String?
          if (_stmt.isNull(_columnIndexOfCertifications)) {
            _tmpCertifications = null
          } else {
            _tmpCertifications = _stmt.getText(_columnIndexOfCertifications)
          }
          val _tmpHourlyRate: Double?
          if (_stmt.isNull(_columnIndexOfHourlyRate)) {
            _tmpHourlyRate = null
          } else {
            _tmpHourlyRate = _stmt.getDouble(_columnIndexOfHourlyRate)
          }
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpAvailability: String?
          if (_stmt.isNull(_columnIndexOfAvailability)) {
            _tmpAvailability = null
          } else {
            _tmpAvailability = _stmt.getText(_columnIndexOfAvailability)
          }
          val _tmpSpeedFactor: Double?
          if (_stmt.isNull(_columnIndexOfSpeedFactor)) {
            _tmpSpeedFactor = null
          } else {
            _tmpSpeedFactor = _stmt.getDouble(_columnIndexOfSpeedFactor)
          }
          val _tmpCurrentLocationLat: Double?
          if (_stmt.isNull(_columnIndexOfCurrentLocationLat)) {
            _tmpCurrentLocationLat = null
          } else {
            _tmpCurrentLocationLat = _stmt.getDouble(_columnIndexOfCurrentLocationLat)
          }
          val _tmpCurrentLocationLng: Double?
          if (_stmt.isNull(_columnIndexOfCurrentLocationLng)) {
            _tmpCurrentLocationLng = null
          } else {
            _tmpCurrentLocationLng = _stmt.getDouble(_columnIndexOfCurrentLocationLng)
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
              TechProfileEntity(_tmpTechId,_tmpAccountId,_tmpUserId,_tmpSkills,_tmpCertifications,_tmpHourlyRate,_tmpCurrency,_tmpAvailability,_tmpSpeedFactor,_tmpCurrentLocationLat,_tmpCurrentLocationLng,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE tech_profiles SET deleted_at = ?, updated_at = ? WHERE tech_id = ?"
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
