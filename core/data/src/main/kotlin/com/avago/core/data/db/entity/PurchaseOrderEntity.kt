package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchase_orders")
data class PurchaseOrderEntity(
    @PrimaryKey
    @ColumnInfo(name = "po_id")
    val poId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "po_number")
    val poNumber: String?,

    @ColumnInfo(name = "vendor_id")
    val vendorId: String?,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "subtotal")
    val subtotal: Double?,

    @ColumnInfo(name = "tax_total")
    val taxTotal: Double?,

    @ColumnInfo(name = "shipping_cost")
    val shippingCost: Double?,

    @ColumnInfo(name = "discount")
    val discount: Double?,

    @ColumnInfo(name = "grand_total")
    val grandTotal: Double?,

    @ColumnInfo(name = "base_grand_total")
    val baseGrandTotal: Double?,

    @ColumnInfo(name = "exchange_rate_used")
    val exchangeRateUsed: Double?,

    @ColumnInfo(name = "expected_delivery")
    val expectedDelivery: String?,

    @ColumnInfo(name = "ship_to_location_id")
    val shipToLocationId: String?,

    @ColumnInfo(name = "work_order_id")
    val workOrderId: String?,

    @ColumnInfo(name = "asset_id")
    val assetId: String?,

    @ColumnInfo(name = "requested_by")
    val requestedBy: String?,

    @ColumnInfo(name = "approved_by")
    val approvedBy: String?,

    @ColumnInfo(name = "approved_at")
    val approvedAt: Long?,

    @ColumnInfo(name = "ordered_at")
    val orderedAt: Long?,

    @ColumnInfo(name = "closed_at")
    val closedAt: Long?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "vendor_invoice_no")
    val vendorInvoiceNo: String?,

    @ColumnInfo(name = "created_by")
    val createdBy: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "seq")
    val seq: Long?,
)
