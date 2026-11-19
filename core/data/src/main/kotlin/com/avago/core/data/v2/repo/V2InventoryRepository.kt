package com.avago.core.data.v2.repo

import com.avago.core.data.domain.Category
import com.avago.core.data.domain.CycleCountDraft
import com.avago.core.data.domain.PartIssueDraft
import com.avago.core.data.domain.ProductDraft
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.MovementStatus
import com.avago.core.data.domain.Product
import com.avago.core.data.domain.ProductStock
import com.avago.core.data.domain.ReorderSuggestion
import com.avago.core.data.domain.StockCondition
import com.avago.core.data.domain.StockDocumentDraft
import com.avago.core.data.domain.StockLedgerEntry
import com.avago.core.data.domain.StockMovement
import com.avago.core.data.domain.StockMovementLine
import com.avago.core.data.domain.StockPosition
import com.avago.core.data.domain.StockingPolicy
import com.avago.core.data.domain.TrackingMode
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2Micros
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Uuid
import com.avago.core.data.v2.commands.InventoryCommands
import com.avago.core.data.v2.commands.PostedLeg
import com.avago.core.data.v2.bool
import com.avago.core.data.v2.commands.KnownPosition
import com.avago.core.data.v2.commands.PositionKey
import com.avago.core.data.v2.commands.StockCommands
import com.avago.core.data.v2.decimalOrNull
import com.avago.core.data.v2.int
import com.avago.core.data.v2.intOrNull
import com.avago.core.data.v2.long
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.micros
import com.avago.core.data.v2.microsOrNull
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class V2InventoryRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
) : InventoryRepository {

    override fun observeProductStock(includeArchived: Boolean): Flow<List<ProductStock>> =
        store.observe(setOf("products", "categories", "stock_positions", "stocking_policies"), emptyList()) { conn ->
            val onHand = conn.query(
                "SELECT product_id, SUM(on_hand_qty_micros) AS qty FROM stock_positions GROUP BY product_id",
            ).associate { it.str("product_id") to it.micros("qty") }
            val policies = policies(conn, null).groupBy { it.productId }
            products(conn, includeArchived).map { p ->
                ProductStock(p, onHand[p.id] ?: BigDecimal.ZERO, policies[p.id].orEmpty())
            }
        }

    override fun observeProduct(id: String): Flow<Product?> =
        store.observe(setOf("products", "categories"), null) { conn -> productById(conn, id) }

    override suspend fun product(id: String): Product? = store.read(null) { conn -> productById(conn, id) }

    override suspend fun productByCode(code: String): Product? = store.read(null) { conn ->
        val trimmed = code.trim()
        val direct = V2OverlayMerge.query(conn, "products", "gtin = ? OR sku = ?", listOf(trimmed, trimmed)) {
            it["gtin"] == trimmed || it["sku"] == trimmed
        }.firstOrNull()
        val viaSupplier = direct ?: conn.query(
            "SELECT product_id FROM product_suppliers WHERE supplier_sku = ? AND archived_at IS NULL LIMIT 1",
            listOf(trimmed),
        ).firstOrNull()?.let { productRow(conn, it.str("product_id")) }
        viaSupplier?.let { product(it, categoryNames(conn)) }
    }

    override fun observePositions(productId: String?, locationId: String?, binId: String?): Flow<List<StockPosition>> =
        store.observe(setOf("stock_positions", "locations", "bins"), emptyList()) { conn ->
            positions(conn, productId, locationId, binId)
        }

    override fun observePolicies(productId: String?): Flow<List<StockingPolicy>> =
        store.observe(setOf("stocking_policies"), emptyList()) { conn -> policies(conn, productId) }

    override fun observeLedger(productId: String, limit: Int): Flow<List<StockLedgerEntry>> =
        store.observe(setOf("stock_ledger_legs", "stock_movements", "stock_positions"), emptyList()) { conn ->
            conn.query(
                "SELECT l.id, l.movement_id, l.product_id, l.qty_micros, l.effective_at, l.posted_at, l.reverses_leg_id, " +
                    "m.kind, m.number, m.work_order_id, m.purchase_order_id, m.reference_text, m.notes, " +
                    "p.location_id, p.bin_id FROM stock_ledger_legs l " +
                    "JOIN stock_movements m ON m.id = l.movement_id LEFT JOIN stock_positions p ON p.id = l.position_id " +
                    "WHERE l.product_id = ? ORDER BY l.effective_at DESC, l.id DESC LIMIT ?",
                listOf(productId, limit.toLong()),
            ).map { r ->
                StockLedgerEntry(
                    id = r.str("id"),
                    movementId = r.str("movement_id"),
                    kind = MovementKind.fromWire(r.strOrNull("kind")),
                    number = r.strOrNull("number"),
                    productId = r.str("product_id"),
                    locationId = r.strOrNull("location_id"),
                    binId = r.strOrNull("bin_id"),
                    quantity = r.micros("qty_micros"),
                    effectiveAt = r.long("effective_at"),
                    postedAt = r.long("posted_at"),
                    workOrderId = r.strOrNull("work_order_id"),
                    purchaseOrderId = r.strOrNull("purchase_order_id"),
                    reference = r.strOrNull("reference_text"),
                    notes = r.strOrNull("notes"),
                    isReversal = r.strOrNull("reverses_leg_id") != null,
                )
            }
        }

    override fun observeMovements(kinds: Set<MovementKind>, statuses: Set<MovementStatus>?): Flow<List<StockMovement>> =
        store.observe(setOf("stock_movements", "stock_movement_lines", "products"), emptyList()) { conn ->
            movements(conn, kinds, statuses)
        }

    override fun observeMovement(id: String): Flow<StockMovement?> =
        store.observe(setOf("stock_movements", "stock_movement_lines", "products"), null) { conn -> movementById(conn, id) }

    override suspend fun movement(id: String): StockMovement? = store.read(null) { conn -> movementById(conn, id) }

    override fun observeReorderSuggestions(): Flow<List<ReorderSuggestion>> =
        store.observe(setOf("reorder_suggestions"), emptyList()) { conn ->
            conn.query("SELECT * FROM reorder_suggestions ORDER BY generated_at DESC, id").map { r ->
                ReorderSuggestion(
                    id = r.str("id"),
                    productId = r.str("product_id"),
                    locationId = r.str("location_id"),
                    onHand = r.micros("on_hand_qty_micros"),
                    minQty = r.microsOrNull("min_qty_micros"),
                    suggestedQty = r.micros("suggested_qty_micros"),
                    supplierPartyId = r.strOrNull("supplier_party_id"),
                    supplierLeadTimeDays = r.intOrNull("supplier_lead_time_days"),
                    suggestedUnitCost = r.decimalOrNull("suggested_unit_cost"),
                    suggestedCurrency = r.strOrNull("suggested_currency"),
                    reasonCode = r.strOrNull("reason_code"),
                    generatedAt = r.long("generated_at"),
                )
            }
        }

    override fun observeCategories(kind: String): Flow<List<Category>> =
        store.observe(setOf("categories"), emptyList()) { conn -> categories(conn, kind) }

    override suspend fun postStockDocument(draft: StockDocumentDraft): String {
        val productIds = draft.lines.map { it.productId }.toSet()
        val known = store.read(emptyMap<PositionKey, KnownPosition>()) { conn ->
            productIds.flatMap { positions(conn, it, null, null) }.associate { p ->
                PositionKey(p.productId, p.locationId, p.binId, p.lotId, p.condition.wire) to
                    KnownPosition(p.id, V2Micros.fromDecimal(p.onHand))
            }
        }
        val command = StockCommands.createAndPost(
            draft = draft,
            positions = known::get,
            nowMillis = System.currentTimeMillis(),
            userId = commands.currentUserId(),
        )
        return commands.submit(command)
    }

    override suspend fun reverseDocument(movementId: String): String {
        val (movement, legs) = store.read(null) { conn ->
            movementById(conn, movementId)?.let { m ->
                m to conn.query(
                    "SELECT position_id, qty_micros FROM stock_ledger_legs WHERE movement_id = ?", listOf(movementId),
                ).map { PostedLeg(it.str("position_id"), it.long("qty_micros")) }
            }
        } ?: throw RecordNotFoundException("stock document", movementId)
        val now = System.currentTimeMillis()
        val reversalId = V2Uuid.v7(now)
        commands.submit(InventoryCommands.reverse(movement, reversalId, legs, now, commands.currentUserId()))
        return reversalId
    }

    override suspend fun issueToWorkOrder(draft: PartIssueDraft): String {
        val productIds = draft.lines.map { it.productId }.toSet()
        val stock = store.read(emptyList<StockPosition>()) { conn ->
            productIds.flatMap { positions(conn, it, draft.locationId, null) }
        }.filter { it.condition == StockCondition.GOOD }
        fun positionFor(productId: String, binId: String?) = stock
            .filter { it.productId == productId && (binId == null || it.binId == binId) }
            .maxByOrNull { it.onHand }
        val source = draft.locationId
            ?: draft.lines.firstNotNullOfOrNull { positionFor(it.productId, it.binId)?.locationId }
            ?: throw IllegalArgumentException("Choose the location the parts come from")
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        val command = InventoryCommands.issueToWorkOrder(
            id = id,
            lineIds = draft.lines.map { V2Uuid.v7(now) },
            draft = draft,
            sourceLocationId = source,
            positionFor = { productId, binId ->
                positionFor(productId, binId)?.let { KnownPosition(it.id, V2Micros.fromDecimal(it.onHand)) }
            },
            nowMillis = now,
            userId = commands.currentUserId(),
        )
        commands.submit(command)
        return id
    }

    override suspend fun createProduct(draft: ProductDraft): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        val categoryId = draft.category?.let { categoryIdByName(it) }
        commands.submit(InventoryCommands.createProduct(id, draft, categoryId, now, commands.currentUserId()))
        return id
    }

    override suspend fun updateProduct(id: String, draft: ProductDraft) {
        val stored = product(id) ?: throw RecordNotFoundException("part", id)
        val categoryId = draft.category?.let { categoryIdByName(it) }
        InventoryCommands.updateProduct(stored, draft, categoryId, System.currentTimeMillis())?.let { submit(it) }
    }

    override suspend fun setProductArchived(id: String, archived: Boolean) {
        val stored = product(id) ?: throw RecordNotFoundException("part", id)
        submit(InventoryCommands.setProductArchived(stored, archived, System.currentTimeMillis()))
    }

    override suspend fun deleteProduct(id: String) {
        val stored = product(id) ?: throw RecordNotFoundException("part", id)
        submit(InventoryCommands.deleteProduct(stored))
    }

    override suspend fun planCycleCount(draft: CycleCountDraft): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        submit(InventoryCommands.planCycleCount(id, draft, now, commands.currentUserId()))
        return id
    }

    override suspend fun startCycleCount(id: String) {
        submit(InventoryCommands.startCycleCount(id, System.currentTimeMillis(), commands.currentUserId()))
    }

    override suspend fun recordCount(lineId: String, countedQty: BigDecimal, notes: String?) {
        submit(InventoryCommands.recordCount(lineId, countedQty, notes, System.currentTimeMillis(), commands.currentUserId()))
    }

    override suspend fun completeCycleCount(id: String) {
        submit(InventoryCommands.completeCycleCount(id, System.currentTimeMillis()))
    }

    override suspend fun postCycleCount(id: String) {
        val variances = store.read(emptyMap<String, Long>()) { conn ->
            val count = movementById(conn, id) ?: return@read emptyMap()
            val positions = count.lines.map { it.productId }.toSet()
                .flatMap { positions(conn, it, count.locationId, null) }
            count.lines.mapNotNull { line ->
                val counted = line.countedQty ?: return@mapNotNull null
                val position = positions.firstOrNull {
                    it.productId == line.productId && it.binId == line.binId && it.lotId == line.lotId &&
                        it.condition == line.condition
                } ?: return@mapNotNull null
                position.id to V2Micros.fromDecimal(counted - position.onHand)
            }.toMap()
        }
        submit(InventoryCommands.postCycleCount(id, variances, System.currentTimeMillis(), commands.currentUserId()))
    }

    override suspend fun cancelCycleCount(id: String) {
        submit(InventoryCommands.cancelCycleCount(id, System.currentTimeMillis()))
    }

    private suspend fun submit(command: V2NewCommand) {
        commands.submit(command)
    }

    private suspend fun categoryIdByName(name: String): String? = store.read(null) { conn ->
        categories(conn, "product").firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id
    }

    companion object {
        fun categories(conn: V2Connection, kind: String): List<Category> =
            V2OverlayMerge.query(conn, "categories", "kind = ?", listOf(kind)) { it["kind"] == kind }
                .map { r ->
                    Category(
                        id = r.str("id"),
                        kind = r.str("kind"),
                        name = r.str("name"),
                        parentId = r.strOrNull("parent_id"),
                        color = r.strOrNull("color"),
                        icon = r.strOrNull("icon"),
                        defaultPriority = r.strOrNull("default_priority"),
                        systemCode = r.strOrNull("system_code"),
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Category::name))

        private fun categoryNames(conn: V2Connection): Map<String, String> =
            conn.query("SELECT id, name FROM categories").associate { it.str("id") to it.str("name") }

        fun products(conn: V2Connection, includeArchived: Boolean = false): List<Product> {
            val names = categoryNames(conn)
            return V2OverlayMerge.query(conn, "products", if (includeArchived) null else "archived_at IS NULL") {
                includeArchived || it["archived_at"] == null
            }.map { product(it, names) }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Product::name))
        }

        private fun productRow(conn: V2Connection, id: String): V2Row? =
            V2OverlayMerge.query(conn, "products", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()

        fun productById(conn: V2Connection, id: String): Product? = productRow(conn, id)?.let { product(it, categoryNames(conn)) }

        fun product(r: V2Row, categoryNames: Map<String, String>) = Product(
            id = r.str("id"),
            sku = r.str("sku"),
            name = r.str("name"),
            categoryId = r.strOrNull("category_id"),
            categoryName = r.strOrNull("category_id")?.let(categoryNames::get),
            uomCode = r.strOrNull("uom_code") ?: "ea",
            trackingMode = TrackingMode.fromWire(r.strOrNull("tracking_mode")),
            manufacturer = r.strOrNull("manufacturer_name"),
            modelNumber = r.strOrNull("model_number"),
            gtin = r.strOrNull("gtin"),
            standardUnitCost = r.decimalOrNull("standard_unit_cost"),
            costCurrency = r.strOrNull("standard_cost_currency"),
            defaultReorderQty = r.microsOrNull("default_reorder_qty_micros"),
            abcClass = r.strOrNull("abc_class"),
            notes = r.strOrNull("notes"),
            archived = r.longOrNull("archived_at") != null,
            version = r.longOrNull("version") ?: 0L,
        )

        fun positions(conn: V2Connection, productId: String?, locationId: String?, binId: String?): List<StockPosition> {
            val filters = listOfNotNull(
                productId?.let { "product_id" to it },
                locationId?.let { "location_id" to it },
                binId?.let { "bin_id" to it },
            )
            val locationNames = conn.query("SELECT id, name FROM locations").associate { it.str("id") to it.str("name") }
            val binCodes = conn.query("SELECT id, code FROM bins").associate { it.str("id") to it.str("code") }
            return V2OverlayMerge.query(
                conn, "stock_positions",
                where = filters.joinToString(" AND ") { "${it.first} = ?" }.ifEmpty { null },
                args = filters.map { it.second },
            ) { row -> filters.all { row[it.first] == it.second } }
                .map { r ->
                    StockPosition(
                        id = r.str("id"),
                        productId = r.str("product_id"),
                        locationId = r.str("location_id"),
                        locationName = locationNames[r.str("location_id")],
                        binId = r.strOrNull("bin_id"),
                        binCode = r.strOrNull("bin_id")?.let(binCodes::get),
                        lotId = r.strOrNull("lot_id"),
                        condition = StockCondition.fromWire(r.strOrNull("condition_code")),
                        onHand = r.micros("on_hand_qty_micros"),
                        version = r.longOrNull("version") ?: 0L,
                    )
                }
                .sortedWith(compareBy<StockPosition, String?>(nullsLast()) { it.locationName }.thenBy(nullsLast()) { it.binCode })
        }

        fun policies(conn: V2Connection, productId: String?): List<StockingPolicy> =
            V2OverlayMerge.query(
                conn, "stocking_policies",
                where = productId?.let { "product_id = ?" },
                args = listOfNotNull(productId),
            ) { productId == null || it["product_id"] == productId }.map { r ->
                StockingPolicy(
                    id = r.str("id"),
                    productId = r.str("product_id"),
                    locationId = r.str("location_id"),
                    minQty = r.microsOrNull("min_qty_micros"),
                    maxQty = r.microsOrNull("max_qty_micros"),
                    reorderQty = r.microsOrNull("reorder_qty_micros"),
                    economicOrderQty = r.microsOrNull("economic_order_qty_micros"),
                    safetyQty = r.microsOrNull("safety_qty_micros"),
                    version = r.longOrNull("version") ?: 0L,
                )
            }

        fun movements(conn: V2Connection, kinds: Set<MovementKind>, statuses: Set<MovementStatus>?): List<StockMovement> {
            val kindWires = kinds.map { it.wire }
            val statusWires = statuses?.map { it.wire }
            val where = buildList {
                add("kind IN (${kindWires.joinToString { "?" }})")
                if (statusWires != null) add("status IN (${statusWires.joinToString { "?" }})")
            }.joinToString(" AND ")
            val heads = V2OverlayMerge.query(conn, "stock_movements", where, kindWires + statusWires.orEmpty()) {
                it["kind"] in kindWires && (statusWires == null || it["status"] in statusWires)
            }
            if (heads.isEmpty()) return emptyList()
            val ids = heads.map { it.str("id") }.toSet()
            val lines = movementLines(conn, ids)
            val reversals = reversalsOf(conn, ids)
            return heads.map { movement(it, lines[it.str("id")].orEmpty(), reversals[it.str("id")]) }
                .sortedWith(compareByDescending<StockMovement> { it.effectiveAt ?: it.createdAt }.thenByDescending { it.id })
        }

        fun movementById(conn: V2Connection, id: String): StockMovement? {
            val head = V2OverlayMerge.query(conn, "stock_movements", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()
                ?: return null
            return movement(head, movementLines(conn, setOf(id))[id].orEmpty(), reversalsOf(conn, setOf(id))[id])
        }

        /** Original document id → id of the reversal that reverses it. */
        private fun reversalsOf(conn: V2Connection, ids: Set<String>): Map<String, String> =
            V2OverlayMerge.query(conn, "stock_movements", "reverses_movement_id IS NOT NULL", emptyList()) {
                it["reverses_movement_id"] != null
            }.mapNotNull { r ->
                val original = r.strOrNull("reverses_movement_id")?.takeIf { it in ids } ?: return@mapNotNull null
                original to r.str("id")
            }.toMap()

        private fun movementLines(conn: V2Connection, movementIds: Set<String>): Map<String, List<StockMovementLine>> {
            val products = conn.query("SELECT id, name, sku FROM products").associateBy { it.str("id") }
            val rows = if (movementIds.size <= MAX_IN) {
                V2OverlayMerge.query(
                    conn, "stock_movement_lines",
                    "movement_id IN (${movementIds.joinToString { "?" }})", movementIds.toList(),
                ) { it["movement_id"] in movementIds }
            } else {
                V2OverlayMerge.query(conn, "stock_movement_lines").filter { it["movement_id"] in movementIds }
            }
            return rows.map { r ->
                val product = products[r.str("product_id")]
                StockMovementLine(
                    id = r.str("id"),
                    movementId = r.str("movement_id"),
                    lineNo = r.int("line_no"),
                    productId = r.str("product_id"),
                    productName = product?.strOrNull("name"),
                    productSku = product?.strOrNull("sku"),
                    uomCode = r.strOrNull("uom_code"),
                    locationId = r.strOrNull("location_id"),
                    binId = r.strOrNull("bin_id"),
                    toBinId = r.strOrNull("to_bin_id"),
                    lotId = r.strOrNull("lot_id"),
                    condition = StockCondition.fromWire(r.strOrNull("condition_code")),
                    requestedQty = r.microsOrNull("requested_qty_micros"),
                    quantity = r.microsOrNull("quantity_micros"),
                    expectedQty = r.microsOrNull("expected_qty_micros"),
                    countedQty = r.microsOrNull("counted_qty_micros"),
                    countedAt = r.longOrNull("counted_at"),
                    countedBy = r.strOrNull("counted_by"),
                    recountRequired = r.bool("recount_required"),
                    purchaseOrderLineId = r.strOrNull("purchase_order_line_id"),
                    workOrderMaterialId = r.strOrNull("work_order_material_id"),
                    discrepancyQty = r.microsOrNull("discrepancy_qty_micros"),
                    discrepancyReason = r.strOrNull("discrepancy_reason"),
                    reasonCode = r.strOrNull("reason_code"),
                    unitCost = r.decimalOrNull("unit_cost"),
                    notes = r.strOrNull("notes"),
                    version = r.longOrNull("version") ?: 0L,
                )
            }.sortedBy { it.lineNo }.groupBy { it.movementId }
        }

        fun movement(r: V2Row, lines: List<StockMovementLine>, reversedById: String? = null) = StockMovement(
            id = r.str("id"),
            kind = MovementKind.fromWire(r.strOrNull("kind")),
            status = MovementStatus.fromWire(r.strOrNull("status")),
            number = r.strOrNull("number"),
            locationId = r.str("location_id"),
            toLocationId = r.strOrNull("to_location_id"),
            effectiveAt = r.longOrNull("effective_at"),
            postedAt = r.longOrNull("posted_at"),
            requestedBy = r.strOrNull("requested_by"),
            approvedBy = r.strOrNull("approved_by"),
            decisionNote = r.strOrNull("decision_note"),
            purchaseOrderId = r.strOrNull("purchase_order_id"),
            workOrderId = r.strOrNull("work_order_id"),
            countScope = r.strOrNull("count_scope"),
            blindCount = r.bool("blind_count"),
            countedBy = r.strOrNull("counted_by"),
            startedAt = r.longOrNull("started_at"),
            completedAt = r.longOrNull("completed_at"),
            scheduledOn = r.strOrNull("scheduled_on"),
            carrier = r.strOrNull("carrier"),
            trackingNumber = r.strOrNull("tracking_number"),
            packingSlipNo = r.strOrNull("packing_slip_no"),
            reference = r.strOrNull("reference_text"),
            notes = r.strOrNull("notes"),
            createdAt = r.longOrNull("created_at") ?: 0L,
            createdBy = r.strOrNull("created_by"),
            version = r.longOrNull("version") ?: 0L,
            lines = lines,
            reversesMovementId = r.strOrNull("reverses_movement_id"),
            reversedById = reversedById,
        )

        private const val MAX_IN = 500
    }
}
