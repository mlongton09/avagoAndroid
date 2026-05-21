package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.converters.Converters
import com.avago.core.`data`.db.entity.GrnEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class GrnDao_Impl(
  __db: RoomDatabase,
) : GrnDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfGrnEntity: EntityInsertAdapter<GrnEntity>

  private val __converters: Converters = Converters()
  init {
    this.__db = __db
    this.__insertAdapterOfGrnEntity = object : EntityInsertAdapter<GrnEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `grns` (`grn_id`,`account_id`,`po_id`,`grn_number`,`received_at`,`received_by`,`received_at_location_id`,`carrier`,`tracking_number`,`packing_slip_no`,`notes`,`has_discrepancy`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: GrnEntity) {
        statement.bindText(1, entity.grnId)
        statement.bindText(2, entity.accountId)
        val _tmpPoId: String? = entity.poId
        if (_tmpPoId == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpPoId)
        }
        val _tmpGrnNumber: String? = entity.grnNumber
        if (_tmpGrnNumber == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpGrnNumber)
        }
        val _tmpReceivedAt: Long? = entity.receivedAt
        if (_tmpReceivedAt == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpReceivedAt)
        }
        val _tmpReceivedBy: String? = entity.receivedBy
        if (_tmpReceivedBy == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpReceivedBy)
        }
        val _tmpReceivedAtLocationId: String? = entity.receivedAtLocationId
        if (_tmpReceivedAtLocationId == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpReceivedAtLocationId)
        }
        val _tmpCarrier: String? = entity.carrier
        if (_tmpCarrier == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpCarrier)
        }
        val _tmpTrackingNumber: String? = entity.trackingNumber
        if (_tmpTrackingNumber == null) {
          statement.bindNull(9)
        } else {
          statement.bindText(9, _tmpTrackingNumber)
        }
        val _tmpPackingSlipNo: String? = entity.packingSlipNo
        if (_tmpPackingSlipNo == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpPackingSlipNo)
        }
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpNotes)
        }
        val _tmp: Int = __converters.fromBooleanToInt(entity.hasDiscrepancy)
        statement.bindLong(12, _tmp.toLong())
        statement.bindLong(13, entity.createdAt)
        statement.bindLong(14, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(15)
        } else {
          statement.bindLong(15, _tmpDeletedAt)
        }
        statement.bindLong(16, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(17)
        } else {
          statement.bindLong(17, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: GrnEntity): Unit = performSuspending(__db, false, true)
      { _connection ->
    __insertAdapterOfGrnEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<GrnEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfGrnEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<GrnEntity>> {
    val _sql: String = "SELECT * FROM grns WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("grns")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfGrnId: Int = getColumnIndexOrThrow(_stmt, "grn_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfPoId: Int = getColumnIndexOrThrow(_stmt, "po_id")
        val _columnIndexOfGrnNumber: Int = getColumnIndexOrThrow(_stmt, "grn_number")
        val _columnIndexOfReceivedAt: Int = getColumnIndexOrThrow(_stmt, "received_at")
        val _columnIndexOfReceivedBy: Int = getColumnIndexOrThrow(_stmt, "received_by")
        val _columnIndexOfReceivedAtLocationId: Int = getColumnIndexOrThrow(_stmt,
            "received_at_location_id")
        val _columnIndexOfCarrier: Int = getColumnIndexOrThrow(_stmt, "carrier")
        val _columnIndexOfTrackingNumber: Int = getColumnIndexOrThrow(_stmt, "tracking_number")
        val _columnIndexOfPackingSlipNo: Int = getColumnIndexOrThrow(_stmt, "packing_slip_no")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfHasDiscrepancy: Int = getColumnIndexOrThrow(_stmt, "has_discrepancy")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<GrnEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: GrnEntity
          val _tmpGrnId: String
          _tmpGrnId = _stmt.getText(_columnIndexOfGrnId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpPoId: String?
          if (_stmt.isNull(_columnIndexOfPoId)) {
            _tmpPoId = null
          } else {
            _tmpPoId = _stmt.getText(_columnIndexOfPoId)
          }
          val _tmpGrnNumber: String?
          if (_stmt.isNull(_columnIndexOfGrnNumber)) {
            _tmpGrnNumber = null
          } else {
            _tmpGrnNumber = _stmt.getText(_columnIndexOfGrnNumber)
          }
          val _tmpReceivedAt: Long?
          if (_stmt.isNull(_columnIndexOfReceivedAt)) {
            _tmpReceivedAt = null
          } else {
            _tmpReceivedAt = _stmt.getLong(_columnIndexOfReceivedAt)
          }
          val _tmpReceivedBy: String?
          if (_stmt.isNull(_columnIndexOfReceivedBy)) {
            _tmpReceivedBy = null
          } else {
            _tmpReceivedBy = _stmt.getText(_columnIndexOfReceivedBy)
          }
          val _tmpReceivedAtLocationId: String?
          if (_stmt.isNull(_columnIndexOfReceivedAtLocationId)) {
            _tmpReceivedAtLocationId = null
          } else {
            _tmpReceivedAtLocationId = _stmt.getText(_columnIndexOfReceivedAtLocationId)
          }
          val _tmpCarrier: String?
          if (_stmt.isNull(_columnIndexOfCarrier)) {
            _tmpCarrier = null
          } else {
            _tmpCarrier = _stmt.getText(_columnIndexOfCarrier)
          }
          val _tmpTrackingNumber: String?
          if (_stmt.isNull(_columnIndexOfTrackingNumber)) {
            _tmpTrackingNumber = null
          } else {
            _tmpTrackingNumber = _stmt.getText(_columnIndexOfTrackingNumber)
          }
          val _tmpPackingSlipNo: String?
          if (_stmt.isNull(_columnIndexOfPackingSlipNo)) {
            _tmpPackingSlipNo = null
          } else {
            _tmpPackingSlipNo = _stmt.getText(_columnIndexOfPackingSlipNo)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpHasDiscrepancy: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfHasDiscrepancy).toInt()
          _tmpHasDiscrepancy = __converters.fromIntToBoolean(_tmp)
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
              GrnEntity(_tmpGrnId,_tmpAccountId,_tmpPoId,_tmpGrnNumber,_tmpReceivedAt,_tmpReceivedBy,_tmpReceivedAtLocationId,_tmpCarrier,_tmpTrackingNumber,_tmpPackingSlipNo,_tmpNotes,_tmpHasDiscrepancy,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): GrnEntity? {
    val _sql: String = "SELECT * FROM grns WHERE grn_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfGrnId: Int = getColumnIndexOrThrow(_stmt, "grn_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfPoId: Int = getColumnIndexOrThrow(_stmt, "po_id")
        val _columnIndexOfGrnNumber: Int = getColumnIndexOrThrow(_stmt, "grn_number")
        val _columnIndexOfReceivedAt: Int = getColumnIndexOrThrow(_stmt, "received_at")
        val _columnIndexOfReceivedBy: Int = getColumnIndexOrThrow(_stmt, "received_by")
        val _columnIndexOfReceivedAtLocationId: Int = getColumnIndexOrThrow(_stmt,
            "received_at_location_id")
        val _columnIndexOfCarrier: Int = getColumnIndexOrThrow(_stmt, "carrier")
        val _columnIndexOfTrackingNumber: Int = getColumnIndexOrThrow(_stmt, "tracking_number")
        val _columnIndexOfPackingSlipNo: Int = getColumnIndexOrThrow(_stmt, "packing_slip_no")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfHasDiscrepancy: Int = getColumnIndexOrThrow(_stmt, "has_discrepancy")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: GrnEntity?
        if (_stmt.step()) {
          val _tmpGrnId: String
          _tmpGrnId = _stmt.getText(_columnIndexOfGrnId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpPoId: String?
          if (_stmt.isNull(_columnIndexOfPoId)) {
            _tmpPoId = null
          } else {
            _tmpPoId = _stmt.getText(_columnIndexOfPoId)
          }
          val _tmpGrnNumber: String?
          if (_stmt.isNull(_columnIndexOfGrnNumber)) {
            _tmpGrnNumber = null
          } else {
            _tmpGrnNumber = _stmt.getText(_columnIndexOfGrnNumber)
          }
          val _tmpReceivedAt: Long?
          if (_stmt.isNull(_columnIndexOfReceivedAt)) {
            _tmpReceivedAt = null
          } else {
            _tmpReceivedAt = _stmt.getLong(_columnIndexOfReceivedAt)
          }
          val _tmpReceivedBy: String?
          if (_stmt.isNull(_columnIndexOfReceivedBy)) {
            _tmpReceivedBy = null
          } else {
            _tmpReceivedBy = _stmt.getText(_columnIndexOfReceivedBy)
          }
          val _tmpReceivedAtLocationId: String?
          if (_stmt.isNull(_columnIndexOfReceivedAtLocationId)) {
            _tmpReceivedAtLocationId = null
          } else {
            _tmpReceivedAtLocationId = _stmt.getText(_columnIndexOfReceivedAtLocationId)
          }
          val _tmpCarrier: String?
          if (_stmt.isNull(_columnIndexOfCarrier)) {
            _tmpCarrier = null
          } else {
            _tmpCarrier = _stmt.getText(_columnIndexOfCarrier)
          }
          val _tmpTrackingNumber: String?
          if (_stmt.isNull(_columnIndexOfTrackingNumber)) {
            _tmpTrackingNumber = null
          } else {
            _tmpTrackingNumber = _stmt.getText(_columnIndexOfTrackingNumber)
          }
          val _tmpPackingSlipNo: String?
          if (_stmt.isNull(_columnIndexOfPackingSlipNo)) {
            _tmpPackingSlipNo = null
          } else {
            _tmpPackingSlipNo = _stmt.getText(_columnIndexOfPackingSlipNo)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpHasDiscrepancy: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfHasDiscrepancy).toInt()
          _tmpHasDiscrepancy = __converters.fromIntToBoolean(_tmp)
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
              GrnEntity(_tmpGrnId,_tmpAccountId,_tmpPoId,_tmpGrnNumber,_tmpReceivedAt,_tmpReceivedBy,_tmpReceivedAtLocationId,_tmpCarrier,_tmpTrackingNumber,_tmpPackingSlipNo,_tmpNotes,_tmpHasDiscrepancy,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE grns SET deleted_at = ?, updated_at = ? WHERE grn_id = ?"
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
