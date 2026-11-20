package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * A toll, ticket or citation billed to an order after the rental (`post_charges`). The amount and
 * the admin fee become adjustment lines on the order; charging the card on file is an online action.
 */
data class PostCharge(
    val id: String,
    val version: Long,
    val orderId: String,
    val assetId: String?,
    /** toll | ticket | citation */
    val kind: String,
    val amountMinor: Long,
    val adminFeeMinor: Long,
    val currency: String,
    val incurredAt: Instant,
    val reference: String?,
    val authority: String?,
    val description: String?,
    val evidenceFileId: String?,
    /** pending | charged | failed | voided */
    val status: String,
    /** Set while a card charge waits for the customer (3-D Secure): the pay link of that request. */
    val paymentRequestId: String?,
    val chargeError: String?,
    val voidReason: String?,
    /** The amount line is on an issued invoice. */
    val invoiced: Boolean,
    val sync: RecordSyncState = RecordSyncState.SAVED,
) {
    val totalMinor: Long get() = amountMinor + adminFeeMinor

    /** A card charge is waiting for the customer to authenticate (server `requires_action`). */
    val requiresAction: Boolean get() = status == STATUS_PENDING && paymentRequestId != null

    /** Server `rental.post_charges.void`: neither charged, waiting on the customer nor invoiced. */
    val voidable: Boolean get() = (status == STATUS_PENDING || status == STATUS_FAILED) && !requiresAction && !invoiced

    /** Charging is an online action on a stored charge that is still open. */
    val chargeable: Boolean get() = sync == RecordSyncState.SAVED && (status == STATUS_PENDING || status == STATUS_FAILED) && !requiresAction

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_CHARGED = "charged"
        const val STATUS_FAILED = "failed"
        const val STATUS_VOIDED = "voided"
        val KINDS = listOf("toll", "ticket", "citation")
    }
}

data class PostChargeDraft(
    val orderId: String,
    /** toll | ticket | citation */
    val kind: String,
    val amountMinor: Long,
    val adminFeeMinor: Long?,
    val incurredAt: Instant,
    /** The photo or scan of the toll notice / ticket (required). */
    val evidence: LocalFileRef,
    val assetId: String?,
    val reference: String?,
    val authority: String?,
    val description: String?,
)

/** One money movement on a customer account (`customer_ledger_entries`). */
data class LedgerEntry(
    val id: String,
    val orderId: String?,
    val customerPartyId: String?,
    /** payment | refund | deposit_received | deposit_applied | deposit_refunded | deposit_forfeited | … */
    val kind: String,
    val method: String?,
    val amountMinor: Long,
    val currency: String,
    /** pending | authorized | settled | failed | canceled */
    val state: String,
    val receivedAt: Instant?,
    val reference: String?,
    val notes: String?,
    val cardBrand: String?,
    /** The card expiry ("MM/YY"), when the entry is a card payment. */
    val cardExpiry: String?,
    val payerName: String?,
    val payerEmail: String?,
    val receiptNumber: String?,
    /** Charged through the payment provider (capture, release and refund go through it). */
    val viaGateway: Boolean,
    val capturedAt: Instant?,
    val authorizationExpiresAt: Instant?,
    val failureMessage: String?,
    val refundOfEntryId: String?,
    val voidReason: String?,
    val sync: RecordSyncState = RecordSyncState.SAVED,
) {
    /** Money in (payments, deposits) counts positive, money out negative. */
    val signedMinor: Long get() = if (kind in OUTGOING) -amountMinor else amountMinor

    val isAuthorization: Boolean get() = state == STATE_AUTHORIZED

    /** A provider payment that settled and is not itself a refund can be refunded online. */
    val refundable: Boolean get() = viaGateway && state == STATE_SETTLED && kind in REFUNDABLE

    companion object {
        const val STATE_PENDING = "pending"
        const val STATE_AUTHORIZED = "authorized"
        const val STATE_SETTLED = "settled"
        const val STATE_FAILED = "failed"
        const val STATE_CANCELED = "canceled"
        private val OUTGOING = setOf("refund", "deposit_refunded", "chargeback", "reversal", "write_off")
        private val REFUNDABLE = setOf("payment", "deposit_received")
    }
}

/** A long-term rental contract (`rental_contracts`): rent-to-own, lease or a minimum-term rental. */
data class RentalContract(
    val id: String,
    val number: String?,
    val orderId: String?,
    val customerPartyId: String?,
    val kind: String,
    val term: String?,
    val status: String,
    val startAt: Instant?,
    val endAt: Instant?,
    val minTermMonths: Int?,
    val noticeDays: Int?,
    val monthlyRateMinor: Long?,
    val currency: String,
    val assetId: String?,
    val purchasePriceMinor: Long?,
    val equityBp: Int?,
    val buyoutFloorMinor: Long?,
    val boughtOutAt: Instant?,
    val terminatedAt: Instant?,
    val terminationReason: String?,
    val notes: String?,
) {
    /** Rent-to-own contracts have a buy-out and an equity statement. */
    val ownsEquity: Boolean get() = purchasePriceMinor != null && equityBp != null

    val buyable: Boolean get() = ownsEquity && boughtOutAt == null && terminatedAt == null && status == "active"
}

data class SignatureSigner(
    val id: String,
    val order: Int,
    val name: String,
    val email: String?,
    val role: String,
    /** in_person | remote */
    val method: String,
    /** pending | sent | viewed | signed | declined */
    val status: String,
    val signedAt: Instant?,
    val declineReason: String?,
)

/** A signature request (`signature_requests` + `signature_signers`). */
data class SignatureRequest(
    val id: String,
    val orderId: String?,
    val purpose: String,
    val docType: String?,
    /** pending | completed | declined | expired | voided */
    val status: String,
    val expiresAt: Instant?,
    val completedAt: Instant?,
    val signedDocumentId: String?,
    val voidReason: String?,
    val createdAt: Instant?,
    val signers: List<SignatureSigner>,
)

/** Equipment hired in from a supplier for an order (`rental_subrentals`). */
data class Subrental(
    val id: String,
    val number: String?,
    val vendorPartyId: String?,
    val vendorName: String?,
    val description: String?,
    val classId: String?,
    val assetId: String?,
    val qty: String?,
    val costRateMinor: Long?,
    val costPeriod: String?,
    val currency: String?,
    val plannedStart: Instant?,
    val plannedEnd: Instant?,
    val receivedAt: Instant?,
    val returnedAt: Instant?,
    val status: String,
    val orderId: String?,
    val orderLineId: String?,
    val poReference: String?,
    val notes: String?,
)

/** An identity or licence document of a customer (`party_documents`; the number is kept as its last 4). */
data class PartyDocument(
    val id: String,
    val partyId: String,
    val kind: String,
    val numberLast4: String?,
    val issuer: String?,
    val issuingCountry: String?,
    val issuingRegion: String?,
    val classCodes: String?,
    val issuedOn: LocalDate?,
    val expiresOn: LocalDate?,
    val verifiedAt: Instant?,
    val notes: String?,
    val sync: RecordSyncState = RecordSyncState.SAVED,
) {
    fun expired(today: LocalDate): Boolean = expiresOn?.isBefore(today) == true
}

/** A site of a customer (`party_locations` + `locations`): job sites, billing and shipping addresses. */
data class CustomerSite(
    val id: String,
    val partyId: String,
    val locationId: String,
    val name: String?,
    val address: String?,
    /** primary | billing | shipping | job_site | … */
    val purpose: String,
    val isPrimary: Boolean,
    val contactNote: String?,
)

/** A manual price line on an order (`rental.rental_order_lines.create` with a unit price). */
data class PriceAdjustmentDraft(
    val orderId: String,
    /** adjustment (either sign) | discount (always reduces) | fee | damage | fuel | late | overage */
    val kind: String,
    val description: String,
    val amountMinor: Long,
    val reason: String,
) {
    companion object {
        val KINDS = listOf("adjustment", "discount", "fee", "damage", "fuel", "late", "overage")
    }
}

/** Contracts, money, signatures, sub-rentals and customer records of the rental workflow. */
interface RentalAccountsRepository {
    fun observePostCharges(orderId: String): Flow<List<PostCharge>>

    /** Ledger entries of an order, or of a customer when [orderId] is null. */
    fun observeLedger(orderId: String?, customerPartyId: String? = null): Flow<List<LedgerEntry>>

    /** Contracts of an order, or of a customer when [orderId] is null. */
    fun observeContracts(orderId: String?, customerPartyId: String? = null): Flow<List<RentalContract>>

    fun observeSignatureRequests(orderId: String): Flow<List<SignatureRequest>>

    fun observeSubrentals(orderId: String): Flow<List<Subrental>>

    fun observeCustomerDocuments(partyId: String): Flow<List<PartyDocument>>

    fun observeCustomerSites(partyId: String): Flow<List<CustomerSite>>

    /** Queues `rental.post_charges.create` with the staged evidence; returns the charge id. */
    suspend fun createPostCharge(draft: PostChargeDraft): String

    suspend fun voidPostCharge(id: String, reason: String?)

    /** Adds a manual price line; returns the line id. */
    suspend fun addPriceAdjustment(draft: PriceAdjustmentDraft): String
}
