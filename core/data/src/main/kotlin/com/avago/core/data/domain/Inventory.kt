package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/** A stocked part or consumable (v2 `products`). */
data class Product(
    val id: String,
    val sku: String,
    val name: String,
    val categoryId: String?,
    val categoryName: String?,
    val uomCode: String,
    val trackingMode: TrackingMode,
    val manufacturer: String?,
    val modelNumber: String?,
    val gtin: String?,
    val standardUnitCost: BigDecimal?,
    val costCurrency: String?,
    val defaultReorderQty: BigDecimal?,
    val abcClass: String?,
    val notes: String?,
    val archived: Boolean,
    val version: Long,
)

enum class TrackingMode(val wire: String) {
    NONE("none"), LOT("lot"), SERIAL("serial");

    companion object {
        fun fromWire(raw: String?) = entries.firstOrNull { it.wire == raw } ?: NONE
    }
}

enum class StockCondition(val wire: String) {
    GOOD("good"), DAMAGED("damaged"), QUARANTINE("quarantine"), IN_REPAIR("in_repair");

    companion object {
        fun fromWire(raw: String?) = entries.firstOrNull { it.wire == raw } ?: GOOD
    }
}

/** On-hand quantity of one product at one location/bin/lot/condition (v2 `stock_positions`). */
data class StockPosition(
    val id: String,
    val productId: String,
    val locationId: String,
    val locationName: String?,
    val binId: String?,
    val binCode: String?,
    val lotId: String?,
    val condition: StockCondition,
    val onHand: BigDecimal,
    val version: Long,
)

/** Min/max/reorder levels of a product at a location (v2 `stocking_policies`). */
data class StockingPolicy(
    val id: String,
    val productId: String,
    val locationId: String,
    val minQty: BigDecimal?,
    val maxQty: BigDecimal?,
    val reorderQty: BigDecimal?,
    val economicOrderQty: BigDecimal?,
    val safetyQty: BigDecimal?,
    val version: Long,
) {
    /** The level at or below which the product needs reordering (min + safety). */
    val reorderPoint: BigDecimal get() = (minQty ?: BigDecimal.ZERO) + (safetyQty ?: BigDecimal.ZERO)
}

enum class StockStatus { IN_STOCK, LOW, OUT }

/** A product with its account-wide stock picture, for lists. */
data class ProductStock(
    val product: Product,
    val onHand: BigDecimal,
    val policies: List<StockingPolicy>,
) {
    val reorderPoint: BigDecimal get() = policies.fold(BigDecimal.ZERO) { acc, p -> acc + p.reorderPoint }

    val status: StockStatus
        get() = when {
            onHand.signum() <= 0 -> StockStatus.OUT
            policies.isNotEmpty() && onHand <= reorderPoint -> StockStatus.LOW
            else -> StockStatus.IN_STOCK
        }

    val needsReorder: Boolean get() = policies.isNotEmpty() && onHand <= reorderPoint
}

enum class MovementKind(val wire: String) {
    RECEIPT("receipt"), ISSUE("issue"), RETURN("return"), TRANSFER("transfer"),
    TRANSFER_DISPATCH("transfer_dispatch"), TRANSFER_RECEIPT("transfer_receipt"),
    ADJUSTMENT("adjustment"), COUNT("count"), OPENING_BALANCE("opening_balance");

    companion object {
        fun fromWire(raw: String?) = entries.firstOrNull { it.wire == raw } ?: ADJUSTMENT
    }
}

enum class MovementStatus(val wire: String) {
    DRAFT("draft"), REQUESTED("requested"), APPROVED("approved"), DECLINED("declined"),
    POSTED("posted"), CANCELLED("cancelled");

    companion object {
        fun fromWire(raw: String?) = entries.firstOrNull { it.wire == raw } ?: DRAFT
    }
}

/**
 * A stock document (v2 `stock_movements` + lines): receipts (GRNs), issues, returns, transfers
 * and transfer requests, adjustments and cycle counts (kind = count).
 */
data class StockMovement(
    val id: String,
    val kind: MovementKind,
    val status: MovementStatus,
    val number: String?,
    val locationId: String,
    val toLocationId: String?,
    val effectiveAt: Long?,
    val postedAt: Long?,
    val requestedBy: String?,
    val approvedBy: String?,
    val decisionNote: String?,
    val purchaseOrderId: String?,
    val workOrderId: String?,
    val countScope: String?,
    val blindCount: Boolean,
    val countedBy: String?,
    val startedAt: Long?,
    val completedAt: Long?,
    val scheduledOn: String?,
    val carrier: String?,
    val trackingNumber: String?,
    val packingSlipNo: String?,
    val reference: String?,
    val notes: String?,
    val createdAt: Long,
    val createdBy: String?,
    val version: Long,
    val lines: List<StockMovementLine>,
    /** Set on a reversing document: the document it reverses. */
    val reversesMovementId: String? = null,
    /** Set when a posted reversal of this document exists. */
    val reversedById: String? = null,
) {
    val isReversible: Boolean
        get() = status == MovementStatus.POSTED && reversesMovementId == null && reversedById == null

    /** Cycle-count progress (kind = count): planned, counting, counted (awaiting posting), posted, cancelled. */
    val countState: CycleCountState
        get() = when {
            status == MovementStatus.CANCELLED -> CycleCountState.CANCELLED
            status == MovementStatus.POSTED -> CycleCountState.POSTED
            completedAt != null -> CycleCountState.COUNTED
            startedAt != null -> CycleCountState.COUNTING
            else -> CycleCountState.PLANNED
        }
}

enum class CycleCountState { PLANNED, COUNTING, COUNTED, POSTED, CANCELLED }

enum class CountScope(val wire: String) {
    FULL("full"), CATEGORY("category"), BIN("bin"), PART_LIST("part_list"),
    ABC_A("abc_a"), ABC_B("abc_b"), ABC_C("abc_c");

    companion object {
        fun fromWire(raw: String?) = entries.firstOrNull { it.wire == raw }
    }
}

data class StockMovementLine(
    val id: String,
    val movementId: String,
    val lineNo: Int,
    val productId: String,
    val productName: String?,
    val productSku: String?,
    val uomCode: String?,
    val locationId: String?,
    val binId: String?,
    val toBinId: String?,
    val lotId: String?,
    val condition: StockCondition,
    val requestedQty: BigDecimal?,
    val quantity: BigDecimal?,
    val expectedQty: BigDecimal?,
    val countedQty: BigDecimal?,
    val countedAt: Long?,
    val countedBy: String?,
    val recountRequired: Boolean,
    val purchaseOrderLineId: String?,
    val workOrderMaterialId: String?,
    val discrepancyQty: BigDecimal?,
    val discrepancyReason: String?,
    val reasonCode: String?,
    val unitCost: BigDecimal?,
    val notes: String?,
    val version: Long,
)

/** One posted ledger leg for a product (transaction history). */
data class StockLedgerEntry(
    val id: String,
    val movementId: String,
    val kind: MovementKind,
    val number: String?,
    val productId: String,
    val locationId: String?,
    val binId: String?,
    /** Signed: positive into the position, negative out. */
    val quantity: BigDecimal,
    val effectiveAt: Long,
    val postedAt: Long,
    val workOrderId: String?,
    val purchaseOrderId: String?,
    val reference: String?,
    val notes: String?,
    val isReversal: Boolean,
)

/** A suggested replenishment computed server-side (v2 `reorder_suggestions`). */
data class ReorderSuggestion(
    val id: String,
    val productId: String,
    val locationId: String,
    val onHand: BigDecimal,
    val minQty: BigDecimal?,
    val suggestedQty: BigDecimal,
    val supplierPartyId: String?,
    val supplierLeadTimeDays: Int?,
    val suggestedUnitCost: BigDecimal?,
    val suggestedCurrency: String?,
    val reasonCode: String?,
    val generatedAt: Long,
)

data class Category(
    val id: String,
    val kind: String,
    val name: String,
    val parentId: String?,
    val color: String?,
    val icon: String?,
    val defaultPriority: String?,
    val systemCode: String?,
)

/** Products, stock, stocking policies and stock documents of the signed-in account. */
interface InventoryRepository {
    fun observeProductStock(includeArchived: Boolean = false): Flow<List<ProductStock>>

    fun observeProduct(id: String): Flow<Product?>

    suspend fun product(id: String): Product?

    /** Matches a scanned code against GTIN, SKU and supplier SKUs. */
    suspend fun productByCode(code: String): Product?

    fun observePositions(productId: String? = null, locationId: String? = null, binId: String? = null): Flow<List<StockPosition>>

    fun observePolicies(productId: String? = null): Flow<List<StockingPolicy>>

    fun observeLedger(productId: String, limit: Int = 200): Flow<List<StockLedgerEntry>>

    fun observeMovements(kinds: Set<MovementKind>, statuses: Set<MovementStatus>? = null): Flow<List<StockMovement>>

    fun observeMovement(id: String): Flow<StockMovement?>

    suspend fun movement(id: String): StockMovement?

    fun observeReorderSuggestions(): Flow<List<ReorderSuggestion>>

    fun observeCategories(kind: String): Flow<List<Category>>

    /**
     * Creates and posts a stock document (receipt, issue, return, transfer, adjustment, count) as
     * one `stock.documents.create_and_post` command. Stock levels change optimistically at once and
     * roll back if the server rejects the document. Returns the command id.
     */
    suspend fun postStockDocument(draft: StockDocumentDraft): String

    /**
     * Reverses a posted document (receipt, issue, part issue, transfer, adjustment, count) with
     * the matching catalog command; the stock effect is undone optimistically. Returns the id of
     * the reversing document.
     */
    suspend fun reverseDocument(movementId: String): String

    /** Issues parts from stock to a work order (`stock.part_issues.create`). Returns the document id. */
    suspend fun issueToWorkOrder(draft: PartIssueDraft): String

    /** Creates a part; returns its id. */
    suspend fun createProduct(draft: ProductDraft): String

    /**
     * Saves the changes between the stored product and [draft]. Fields emptied in the draft are
     * cleared where the catalog allows it ([FieldNotClearableException] otherwise).
     */
    suspend fun updateProduct(id: String, draft: ProductDraft)

    suspend fun setProductArchived(id: String, archived: Boolean)

    suspend fun deleteProduct(id: String)

    /** Plans a cycle count at [locationId]; returns its id. */
    suspend fun planCycleCount(draft: CycleCountDraft): String

    /** Starts counting (the server snapshots the expected quantities into lines). */
    suspend fun startCycleCount(id: String)

    /** Records the counted quantity of a count line (the latest count wins). */
    suspend fun recordCount(lineId: String, countedQty: BigDecimal, notes: String? = null)

    suspend fun completeCycleCount(id: String)

    /** Posts a completed count's variances to stock. */
    suspend fun postCycleCount(id: String)

    suspend fun cancelCycleCount(id: String)
}

/** The editable fields of a part. Blank text means empty. */
data class ProductDraft(
    val name: String,
    val sku: String? = null,
    val manufacturer: String? = null,
    /** Category name; the server creates unknown categories. */
    val category: String? = null,
    val uomCode: String? = null,
    val standardUnitCost: BigDecimal? = null,
    val costCurrency: String? = null,
    val defaultReorderQty: BigDecimal? = null,
    val abcClass: String? = null,
    val gtin: String? = null,
    val notes: String? = null,
)

fun Product.toDraft() = ProductDraft(
    name = name,
    sku = sku,
    manufacturer = manufacturer,
    category = categoryName,
    uomCode = uomCode,
    standardUnitCost = standardUnitCost,
    costCurrency = costCurrency,
    defaultReorderQty = defaultReorderQty,
    abcClass = abcClass,
    gtin = gtin,
    notes = notes,
)

data class CycleCountDraft(
    val locationId: String,
    val scope: CountScope = CountScope.FULL,
    val blindCount: Boolean = false,
    /** ISO date (`YYYY-MM-DD`). */
    val scheduledOn: String? = null,
    val notes: String? = null,
)

data class PartIssueDraft(
    val workOrderId: String,
    val lines: List<PartIssueLineDraft>,
    val locationId: String? = null,
    val issuedToUserId: String? = null,
    val notes: String? = null,
)

data class PartIssueLineDraft(
    val productId: String,
    val quantity: BigDecimal,
    val binId: String? = null,
    val unitCost: BigDecimal? = null,
    val currency: String? = null,
)

/** A stock document to create and post. */
data class StockDocumentDraft(
    val kind: MovementKind,
    val locationId: String,
    val lines: List<StockLineDraft>,
    val toLocationId: String? = null,
    val workOrderId: String? = null,
    val purchaseOrderId: String? = null,
    val issuedToUserId: String? = null,
    val reference: String? = null,
    val notes: String? = null,
    /** Epoch ms; null posts at the server's now. */
    val effectiveAt: Long? = null,
    val countScope: String? = null,
)

/**
 * One line of a [StockDocumentDraft]. [quantity] is positive except for adjustments (signed
 * change); counts use [countedQty] instead.
 */
data class StockLineDraft(
    val productId: String,
    val quantity: BigDecimal? = null,
    val locationId: String? = null,
    val binId: String? = null,
    val toBinId: String? = null,
    val lotId: String? = null,
    val condition: StockCondition = StockCondition.GOOD,
    val unitCost: BigDecimal? = null,
    val currency: String? = null,
    val reasonCode: String? = null,
    val notes: String? = null,
    val purchaseOrderLineId: String? = null,
    val workOrderMaterialId: String? = null,
    val countedQty: BigDecimal? = null,
)
