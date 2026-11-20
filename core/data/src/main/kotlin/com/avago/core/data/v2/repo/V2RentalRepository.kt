package com.avago.core.data.v2.repo

import android.net.Uri
import com.avago.core.data.domain.BulkCountsDraft
import com.avago.core.data.domain.CustomerDocumentDraft
import com.avago.core.data.domain.HandoverDraft
import com.avago.core.data.domain.HandoverOverride
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.PaymentDraft
import com.avago.core.data.domain.ReadinessCheck
import com.avago.core.data.domain.ReadinessCheckItem
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalAllocation
import com.avago.core.data.domain.RentalBookingDraft
import com.avago.core.data.domain.RentalBulkReturn
import com.avago.core.data.domain.RentalClass
import com.avago.core.data.domain.RentalCustomer
import com.avago.core.data.domain.RentalCustomerDraft
import com.avago.core.data.domain.RentalDamage
import com.avago.core.data.domain.RentalFailure
import com.avago.core.data.domain.RentalHandover
import com.avago.core.data.domain.RentalInvoice
import com.avago.core.data.domain.RentalOrder
import com.avago.core.data.domain.RentalOrderAction
import com.avago.core.data.domain.RentalOrderLine
import com.avago.core.data.domain.RentalRates
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.domain.RentalSignature
import com.avago.core.data.domain.SwapDraft
import com.avago.core.data.rental.ClassAvailability
import com.avago.core.data.rental.FleetFacts
import com.avago.core.data.rental.LocalFleet
import com.avago.core.data.v2.V2CommandQueue
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2FileStager
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2StagedFile
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Uuid
import com.avago.core.data.v2.bool
import com.avago.core.data.v2.commands.RentalCommands
import com.avago.core.data.v2.commands.SignerRole
import com.avago.core.data.v2.commands.V2_COMMAND_CATALOG_VERSION
import com.avago.core.data.v2.intOrNull
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.microsOrNull
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val ORDER_TABLES = setOf("rental_orders", "parties", "invoices", "pending_commands")
private val CUSTOMER_TABLES = setOf(
    "parties", "customer_profiles", "party_contact_points", "party_locations", "locations", "customer_balances", "pending_commands",
)
private val FLEET_TABLES = setOf(
    "assets", "rental_classes", "locations", "inspection_defects", "work_orders", "work_order_assets", "pm_occurrences",
    "pm_triggers", "meter_readings", "rental_handovers", "resource_allocations", "rental_order_lines", "rental_orders",
    "pending_commands",
)

/** Rentals on the v2 store: reads over the projected rental tables, writes as rental catalog commands. */
@Singleton
class V2RentalRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
    private val stager: V2FileStager,
) : RentalRepository {

    // ------------------------------------------------------------------------------------ reads

    override fun observeOrders(customerPartyId: String?): Flow<List<RentalOrder>> =
        store.observe(ORDER_TABLES, emptyList()) { conn ->
            orders(conn, customerPartyId?.let { "customer_party_id = ?" to listOf<Any?>(it) }) {
                customerPartyId == null || it["customer_party_id"] == customerPartyId
            }
        }

    override fun observeOrder(id: String): Flow<RentalOrder?> =
        store.observe(ORDER_TABLES, null) { conn -> orderById(conn, id) }

    override suspend fun order(id: String): RentalOrder? = store.read(null) { conn -> orderById(conn, id) }

    override fun observeLines(orderId: String?): Flow<List<RentalOrderLine>> =
        store.observe(setOf("rental_order_lines", "pending_commands"), emptyList()) { conn -> lines(conn, orderId) }

    override fun observeAllocations(orderId: String?, assetId: String?): Flow<List<RentalAllocation>> =
        store.observe(setOf("resource_allocations"), emptyList()) { conn ->
            V2OverlayMerge.query(conn, "resource_allocations", "active = 1") { it["active"] == 1L }
                .filter { r -> (orderId == null || r["order_id"] == orderId) && (assetId == null || r["asset_id"] == assetId) }
                .map(::allocation)
                .sortedWith(compareBy<RentalAllocation> { it.startAt }.thenBy { it.id })
        }

    override fun observeClassAvailability(): Flow<ClassAvailability.Facts> =
        store.observe(setOf("assets", "resource_allocations", "rental_orders"), ClassAvailability.Facts.EMPTY) { conn ->
            classAvailability(conn)
        }

    override fun observeHandovers(orderId: String?, assetId: String?): Flow<List<RentalHandover>> =
        store.observe(setOf("rental_handovers"), emptyList()) { conn ->
            V2OverlayMerge.query(conn, "rental_handovers", "kind IN ('checkout', 'checkin')") { it["kind"] != "readiness" }
                .filter { r -> (orderId == null || r["order_id"] == orderId) && (assetId == null || r["asset_id"] == assetId) }
                .map(::handover)
                .sortedByDescending { it.at }
        }

    override fun observeReadinessChecks(assetId: String): Flow<List<ReadinessCheck>> =
        store.observe(setOf("rental_handovers", "rental_handover_files", "pending_commands"), emptyList()) { conn ->
            val sync = syncStates(conn, "rental_handovers")
            val rows = V2OverlayMerge.query(conn, "rental_handovers", "kind = 'readiness' AND asset_id = ?", listOf(assetId)) {
                it["kind"] == "readiness" && it["asset_id"] == assetId
            }.filter { it["voided_at"] == null }
            val photos = handoverPhotos(conn, rows.map { it.str("id") }.toSet())
            rows.map { readiness(it, sync, photos) }.sortedByDescending { it.at }
        }

    override fun observeBulkReturns(orderId: String): Flow<List<RentalBulkReturn>> =
        store.observe(setOf("rental_bulk_returns"), emptyList()) { conn ->
            V2OverlayMerge.query(conn, "rental_bulk_returns", "order_id = ?", listOf(orderId)) { it["order_id"] == orderId }
                .map { r ->
                    RentalBulkReturn(
                        id = r.str("id"),
                        orderId = r.str("order_id"),
                        orderLineId = r.str("order_line_id"),
                        productId = r.str("product_id"),
                        returned = r.microsOrNull("returned_qty_micros") ?: BigDecimal.ZERO,
                        damaged = r.microsOrNull("damaged_qty_micros") ?: BigDecimal.ZERO,
                        lost = r.microsOrNull("lost_qty_micros") ?: BigDecimal.ZERO,
                        chargeMinor = r.longOrNull("charge_minor") ?: 0L,
                        notes = r.strOrNull("notes"),
                        at = r.instant("returned_at") ?: Instant.EPOCH,
                    )
                }.sortedByDescending { it.at }
        }

    override fun observeSignatures(orderId: String): Flow<List<RentalSignature>> =
        store.observe(setOf("signatures"), emptyList()) { conn ->
            V2OverlayMerge.query(conn, "signatures", "order_id = ?", listOf(orderId)) { it["order_id"] == orderId }
                .map { r ->
                    RentalSignature(
                        id = r.str("id"),
                        orderId = r.strOrNull("order_id"),
                        documentId = r.strOrNull("document_id"),
                        signerName = r.strOrNull("signer_name").orEmpty(),
                        signerRole = r.strOrNull("signer_role") ?: "customer",
                        method = r.strOrNull("method") ?: "in_person",
                        signedAt = r.instant("signed_at") ?: Instant.EPOCH,
                        requestId = r.strOrNull("request_id"),
                        signerId = r.strOrNull("signer_id"),
                        handoverId = r.strOrNull("handover_id"),
                    )
                }.sortedByDescending { it.signedAt }
        }

    override fun observeInvoices(orderId: String): Flow<List<RentalInvoice>> =
        store.observe(setOf("invoices", "pending_commands"), emptyList()) { conn ->
            val sync = syncStates(conn, "invoices")
            V2OverlayMerge.query(conn, "invoices", "order_id = ?", listOf(orderId)) { it["order_id"] == orderId }
                .map { r ->
                    val id = r.str("id")
                    RentalInvoice(
                        id = id,
                        orderId = r.strOrNull("order_id"),
                        number = r.strOrNull("number"),
                        status = r.strOrNull("status") ?: "draft",
                        kind = r.strOrNull("kind") ?: "invoice",
                        currency = r.strOrNull("currency").orEmpty(),
                        totalMinor = r.longOrNull("total_minor") ?: 0L,
                        paidMinor = r.longOrNull("paid_minor") ?: 0L,
                        dueOn = r.strOrNull("due_on"),
                        sync = sync[id] ?: RecordSyncState.SAVED,
                    )
                }
        }

    override fun observeCustomers(): Flow<List<RentalCustomer>> =
        store.observe(CUSTOMER_TABLES, emptyList()) { conn ->
            customers(conn, null).filterNot { it.archived }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, RentalCustomer::name))
        }

    override fun observeCustomer(partyId: String): Flow<RentalCustomer?> =
        store.observe(CUSTOMER_TABLES, null) { conn -> customers(conn, partyId).firstOrNull() }

    override fun observeClasses(): Flow<List<RentalClass>> =
        store.observe(setOf("rental_classes"), emptyList()) { conn ->
            V2OverlayMerge.query(conn, "rental_classes").map { r ->
                RentalClass(
                    id = r.str("id"),
                    name = r.strOrNull("name").orEmpty(),
                    tracking = r.strOrNull("tracking") ?: "serialized",
                    meterKind = r.strOrNull("meter_kind"),
                    depositMinor = r.longOrNull("deposit_minor"),
                    depositCurrency = r.strOrNull("deposit_currency"),
                    active = r["active"] != 0L,
                    hidden = r.bool("hidden"),
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, RentalClass::name))
        }

    override fun observeFleet(): Flow<LocalFleet.Inputs> =
        combine(store.observe(FLEET_TABLES, FleetFacts.EMPTY) { conn -> fleetFacts(conn) }, minuteTicks()) { facts, now ->
            facts.toInputs(now)
        }

    override fun observeFailures(orderId: String?): Flow<List<RentalFailure>> =
        store.observe(setOf("pending_commands", "rental_order_lines"), emptyList()) { conn ->
            failures(conn).filter { orderId == null || it.orderId == orderId }
        }

    // ----------------------------------------------------------------------------------- writes

    override suspend fun saveCustomer(partyId: String?, draft: RentalCustomerDraft): String {
        val now = System.currentTimeMillis()
        if (partyId == null) {
            val id = V2Uuid.v7(now)
            commands.submit(RentalCommands.createCustomer(id, draft, { V2Uuid.v7(now) }, now, commands.currentUserId()))
            return id
        }
        val (stored, points) = store.read(null) { conn ->
            customers(conn, partyId).firstOrNull()?.let { it to contactPointIds(conn, partyId) }
        } ?: throw RecordNotFoundException("customer", partyId)
        RentalCommands.updateCustomer(stored, draft, points, { V2Uuid.v7(now) }, now)?.let { commands.submit(it) }
        return partyId
    }

    override suspend fun addCustomerDocument(partyId: String, draft: CustomerDocumentDraft): String {
        val id = V2Uuid.v7()
        val scans = stage(draft.scans)
        val now = System.currentTimeMillis()
        commands.submit(RentalCommands.addCustomerDocument(partyId, id, draft, scans, now, commands.currentUserId()))
        return id
    }

    override suspend fun createBooking(draft: RentalBookingDraft): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        val (currency, customerName) = store.read(null to null) { conn ->
            val customer = draft.customerPartyId?.let { customers(conn, it).firstOrNull() }
            val currency = draft.currency ?: customer?.currency
                ?: conn.query("SELECT currency FROM rental_orders ORDER BY start_at DESC LIMIT 1").firstOrNull()?.strOrNull("currency")
            currency to (customer?.name ?: draft.walkInName)
        }
        val created = commands.submit(
            RentalCommands.createOrder(
                id, V2Uuid.v7(now), draft, currency ?: localCurrency(), customerName, now, commands.currentUserId(),
            ),
        )
        val action = when (draft.action) {
            null -> null
            "quote" -> RentalOrderAction.Quote
            "hold" -> RentalOrderAction.Hold()
            "reserve" -> RentalOrderAction.Reserve()
            else -> throw IllegalArgumentException("unknown booking action ${draft.action}")
        }
        if (action != null) submitAction(id, action, listOf(created))
        return id
    }

    override suspend fun act(orderId: String, action: RentalOrderAction) {
        submitAction(orderId, action, emptyList())
    }

    private suspend fun submitAction(orderId: String, action: RentalOrderAction, dependsOn: List<String>) {
        data class Current(val order: RentalOrder, val allocations: List<String>, val lines: List<RentalOrderLine>)
        val current = store.read(null) { conn ->
            orderById(conn, orderId)?.let { Current(it, allocationIds(conn, orderId = orderId), lines(conn, orderId)) }
        } ?: throw RecordNotFoundException("rental order", orderId)
        val now = System.currentTimeMillis()
        val cmd = RentalCommands.act(current.order, action, current.allocations, current.lines, { V2Uuid.v7(now) }, now, commands.currentUserId())
        commands.submit(cmd.copy(dependsOn = cmd.dependsOn + dependsOn))
    }

    override suspend fun assignUnit(lineId: String, assetId: String, overrideReady: Boolean) {
        val (line, allocations) = store.read(null) { conn ->
            lineById(conn, lineId)?.let { it to allocationIds(conn, lineId = lineId) }
        } ?: throw RecordNotFoundException("rental line", lineId)
        commands.submit(RentalCommands.assignUnit(line, assetId, overrideReady, allocations, System.currentTimeMillis()))
    }

    override suspend fun handover(draft: HandoverDraft): String {
        val ctx = lineContext(draft.lineId)
        require(ctx.order.id == draft.orderId) { "line ${draft.lineId} is not on order ${draft.orderId}" }
        val photos = stage(draft.photos)
        val meterPhoto = draft.meterPhoto?.let { stage(listOf(it)).single() }
        val now = System.currentTimeMillis()
        val userId = commands.currentUserId()
        var signatureId: String? = null
        val dependsOn = mutableListOf<String>()
        draft.signaturePng?.let { png ->
            val name = requireNotNull(draft.signerName?.trim()?.ifEmpty { null }) { "a signature needs the signer's name" }
            val staged = stager.stage(png, PNG, "signature.png")
            val id = V2Uuid.v7(now)
            dependsOn += commands.submit(
                RentalCommands.captureSignature(id, draft.orderId, name, SignerRole.CUSTOMER, staged, draft.at, now, userId),
            )
            signatureId = id
        }
        val id = V2Uuid.v7(now)
        commands.submit(
            RentalCommands.handover(
                id, draft, ctx, photos, meterPhoto, signatureId, dependsOn, V2Uuid.v7(now), { V2Uuid.v7(now) }, now, userId,
            ),
        )
        return id
    }

    override suspend fun swap(draft: SwapDraft): String {
        val ctx = lineContext(draft.lineId)
        val now = System.currentTimeMillis()
        val newLineId = V2Uuid.v7(now)
        commands.submit(
            RentalCommands.swap(
                draft, ctx, newLineId, V2Uuid.v7(now), V2Uuid.v7(now), { V2Uuid.v7(now) }, now, commands.currentUserId(),
            ),
        )
        return newLineId
    }

    override suspend fun bulkReturn(orderId: String, lineId: String, counts: BulkCountsDraft, at: Instant, locationId: String?): String {
        val line = store.read(null) { conn -> lineById(conn, lineId) } ?: throw RecordNotFoundException("rental line", lineId)
        require(line.orderId == orderId) { "line $lineId is not on order $orderId" }
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(RentalCommands.bulkReturn(id, line, counts, at, locationId, now, commands.currentUserId()))
        return id
    }

    override suspend fun recordPayment(draft: PaymentDraft): String {
        val order = order(draft.orderId) ?: throw RecordNotFoundException("rental order", draft.orderId)
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(RentalCommands.recordPayment(id, draft, order.customerPartyId, now, commands.currentUserId()))
        return id
    }

    override suspend fun createInvoiceDraft(orderId: String): String {
        val order = order(orderId) ?: throw RecordNotFoundException("rental order", orderId)
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(RentalCommands.createInvoiceDraft(id, order, now, commands.currentUserId()))
        return id
    }

    override suspend fun requestAgreementSignature(orderId: String, signerName: String, locale: String?): String {
        order(orderId) ?: throw RecordNotFoundException("rental order", orderId)
        val requestId = V2Uuid.v7(System.currentTimeMillis())
        commands.submit(RentalCommands.requestAgreementSignature(orderId, requestId, signerName, locale))
        return requestId
    }

    override suspend fun signRequest(
        orderId: String,
        requestId: String,
        signerId: String?,
        signerName: String,
        signaturePng: ByteArray?,
        typedName: String?,
        consentTextVersion: String,
    ): String {
        require(signaturePng != null || !typedName.isNullOrBlank()) { "a signature needs a drawn image or a typed name" }
        val now = System.currentTimeMillis()
        val png = signaturePng?.let { stager.stage(it, PNG, "signature.png") }
        val id = V2Uuid.v7(now)
        commands.submit(
            RentalCommands.signRequest(
                id, orderId, requestId, signerId, signerName, png, typedName, consentTextVersion, Instant.ofEpochMilli(now), null, now,
                commands.currentUserId(),
            ),
        )
        return id
    }

    override suspend fun startReadinessCheck(
        assetId: String,
        items: List<ReadinessCheckItem>,
        notes: String?,
        photos: List<LocalFileRef>,
    ): String {
        val staged = stage(photos)
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(RentalCommands.createCheck(id, assetId, items, notes, now, commands.currentUserId(), staged))
        return id
    }

    override suspend fun decideReadiness(
        checkId: String,
        status: String,
        items: List<ReadinessCheckItem>,
        notes: String?,
        photos: List<LocalFileRef>,
    ) {
        val stored = store.read(null) { conn ->
            V2OverlayMerge.query(conn, "rental_handovers", "id = ?", listOf(checkId)) { it["id"] == checkId }
                .firstOrNull { it["kind"] == "readiness" }?.let { readiness(it, emptyMap(), handoverPhotos(conn, setOf(checkId))) }
        } ?: throw RecordNotFoundException("readiness checklist", checkId)
        val staged = stage(photos)
        val now = System.currentTimeMillis()
        commands.submit(RentalCommands.updateCheck(stored, status, items, notes, now, staged, commands.currentUserId()))
    }

    override suspend fun enableRental(assetId: String, rates: RentalRates) {
        commands.submit(RentalCommands.enableRental(assetId, rates, System.currentTimeMillis()))
    }

    override suspend fun disableRental(assetId: String) {
        commands.submit(RentalCommands.disableRental(assetId, System.currentTimeMillis()))
    }

    override suspend fun retryHandover(commandId: String, fix: HandoverOverride?) {
        val refused = commands.rejected(commandId) ?: throw RecordNotFoundException("refused hand-over", commandId)
        val checkout = when (refused.failure.operation) {
            RentalFailure.OP_CHECKOUT -> true
            RentalFailure.OP_CHECKIN -> false
            else -> throw IllegalArgumentException("${refused.failure.operation} is not a hand-over")
        }
        val lineId = requireNotNull(refused.payload.text("line_id")) { "the refused hand-over has no line" }
        val ctx = lineContext(lineId)
        val payload = fix?.let { RentalCommands.withOverride(refused.payload, checkout, it) } ?: refused.payload
        val now = System.currentTimeMillis()
        val overlay = RentalCommands.handoverOverlay(
            payload, checkout, ctx, V2Uuid.v7(now), { V2Uuid.v7(now) }, now, commands.currentUserId(),
        )
        // The photos and signature of the refused command are already uploaded: only the command is resent.
        commands.submit(
            V2NewCommand(
                operation = refused.failure.operation,
                payload = payload,
                payloadSchemaVersion = V2_COMMAND_CATALOG_VERSION,
                entityType = refused.failure.entityType,
                entityKey = refused.failure.entityKey,
                baseVersion = refused.baseVersion,
                overlayPatch = V2OverlayPatch(overlay).toJson(),
                reducerVersion = V2OverlayPatch.REDUCER_VERSION,
            ),
        )
        commands.dismiss(commandId)
    }

    override suspend fun dismissFailure(commandId: String) {
        commands.dismiss(commandId)
    }

    // ---------------------------------------------------------------------------------- helpers

    private suspend fun stage(files: List<LocalFileRef>): List<V2StagedFile> =
        files.map { stager.stage(Uri.parse(it.uri), it.fallbackMediaType) }

    private suspend fun lineContext(lineId: String): RentalCommands.LineContext = store.read(null) { conn ->
        val line = lineById(conn, lineId) ?: return@read null
        val order = orderById(conn, line.orderId) ?: return@read null
        val others = lines(conn, order.id).count { l ->
            l.id != line.id && (l.kind == "rental" || l.kind == "accessory") && l.isOpen
        }
        RentalCommands.LineContext(order, line, allocationIds(conn, lineId = lineId), others)
    } ?: throw RecordNotFoundException("rental line", lineId)

    private fun localCurrency(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "USD"

    private fun minuteTicks(): Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(TICK_MILLIS)
        }
    }

    companion object {
        private const val PNG = "image/png"
        private const val TICK_MILLIS = 60_000L
        private val PENDING = setOf("queued", "leased", "sent_unknown")
        private val WO_DONE = setOf("completed", "closed", "cancelled", "skipped")
        private val INVOICED = setOf("sent", "issued", "partially_paid", "paid", "credited")
        private val json = Json { ignoreUnknownKeys = true }

        /** Sync state of each record of [entityType] with local commands. */
        fun syncStates(conn: V2Connection, entityType: String): Map<String, RecordSyncState> =
            conn.query("SELECT entity_key, state FROM pending_commands WHERE entity_type = ?", listOf(entityType))
                .filter { it["entity_key"] != null }
                .groupBy({ it.str("entity_key") }, { it.str("state") })
                .mapValues { (_, states) ->
                    when {
                        "rejected" in states -> RecordSyncState.FAILED
                        states.any { it in PENDING } -> RecordSyncState.PENDING
                        else -> RecordSyncState.SAVED
                    }
                }

        fun orders(
            conn: V2Connection,
            where: Pair<String, List<Any?>>? = null,
            predicate: (V2Row) -> Boolean = { true },
        ): List<RentalOrder> {
            val rows = V2OverlayMerge.query(conn, "rental_orders", where?.first, where?.second.orEmpty(), predicate)
            if (rows.isEmpty()) return emptyList()
            val names = V2OverlayMerge.query(conn, "parties").associate { it.str("id") to it.strOrNull("display_name") }
            val invoices = V2OverlayMerge.query(conn, "invoices", "order_id IS NOT NULL") { it["order_id"] != null }
                .filter { it["status"] in INVOICED && it["kind"] != "credit_note" }
                .groupBy { it.str("order_id") }
            val sync = syncStates(conn, "rental_orders")
            return rows.map { r ->
                val id = r.str("id")
                val customer = r.strOrNull("customer_party_id")
                val inv = invoices[id]
                RentalOrder(
                    id = id,
                    version = r.longOrNull("version") ?: 0L,
                    number = r.strOrNull("number"),
                    quoteNumber = r.strOrNull("quote_number"),
                    status = r.strOrNull("status") ?: "draft",
                    source = r.strOrNull("source") ?: "staff",
                    kind = r.strOrNull("kind") ?: "standard",
                    currency = r.strOrNull("currency").orEmpty(),
                    locale = r.strOrNull("locale")?.ifEmpty { null },
                    customerPartyId = customer,
                    customerName = customer?.let(names::get) ?: r.strOrNull("walk_in_name"),
                    walkInName = r.strOrNull("walk_in_name"),
                    contactId = r.strOrNull("contact_id"),
                    siteLocationId = r.strOrNull("site_location_id"),
                    locationId = r.strOrNull("location_id"),
                    startAt = r.instant("start_at"),
                    endAt = r.instant("end_at"),
                    offRentAt = r.instant("off_rent_at"),
                    holdExpiresAt = r.instant("hold_expires_at"),
                    quoteExpiresAt = r.instant("quote_expires_at"),
                    fulfilment = r.strOrNull("fulfilment") ?: "pickup",
                    poNumber = r.strOrNull("po_number"),
                    reference = r.strOrNull("reference"),
                    notes = r.strOrNull("notes"),
                    internalNotes = r.strOrNull("internal_notes"),
                    subtotalMinor = r.longOrNull("subtotal_minor") ?: 0L,
                    discountMinor = r.longOrNull("discount_minor") ?: 0L,
                    taxMinor = r.longOrNull("tax_minor") ?: 0L,
                    totalMinor = r.longOrNull("total_minor") ?: 0L,
                    depositRequiredMinor = r.longOrNull("deposit_required_minor") ?: 0L,
                    invoicedMinor = inv?.sumOf { it.longOrNull("total_minor") ?: 0L },
                    invoicePaidMinor = inv?.sumOf { it.longOrNull("paid_minor") ?: 0L },
                    cancelReason = r.strOrNull("cancel_reason"),
                    needsReview = r.bool("needs_review"),
                    sync = sync[id] ?: RecordSyncState.SAVED,
                )
            }.sortedWith(compareByDescending<RentalOrder> { it.startAt }.thenByDescending { it.id })
        }

        fun orderById(conn: V2Connection, id: String): RentalOrder? =
            orders(conn, "id = ?" to listOf(id)) { it["id"] == id }.firstOrNull()

        fun lines(conn: V2Connection, orderId: String?): List<RentalOrderLine> {
            val sync = syncStates(conn, "rental_order_lines")
            val rows = if (orderId == null) {
                V2OverlayMerge.query(conn, "rental_order_lines")
            } else {
                V2OverlayMerge.query(conn, "rental_order_lines", "order_id = ?", listOf(orderId)) { it["order_id"] == orderId }
            }
            return rows.map { line(it, sync) }.sortedWith(compareBy<RentalOrderLine> { it.orderId }.thenBy { it.lineNo })
        }

        fun lineById(conn: V2Connection, id: String): RentalOrderLine? =
            V2OverlayMerge.query(conn, "rental_order_lines", "id = ?", listOf(id)) { it["id"] == id }
                .firstOrNull()?.let { line(it, syncStates(conn, "rental_order_lines")) }

        private fun line(r: V2Row, sync: Map<String, RecordSyncState>): RentalOrderLine {
            val id = r.str("id")
            return RentalOrderLine(
                id = id,
                version = r.longOrNull("version") ?: 0L,
                orderId = r.str("order_id"),
                lineNo = r.intOrNull("line_no") ?: 0,
                kind = r.strOrNull("kind") ?: "rental",
                classId = r.strOrNull("class_id"),
                assetId = r.strOrNull("asset_id"),
                productId = r.strOrNull("product_id"),
                parentLineId = r.strOrNull("parent_line_id"),
                description = r.strOrNull("description").orEmpty(),
                qty = r.microsOrNull("qty_micros") ?: BigDecimal.ONE,
                startAt = r.instant("start_at"),
                endAt = r.instant("end_at"),
                actualOutAt = r.instant("actual_out_at"),
                actualInAt = r.instant("actual_in_at"),
                unitPriceMinor = r.longOrNull("unit_price_minor"),
                amountMinor = r.longOrNull("amount_minor") ?: 0L,
                discountMinor = r.longOrNull("discount_minor") ?: 0L,
                taxMinor = r.longOrNull("tax_minor") ?: 0L,
                status = r.strOrNull("status") ?: "open",
                kitInstanceId = r.strOrNull("kit_instance_id"),
                assignUnitLater = r.bool("assign_unit_later"),
                sync = sync[id] ?: RecordSyncState.SAVED,
            )
        }

        fun allocationIds(conn: V2Connection, orderId: String? = null, lineId: String? = null): List<String> =
            V2OverlayMerge.query(conn, "resource_allocations", "active = 1") { it["active"] == 1L }
                .filter { (orderId == null || it["order_id"] == orderId) && (lineId == null || it["order_line_id"] == lineId) }
                .map { it.str("id") }

        private fun allocation(r: V2Row) = RentalAllocation(
            id = r.str("id"),
            assetId = r.strOrNull("asset_id"),
            classId = r.strOrNull("class_id"),
            productId = r.strOrNull("product_id"),
            orderId = r.strOrNull("order_id"),
            orderLineId = r.strOrNull("order_line_id"),
            workOrderId = r.strOrNull("work_order_id"),
            purpose = r.strOrNull("purpose") ?: "reserved",
            startAt = r.instant("during_start") ?: Instant.EPOCH,
            endAt = r.instant("during_end"),
            expiresAt = r.instant("expires_at"),
            active = r.bool("active"),
            blocking = r["blocking"] != 0L,
            qty = r.microsOrNull("qty_micros") ?: BigDecimal.ONE,
            resourceKind = r.strOrNull("resource_kind") ?: "unit",
        )

        private fun handover(r: V2Row) = RentalHandover(
            id = r.str("id"),
            kind = r.strOrNull("kind") ?: "checkout",
            at = r.instant("at") ?: Instant.EPOCH,
            orderId = r.strOrNull("order_id"),
            orderLineId = r.strOrNull("order_line_id"),
            assetId = r.strOrNull("asset_id"),
            fuelLevelPct = r.intOrNull("fuel_level_pct"),
            chargeLevelPct = r.intOrNull("charge_level_pct"),
            customerSignatureId = r.strOrNull("customer_signature_id"),
            notes = r.strOrNull("notes"),
            damage = damage(r.strOrNull("damage_summary")),
            voided = r["voided_at"] != null,
        )

        /** `damage_summary`: `[{label, severity, decision, charge_minor, location, ...}]`. */
        fun damage(text: String?): List<RentalDamage> = jsonArray(text).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            RentalDamage(
                label = o.text("label") ?: o.text("note") ?: return@mapNotNull null,
                severity = o.text("severity"),
                decision = o.text("decision"),
                chargeMinor = (o["charge_minor"] as? JsonPrimitive)?.longOrNull,
                location = o.text("location"),
            )
        }

        fun readinessItems(text: String?): List<ReadinessCheckItem> = jsonArray(text).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val key = o.text("key") ?: return@mapNotNull null
            ReadinessCheckItem(
                key = key,
                label = o.text("label") ?: key,
                done = (o["done"] as? JsonPrimitive)?.booleanOrNull ?: false,
                note = o.text("note"),
            )
        }

        /** Photo file ids per handover, oldest first. */
        internal fun handoverPhotos(conn: V2Connection, handoverIds: Set<String>): Map<String, List<String>> =
            if (handoverIds.isEmpty()) {
                emptyMap()
            } else {
                V2OverlayMerge.query(
                    conn, "rental_handover_files",
                    "role = 'photo' AND handover_id IN (${handoverIds.joinToString(",") { "?" }})", handoverIds.toList(),
                ) { it["role"] == "photo" && it["handover_id"] in handoverIds }
                    .sortedBy { it.longOrNull("created_at") ?: 0L }
                    .groupBy({ it.str("handover_id") }, { it.str("file_object_id") })
            }

        private fun readiness(r: V2Row, sync: Map<String, RecordSyncState>, photos: Map<String, List<String>>): ReadinessCheck {
            val id = r.str("id")
            return ReadinessCheck(
                id = id,
                version = r.longOrNull("version") ?: 0L,
                assetId = r.str("asset_id"),
                orderId = r.strOrNull("order_id"),
                status = r.strOrNull("readiness_status") ?: "pending",
                items = readinessItems(r.strOrNull("readiness_items")),
                notes = r.strOrNull("notes"),
                at = r.instant("at") ?: Instant.EPOCH,
                sync = sync[id] ?: RecordSyncState.SAVED,
                photoFileIds = photos[id].orEmpty(),
            )
        }

        fun customers(conn: V2Connection, partyId: String?): List<RentalCustomer> {
            val profiles = if (partyId == null) {
                V2OverlayMerge.query(conn, "customer_profiles")
            } else {
                V2OverlayMerge.query(conn, "customer_profiles", "party_id = ?", listOf(partyId)) { it["party_id"] == partyId }
            }
            if (profiles.isEmpty()) return emptyList()
            val parties = V2OverlayMerge.query(conn, "parties").associateBy { it.str("id") }
            val points = V2OverlayMerge.query(conn, "party_contact_points").groupBy { it.str("party_id") }
            val addresses = V2OverlayMerge.query(conn, "party_locations").groupBy { it.str("party_id") }
            val locations = V2LocationRepository.locations(conn, includeArchived = true).associateBy { it.id }
            val balances = V2OverlayMerge.query(conn, "customer_balances").groupBy { it.str("party_id") }
            val sync = syncStates(conn, "parties")
            return profiles.mapNotNull { p ->
                val id = p.str("party_id")
                val party = parties[id] ?: return@mapNotNull null
                val contact = points[id].orEmpty().sortedByDescending { it.bool("is_primary") }
                val currency = p.strOrNull("currency_code")
                val address = addresses[id].orEmpty()
                    .sortedWith(compareByDescending<V2Row> { it["purpose"] == "primary" }.thenByDescending { it.bool("is_primary") })
                    .firstNotNullOfOrNull { locations[it.strOrNull("location_id")] }
                val balance = balances[id].orEmpty().let { rows -> rows.firstOrNull { it["currency"] == currency } ?: rows.firstOrNull() }
                RentalCustomer(
                    partyId = id,
                    version = party.longOrNull("version") ?: 0L,
                    kind = party.strOrNull("kind") ?: "person",
                    name = party.strOrNull("display_name").orEmpty(),
                    legalName = party.strOrNull("legal_name"),
                    company = party.strOrNull("organization_name"),
                    email = contact.firstOrNull { it["kind"] == "email" }?.strOrNull("value"),
                    phone = contact.firstOrNull { it["kind"] == "phone" || it["kind"] == "mobile" }?.strOrNull("value"),
                    address = address?.let {
                        listOfNotNull(
                            it.addressLine1, it.addressLine2,
                            listOfNotNull(it.locality, it.region, it.postalCode).joinToString(" ").ifBlank { null },
                            it.countryCode,
                        ).joinToString("\n").ifBlank { null }
                    },
                    notes = party.strOrNull("notes"),
                    status = p.strOrNull("status") ?: "active",
                    statusReason = p.strOrNull("status_reason"),
                    creditLimitMinor = p.longOrNull("credit_limit_minor"),
                    currency = currency,
                    paymentTermsDays = p.intOrNull("payment_terms_days"),
                    poRequired = p.bool("po_required"),
                    doNotRent = p.bool("do_not_rent"),
                    doNotRentReason = p.strOrNull("do_not_rent_reason"),
                    taxExempt = p.bool("is_tax_exempt"),
                    locale = p.strOrNull("locale"),
                    receivableMinor = balance?.longOrNull("receivable_minor"),
                    archived = p["archived_at"] != null || party["archived_at"] != null,
                    sync = sync[id] ?: RecordSyncState.SAVED,
                )
            }
        }

        /** The customer's contact point ids by kind (primary first); phone also covers mobile. */
        private fun contactPointIds(conn: V2Connection, partyId: String): Map<String, String> =
            V2OverlayMerge.query(conn, "party_contact_points", "party_id = ?", listOf(partyId)) { it["party_id"] == partyId }
                .sortedByDescending { it.bool("is_primary") }
                .groupBy { if (it["kind"] == "mobile") "phone" else it.str("kind") }
                .mapValues { (_, rows) -> rows.first().str("id") }

        /** Refused rental commands with the order / line they were about (from the sent payload). */
        fun failures(conn: V2Connection): List<RentalFailure> {
            val rows = conn.query(
                "SELECT id, operation, entity_type, entity_key, payload, last_error, created_at FROM pending_commands " +
                    "WHERE state = 'rejected' AND (operation LIKE 'rental.%' OR entity_type = 'rental_orders') ORDER BY causal_order DESC",
            )
            if (rows.isEmpty()) return emptyList()
            val lineOrders = V2OverlayMerge.query(conn, "rental_order_lines").associate { it.str("id") to it.str("order_id") }
            return rows.map { r ->
                val failure = V2CommandQueue.failure(r)
                val payload = runCatching { json.parseToJsonElement(r.str("payload")) as? JsonObject }.getOrNull()
                val type = r.strOrNull("entity_type")
                val lineId = payload?.text("line_id") ?: payload?.text("id")?.takeIf { type == "rental_order_lines" }
                val orderId = payload?.text("order_id") ?: payload?.text("id")?.takeIf { type == "rental_orders" }
                    ?: lineId?.let(lineOrders::get)
                RentalFailure(
                    commandId = failure.id,
                    operation = failure.operation,
                    orderId = orderId,
                    lineId = lineId,
                    code = failure.code,
                    message = failure.message,
                    createdAt = r.longOrNull("created_at") ?: 0L,
                )
            }
        }

        /** Units, unit allocations and class holds as `class_free_sql` reads them. */
        fun classAvailability(conn: V2Connection): ClassAvailability.Facts {
            val units = V2OverlayMerge.query(conn, "assets", "rentable = 1 AND rental_class_id IS NOT NULL") {
                it["rentable"] == 1L && it["rental_class_id"] != null
            }.map { ClassAvailability.Unit(it.str("id"), it.str("rental_class_id"), it.strOrNull("location_id")) }
            val allocations = V2OverlayMerge.query(conn, "resource_allocations", "active = 1") { it["active"] == 1L }
            val busy = allocations.filter { it["resource_kind"] == "unit" && it["blocking"] != 0L && it["asset_id"] != null }.map {
                ClassAvailability.UnitBusy(it.str("asset_id"), it.instant("during_start") ?: Instant.EPOCH, it.instant("during_end"))
            }
            val classRows = allocations.filter { it["resource_kind"] == "class" && it["class_id"] != null }
            val orderIds = classRows.mapNotNull { it.strOrNull("order_id") }.toSet()
            val statuses = if (orderIds.isEmpty()) emptyMap() else V2OverlayMerge.query(conn, "rental_orders")
                .filter { it["id"] in orderIds }.associate { it.str("id") to it.strOrNull("status") }
            val holds = classRows.map {
                ClassAvailability.ClassHold(
                    classId = it.str("class_id"),
                    locationId = it.strOrNull("location_id"),
                    qty = it.microsOrNull("qty_micros") ?: BigDecimal.ONE,
                    start = it.instant("during_start") ?: Instant.EPOCH,
                    end = it.instant("during_end"),
                    expiresAt = it.instant("expires_at"),
                    orderStatus = it.strOrNull("order_id")?.let(statuses::get),
                )
            }
            return ClassAvailability.Facts(units, busy, holds)
        }

        /** The stored facts of the fleet rule (fleet.rs `load_units` / `load_allocations`). */
        fun fleetFacts(conn: V2Connection): FleetFacts {
            val assets = V2OverlayMerge.query(conn, "assets", "rentable = 1 OR rental_class_id IS NOT NULL") {
                it["rentable"] == 1L || it["rental_class_id"] != null
            }.filter { it["rental_ownership"] != "sold" }
            if (assets.isEmpty()) return FleetFacts.EMPTY
            val ids = assets.map { it.str("id") }.toSet()
            val classes = V2OverlayMerge.query(conn, "rental_classes").associate { it.str("id") to it.strOrNull("name") }
            val zones = V2OverlayMerge.query(conn, "locations").associate { it.str("id") to it.strOrNull("timezone") }
            val defects = V2OverlayMerge.query(conn, "inspection_defects", "status IN ('open', 'monitoring')") {
                it["status"] == "open" || it["status"] == "monitoring"
            }.filter { it["asset_id"] in ids }
                .groupBy({ it.str("asset_id") }, { FleetFacts.severityRank(it.strOrNull("severity")) })
            val wos = V2OverlayMerge.query(conn, "work_orders").filter { it["status"] !in WO_DONE }.associateBy { it.str("id") }
            val woAssets = V2OverlayMerge.query(conn, "work_order_assets")
            val windows = woAssets.filter { it["asset_id"] in ids }.mapNotNull { wa ->
                val w = wos[wa.strOrNull("work_order_id")] ?: return@mapNotNull null
                wa.str("asset_id") to FleetFacts.WorkWindow(
                    inProgress = w["status"] == "in_progress",
                    start = w.instant("planned_during_start"),
                    end = w.instant("planned_during_end"),
                )
            }.groupBy({ it.first }, { it.second })
            val pm = pmDue(conn, ids, woAssets)
            val readiness = V2OverlayMerge.query(conn, "rental_handovers", "kind = 'readiness'") { it["kind"] == "readiness" }
                .filter { it["asset_id"] in ids && it["voided_at"] == null }
            val checklistOpen = readiness.filter { it["readiness_status"] in LocalFleet.READINESS_OPEN }.map { it.str("asset_id") }.toSet()
            val pendingChecks = syncStates(conn, "rental_handovers").filterValues { it == RecordSyncState.PENDING }.keys
            val latest = readiness.groupBy { it.str("asset_id") }.mapValues { (_, rows) ->
                val r = rows.maxWith(compareBy<V2Row> { it.longOrNull("at") ?: 0L }.thenBy { it.str("id") })
                LocalFleet.ReadinessFlag(
                    checkId = r.str("id"),
                    status = r.strOrNull("readiness_status") ?: "pending",
                    pending = r.str("id") in pendingChecks,
                    at = r.instant("at") ?: Instant.EPOCH,
                )
            }
            val allocations = V2OverlayMerge.query(conn, "resource_allocations", "active = 1") { it["active"] == 1L }
                .filter { it["asset_id"] in ids }
                .map { a ->
                    LocalFleet.Alloc(
                        id = a.str("id"),
                        assetId = a.str("asset_id"),
                        kind = a.strOrNull("purpose") ?: "reserved",
                        blocking = a["blocking"] != 0L,
                        startAt = a.instant("during_start") ?: Instant.EPOCH,
                        endAt = a.instant("during_end"),
                        orderId = a.strOrNull("order_id"),
                        orderLineId = a.strOrNull("order_line_id"),
                        expiresAt = a.instant("expires_at"),
                    )
                }
            val lineIds = allocations.mapNotNull { it.orderLineId }.toSet()
            val orderIds = allocations.mapNotNull { it.orderId }.toSet()
            val lineEnds = if (lineIds.isEmpty()) emptyMap() else V2OverlayMerge.query(conn, "rental_order_lines")
                .filter { it["id"] in lineIds }.associate { it.str("id") to it.instant("end_at") }
            val orderEnds = if (orderIds.isEmpty()) emptyMap() else V2OverlayMerge.query(conn, "rental_orders")
                .filter { it["id"] in orderIds }.associate { it.str("id") to it.instant("end_at") }
            val units = assets.map { a ->
                val id = a.str("id")
                val classId = a.strOrNull("rental_class_id")
                FleetFacts.UnitFacts(
                    assetId = id,
                    name = a.strOrNull("name").orEmpty(),
                    assetType = a.strOrNull("asset_type_code"),
                    classId = classId,
                    className = classId?.let(classes::get),
                    locationId = a.strOrNull("location_id"),
                    timeZone = a.strOrNull("location_id")?.let(zones::get),
                    offline = a["status_code"] == "offline" || a["rental_sold_at"] != null,
                    defectRank = defects[id].orEmpty().maxOrNull() ?: 0,
                    workOrders = windows[id].orEmpty(),
                    pm = pm[id].orEmpty(),
                    checklistOpen = id in checklistOpen,
                )
            }.sortedWith(compareBy<FleetFacts.UnitFacts, String?>(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.className }.thenBy { it.name }.thenBy { it.assetId })
            return FleetFacts(units, allocations, lineEnds, orderEnds, latest)
        }

        /** Open PM occurrences per unit: the trigger's asset, else the occurrence work order's assets. */
        private fun pmDue(conn: V2Connection, ids: Set<String>, woAssets: List<V2Row>): Map<String, List<FleetFacts.PmDue>> {
            val open = V2OverlayMerge.query(conn, "pm_occurrences", "state = 'open'") { it["state"] == "open" }
            if (open.isEmpty()) return emptyMap()
            val triggers = V2OverlayMerge.query(conn, "pm_triggers").associateBy { it.str("id") }
            val assetsByWo = woAssets.groupBy({ it.str("work_order_id") }, { it.str("asset_id") })
            val latestReading = conn.query(
                "SELECT asset_id, meter_id, value_micros FROM meter_readings r WHERE NOT EXISTS (" +
                    "SELECT 1 FROM meter_readings n WHERE n.asset_id = r.asset_id AND n.meter_id = r.meter_id " +
                    "AND (n.read_at > r.read_at OR (n.read_at = r.read_at AND n.id > r.id)))",
            ).associate { (it.str("asset_id") to it.str("meter_id")) to it.longOrNull("value_micros") }
            return open.flatMap { o ->
                val trigger = o.strOrNull("trigger_id")?.let(triggers::get)
                val assets = trigger?.strOrNull("asset_id")?.let(::listOf)
                    ?: o.strOrNull("work_order_id")?.let(assetsByWo::get).orEmpty()
                assets.filter { it in ids }.map { asset ->
                    val dueValue = o.longOrNull("due_value_micros")
                    val meter = trigger?.strOrNull("meter_id")
                    val reading = meter?.let { latestReading[asset to it] }
                    asset to FleetFacts.PmDue(
                        dueAt = o.instant("due_at"),
                        meterDue = dueValue != null && reading != null && dueValue <= reading,
                    )
                }
            }.groupBy({ it.first }, { it.second })
        }

        private fun jsonArray(text: String?): List<JsonElement> =
            text?.let { runCatching { json.parseToJsonElement(it) as? JsonArray }.getOrNull() }.orEmpty()
    }
}

internal fun V2Row.instant(column: String): Instant? = longOrNull(column)?.let(Instant::ofEpochMilli)

internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
