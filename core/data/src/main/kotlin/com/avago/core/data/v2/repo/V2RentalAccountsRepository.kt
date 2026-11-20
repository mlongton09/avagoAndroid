package com.avago.core.data.v2.repo

import androidx.core.net.toUri
import com.avago.core.data.domain.CustomerSite
import com.avago.core.data.domain.LedgerEntry
import com.avago.core.data.domain.PartyDocument
import com.avago.core.data.domain.PostCharge
import com.avago.core.data.domain.PostChargeDraft
import com.avago.core.data.domain.PriceAdjustmentDraft
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalAccountsRepository
import com.avago.core.data.domain.RentalContract
import com.avago.core.data.domain.SignatureRequest
import com.avago.core.data.domain.SignatureSigner
import com.avago.core.data.domain.Subrental
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2FileStager
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Uuid
import com.avago.core.data.v2.bool
import com.avago.core.data.v2.commands.RentalAccountCommands
import com.avago.core.data.v2.intOrNull
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.microsOrNull
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Post charges, ledger, contracts, signature requests, sub-rentals and customer records on the v2 store. */
@Singleton
class V2RentalAccountsRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
    private val stager: V2FileStager,
) : RentalAccountsRepository {

    override fun observePostCharges(orderId: String): Flow<List<PostCharge>> =
        store.observe(setOf("post_charges", "pending_commands"), emptyList()) { conn -> postCharges(conn, orderId) }

    override fun observeLedger(orderId: String?, customerPartyId: String?): Flow<List<LedgerEntry>> {
        require(orderId != null || customerPartyId != null) { "the ledger needs an order or a customer" }
        return store.observe(setOf("customer_ledger_entries", "pending_commands"), emptyList()) { conn ->
            ledger(conn, orderId, customerPartyId)
        }
    }

    override fun observeContracts(orderId: String?, customerPartyId: String?): Flow<List<RentalContract>> {
        require(orderId != null || customerPartyId != null) { "contracts need an order or a customer" }
        return store.observe(setOf("rental_contracts"), emptyList()) { conn ->
            val (where, arg) = if (orderId != null) "order_id = ?" to orderId else "customer_party_id = ?" to customerPartyId
            val column = if (orderId != null) "order_id" else "customer_party_id"
            V2OverlayMerge.query(conn, "rental_contracts", where, listOf(arg)) { it[column] == arg }
                .map(::contract)
                .sortedByDescending { it.startAt ?: Instant.EPOCH }
        }
    }

    override fun observeSignatureRequests(orderId: String): Flow<List<SignatureRequest>> =
        store.observe(setOf("signature_requests", "signature_signers"), emptyList()) { conn ->
            val requests = V2OverlayMerge.query(conn, "signature_requests", "order_id = ?", listOf(orderId)) { it["order_id"] == orderId }
            if (requests.isEmpty()) return@observe emptyList()
            val ids = requests.map { it.str("id") }.toSet()
            val signers = V2OverlayMerge.query(conn, "signature_signers")
                .filter { it["request_id"] in ids }
                .groupBy { it.str("request_id") }
            requests.map { r -> signatureRequest(r, signers[r.str("id")].orEmpty()) }.sortedByDescending { it.createdAt ?: Instant.EPOCH }
        }

    override fun observeSubrentals(orderId: String): Flow<List<Subrental>> =
        store.observe(setOf("rental_subrentals", "parties"), emptyList()) { conn ->
            val rows = V2OverlayMerge.query(conn, "rental_subrentals", "order_id = ?", listOf(orderId)) { it["order_id"] == orderId }
            if (rows.isEmpty()) return@observe emptyList()
            val names = V2OverlayMerge.query(conn, "parties").associate { it.str("id") to it.strOrNull("display_name") }
            rows.map { subrental(it, names) }.sortedBy { it.plannedStart ?: Instant.EPOCH }
        }

    override fun observeCustomerDocuments(partyId: String): Flow<List<PartyDocument>> =
        store.observe(setOf("party_documents", "pending_commands"), emptyList()) { conn ->
            val sync = V2RentalRepository.syncStates(conn, "party_documents")
            V2OverlayMerge.query(conn, "party_documents", "party_id = ?", listOf(partyId)) { it["party_id"] == partyId }
                .map { partyDocument(it, sync) }
                .sortedWith(compareBy<PartyDocument> { it.kind }.thenByDescending { it.expiresOn ?: LocalDate.MIN })
        }

    override fun observeCustomerSites(partyId: String): Flow<List<CustomerSite>> =
        store.observe(setOf("party_locations", "locations"), emptyList()) { conn ->
            val rows = V2OverlayMerge.query(conn, "party_locations", "party_id = ?", listOf(partyId)) { it["party_id"] == partyId }
            if (rows.isEmpty()) return@observe emptyList()
            val locations = V2LocationRepository.locations(conn, includeArchived = true).associateBy { it.id }
            rows.mapNotNull { r ->
                val location = locations[r.strOrNull("location_id")] ?: return@mapNotNull null
                CustomerSite(
                    id = r.str("id"),
                    partyId = r.str("party_id"),
                    locationId = location.id,
                    name = location.name.ifBlank { null },
                    address = listOfNotNull(
                        location.addressLine1, location.addressLine2,
                        listOfNotNull(location.locality, location.region, location.postalCode).joinToString(" ").ifBlank { null },
                        location.countryCode,
                    ).joinToString("\n").ifBlank { null },
                    purpose = r.strOrNull("purpose") ?: "primary",
                    isPrimary = r.bool("is_primary"),
                    contactNote = r.strOrNull("contact_note"),
                )
            }.sortedWith(compareByDescending<CustomerSite> { it.isPrimary }.thenBy { it.purpose }.thenBy { it.name.orEmpty() })
        }

    override suspend fun createPostCharge(draft: PostChargeDraft): String {
        val order = store.read(null) { conn -> V2RentalRepository.orders(conn, "id = ?" to listOf(draft.orderId)) { it["id"] == draft.orderId }.firstOrNull() }
            ?: throw RecordNotFoundException("rental order", draft.orderId)
        val evidence = stager.stage(draft.evidence.uri.toUri(), draft.evidence.fallbackMediaType)
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(RentalAccountCommands.createPostCharge(id, draft, order, evidence, now, commands.currentUserId()))
        return id
    }

    override suspend fun voidPostCharge(id: String, reason: String?) {
        val stored = store.read(null) { conn -> postCharges(conn, null).firstOrNull { it.id == id } }
            ?: throw RecordNotFoundException("post charge", id)
        val now = System.currentTimeMillis()
        commands.submit(RentalAccountCommands.voidPostCharge(stored, reason, now, commands.currentUserId()))
    }

    override suspend fun addPriceAdjustment(draft: PriceAdjustmentDraft): String {
        val (order, lineNo) = store.read(null) { conn ->
            val order = V2RentalRepository.orders(conn, "id = ?" to listOf(draft.orderId)) { it["id"] == draft.orderId }.firstOrNull()
                ?: return@read null
            val last = V2OverlayMerge.query(conn, "rental_order_lines", "order_id = ?", listOf(draft.orderId)) { it["order_id"] == draft.orderId }
                .maxOfOrNull { it.longOrNull("line_no") ?: 0L } ?: 0L
            order to last + 1
        } ?: throw RecordNotFoundException("rental order", draft.orderId)
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(RentalAccountCommands.priceAdjustment(id, draft, order, lineNo, now, commands.currentUserId()))
        return id
    }

    companion object {
        fun postCharges(conn: V2Connection, orderId: String?): List<PostCharge> {
            val rows = if (orderId == null) {
                V2OverlayMerge.query(conn, "post_charges")
            } else {
                V2OverlayMerge.query(conn, "post_charges", "order_id = ?", listOf(orderId)) { it["order_id"] == orderId }
            }
            if (rows.isEmpty()) return emptyList()
            val sync = V2RentalRepository.syncStates(conn, "post_charges")
            val invoicedLines = V2OverlayMerge.query(conn, "rental_order_lines", "invoiced_through IS NOT NULL") { it["invoiced_through"] != null }
                .map { it.str("id") }.toSet()
            return rows.map { r ->
                val id = r.str("id")
                PostCharge(
                    id = id,
                    version = r.longOrNull("version") ?: 0L,
                    orderId = r.str("order_id"),
                    assetId = r.strOrNull("asset_id"),
                    kind = r.strOrNull("kind") ?: "toll",
                    amountMinor = r.longOrNull("amount_minor") ?: 0L,
                    adminFeeMinor = r.longOrNull("admin_fee_minor") ?: 0L,
                    currency = r.strOrNull("currency").orEmpty(),
                    incurredAt = r.instant("incurred_at") ?: Instant.EPOCH,
                    reference = r.strOrNull("reference"),
                    authority = r.strOrNull("authority"),
                    description = r.strOrNull("description"),
                    evidenceFileId = r.strOrNull("evidence_file_object_id"),
                    status = r.strOrNull("status") ?: PostCharge.STATUS_PENDING,
                    paymentRequestId = r.strOrNull("payment_request_id"),
                    chargeError = r.strOrNull("charge_error"),
                    voidReason = r.strOrNull("void_reason"),
                    invoiced = r.strOrNull("order_line_id") in invoicedLines,
                    sync = sync[id] ?: RecordSyncState.SAVED,
                )
            }.sortedByDescending { it.incurredAt }
        }

        /**
         * Entries of an order (or a customer), newest first. A payment is recorded through its order's
         * command, so an entry the server has not stored yet is pending by its absence from the base table.
         */
        fun ledger(conn: V2Connection, orderId: String?, customerPartyId: String?): List<LedgerEntry> {
            val column = if (orderId != null) "order_id" else "customer_party_id"
            val arg = orderId ?: requireNotNull(customerPartyId)
            val rows = V2OverlayMerge.query(conn, "customer_ledger_entries", "$column = ?", listOf(arg)) { it[column] == arg }
            if (rows.isEmpty()) return emptyList()
            val stored = conn.query("SELECT id FROM customer_ledger_entries WHERE $column = ?", listOf(arg)).map { it.str("id") }.toSet()
            val sync = V2RentalRepository.syncStates(conn, "customer_ledger_entries") +
                rows.map { it.str("id") }.filter { it !in stored }.associateWith { RecordSyncState.PENDING }
            return rows.map { ledgerEntry(it, sync) }.sortedByDescending { it.receivedAt ?: Instant.EPOCH }
        }

        fun ledgerEntry(r: V2Row, sync: Map<String, RecordSyncState>): LedgerEntry {
            val id = r.str("id")
            val method = r.strOrNull("method")
            return LedgerEntry(
                id = id,
                orderId = r.strOrNull("order_id"),
                customerPartyId = r.strOrNull("customer_party_id"),
                kind = r.strOrNull("kind") ?: "payment",
                method = method,
                amountMinor = r.longOrNull("amount_minor") ?: 0L,
                currency = r.strOrNull("currency").orEmpty(),
                state = r.strOrNull("state") ?: LedgerEntry.STATE_SETTLED,
                receivedAt = r.instant("received_at") ?: r.instant("created_at"),
                reference = r.strOrNull("reference"),
                notes = r.strOrNull("notes"),
                cardBrand = r.strOrNull("card_brand"),
                cardLast4 = r.strOrNull("card_last4"),
                cardExpiry = r.longOrNull("card_exp_month")?.let { m ->
                    r.longOrNull("card_exp_year")?.let { y -> "%02d/%02d".format(m, y % 100) }
                },
                payerName = r.strOrNull("payer_name"),
                payerEmail = r.strOrNull("payer_email"),
                receiptNumber = r.strOrNull("receipt_number"),
                viaGateway = method == "card" || r.strOrNull("provider_payment_id") != null,
                capturedAt = r.instant("captured_at"),
                authorizationExpiresAt = r.instant("authorization_expires_at"),
                failureMessage = r.strOrNull("failure_message"),
                refundOfEntryId = r.strOrNull("refund_of_entry_id"),
                voidReason = r.strOrNull("void_reason"),
                sync = sync[id] ?: RecordSyncState.SAVED,
            )
        }

        private fun contract(r: V2Row) = RentalContract(
            id = r.str("id"),
            number = r.strOrNull("number"),
            orderId = r.strOrNull("order_id"),
            customerPartyId = r.strOrNull("customer_party_id"),
            kind = r.strOrNull("kind").orEmpty(),
            term = r.strOrNull("term"),
            status = r.strOrNull("status") ?: "active",
            startAt = r.instant("start_at"),
            endAt = r.instant("end_at"),
            minTermMonths = r.intOrNull("min_term_months"),
            noticeDays = r.intOrNull("notice_days"),
            monthlyRateMinor = r.longOrNull("monthly_rate_minor"),
            currency = r.strOrNull("currency").orEmpty(),
            assetId = r.strOrNull("asset_id"),
            purchasePriceMinor = r.longOrNull("purchase_price_minor"),
            equityBp = r.intOrNull("equity_bp"),
            buyoutFloorMinor = r.longOrNull("buyout_floor_minor"),
            boughtOutAt = r.instant("bought_out_at"),
            terminatedAt = r.instant("terminated_at"),
            terminationReason = r.strOrNull("termination_reason"),
            notes = r.strOrNull("notes"),
        )

        private fun signatureRequest(r: V2Row, signers: List<V2Row>) = SignatureRequest(
            id = r.str("id"),
            orderId = r.strOrNull("order_id"),
            purpose = r.strOrNull("purpose").orEmpty(),
            docType = r.strOrNull("doc_type"),
            status = r.strOrNull("status") ?: "pending",
            expiresAt = r.instant("expires_at"),
            completedAt = r.instant("completed_at"),
            signedDocumentId = r.strOrNull("signed_document_id"),
            voidReason = r.strOrNull("void_reason"),
            createdAt = r.instant("created_at"),
            signers = signers.map { s ->
                SignatureSigner(
                    id = s.str("id"),
                    order = s.intOrNull("signer_order") ?: 0,
                    name = s.strOrNull("signer_name").orEmpty(),
                    email = s.strOrNull("signer_email"),
                    role = s.strOrNull("signer_role") ?: "customer",
                    method = s.strOrNull("method") ?: "in_person",
                    status = s.strOrNull("status") ?: "pending",
                    signedAt = s.instant("signed_at"),
                    declineReason = s.strOrNull("decline_reason"),
                )
            }.sortedBy { it.order },
        )

        private fun subrental(r: V2Row, names: Map<String, String?>): Subrental {
            val vendor = r.strOrNull("vendor_party_id")
            return Subrental(
                id = r.str("id"),
                number = r.strOrNull("number"),
                vendorPartyId = vendor,
                vendorName = vendor?.let(names::get),
                description = r.strOrNull("description"),
                classId = r.strOrNull("class_id"),
                assetId = r.strOrNull("asset_id"),
                qty = r.microsOrNull("qty_micros")?.stripTrailingZeros()?.toPlainString(),
                costRateMinor = r.longOrNull("cost_rate_minor"),
                costPeriod = r.strOrNull("cost_period"),
                currency = r.strOrNull("currency"),
                plannedStart = r.instant("planned_during_start"),
                plannedEnd = r.instant("planned_during_end"),
                receivedAt = r.instant("received_at"),
                returnedAt = r.instant("returned_at"),
                status = r.strOrNull("status").orEmpty(),
                orderId = r.strOrNull("order_id"),
                orderLineId = r.strOrNull("order_line_id"),
                poReference = r.strOrNull("po_reference"),
                notes = r.strOrNull("notes"),
            )
        }

        private fun partyDocument(r: V2Row, sync: Map<String, RecordSyncState>): PartyDocument {
            val id = r.str("id")
            return PartyDocument(
                id = id,
                partyId = r.str("party_id"),
                kind = r.strOrNull("kind").orEmpty(),
                numberLast4 = r.strOrNull("number_last4"),
                issuer = r.strOrNull("issuer"),
                issuingCountry = r.strOrNull("issuing_country"),
                issuingRegion = r.strOrNull("issuing_region"),
                classCodes = r.strOrNull("class_codes"),
                issuedOn = r.strOrNull("issued_on")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                expiresOn = r.strOrNull("expires_on")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                verifiedAt = r.instant("verified_at"),
                notes = r.strOrNull("notes"),
                sync = sync[id] ?: RecordSyncState.SAVED,
            )
        }
    }
}
