package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.converters.Converters
import com.avago.core.`data`.db.entity.AssetEntity
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
public class AssetDao_Impl(
  __db: RoomDatabase,
) : AssetDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfAssetEntity: EntityInsertAdapter<AssetEntity>

  private val __converters: Converters = Converters()
  init {
    this.__db = __db
    this.__insertAdapterOfAssetEntity = object : EntityInsertAdapter<AssetEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `assets` (`asset_id`,`account_id`,`name`,`make`,`model`,`year`,`asset_type`,`meter_type`,`avatar_color`,`avatar_initial`,`address_line1`,`address_line2`,`city`,`state`,`postal_code`,`country`,`location_id`,`attributes`,`is_fre_sample`,`parent_asset_id`,`path`,`depth`,`child_count`,`is_rental`,`rental_rate`,`rental_rate_unit`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: AssetEntity) {
        statement.bindText(1, entity.assetId)
        statement.bindText(2, entity.accountId)
        statement.bindText(3, entity.name)
        val _tmpMake: String? = entity.make
        if (_tmpMake == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpMake)
        }
        val _tmpModel: String? = entity.model
        if (_tmpModel == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpModel)
        }
        val _tmpYear: Long? = entity.year
        if (_tmpYear == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpYear)
        }
        val _tmpAssetType: String? = entity.assetType
        if (_tmpAssetType == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpAssetType)
        }
        val _tmpMeterType: String? = entity.meterType
        if (_tmpMeterType == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpMeterType)
        }
        val _tmpAvatarColor: String? = entity.avatarColor
        if (_tmpAvatarColor == null) {
          statement.bindNull(9)
        } else {
          statement.bindText(9, _tmpAvatarColor)
        }
        val _tmpAvatarInitial: String? = entity.avatarInitial
        if (_tmpAvatarInitial == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpAvatarInitial)
        }
        val _tmpAddressLine1: String? = entity.addressLine1
        if (_tmpAddressLine1 == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpAddressLine1)
        }
        val _tmpAddressLine2: String? = entity.addressLine2
        if (_tmpAddressLine2 == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpAddressLine2)
        }
        val _tmpCity: String? = entity.city
        if (_tmpCity == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpCity)
        }
        val _tmpState: String? = entity.state
        if (_tmpState == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpState)
        }
        val _tmpPostalCode: String? = entity.postalCode
        if (_tmpPostalCode == null) {
          statement.bindNull(15)
        } else {
          statement.bindText(15, _tmpPostalCode)
        }
        val _tmpCountry: String? = entity.country
        if (_tmpCountry == null) {
          statement.bindNull(16)
        } else {
          statement.bindText(16, _tmpCountry)
        }
        val _tmpLocationId: String? = entity.locationId
        if (_tmpLocationId == null) {
          statement.bindNull(17)
        } else {
          statement.bindText(17, _tmpLocationId)
        }
        val _tmpAttributes: String? = entity.attributes
        if (_tmpAttributes == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpAttributes)
        }
        val _tmp: Int = __converters.fromBooleanToInt(entity.isFreSample)
        statement.bindLong(19, _tmp.toLong())
        val _tmpParentAssetId: String? = entity.parentAssetId
        if (_tmpParentAssetId == null) {
          statement.bindNull(20)
        } else {
          statement.bindText(20, _tmpParentAssetId)
        }
        val _tmpPath: String? = entity.path
        if (_tmpPath == null) {
          statement.bindNull(21)
        } else {
          statement.bindText(21, _tmpPath)
        }
        statement.bindLong(22, entity.depth)
        statement.bindLong(23, entity.childCount)
        val _tmp_1: Int = __converters.fromBooleanToInt(entity.isRental)
        statement.bindLong(24, _tmp_1.toLong())
        val _tmpRentalRate: Double? = entity.rentalRate
        if (_tmpRentalRate == null) {
          statement.bindNull(25)
        } else {
          statement.bindDouble(25, _tmpRentalRate)
        }
        val _tmpRentalRateUnit: String? = entity.rentalRateUnit
        if (_tmpRentalRateUnit == null) {
          statement.bindNull(26)
        } else {
          statement.bindText(26, _tmpRentalRateUnit)
        }
        statement.bindLong(27, entity.createdAt)
        statement.bindLong(28, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(29)
        } else {
          statement.bindLong(29, _tmpDeletedAt)
        }
        statement.bindLong(30, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(31)
        } else {
          statement.bindLong(31, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: AssetEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfAssetEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<AssetEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfAssetEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<AssetEntity>> {
    val _sql: String = "SELECT * FROM assets WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("assets")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfAssetId: Int = getColumnIndexOrThrow(_stmt, "asset_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfMake: Int = getColumnIndexOrThrow(_stmt, "make")
        val _columnIndexOfModel: Int = getColumnIndexOrThrow(_stmt, "model")
        val _columnIndexOfYear: Int = getColumnIndexOrThrow(_stmt, "year")
        val _columnIndexOfAssetType: Int = getColumnIndexOrThrow(_stmt, "asset_type")
        val _columnIndexOfMeterType: Int = getColumnIndexOrThrow(_stmt, "meter_type")
        val _columnIndexOfAvatarColor: Int = getColumnIndexOrThrow(_stmt, "avatar_color")
        val _columnIndexOfAvatarInitial: Int = getColumnIndexOrThrow(_stmt, "avatar_initial")
        val _columnIndexOfAddressLine1: Int = getColumnIndexOrThrow(_stmt, "address_line1")
        val _columnIndexOfAddressLine2: Int = getColumnIndexOrThrow(_stmt, "address_line2")
        val _columnIndexOfCity: Int = getColumnIndexOrThrow(_stmt, "city")
        val _columnIndexOfState: Int = getColumnIndexOrThrow(_stmt, "state")
        val _columnIndexOfPostalCode: Int = getColumnIndexOrThrow(_stmt, "postal_code")
        val _columnIndexOfCountry: Int = getColumnIndexOrThrow(_stmt, "country")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfAttributes: Int = getColumnIndexOrThrow(_stmt, "attributes")
        val _columnIndexOfIsFreSample: Int = getColumnIndexOrThrow(_stmt, "is_fre_sample")
        val _columnIndexOfParentAssetId: Int = getColumnIndexOrThrow(_stmt, "parent_asset_id")
        val _columnIndexOfPath: Int = getColumnIndexOrThrow(_stmt, "path")
        val _columnIndexOfDepth: Int = getColumnIndexOrThrow(_stmt, "depth")
        val _columnIndexOfChildCount: Int = getColumnIndexOrThrow(_stmt, "child_count")
        val _columnIndexOfIsRental: Int = getColumnIndexOrThrow(_stmt, "is_rental")
        val _columnIndexOfRentalRate: Int = getColumnIndexOrThrow(_stmt, "rental_rate")
        val _columnIndexOfRentalRateUnit: Int = getColumnIndexOrThrow(_stmt, "rental_rate_unit")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<AssetEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: AssetEntity
          val _tmpAssetId: String
          _tmpAssetId = _stmt.getText(_columnIndexOfAssetId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpMake: String?
          if (_stmt.isNull(_columnIndexOfMake)) {
            _tmpMake = null
          } else {
            _tmpMake = _stmt.getText(_columnIndexOfMake)
          }
          val _tmpModel: String?
          if (_stmt.isNull(_columnIndexOfModel)) {
            _tmpModel = null
          } else {
            _tmpModel = _stmt.getText(_columnIndexOfModel)
          }
          val _tmpYear: Long?
          if (_stmt.isNull(_columnIndexOfYear)) {
            _tmpYear = null
          } else {
            _tmpYear = _stmt.getLong(_columnIndexOfYear)
          }
          val _tmpAssetType: String?
          if (_stmt.isNull(_columnIndexOfAssetType)) {
            _tmpAssetType = null
          } else {
            _tmpAssetType = _stmt.getText(_columnIndexOfAssetType)
          }
          val _tmpMeterType: String?
          if (_stmt.isNull(_columnIndexOfMeterType)) {
            _tmpMeterType = null
          } else {
            _tmpMeterType = _stmt.getText(_columnIndexOfMeterType)
          }
          val _tmpAvatarColor: String?
          if (_stmt.isNull(_columnIndexOfAvatarColor)) {
            _tmpAvatarColor = null
          } else {
            _tmpAvatarColor = _stmt.getText(_columnIndexOfAvatarColor)
          }
          val _tmpAvatarInitial: String?
          if (_stmt.isNull(_columnIndexOfAvatarInitial)) {
            _tmpAvatarInitial = null
          } else {
            _tmpAvatarInitial = _stmt.getText(_columnIndexOfAvatarInitial)
          }
          val _tmpAddressLine1: String?
          if (_stmt.isNull(_columnIndexOfAddressLine1)) {
            _tmpAddressLine1 = null
          } else {
            _tmpAddressLine1 = _stmt.getText(_columnIndexOfAddressLine1)
          }
          val _tmpAddressLine2: String?
          if (_stmt.isNull(_columnIndexOfAddressLine2)) {
            _tmpAddressLine2 = null
          } else {
            _tmpAddressLine2 = _stmt.getText(_columnIndexOfAddressLine2)
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
          val _tmpLocationId: String?
          if (_stmt.isNull(_columnIndexOfLocationId)) {
            _tmpLocationId = null
          } else {
            _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          }
          val _tmpAttributes: String?
          if (_stmt.isNull(_columnIndexOfAttributes)) {
            _tmpAttributes = null
          } else {
            _tmpAttributes = _stmt.getText(_columnIndexOfAttributes)
          }
          val _tmpIsFreSample: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFreSample).toInt()
          _tmpIsFreSample = __converters.fromIntToBoolean(_tmp)
          val _tmpParentAssetId: String?
          if (_stmt.isNull(_columnIndexOfParentAssetId)) {
            _tmpParentAssetId = null
          } else {
            _tmpParentAssetId = _stmt.getText(_columnIndexOfParentAssetId)
          }
          val _tmpPath: String?
          if (_stmt.isNull(_columnIndexOfPath)) {
            _tmpPath = null
          } else {
            _tmpPath = _stmt.getText(_columnIndexOfPath)
          }
          val _tmpDepth: Long
          _tmpDepth = _stmt.getLong(_columnIndexOfDepth)
          val _tmpChildCount: Long
          _tmpChildCount = _stmt.getLong(_columnIndexOfChildCount)
          val _tmpIsRental: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsRental).toInt()
          _tmpIsRental = __converters.fromIntToBoolean(_tmp_1)
          val _tmpRentalRate: Double?
          if (_stmt.isNull(_columnIndexOfRentalRate)) {
            _tmpRentalRate = null
          } else {
            _tmpRentalRate = _stmt.getDouble(_columnIndexOfRentalRate)
          }
          val _tmpRentalRateUnit: String?
          if (_stmt.isNull(_columnIndexOfRentalRateUnit)) {
            _tmpRentalRateUnit = null
          } else {
            _tmpRentalRateUnit = _stmt.getText(_columnIndexOfRentalRateUnit)
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
              AssetEntity(_tmpAssetId,_tmpAccountId,_tmpName,_tmpMake,_tmpModel,_tmpYear,_tmpAssetType,_tmpMeterType,_tmpAvatarColor,_tmpAvatarInitial,_tmpAddressLine1,_tmpAddressLine2,_tmpCity,_tmpState,_tmpPostalCode,_tmpCountry,_tmpLocationId,_tmpAttributes,_tmpIsFreSample,_tmpParentAssetId,_tmpPath,_tmpDepth,_tmpChildCount,_tmpIsRental,_tmpRentalRate,_tmpRentalRateUnit,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): AssetEntity? {
    val _sql: String = "SELECT * FROM assets WHERE asset_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfAssetId: Int = getColumnIndexOrThrow(_stmt, "asset_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfMake: Int = getColumnIndexOrThrow(_stmt, "make")
        val _columnIndexOfModel: Int = getColumnIndexOrThrow(_stmt, "model")
        val _columnIndexOfYear: Int = getColumnIndexOrThrow(_stmt, "year")
        val _columnIndexOfAssetType: Int = getColumnIndexOrThrow(_stmt, "asset_type")
        val _columnIndexOfMeterType: Int = getColumnIndexOrThrow(_stmt, "meter_type")
        val _columnIndexOfAvatarColor: Int = getColumnIndexOrThrow(_stmt, "avatar_color")
        val _columnIndexOfAvatarInitial: Int = getColumnIndexOrThrow(_stmt, "avatar_initial")
        val _columnIndexOfAddressLine1: Int = getColumnIndexOrThrow(_stmt, "address_line1")
        val _columnIndexOfAddressLine2: Int = getColumnIndexOrThrow(_stmt, "address_line2")
        val _columnIndexOfCity: Int = getColumnIndexOrThrow(_stmt, "city")
        val _columnIndexOfState: Int = getColumnIndexOrThrow(_stmt, "state")
        val _columnIndexOfPostalCode: Int = getColumnIndexOrThrow(_stmt, "postal_code")
        val _columnIndexOfCountry: Int = getColumnIndexOrThrow(_stmt, "country")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfAttributes: Int = getColumnIndexOrThrow(_stmt, "attributes")
        val _columnIndexOfIsFreSample: Int = getColumnIndexOrThrow(_stmt, "is_fre_sample")
        val _columnIndexOfParentAssetId: Int = getColumnIndexOrThrow(_stmt, "parent_asset_id")
        val _columnIndexOfPath: Int = getColumnIndexOrThrow(_stmt, "path")
        val _columnIndexOfDepth: Int = getColumnIndexOrThrow(_stmt, "depth")
        val _columnIndexOfChildCount: Int = getColumnIndexOrThrow(_stmt, "child_count")
        val _columnIndexOfIsRental: Int = getColumnIndexOrThrow(_stmt, "is_rental")
        val _columnIndexOfRentalRate: Int = getColumnIndexOrThrow(_stmt, "rental_rate")
        val _columnIndexOfRentalRateUnit: Int = getColumnIndexOrThrow(_stmt, "rental_rate_unit")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: AssetEntity?
        if (_stmt.step()) {
          val _tmpAssetId: String
          _tmpAssetId = _stmt.getText(_columnIndexOfAssetId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpMake: String?
          if (_stmt.isNull(_columnIndexOfMake)) {
            _tmpMake = null
          } else {
            _tmpMake = _stmt.getText(_columnIndexOfMake)
          }
          val _tmpModel: String?
          if (_stmt.isNull(_columnIndexOfModel)) {
            _tmpModel = null
          } else {
            _tmpModel = _stmt.getText(_columnIndexOfModel)
          }
          val _tmpYear: Long?
          if (_stmt.isNull(_columnIndexOfYear)) {
            _tmpYear = null
          } else {
            _tmpYear = _stmt.getLong(_columnIndexOfYear)
          }
          val _tmpAssetType: String?
          if (_stmt.isNull(_columnIndexOfAssetType)) {
            _tmpAssetType = null
          } else {
            _tmpAssetType = _stmt.getText(_columnIndexOfAssetType)
          }
          val _tmpMeterType: String?
          if (_stmt.isNull(_columnIndexOfMeterType)) {
            _tmpMeterType = null
          } else {
            _tmpMeterType = _stmt.getText(_columnIndexOfMeterType)
          }
          val _tmpAvatarColor: String?
          if (_stmt.isNull(_columnIndexOfAvatarColor)) {
            _tmpAvatarColor = null
          } else {
            _tmpAvatarColor = _stmt.getText(_columnIndexOfAvatarColor)
          }
          val _tmpAvatarInitial: String?
          if (_stmt.isNull(_columnIndexOfAvatarInitial)) {
            _tmpAvatarInitial = null
          } else {
            _tmpAvatarInitial = _stmt.getText(_columnIndexOfAvatarInitial)
          }
          val _tmpAddressLine1: String?
          if (_stmt.isNull(_columnIndexOfAddressLine1)) {
            _tmpAddressLine1 = null
          } else {
            _tmpAddressLine1 = _stmt.getText(_columnIndexOfAddressLine1)
          }
          val _tmpAddressLine2: String?
          if (_stmt.isNull(_columnIndexOfAddressLine2)) {
            _tmpAddressLine2 = null
          } else {
            _tmpAddressLine2 = _stmt.getText(_columnIndexOfAddressLine2)
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
          val _tmpLocationId: String?
          if (_stmt.isNull(_columnIndexOfLocationId)) {
            _tmpLocationId = null
          } else {
            _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          }
          val _tmpAttributes: String?
          if (_stmt.isNull(_columnIndexOfAttributes)) {
            _tmpAttributes = null
          } else {
            _tmpAttributes = _stmt.getText(_columnIndexOfAttributes)
          }
          val _tmpIsFreSample: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFreSample).toInt()
          _tmpIsFreSample = __converters.fromIntToBoolean(_tmp)
          val _tmpParentAssetId: String?
          if (_stmt.isNull(_columnIndexOfParentAssetId)) {
            _tmpParentAssetId = null
          } else {
            _tmpParentAssetId = _stmt.getText(_columnIndexOfParentAssetId)
          }
          val _tmpPath: String?
          if (_stmt.isNull(_columnIndexOfPath)) {
            _tmpPath = null
          } else {
            _tmpPath = _stmt.getText(_columnIndexOfPath)
          }
          val _tmpDepth: Long
          _tmpDepth = _stmt.getLong(_columnIndexOfDepth)
          val _tmpChildCount: Long
          _tmpChildCount = _stmt.getLong(_columnIndexOfChildCount)
          val _tmpIsRental: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsRental).toInt()
          _tmpIsRental = __converters.fromIntToBoolean(_tmp_1)
          val _tmpRentalRate: Double?
          if (_stmt.isNull(_columnIndexOfRentalRate)) {
            _tmpRentalRate = null
          } else {
            _tmpRentalRate = _stmt.getDouble(_columnIndexOfRentalRate)
          }
          val _tmpRentalRateUnit: String?
          if (_stmt.isNull(_columnIndexOfRentalRateUnit)) {
            _tmpRentalRateUnit = null
          } else {
            _tmpRentalRateUnit = _stmt.getText(_columnIndexOfRentalRateUnit)
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
              AssetEntity(_tmpAssetId,_tmpAccountId,_tmpName,_tmpMake,_tmpModel,_tmpYear,_tmpAssetType,_tmpMeterType,_tmpAvatarColor,_tmpAvatarInitial,_tmpAddressLine1,_tmpAddressLine2,_tmpCity,_tmpState,_tmpPostalCode,_tmpCountry,_tmpLocationId,_tmpAttributes,_tmpIsFreSample,_tmpParentAssetId,_tmpPath,_tmpDepth,_tmpChildCount,_tmpIsRental,_tmpRentalRate,_tmpRentalRateUnit,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE assets SET deleted_at = ?, updated_at = ? WHERE asset_id = ?"
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
