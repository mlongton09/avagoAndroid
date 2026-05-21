package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.PurchaseOrderEntity
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
public class PurchaseOrderDao_Impl(
  __db: RoomDatabase,
) : PurchaseOrderDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPurchaseOrderEntity: EntityInsertAdapter<PurchaseOrderEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPurchaseOrderEntity = object : EntityInsertAdapter<PurchaseOrderEntity>()
        {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `purchase_orders` (`po_id`,`account_id`,`po_number`,`vendor_id`,`status`,`currency`,`subtotal`,`tax_total`,`shipping_cost`,`discount`,`grand_total`,`base_grand_total`,`exchange_rate_used`,`expected_delivery`,`ship_to_location_id`,`work_order_id`,`asset_id`,`requested_by`,`approved_by`,`approved_at`,`ordered_at`,`closed_at`,`notes`,`vendor_invoice_no`,`created_by`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PurchaseOrderEntity) {
        statement.bindText(1, entity.poId)
        statement.bindText(2, entity.accountId)
        val _tmpPoNumber: String? = entity.poNumber
        if (_tmpPoNumber == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpPoNumber)
        }
        val _tmpVendorId: String? = entity.vendorId
        if (_tmpVendorId == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpVendorId)
        }
        statement.bindText(5, entity.status)
        val _tmpCurrency: String? = entity.currency
        if (_tmpCurrency == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpCurrency)
        }
        val _tmpSubtotal: Double? = entity.subtotal
        if (_tmpSubtotal == null) {
          statement.bindNull(7)
        } else {
          statement.bindDouble(7, _tmpSubtotal)
        }
        val _tmpTaxTotal: Double? = entity.taxTotal
        if (_tmpTaxTotal == null) {
          statement.bindNull(8)
        } else {
          statement.bindDouble(8, _tmpTaxTotal)
        }
        val _tmpShippingCost: Double? = entity.shippingCost
        if (_tmpShippingCost == null) {
          statement.bindNull(9)
        } else {
          statement.bindDouble(9, _tmpShippingCost)
        }
        val _tmpDiscount: Double? = entity.discount
        if (_tmpDiscount == null) {
          statement.bindNull(10)
        } else {
          statement.bindDouble(10, _tmpDiscount)
        }
        val _tmpGrandTotal: Double? = entity.grandTotal
        if (_tmpGrandTotal == null) {
          statement.bindNull(11)
        } else {
          statement.bindDouble(11, _tmpGrandTotal)
        }
        val _tmpBaseGrandTotal: Double? = entity.baseGrandTotal
        if (_tmpBaseGrandTotal == null) {
          statement.bindNull(12)
        } else {
          statement.bindDouble(12, _tmpBaseGrandTotal)
        }
        val _tmpExchangeRateUsed: Double? = entity.exchangeRateUsed
        if (_tmpExchangeRateUsed == null) {
          statement.bindNull(13)
        } else {
          statement.bindDouble(13, _tmpExchangeRateUsed)
        }
        val _tmpExpectedDelivery: String? = entity.expectedDelivery
        if (_tmpExpectedDelivery == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpExpectedDelivery)
        }
        val _tmpShipToLocationId: String? = entity.shipToLocationId
        if (_tmpShipToLocationId == null) {
          statement.bindNull(15)
        } else {
          statement.bindText(15, _tmpShipToLocationId)
        }
        val _tmpWorkOrderId: String? = entity.workOrderId
        if (_tmpWorkOrderId == null) {
          statement.bindNull(16)
        } else {
          statement.bindText(16, _tmpWorkOrderId)
        }
        val _tmpAssetId: String? = entity.assetId
        if (_tmpAssetId == null) {
          statement.bindNull(17)
        } else {
          statement.bindText(17, _tmpAssetId)
        }
        val _tmpRequestedBy: String? = entity.requestedBy
        if (_tmpRequestedBy == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpRequestedBy)
        }
        val _tmpApprovedBy: String? = entity.approvedBy
        if (_tmpApprovedBy == null) {
          statement.bindNull(19)
        } else {
          statement.bindText(19, _tmpApprovedBy)
        }
        val _tmpApprovedAt: Long? = entity.approvedAt
        if (_tmpApprovedAt == null) {
          statement.bindNull(20)
        } else {
          statement.bindLong(20, _tmpApprovedAt)
        }
        val _tmpOrderedAt: Long? = entity.orderedAt
        if (_tmpOrderedAt == null) {
          statement.bindNull(21)
        } else {
          statement.bindLong(21, _tmpOrderedAt)
        }
        val _tmpClosedAt: Long? = entity.closedAt
        if (_tmpClosedAt == null) {
          statement.bindNull(22)
        } else {
          statement.bindLong(22, _tmpClosedAt)
        }
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(23)
        } else {
          statement.bindText(23, _tmpNotes)
        }
        val _tmpVendorInvoiceNo: String? = entity.vendorInvoiceNo
        if (_tmpVendorInvoiceNo == null) {
          statement.bindNull(24)
        } else {
          statement.bindText(24, _tmpVendorInvoiceNo)
        }
        val _tmpCreatedBy: String? = entity.createdBy
        if (_tmpCreatedBy == null) {
          statement.bindNull(25)
        } else {
          statement.bindText(25, _tmpCreatedBy)
        }
        statement.bindLong(26, entity.createdAt)
        statement.bindLong(27, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(28)
        } else {
          statement.bindLong(28, _tmpDeletedAt)
        }
        statement.bindLong(29, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(30)
        } else {
          statement.bindLong(30, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: PurchaseOrderEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfPurchaseOrderEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<PurchaseOrderEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPurchaseOrderEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<PurchaseOrderEntity>> {
    val _sql: String = "SELECT * FROM purchase_orders WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("purchase_orders")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfPoId: Int = getColumnIndexOrThrow(_stmt, "po_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfPoNumber: Int = getColumnIndexOrThrow(_stmt, "po_number")
        val _columnIndexOfVendorId: Int = getColumnIndexOrThrow(_stmt, "vendor_id")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfSubtotal: Int = getColumnIndexOrThrow(_stmt, "subtotal")
        val _columnIndexOfTaxTotal: Int = getColumnIndexOrThrow(_stmt, "tax_total")
        val _columnIndexOfShippingCost: Int = getColumnIndexOrThrow(_stmt, "shipping_cost")
        val _columnIndexOfDiscount: Int = getColumnIndexOrThrow(_stmt, "discount")
        val _columnIndexOfGrandTotal: Int = getColumnIndexOrThrow(_stmt, "grand_total")
        val _columnIndexOfBaseGrandTotal: Int = getColumnIndexOrThrow(_stmt, "base_grand_total")
        val _columnIndexOfExchangeRateUsed: Int = getColumnIndexOrThrow(_stmt, "exchange_rate_used")
        val _columnIndexOfExpectedDelivery: Int = getColumnIndexOrThrow(_stmt, "expected_delivery")
        val _columnIndexOfShipToLocationId: Int = getColumnIndexOrThrow(_stmt,
            "ship_to_location_id")
        val _columnIndexOfWorkOrderId: Int = getColumnIndexOrThrow(_stmt, "work_order_id")
        val _columnIndexOfAssetId: Int = getColumnIndexOrThrow(_stmt, "asset_id")
        val _columnIndexOfRequestedBy: Int = getColumnIndexOrThrow(_stmt, "requested_by")
        val _columnIndexOfApprovedBy: Int = getColumnIndexOrThrow(_stmt, "approved_by")
        val _columnIndexOfApprovedAt: Int = getColumnIndexOrThrow(_stmt, "approved_at")
        val _columnIndexOfOrderedAt: Int = getColumnIndexOrThrow(_stmt, "ordered_at")
        val _columnIndexOfClosedAt: Int = getColumnIndexOrThrow(_stmt, "closed_at")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfVendorInvoiceNo: Int = getColumnIndexOrThrow(_stmt, "vendor_invoice_no")
        val _columnIndexOfCreatedBy: Int = getColumnIndexOrThrow(_stmt, "created_by")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<PurchaseOrderEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PurchaseOrderEntity
          val _tmpPoId: String
          _tmpPoId = _stmt.getText(_columnIndexOfPoId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpPoNumber: String?
          if (_stmt.isNull(_columnIndexOfPoNumber)) {
            _tmpPoNumber = null
          } else {
            _tmpPoNumber = _stmt.getText(_columnIndexOfPoNumber)
          }
          val _tmpVendorId: String?
          if (_stmt.isNull(_columnIndexOfVendorId)) {
            _tmpVendorId = null
          } else {
            _tmpVendorId = _stmt.getText(_columnIndexOfVendorId)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpSubtotal: Double?
          if (_stmt.isNull(_columnIndexOfSubtotal)) {
            _tmpSubtotal = null
          } else {
            _tmpSubtotal = _stmt.getDouble(_columnIndexOfSubtotal)
          }
          val _tmpTaxTotal: Double?
          if (_stmt.isNull(_columnIndexOfTaxTotal)) {
            _tmpTaxTotal = null
          } else {
            _tmpTaxTotal = _stmt.getDouble(_columnIndexOfTaxTotal)
          }
          val _tmpShippingCost: Double?
          if (_stmt.isNull(_columnIndexOfShippingCost)) {
            _tmpShippingCost = null
          } else {
            _tmpShippingCost = _stmt.getDouble(_columnIndexOfShippingCost)
          }
          val _tmpDiscount: Double?
          if (_stmt.isNull(_columnIndexOfDiscount)) {
            _tmpDiscount = null
          } else {
            _tmpDiscount = _stmt.getDouble(_columnIndexOfDiscount)
          }
          val _tmpGrandTotal: Double?
          if (_stmt.isNull(_columnIndexOfGrandTotal)) {
            _tmpGrandTotal = null
          } else {
            _tmpGrandTotal = _stmt.getDouble(_columnIndexOfGrandTotal)
          }
          val _tmpBaseGrandTotal: Double?
          if (_stmt.isNull(_columnIndexOfBaseGrandTotal)) {
            _tmpBaseGrandTotal = null
          } else {
            _tmpBaseGrandTotal = _stmt.getDouble(_columnIndexOfBaseGrandTotal)
          }
          val _tmpExchangeRateUsed: Double?
          if (_stmt.isNull(_columnIndexOfExchangeRateUsed)) {
            _tmpExchangeRateUsed = null
          } else {
            _tmpExchangeRateUsed = _stmt.getDouble(_columnIndexOfExchangeRateUsed)
          }
          val _tmpExpectedDelivery: String?
          if (_stmt.isNull(_columnIndexOfExpectedDelivery)) {
            _tmpExpectedDelivery = null
          } else {
            _tmpExpectedDelivery = _stmt.getText(_columnIndexOfExpectedDelivery)
          }
          val _tmpShipToLocationId: String?
          if (_stmt.isNull(_columnIndexOfShipToLocationId)) {
            _tmpShipToLocationId = null
          } else {
            _tmpShipToLocationId = _stmt.getText(_columnIndexOfShipToLocationId)
          }
          val _tmpWorkOrderId: String?
          if (_stmt.isNull(_columnIndexOfWorkOrderId)) {
            _tmpWorkOrderId = null
          } else {
            _tmpWorkOrderId = _stmt.getText(_columnIndexOfWorkOrderId)
          }
          val _tmpAssetId: String?
          if (_stmt.isNull(_columnIndexOfAssetId)) {
            _tmpAssetId = null
          } else {
            _tmpAssetId = _stmt.getText(_columnIndexOfAssetId)
          }
          val _tmpRequestedBy: String?
          if (_stmt.isNull(_columnIndexOfRequestedBy)) {
            _tmpRequestedBy = null
          } else {
            _tmpRequestedBy = _stmt.getText(_columnIndexOfRequestedBy)
          }
          val _tmpApprovedBy: String?
          if (_stmt.isNull(_columnIndexOfApprovedBy)) {
            _tmpApprovedBy = null
          } else {
            _tmpApprovedBy = _stmt.getText(_columnIndexOfApprovedBy)
          }
          val _tmpApprovedAt: Long?
          if (_stmt.isNull(_columnIndexOfApprovedAt)) {
            _tmpApprovedAt = null
          } else {
            _tmpApprovedAt = _stmt.getLong(_columnIndexOfApprovedAt)
          }
          val _tmpOrderedAt: Long?
          if (_stmt.isNull(_columnIndexOfOrderedAt)) {
            _tmpOrderedAt = null
          } else {
            _tmpOrderedAt = _stmt.getLong(_columnIndexOfOrderedAt)
          }
          val _tmpClosedAt: Long?
          if (_stmt.isNull(_columnIndexOfClosedAt)) {
            _tmpClosedAt = null
          } else {
            _tmpClosedAt = _stmt.getLong(_columnIndexOfClosedAt)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpVendorInvoiceNo: String?
          if (_stmt.isNull(_columnIndexOfVendorInvoiceNo)) {
            _tmpVendorInvoiceNo = null
          } else {
            _tmpVendorInvoiceNo = _stmt.getText(_columnIndexOfVendorInvoiceNo)
          }
          val _tmpCreatedBy: String?
          if (_stmt.isNull(_columnIndexOfCreatedBy)) {
            _tmpCreatedBy = null
          } else {
            _tmpCreatedBy = _stmt.getText(_columnIndexOfCreatedBy)
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
              PurchaseOrderEntity(_tmpPoId,_tmpAccountId,_tmpPoNumber,_tmpVendorId,_tmpStatus,_tmpCurrency,_tmpSubtotal,_tmpTaxTotal,_tmpShippingCost,_tmpDiscount,_tmpGrandTotal,_tmpBaseGrandTotal,_tmpExchangeRateUsed,_tmpExpectedDelivery,_tmpShipToLocationId,_tmpWorkOrderId,_tmpAssetId,_tmpRequestedBy,_tmpApprovedBy,_tmpApprovedAt,_tmpOrderedAt,_tmpClosedAt,_tmpNotes,_tmpVendorInvoiceNo,_tmpCreatedBy,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): PurchaseOrderEntity? {
    val _sql: String = "SELECT * FROM purchase_orders WHERE po_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfPoId: Int = getColumnIndexOrThrow(_stmt, "po_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfPoNumber: Int = getColumnIndexOrThrow(_stmt, "po_number")
        val _columnIndexOfVendorId: Int = getColumnIndexOrThrow(_stmt, "vendor_id")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfSubtotal: Int = getColumnIndexOrThrow(_stmt, "subtotal")
        val _columnIndexOfTaxTotal: Int = getColumnIndexOrThrow(_stmt, "tax_total")
        val _columnIndexOfShippingCost: Int = getColumnIndexOrThrow(_stmt, "shipping_cost")
        val _columnIndexOfDiscount: Int = getColumnIndexOrThrow(_stmt, "discount")
        val _columnIndexOfGrandTotal: Int = getColumnIndexOrThrow(_stmt, "grand_total")
        val _columnIndexOfBaseGrandTotal: Int = getColumnIndexOrThrow(_stmt, "base_grand_total")
        val _columnIndexOfExchangeRateUsed: Int = getColumnIndexOrThrow(_stmt, "exchange_rate_used")
        val _columnIndexOfExpectedDelivery: Int = getColumnIndexOrThrow(_stmt, "expected_delivery")
        val _columnIndexOfShipToLocationId: Int = getColumnIndexOrThrow(_stmt,
            "ship_to_location_id")
        val _columnIndexOfWorkOrderId: Int = getColumnIndexOrThrow(_stmt, "work_order_id")
        val _columnIndexOfAssetId: Int = getColumnIndexOrThrow(_stmt, "asset_id")
        val _columnIndexOfRequestedBy: Int = getColumnIndexOrThrow(_stmt, "requested_by")
        val _columnIndexOfApprovedBy: Int = getColumnIndexOrThrow(_stmt, "approved_by")
        val _columnIndexOfApprovedAt: Int = getColumnIndexOrThrow(_stmt, "approved_at")
        val _columnIndexOfOrderedAt: Int = getColumnIndexOrThrow(_stmt, "ordered_at")
        val _columnIndexOfClosedAt: Int = getColumnIndexOrThrow(_stmt, "closed_at")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfVendorInvoiceNo: Int = getColumnIndexOrThrow(_stmt, "vendor_invoice_no")
        val _columnIndexOfCreatedBy: Int = getColumnIndexOrThrow(_stmt, "created_by")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: PurchaseOrderEntity?
        if (_stmt.step()) {
          val _tmpPoId: String
          _tmpPoId = _stmt.getText(_columnIndexOfPoId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpPoNumber: String?
          if (_stmt.isNull(_columnIndexOfPoNumber)) {
            _tmpPoNumber = null
          } else {
            _tmpPoNumber = _stmt.getText(_columnIndexOfPoNumber)
          }
          val _tmpVendorId: String?
          if (_stmt.isNull(_columnIndexOfVendorId)) {
            _tmpVendorId = null
          } else {
            _tmpVendorId = _stmt.getText(_columnIndexOfVendorId)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpSubtotal: Double?
          if (_stmt.isNull(_columnIndexOfSubtotal)) {
            _tmpSubtotal = null
          } else {
            _tmpSubtotal = _stmt.getDouble(_columnIndexOfSubtotal)
          }
          val _tmpTaxTotal: Double?
          if (_stmt.isNull(_columnIndexOfTaxTotal)) {
            _tmpTaxTotal = null
          } else {
            _tmpTaxTotal = _stmt.getDouble(_columnIndexOfTaxTotal)
          }
          val _tmpShippingCost: Double?
          if (_stmt.isNull(_columnIndexOfShippingCost)) {
            _tmpShippingCost = null
          } else {
            _tmpShippingCost = _stmt.getDouble(_columnIndexOfShippingCost)
          }
          val _tmpDiscount: Double?
          if (_stmt.isNull(_columnIndexOfDiscount)) {
            _tmpDiscount = null
          } else {
            _tmpDiscount = _stmt.getDouble(_columnIndexOfDiscount)
          }
          val _tmpGrandTotal: Double?
          if (_stmt.isNull(_columnIndexOfGrandTotal)) {
            _tmpGrandTotal = null
          } else {
            _tmpGrandTotal = _stmt.getDouble(_columnIndexOfGrandTotal)
          }
          val _tmpBaseGrandTotal: Double?
          if (_stmt.isNull(_columnIndexOfBaseGrandTotal)) {
            _tmpBaseGrandTotal = null
          } else {
            _tmpBaseGrandTotal = _stmt.getDouble(_columnIndexOfBaseGrandTotal)
          }
          val _tmpExchangeRateUsed: Double?
          if (_stmt.isNull(_columnIndexOfExchangeRateUsed)) {
            _tmpExchangeRateUsed = null
          } else {
            _tmpExchangeRateUsed = _stmt.getDouble(_columnIndexOfExchangeRateUsed)
          }
          val _tmpExpectedDelivery: String?
          if (_stmt.isNull(_columnIndexOfExpectedDelivery)) {
            _tmpExpectedDelivery = null
          } else {
            _tmpExpectedDelivery = _stmt.getText(_columnIndexOfExpectedDelivery)
          }
          val _tmpShipToLocationId: String?
          if (_stmt.isNull(_columnIndexOfShipToLocationId)) {
            _tmpShipToLocationId = null
          } else {
            _tmpShipToLocationId = _stmt.getText(_columnIndexOfShipToLocationId)
          }
          val _tmpWorkOrderId: String?
          if (_stmt.isNull(_columnIndexOfWorkOrderId)) {
            _tmpWorkOrderId = null
          } else {
            _tmpWorkOrderId = _stmt.getText(_columnIndexOfWorkOrderId)
          }
          val _tmpAssetId: String?
          if (_stmt.isNull(_columnIndexOfAssetId)) {
            _tmpAssetId = null
          } else {
            _tmpAssetId = _stmt.getText(_columnIndexOfAssetId)
          }
          val _tmpRequestedBy: String?
          if (_stmt.isNull(_columnIndexOfRequestedBy)) {
            _tmpRequestedBy = null
          } else {
            _tmpRequestedBy = _stmt.getText(_columnIndexOfRequestedBy)
          }
          val _tmpApprovedBy: String?
          if (_stmt.isNull(_columnIndexOfApprovedBy)) {
            _tmpApprovedBy = null
          } else {
            _tmpApprovedBy = _stmt.getText(_columnIndexOfApprovedBy)
          }
          val _tmpApprovedAt: Long?
          if (_stmt.isNull(_columnIndexOfApprovedAt)) {
            _tmpApprovedAt = null
          } else {
            _tmpApprovedAt = _stmt.getLong(_columnIndexOfApprovedAt)
          }
          val _tmpOrderedAt: Long?
          if (_stmt.isNull(_columnIndexOfOrderedAt)) {
            _tmpOrderedAt = null
          } else {
            _tmpOrderedAt = _stmt.getLong(_columnIndexOfOrderedAt)
          }
          val _tmpClosedAt: Long?
          if (_stmt.isNull(_columnIndexOfClosedAt)) {
            _tmpClosedAt = null
          } else {
            _tmpClosedAt = _stmt.getLong(_columnIndexOfClosedAt)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpVendorInvoiceNo: String?
          if (_stmt.isNull(_columnIndexOfVendorInvoiceNo)) {
            _tmpVendorInvoiceNo = null
          } else {
            _tmpVendorInvoiceNo = _stmt.getText(_columnIndexOfVendorInvoiceNo)
          }
          val _tmpCreatedBy: String?
          if (_stmt.isNull(_columnIndexOfCreatedBy)) {
            _tmpCreatedBy = null
          } else {
            _tmpCreatedBy = _stmt.getText(_columnIndexOfCreatedBy)
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
              PurchaseOrderEntity(_tmpPoId,_tmpAccountId,_tmpPoNumber,_tmpVendorId,_tmpStatus,_tmpCurrency,_tmpSubtotal,_tmpTaxTotal,_tmpShippingCost,_tmpDiscount,_tmpGrandTotal,_tmpBaseGrandTotal,_tmpExchangeRateUsed,_tmpExpectedDelivery,_tmpShipToLocationId,_tmpWorkOrderId,_tmpAssetId,_tmpRequestedBy,_tmpApprovedBy,_tmpApprovedAt,_tmpOrderedAt,_tmpClosedAt,_tmpNotes,_tmpVendorInvoiceNo,_tmpCreatedBy,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE purchase_orders SET deleted_at = ?, updated_at = ? WHERE po_id = ?"
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
