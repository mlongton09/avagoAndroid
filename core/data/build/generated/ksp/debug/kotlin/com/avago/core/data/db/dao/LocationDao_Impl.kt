package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.LocationEntity
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
public class LocationDao_Impl(
  __db: RoomDatabase,
) : LocationDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfLocationEntity: EntityInsertAdapter<LocationEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfLocationEntity = object : EntityInsertAdapter<LocationEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `locations` (`location_id`,`account_id`,`name`,`address`,`city`,`state`,`postal_code`,`country`,`latitude`,`longitude`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LocationEntity) {
        statement.bindText(1, entity.locationId)
        statement.bindText(2, entity.accountId)
        statement.bindText(3, entity.name)
        val _tmpAddress: String? = entity.address
        if (_tmpAddress == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpAddress)
        }
        val _tmpCity: String? = entity.city
        if (_tmpCity == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpCity)
        }
        val _tmpState: String? = entity.state
        if (_tmpState == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpState)
        }
        val _tmpPostalCode: String? = entity.postalCode
        if (_tmpPostalCode == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpPostalCode)
        }
        val _tmpCountry: String? = entity.country
        if (_tmpCountry == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpCountry)
        }
        val _tmpLatitude: Double? = entity.latitude
        if (_tmpLatitude == null) {
          statement.bindNull(9)
        } else {
          statement.bindDouble(9, _tmpLatitude)
        }
        val _tmpLongitude: Double? = entity.longitude
        if (_tmpLongitude == null) {
          statement.bindNull(10)
        } else {
          statement.bindDouble(10, _tmpLongitude)
        }
        statement.bindLong(11, entity.createdAt)
        statement.bindLong(12, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(13)
        } else {
          statement.bindLong(13, _tmpDeletedAt)
        }
        statement.bindLong(14, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(15)
        } else {
          statement.bindLong(15, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: LocationEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfLocationEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<LocationEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfLocationEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<LocationEntity>> {
    val _sql: String = "SELECT * FROM locations WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("locations")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
        val _columnIndexOfCity: Int = getColumnIndexOrThrow(_stmt, "city")
        val _columnIndexOfState: Int = getColumnIndexOrThrow(_stmt, "state")
        val _columnIndexOfPostalCode: Int = getColumnIndexOrThrow(_stmt, "postal_code")
        val _columnIndexOfCountry: Int = getColumnIndexOrThrow(_stmt, "country")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<LocationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocationEntity
          val _tmpLocationId: String
          _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpAddress: String?
          if (_stmt.isNull(_columnIndexOfAddress)) {
            _tmpAddress = null
          } else {
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
          }
          val _tmpCity: String?
          if (_stmt.isNull(_columnIndexOfCity)) {
            _tmpCity = null
          } else {
            _tmpCity = _stmt.getText(_columnIndexOfCity)
          }
          val _tmpState: String?
          if (_stmt.isNull(_columnIndexOfState)) {
            _tmpState = null
          } else {
            _tmpState = _stmt.getText(_columnIndexOfState)
          }
          val _tmpPostalCode: String?
          if (_stmt.isNull(_columnIndexOfPostalCode)) {
            _tmpPostalCode = null
          } else {
            _tmpPostalCode = _stmt.getText(_columnIndexOfPostalCode)
          }
          val _tmpCountry: String?
          if (_stmt.isNull(_columnIndexOfCountry)) {
            _tmpCountry = null
          } else {
            _tmpCountry = _stmt.getText(_columnIndexOfCountry)
          }
          val _tmpLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLatitude)) {
            _tmpLatitude = null
          } else {
            _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          }
          val _tmpLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLongitude)) {
            _tmpLongitude = null
          } else {
            _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
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
              LocationEntity(_tmpLocationId,_tmpAccountId,_tmpName,_tmpAddress,_tmpCity,_tmpState,_tmpPostalCode,_tmpCountry,_tmpLatitude,_tmpLongitude,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): LocationEntity? {
    val _sql: String = "SELECT * FROM locations WHERE location_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
        val _columnIndexOfCity: Int = getColumnIndexOrThrow(_stmt, "city")
        val _columnIndexOfState: Int = getColumnIndexOrThrow(_stmt, "state")
        val _columnIndexOfPostalCode: Int = getColumnIndexOrThrow(_stmt, "postal_code")
        val _columnIndexOfCountry: Int = getColumnIndexOrThrow(_stmt, "country")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: LocationEntity?
        if (_stmt.step()) {
          val _tmpLocationId: String
          _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpAddress: String?
          if (_stmt.isNull(_columnIndexOfAddress)) {
            _tmpAddress = null
          } else {
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
          }
          val _tmpCity: String?
          if (_stmt.isNull(_columnIndexOfCity)) {
            _tmpCity = null
          } else {
            _tmpCity = _stmt.getText(_columnIndexOfCity)
          }
          val _tmpState: String?
          if (_stmt.isNull(_columnIndexOfState)) {
            _tmpState = null
          } else {
            _tmpState = _stmt.getText(_columnIndexOfState)
          }
          val _tmpPostalCode: String?
          if (_stmt.isNull(_columnIndexOfPostalCode)) {
            _tmpPostalCode = null
          } else {
            _tmpPostalCode = _stmt.getText(_columnIndexOfPostalCode)
          }
          val _tmpCountry: String?
          if (_stmt.isNull(_columnIndexOfCountry)) {
            _tmpCountry = null
          } else {
            _tmpCountry = _stmt.getText(_columnIndexOfCountry)
          }
          val _tmpLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLatitude)) {
            _tmpLatitude = null
          } else {
            _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          }
          val _tmpLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLongitude)) {
            _tmpLongitude = null
          } else {
            _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
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
              LocationEntity(_tmpLocationId,_tmpAccountId,_tmpName,_tmpAddress,_tmpCity,_tmpState,_tmpPostalCode,_tmpCountry,_tmpLatitude,_tmpLongitude,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE locations SET deleted_at = ?, updated_at = ? WHERE location_id = ?"
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
