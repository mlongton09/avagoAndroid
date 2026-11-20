package com.avago.core.data.v2.repo

import com.avago.core.data.domain.GoodsReceiptDraft
import com.avago.core.data.domain.ProductSupplier
import com.avago.core.data.domain.ProductSupplierDraft
import com.avago.core.data.domain.PurchaseOrderDraft
import com.avago.core.data.domain.PurchaseOrderEdit
import com.avago.core.data.domain.PurchaseOrderLineDraft
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.StockCondition
import com.avago.core.data.domain.SupplierDraft
import com.avago.core.data.domain.PurchaseOrder
import com.avago.core.data.domain.PurchaseOrderLine
import com.avago.core.data.domain.PurchaseOrderRepository
import com.avago.core.data.domain.PurchaseOrderStatus
import com.avago.core.data.domain.Supplier
import com.avago.core.data.domain.SupplierRepository
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2Micros
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2Uuid
import com.avago.core.data.v2.commands.KnownPosition
import com.avago.core.data.v2.commands.PurchasingCommands
import com.avago.core.data.v2.commands.PurchasingCommands.Transition
import com.avago.core.data.v2.V2Money
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.bool
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

private val SUPPLIER_TABLES = setOf(
    "parties", "supplier_profiles", "party_contacts", "party_contact_points", "party_locations", "locations",
)

@Singleton
class V2SupplierRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
) : SupplierRepository {

    override fun observeSuppliers(includeArchived: Boolean): Flow<List<Supplier>> =
        store.observe(SUPPLIER_TABLES, emptyList()) { conn -> suppliers(conn, includeArchived) }

    override fun observeSupplier(partyId: String): Flow<Supplier?> =
        store.observe(SUPPLIER_TABLES, null) { conn -> supplierById(conn, partyId) }

    override suspend fun supplier(partyId: String): Supplier? = store.read(null) { conn -> supplierById(conn, partyId) }

    override fun observeProductSuppliers(productId: String?, supplierPartyId: String?): Flow<List<ProductSupplier>> =
        store.observe(setOf("product_suppliers", "parties"), emptyList()) { conn ->
            productSuppliers(conn, productId, supplierPartyId)
        }

    override suspend fun createSupplier(draft: SupplierDraft): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(PurchasingCommands.createSupplier(id, draft, { V2Uuid.v7(now) }, now, commands.currentUserId()))
        return id
    }

    override suspend fun updateSupplier(partyId: String, draft: SupplierDraft) {
        data class Current(val supplier: Supplier, val points: Map<String, String>, val contact: PurchasingCommands.ContactRef?)
        val current = store.read(null) { conn ->
            supplierById(conn, partyId)?.let { s ->
                val points = V2OverlayMerge.query(conn, "party_contact_points", "party_id = ?", listOf(partyId)) {
                    it["party_id"] == partyId
                }.sortedByDescending { it.bool("is_primary") }
                    .groupBy { it.str("kind") }
                    .mapValues { (_, rows) -> rows.first().str("id") }
                val contact = V2OverlayMerge.query(conn, "party_contacts", "party_id = ?", listOf(partyId)) {
                    it["party_id"] == partyId
                }.sortedByDescending { it.bool("is_primary") }.firstOrNull()
                    ?.let { PurchasingCommands.ContactRef(it.str("id"), it.str("name"), it.longOrNull("version") ?: 0L) }
                Current(s, points, contact)
            }
        } ?: throw RecordNotFoundException("vendor", partyId)
        val now = System.currentTimeMillis()
        PurchasingCommands.updateSupplier(current.supplier, draft, current.points, { V2Uuid.v7(now) }, now)
            ?.let { commands.submit(it) }
        PurchasingCommands.primaryContact(partyId, current.contact, draft.contactName, { V2Uuid.v7(now) }, now, commands.currentUserId())
            ?.let { commands.submit(it) }
    }

    override suspend fun setSupplierActive(partyId: String, active: Boolean) {
        val stored = supplier(partyId) ?: throw RecordNotFoundException("vendor", partyId)
        if (stored.archived == !active) return
        commands.submit(PurchasingCommands.setSupplierActive(stored, active, System.currentTimeMillis()))
    }

    override suspend fun deleteSupplier(partyId: String) {
        val stored = supplier(partyId) ?: throw RecordNotFoundException("vendor", partyId)
        commands.submit(PurchasingCommands.deleteSupplier(stored))
    }

    override suspend fun createProductSupplier(draft: ProductSupplierDraft): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(PurchasingCommands.createProductSupplier(id, draft, now, commands.currentUserId()))
        return id
    }

    override suspend fun updateProductSupplier(id: String, draft: ProductSupplierDraft) {
        val stored = productSupplier(id)
        PurchasingCommands.updateProductSupplier(stored, draft, System.currentTimeMillis())?.let { commands.submit(it) }
    }

    override suspend fun deleteProductSupplier(id: String) {
        commands.submit(PurchasingCommands.deleteProductSupplier(productSupplier(id)))
    }

    private suspend fun productSupplier(id: String): ProductSupplier =
        store.read(null) { conn -> productSuppliers(conn, null, null).firstOrNull { it.id == id } }
            ?: throw RecordNotFoundException("vendor catalog entry", id)

    companion object {
        fun suppliers(conn: V2Connection, includeArchived: Boolean = false): List<Supplier> {
            val profiles = V2OverlayMerge.query(
                conn, "supplier_profiles", if (includeArchived) null else "archived_at IS NULL",
            ) { includeArchived || it["archived_at"] == null }
            return assemble(conn, profiles).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Supplier::name))
        }

        fun supplierById(conn: V2Connection, partyId: String): Supplier? {
            val profiles = V2OverlayMerge.query(conn, "supplier_profiles", "party_id = ?", listOf(partyId)) {
                it["party_id"] == partyId
            }
            return assemble(conn, profiles).firstOrNull()
        }

        private fun assemble(conn: V2Connection, profiles: List<V2Row>): List<Supplier> {
            if (profiles.isEmpty()) return emptyList()
            val ids = profiles.map { it.str("party_id") }.toSet()
            val parties = V2OverlayMerge.query(conn, "parties").filter { it["id"] in ids }.associateBy { it.str("id") }
            val points = V2OverlayMerge.query(conn, "party_contact_points").filter { it["party_id"] in ids }
                .sortedByDescending { it.bool("is_primary") }
                .groupBy { it.str("party_id") }
            val contacts = V2OverlayMerge.query(conn, "party_contacts").filter { it["party_id"] in ids }
                .sortedByDescending { it.bool("is_primary") }
                .groupBy { it.str("party_id") }
            val partyLocations = V2OverlayMerge.query(conn, "party_locations").filter { it["party_id"] in ids }
                .sortedByDescending { it.bool("is_primary") }
                .groupBy { it.str("party_id") }
            val locations = V2LocationRepository.locations(conn, includeArchived = true).associateBy { it.id }
            return profiles.mapNotNull { profile ->
                val partyId = profile.str("party_id")
                val party = parties[partyId] ?: return@mapNotNull null
                val pts = points[partyId].orEmpty()
                fun point(vararg kinds: String) = pts.firstOrNull { it["kind"] in kinds }?.strOrNull("value")
                val address = partyLocations[partyId].orEmpty()
                    .firstNotNullOfOrNull { locations[it.str("location_id")]?.addressSummary?.takeIf(String::isNotBlank) }
                Supplier(
                    partyId = partyId,
                    name = party.str("display_name"),
                    legalName = party.strOrNull("legal_name"),
                    supplierCode = profile.strOrNull("supplier_code"),
                    accountNumber = profile.strOrNull("account_number"),
                    paymentTerms = profile.strOrNull("payment_terms"),
                    currency = profile.strOrNull("currency_code"),
                    leadTimeDays = profile.intOrNull("lead_time_days"),
                    isPreferred = profile.bool("is_preferred"),
                    rating = profile.intOrNull("rating"),
                    email = point("email"),
                    phone = point("phone", "mobile"),
                    website = point("website"),
                    contactName = contacts[partyId]?.firstOrNull()?.strOrNull("name"),
                    address = address,
                    notes = party.strOrNull("notes"),
                    archived = profile.longOrNull("archived_at") != null || party.longOrNull("archived_at") != null,
                    version = party.longOrNull("version") ?: 0L,
                )
            }
        }

        fun productSuppliers(conn: V2Connection, productId: String?, supplierPartyId: String?): List<ProductSupplier> {
            val names = conn.query("SELECT id, display_name FROM parties").associate { it.str("id") to it.str("display_name") }
            return V2OverlayMerge.query(conn, "product_suppliers", "archived_at IS NULL") { row ->
                row["archived_at"] == null &&
                    (productId == null || row["product_id"] == productId) &&
                    (supplierPartyId == null || row["supplier_party_id"] == supplierPartyId)
            }.filter { row ->
                (productId == null || row["product_id"] == productId) &&
                    (supplierPartyId == null || row["supplier_party_id"] == supplierPartyId)
            }.map { r ->
                ProductSupplier(
                    id = r.str("id"),
                    productId = r.str("product_id"),
                    supplierPartyId = r.str("supplier_party_id"),
                    supplierName = names[r.str("supplier_party_id")],
                    supplierSku = r.strOrNull("supplier_sku"),
                    description = r.strOrNull("supplier_description"),
                    unitCost = r.decimalOrNull("unit_cost"),
                    currency = r.strOrNull("currency"),
                    minOrderQty = r.microsOrNull("min_order_qty_micros"),
                    packSizeQty = r.microsOrNull("pack_size_qty_micros"),
                    leadTimeDays = r.intOrNull("lead_time_days"),
                    isPreferred = r.bool("is_preferred"),
                    notes = r.strOrNull("notes"),
                    version = r.longOrNull("version") ?: 0L,
                )
            }.sortedWith(compareByDescending<ProductSupplier> { it.isPreferred }.thenBy { it.supplierName })
        }
    }
}

private val PO_TABLES = setOf("purchase_orders", "purchase_order_lines", "parties", "products")

@Singleton
class V2PurchaseOrderRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
) : PurchaseOrderRepository {

    override fun observePurchaseOrders(): Flow<List<PurchaseOrder>> =
        store.observe(PO_TABLES, emptyList()) { conn -> purchaseOrders(conn) }

    override fun observePurchaseOrder(id: String): Flow<PurchaseOrder?> =
        store.observe(PO_TABLES, null) { conn -> purchaseOrderById(conn, id) }

    override suspend fun purchaseOrder(id: String): PurchaseOrder? = store.read(null) { conn -> purchaseOrderById(conn, id) }

    override suspend fun createPurchaseOrder(draft: PurchaseOrderDraft): String {
        val now = System.currentTimeMillis()
        val userId = commands.currentUserId()
        val id = V2Uuid.v7(now)
        // Line commands carry their own ids; they depend on the header so a rejected header
        // fails (and rolls back) the lines with it.
        val header = commands.submit(PurchasingCommands.createPurchaseOrder(id, draft, now, userId))
        draft.lines.forEachIndexed { i, line ->
            commands.submit(
                PurchasingCommands.addLine(
                    V2Uuid.v7(now), id, i + 1, draft.currency, line, listOf(header), now, userId,
                ),
            )
        }
        return id
    }

    override suspend fun updatePurchaseOrder(id: String, edit: PurchaseOrderEdit) {
        val stored = order(id)
        PurchasingCommands.updatePurchaseOrder(stored, edit, System.currentTimeMillis())?.let { commands.submit(it) }
    }

    override suspend fun addLine(purchaseOrderId: String, line: PurchaseOrderLineDraft): String {
        val order = order(purchaseOrderId)
        require(order.isEditable) { "Lines can only be added while the order is a draft" }
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        val lineNo = (order.lines.maxOfOrNull { it.lineNo } ?: 0) + 1
        commands.submit(
            PurchasingCommands.addLine(id, order.id, lineNo, order.currency, line, emptyList(), now, commands.currentUserId()),
        )
        return id
    }

    override suspend fun updateLine(lineId: String, line: PurchaseOrderLineDraft) {
        val (order, stored) = orderOfLine(lineId)
        require(order.isEditable) { "Lines can only be changed while the order is a draft" }
        PurchasingCommands.updateLine(order, stored, line, System.currentTimeMillis())?.let { commands.submit(it) }
    }

    override suspend fun deleteLine(lineId: String) {
        val (order, stored) = orderOfLine(lineId)
        require(order.isEditable) { "Lines can only be removed while the order is a draft" }
        commands.submit(PurchasingCommands.deleteLine(order, stored, System.currentTimeMillis()))
    }

    override suspend fun submit(id: String) = transition(id, Transition.SUBMIT)

    override suspend fun approve(id: String) = transition(id, Transition.APPROVE)

    override suspend fun reject(id: String, reason: String?) = transition(id, Transition.REJECT, reason)

    override suspend fun markOrdered(id: String) = transition(id, Transition.MARK_ORDERED)

    override suspend fun cancel(id: String) = transition(id, Transition.CANCEL)

    override suspend fun close(id: String) = transition(id, Transition.CLOSE)

    override suspend fun receive(draft: GoodsReceiptDraft): String {
        val order = order(draft.purchaseOrderId)
        val receivingLocation = draft.locationId ?: order.shipToLocationId
            ?: draft.lines.firstNotNullOfOrNull { it.locationId }
            ?: throw IllegalArgumentException("Choose the location the goods are received into")
        val products = order.lines.mapNotNull { it.productId }.toSet()
        val positions = store.read(emptyList<com.avago.core.data.domain.StockPosition>()) { conn ->
            products.flatMap { V2InventoryRepository.positions(conn, it, null, null) }
        }.filter { it.condition == StockCondition.GOOD && it.lotId == null }
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(
            PurchasingCommands.receive(
                id = id,
                lineIds = draft.lines.map { V2Uuid.v7(now) },
                draft = draft,
                order = order,
                receivingLocationId = receivingLocation,
                positionFor = { productId, locationId, binId ->
                    positions.firstOrNull { it.productId == productId && it.locationId == locationId && it.binId == binId }
                        ?.let { KnownPosition(it.id, V2Micros.fromDecimal(it.onHand)) }
                },
                newId = { V2Uuid.v7(now) },
                nowMillis = now,
                userId = commands.currentUserId(),
            ),
        )
        return id
    }

    private suspend fun transition(id: String, transition: Transition, reason: String? = null) {
        commands.submit(PurchasingCommands.transition(order(id), transition, System.currentTimeMillis(), commands.currentUserId(), reason))
    }

    private suspend fun order(id: String): PurchaseOrder =
        purchaseOrder(id) ?: throw RecordNotFoundException("purchase order", id)

    private suspend fun orderOfLine(lineId: String): Pair<PurchaseOrder, PurchaseOrderLine> =
        store.read(null) { conn ->
            val poId = V2OverlayMerge.query(conn, "purchase_order_lines", "id = ?", listOf(lineId)) { it["id"] == lineId }
                .firstOrNull()?.str("purchase_order_id") ?: return@read null
            purchaseOrderById(conn, poId)?.let { po -> po.lines.firstOrNull { it.id == lineId }?.let { po to it } }
        } ?: throw RecordNotFoundException("purchase order line", lineId)

    companion object {
        fun purchaseOrders(conn: V2Connection): List<PurchaseOrder> {
            val heads = V2OverlayMerge.query(conn, "purchase_orders")
            val lines = lines(conn, null)
            return heads.map { assemble(conn, it, lines[it.str("id")].orEmpty()) }
                .sortedWith(compareByDescending<PurchaseOrder> { it.createdAt }.thenByDescending { it.id })
        }

        fun purchaseOrderById(conn: V2Connection, id: String): PurchaseOrder? {
            val head = V2OverlayMerge.query(conn, "purchase_orders", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()
                ?: return null
            return assemble(conn, head, lines(conn, id)[id].orEmpty())
        }

        private fun lines(conn: V2Connection, purchaseOrderId: String?): Map<String, List<PurchaseOrderLine>> {
            val productNames = conn.query("SELECT id, name FROM products").associate { it.str("id") to it.str("name") }
            return V2OverlayMerge.query(
                conn, "purchase_order_lines",
                purchaseOrderId?.let { "purchase_order_id = ?" }, listOfNotNull(purchaseOrderId),
            ) { purchaseOrderId == null || it["purchase_order_id"] == purchaseOrderId }.map { r ->
                val currency = r.str("currency")
                PurchaseOrderLine(
                    id = r.str("id"),
                    purchaseOrderId = r.str("purchase_order_id"),
                    lineNo = r.int("line_no"),
                    productId = r.strOrNull("product_id"),
                    productName = r.strOrNull("product_id")?.let(productNames::get),
                    description = r.strOrNull("description"),
                    supplierSku = r.strOrNull("supplier_sku"),
                    orderedQty = r.micros("ordered_qty_micros"),
                    receivedQty = r.micros("received_qty_micros"),
                    unitCost = r.decimalOrNull("unit_cost") ?: BigDecimal.ZERO,
                    currency = currency,
                    taxRatePct = r.decimalOrNull("tax_rate_pct"),
                    lineTotal = V2Money.toMajor(r.long("line_total_minor"), currency),
                    deliverToLocationId = r.strOrNull("deliver_to_location_id"),
                    deliverToBinId = r.strOrNull("deliver_to_bin_id"),
                    notes = r.strOrNull("notes"),
                    version = r.longOrNull("version") ?: 0L,
                    workOrderId = r.strOrNull("work_order_id"),
                )
            }.sortedBy { it.lineNo }.groupBy { it.purchaseOrderId }
        }

        private fun assemble(conn: V2Connection, r: V2Row, lines: List<PurchaseOrderLine>): PurchaseOrder {
            val currency = r.str("currency")
            val supplierId = r.str("supplier_party_id")
            val supplierName = conn.query("SELECT display_name FROM parties WHERE id = ?", listOf(supplierId))
                .firstOrNull()?.strOrNull("display_name")
            fun money(col: String) = V2Money.toMajor(r.longOrNull(col) ?: 0L, currency)
            return PurchaseOrder(
                id = r.str("id"),
                number = r.strOrNull("number"),
                supplierPartyId = supplierId,
                supplierName = supplierName,
                status = PurchaseOrderStatus.fromWire(r.strOrNull("status")),
                currency = currency,
                subtotal = money("subtotal_minor"),
                taxTotal = money("tax_total_minor"),
                shipping = money("shipping_minor"),
                discount = money("discount_minor"),
                grandTotal = money("grand_total_minor"),
                expectedOn = r.strOrNull("expected_on"),
                shipToLocationId = r.strOrNull("ship_to_location_id"),
                workOrderId = r.strOrNull("work_order_id"),
                assetId = r.strOrNull("asset_id"),
                requestedBy = r.strOrNull("requested_by"),
                approvedBy = r.strOrNull("approved_by"),
                approvedAt = r.longOrNull("approved_at"),
                orderedAt = r.longOrNull("ordered_at"),
                closedAt = r.longOrNull("closed_at"),
                notes = r.strOrNull("notes"),
                supplierInvoiceRef = r.strOrNull("supplier_invoice_ref"),
                createdAt = r.longOrNull("created_at") ?: 0L,
                version = r.longOrNull("version") ?: 0L,
                lines = lines,
                internalNotes = r.strOrNull("internal_notes"),
            )
        }
    }
}
