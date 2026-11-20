package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/** A supplier: an organization or person party with a `supplier_profiles` row. */
data class Supplier(
    val partyId: String,
    val name: String,
    val legalName: String?,
    val supplierCode: String?,
    val accountNumber: String?,
    val paymentTerms: String?,
    val currency: String?,
    val leadTimeDays: Int?,
    val isPreferred: Boolean,
    val rating: Int?,
    val email: String?,
    val phone: String?,
    val website: String?,
    val contactName: String?,
    val address: String?,
    val notes: String?,
    val archived: Boolean,
    val version: Long,
)

/** A supplier's offer for a product (v2 `product_suppliers`). */
data class ProductSupplier(
    val id: String,
    val productId: String,
    val supplierPartyId: String,
    val supplierName: String?,
    val supplierSku: String?,
    val description: String?,
    val unitCost: BigDecimal?,
    val currency: String?,
    val minOrderQty: BigDecimal?,
    val packSizeQty: BigDecimal?,
    val leadTimeDays: Int?,
    val isPreferred: Boolean,
    val notes: String?,
    val version: Long,
)

enum class PurchaseOrderStatus(val wire: String) {
    DRAFT("draft"), PENDING_APPROVAL("pending_approval"), APPROVED("approved"), ORDERED("ordered"),
    PARTIALLY_RECEIVED("partially_received"), RECEIVED("received"), CLOSED("closed"), CANCELLED("cancelled");

    val isOpen: Boolean get() = this in setOf(DRAFT, PENDING_APPROVAL, APPROVED, ORDERED, PARTIALLY_RECEIVED)

    companion object {
        fun fromWire(raw: String?) = entries.firstOrNull { it.wire == raw } ?: DRAFT
    }
}

data class PurchaseOrder(
    val id: String,
    val number: String?,
    val supplierPartyId: String,
    val supplierName: String?,
    val status: PurchaseOrderStatus,
    val currency: String,
    val subtotal: BigDecimal,
    val taxTotal: BigDecimal,
    val shipping: BigDecimal,
    val discount: BigDecimal,
    val grandTotal: BigDecimal,
    val expectedOn: String?,
    val shipToLocationId: String?,
    val workOrderId: String?,
    val assetId: String?,
    val requestedBy: String?,
    val approvedBy: String?,
    val approvedAt: Long?,
    val orderedAt: Long?,
    val closedAt: Long?,
    val notes: String?,
    val supplierInvoiceRef: String?,
    val createdAt: Long,
    val version: Long,
    val lines: List<PurchaseOrderLine>,
    val internalNotes: String? = null,
) {
    /** Header fields other than notes/expected date/invoice ref, and lines, change only while draft. */
    val isEditable: Boolean get() = status == PurchaseOrderStatus.DRAFT
    val canSubmit: Boolean get() = status == PurchaseOrderStatus.DRAFT && lines.isNotEmpty()
    val canApprove: Boolean get() = status == PurchaseOrderStatus.PENDING_APPROVAL
    val canMarkOrdered: Boolean get() = status == PurchaseOrderStatus.APPROVED
    val canReceive: Boolean
        get() = status in setOf(PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTIALLY_RECEIVED)
    val canCancel: Boolean get() = status.isOpen && lines.all { it.receivedQty.signum() == 0 }
    val canClose: Boolean get() = status == PurchaseOrderStatus.PARTIALLY_RECEIVED || status == PurchaseOrderStatus.RECEIVED
}

data class PurchaseOrderLine(
    val id: String,
    val purchaseOrderId: String,
    val lineNo: Int,
    val productId: String?,
    val productName: String?,
    val description: String?,
    val supplierSku: String?,
    val orderedQty: BigDecimal,
    val receivedQty: BigDecimal,
    val unitCost: BigDecimal,
    val currency: String,
    val taxRatePct: BigDecimal?,
    val lineTotal: BigDecimal,
    val deliverToLocationId: String?,
    val deliverToBinId: String?,
    val notes: String?,
    val version: Long,
    val workOrderId: String? = null,
) {
    val outstandingQty: BigDecimal get() = (orderedQty - receivedQty).max(BigDecimal.ZERO)
}

interface SupplierRepository {
    fun observeSuppliers(includeArchived: Boolean = false): Flow<List<Supplier>>

    fun observeSupplier(partyId: String): Flow<Supplier?>

    suspend fun supplier(partyId: String): Supplier?

    fun observeProductSuppliers(productId: String? = null, supplierPartyId: String? = null): Flow<List<ProductSupplier>>

    /** Creates a vendor; returns its party id. */
    suspend fun createSupplier(draft: SupplierDraft): String

    /** Saves the changes between the stored vendor and [draft] (emptied fields are cleared). */
    suspend fun updateSupplier(partyId: String, draft: SupplierDraft)

    /** Archives (false) or restores (true) a vendor. */
    suspend fun setSupplierActive(partyId: String, active: Boolean)

    suspend fun deleteSupplier(partyId: String)

    /** Adds a vendor catalog entry (vendor part); returns its id. */
    suspend fun createProductSupplier(draft: ProductSupplierDraft): String

    suspend fun updateProductSupplier(id: String, draft: ProductSupplierDraft)

    suspend fun deleteProductSupplier(id: String)
}

/** The editable fields of a vendor. Blank text means empty. */
data class SupplierDraft(
    val name: String,
    val supplierCode: String? = null,
    val accountNumber: String? = null,
    val paymentTerms: String? = null,
    val currency: String? = null,
    val leadTimeDays: Int? = null,
    val isPreferred: Boolean = false,
    val rating: Int? = null,
    /** Initial contact person (create only; contacts are managed separately afterwards). */
    val contactName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val address: String? = null,
    val notes: String? = null,
)

fun Supplier.toDraft() = SupplierDraft(
    name = name,
    supplierCode = supplierCode,
    accountNumber = accountNumber,
    paymentTerms = paymentTerms,
    currency = currency,
    leadTimeDays = leadTimeDays,
    isPreferred = isPreferred,
    rating = rating,
    contactName = contactName,
    email = email,
    phone = phone,
    website = website,
    address = address,
    notes = notes,
)

data class ProductSupplierDraft(
    val productId: String,
    val supplierPartyId: String,
    val unitCost: BigDecimal,
    val currency: String,
    val supplierSku: String? = null,
    val description: String? = null,
    val minOrderQty: BigDecimal? = null,
    val packSizeQty: BigDecimal? = null,
    val leadTimeDays: Int? = null,
    val isPreferred: Boolean = false,
    val notes: String? = null,
)

interface PurchaseOrderRepository {
    fun observePurchaseOrders(): Flow<List<PurchaseOrder>>

    fun observePurchaseOrder(id: String): Flow<PurchaseOrder?>

    suspend fun purchaseOrder(id: String): PurchaseOrder?

    /** Creates a draft purchase order with its lines; returns its id. */
    suspend fun createPurchaseOrder(draft: PurchaseOrderDraft): String

    /** Saves header changes (emptied optional fields are cleared). */
    suspend fun updatePurchaseOrder(id: String, edit: PurchaseOrderEdit)

    /** Adds a line to a draft order; returns its id. */
    suspend fun addLine(purchaseOrderId: String, line: PurchaseOrderLineDraft): String

    suspend fun updateLine(lineId: String, line: PurchaseOrderLineDraft)

    suspend fun deleteLine(lineId: String)

    suspend fun submit(id: String)

    suspend fun approve(id: String)

    /** Sends an order pending approval back to draft; a [reason] is kept as a comment on the order. */
    suspend fun reject(id: String, reason: String? = null)

    suspend fun markOrdered(id: String)

    suspend fun cancel(id: String)

    suspend fun close(id: String)

    /** Receives purchase-order lines into stock (a posted goods receipt); returns its id. */
    suspend fun receive(draft: GoodsReceiptDraft): String
}

data class PurchaseOrderDraft(
    val supplierPartyId: String,
    val currency: String,
    val lines: List<PurchaseOrderLineDraft>,
    val shipToLocationId: String? = null,
    val workOrderId: String? = null,
    val assetId: String? = null,
    /** ISO date (`YYYY-MM-DD`). */
    val expectedOn: String? = null,
    val notes: String? = null,
)

/** Header edit; [supplierPartyId] and [currency] change only while the order is a draft. */
data class PurchaseOrderEdit(
    val supplierPartyId: String,
    val currency: String,
    val shipToLocationId: String?,
    val workOrderId: String?,
    val assetId: String?,
    val expectedOn: String?,
    val notes: String?,
    val internalNotes: String?,
    val supplierInvoiceRef: String?,
    /** Order-level shipping charge in major units; null or empty is zero. */
    val shipping: BigDecimal? = null,
    /** Order-level discount in major units; null or empty is zero. */
    val discount: BigDecimal? = null,
)

fun PurchaseOrder.toEdit() = PurchaseOrderEdit(
    supplierPartyId = supplierPartyId,
    currency = currency,
    shipToLocationId = shipToLocationId,
    workOrderId = workOrderId,
    assetId = assetId,
    expectedOn = expectedOn,
    notes = notes,
    internalNotes = internalNotes,
    supplierInvoiceRef = supplierInvoiceRef,
    shipping = shipping,
    discount = discount,
)

data class PurchaseOrderLineDraft(
    val description: String,
    val orderedQty: BigDecimal,
    val unitCost: BigDecimal,
    val productId: String? = null,
    val supplierSku: String? = null,
    val taxRatePct: BigDecimal? = null,
    val deliverToLocationId: String? = null,
    val deliverToBinId: String? = null,
    val workOrderId: String? = null,
    val notes: String? = null,
)

data class GoodsReceiptDraft(
    val purchaseOrderId: String,
    val lines: List<ReceiptLineDraft>,
    val locationId: String? = null,
    val carrier: String? = null,
    val trackingNumber: String? = null,
    val packingSlipNo: String? = null,
    val notes: String? = null,
)

data class ReceiptLineDraft(
    val purchaseOrderLineId: String,
    val quantity: BigDecimal,
    val locationId: String? = null,
    val binId: String? = null,
    val discrepancyQty: BigDecimal? = null,
    val discrepancyReason: String? = null,
    val notes: String? = null,
)
