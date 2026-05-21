package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.VendorEntity
import javax.`annotation`.processing.Generated
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
public class VendorDao_Impl(
  __db: RoomDatabase,
) : VendorDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfVendorEntity: EntityInsertAdapter<VendorEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfVendorEntity = object : EntityInsertAdapter<VendorEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `vendors` (`vendor_id`,`account_id`,`name`,`email`,`phone`,`address`,`payment_terms`,`tax_id`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: VendorEntity) {
        statement.bindText(1, entity.vendorId)
        statement.bindText(2, entity.accountId)
        statement.bindText(3, entity.name)
        val _tmpEmail: String? = entity.email
        if (_tmpEmail == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpEmail)
        }
        val _tmpPhone: String? = entity.phone
        if (_tmpPhone == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpPhone)
        }
        val _tmpAddress: String? = entity.address
        if (_tmpAddress == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpAddress)
        }
        val _tmpPaymentTerms: String? = entity.paymentTerms
        if (_tmpPaymentTerms == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpPaymentTerms)
        }
        val _tmpTaxId: String? = entity.taxId
        if (_tmpTaxId == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpTaxId)
        }
        statement.bindLong(9, entity.createdAt)
        statement.bindLong(10, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(11)
        } else {
          statement.bindLong(11, _tmpDeletedAt)
        }
        statement.bindLong(12, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(13)
        } else {
          statement.bindLong(13, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: VendorEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfVendorEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<VendorEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfVendorEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<VendorEntity>> {
    val _sql: String = "SELECT * FROM vendors WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("vendors")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfVendorId: Int = getColumnIndexOrThrow(_stmt, "vendor_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfEmail: Int = getColumnIndexOrThrow(_stmt, "email")
        val _columnIndexOfPhone: Int = getColumnIndexOrThrow(_stmt, "phone")
        val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
        val _columnIndexOfPaymentTerms: Int = getColumnIndexOrThrow(_stmt, "payment_terms")
        val _columnIndexOfTaxId: Int = getColumnIndexOrThrow(_stmt, "tax_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<VendorEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: VendorEntity
          val _tmpVendorId: String
          _tmpVendorId = _stmt.getText(_columnIndexOfVendorId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpEmail: String?
          if (_stmt.isNull(_columnIndexOfEmail)) {
            _tmpEmail = null
          } else {
            _tmpEmail = _stmt.getText(_columnIndexOfEmail)
          }
          val _tmpPhone: String?
          if (_stmt.isNull(_columnIndexOfPhone)) {
            _tmpPhone = null
          } else {
            _tmpPhone = _stmt.getText(_columnIndexOfPhone)
          }
          val _tmpAddress: String?
          if (_stmt.isNull(_columnIndexOfAddress)) {
            _tmpAddress = null
          } else {
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
          }
          val _tmpPaymentTerms: String?
          if (_stmt.isNull(_columnIndexOfPaymentTerms)) {
            _tmpPaymentTerms = null
          } else {
            _tmpPaymentTerms = _stmt.getText(_columnIndexOfPaymentTerms)
          }
          val _tmpTaxId: String?
          if (_stmt.isNull(_columnIndexOfTaxId)) {
            _tmpTaxId = null
          } else {
            _tmpTaxId = _stmt.getText(_columnIndexOfTaxId)
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
              VendorEntity(_tmpVendorId,_tmpAccountId,_tmpName,_tmpEmail,_tmpPhone,_tmpAddress,_tmpPaymentTerms,_tmpTaxId,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): VendorEntity? {
    val _sql: String = "SELECT * FROM vendors WHERE vendor_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfVendorId: Int = getColumnIndexOrThrow(_stmt, "vendor_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfEmail: Int = getColumnIndexOrThrow(_stmt, "email")
        val _columnIndexOfPhone: Int = getColumnIndexOrThrow(_stmt, "phone")
        val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
        val _columnIndexOfPaymentTerms: Int = getColumnIndexOrThrow(_stmt, "payment_terms")
        val _columnIndexOfTaxId: Int = getColumnIndexOrThrow(_stmt, "tax_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: VendorEntity?
        if (_stmt.step()) {
          val _tmpVendorId: String
          _tmpVendorId = _stmt.getText(_columnIndexOfVendorId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpEmail: String?
          if (_stmt.isNull(_columnIndexOfEmail)) {
            _tmpEmail = null
          } else {
            _tmpEmail = _stmt.getText(_columnIndexOfEmail)
          }
          val _tmpPhone: String?
          if (_stmt.isNull(_columnIndexOfPhone)) {
            _tmpPhone = null
          } else {
            _tmpPhone = _stmt.getText(_columnIndexOfPhone)
          }
          val _tmpAddress: String?
          if (_stmt.isNull(_columnIndexOfAddress)) {
            _tmpAddress = null
          } else {
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
          }
          val _tmpPaymentTerms: String?
          if (_stmt.isNull(_columnIndexOfPaymentTerms)) {
            _tmpPaymentTerms = null
          } else {
            _tmpPaymentTerms = _stmt.getText(_columnIndexOfPaymentTerms)
          }
          val _tmpTaxId: String?
          if (_stmt.isNull(_columnIndexOfTaxId)) {
            _tmpTaxId = null
          } else {
            _tmpTaxId = _stmt.getText(_columnIndexOfTaxId)
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
              VendorEntity(_tmpVendorId,_tmpAccountId,_tmpName,_tmpEmail,_tmpPhone,_tmpAddress,_tmpPaymentTerms,_tmpTaxId,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE vendors SET deleted_at = ?, updated_at = ? WHERE vendor_id = ?"
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
