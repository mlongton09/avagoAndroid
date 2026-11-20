package com.avago.core.data.domain

import com.avago.core.data.rental.ClassAvailability
import com.avago.core.data.rental.LocalFleet
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Order lifecycle (`rental_orders.status`). */
object RentalOrderStatus {
    const val DRAFT = "draft"
    const val QUOTED = "quoted"
    const val HELD = "held"
    const val RESERVED = "reserved"
    const val ON_RENT = "on_rent"
    const val OFF_RENT_REQUESTED = "off_rent_requested"
    const val RETURNED = "returned"
    const val CLOSED = "closed"
    const val CANCELLED = "cancelled"

    /** Statuses whose order still holds units (allocations count for availability). */
    val ACTIVE = setOf(HELD, RESERVED, ON_RENT, OFF_RENT_REQUESTED)
}

data class RentalOrder(
    val id: String,
    val version: Long,
    val number: String?,
    val quoteNumber: String?,
    val status: String,
    /** staff | storefront | portal | api | legacy | marketplace | network */
    val source: String,
    val kind: String,
    val currency: String,
    val locale: String?,
    val customerPartyId: String?,
    /** The customer's display name, else the walk-in name. */
    val customerName: String?,
    val walkInName: String?,
    val contactId: String?,
    val siteLocationId: String?,
    val locationId: String?,
    val startAt: Instant?,
    val endAt: Instant?,
    val offRentAt: Instant?,
    val holdExpiresAt: Instant?,
    val quoteExpiresAt: Instant?,
    val fulfilment: String,
    val poNumber: String?,
    val reference: String?,
    val notes: String?,
    val internalNotes: String?,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val depositRequiredMinor: Long,
    /** Sum of the order's issued invoices and what was paid on them (null without invoices). */
    val invoicedMinor: Long?,
    val invoicePaidMinor: Long?,
    val cancelReason: String?,
    val needsReview: Boolean,
    val sync: RecordSyncState,
)

data class RentalOrderLine(
    val id: String,
    val version: Long,
    val orderId: String,
    val lineNo: Int,
    /** rental | accessory | sale | fee | damage | … | kit */
    val kind: String,
    val classId: String?,
    val assetId: String?,
    val productId: String?,
    val parentLineId: String?,
    val description: String,
    val qty: BigDecimal,
    val startAt: Instant?,
    val endAt: Instant?,
    val actualOutAt: Instant?,
    val actualInAt: Instant?,
    val unitPriceMinor: Long?,
    val amountMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    /** open | out | returned | cancelled | swapped */
    val status: String,
    val kitInstanceId: String?,
    val assignUnitLater: Boolean,
    val sync: RecordSyncState,
) {
    val isKitHeader: Boolean get() = kind == "kit"

    /** A bulk (non-serialized) line: a product rented by quantity, returned with counts. */
    val isBulk: Boolean get() = productId != null && assetId == null && classId == null && (kind == "rental" || kind == "accessory")

    /** Still with the customer or yet to go out. */
    val isOpen: Boolean get() = status == "open" || status == "out"
}

data class RentalAllocation(
    val id: String,
    val assetId: String?,
    val classId: String?,
    val productId: String?,
    val orderId: String?,
    val orderLineId: String?,
    val workOrderId: String?,
    /** quote | hold | reserved | on_rent | buffer | maintenance | transfer | loan | coowner */
    val purpose: String,
    val startAt: Instant,
    val endAt: Instant?,
    val expiresAt: Instant?,
    val active: Boolean,
    val blocking: Boolean,
    val qty: BigDecimal,
    /** unit | class | bulk */
    val resourceKind: String,
)

/** A damage finding stored on a handover (`damage_summary`). */
data class RentalDamage(
    val label: String,
    val severity: String?,
    /** charge | waive | pre_existing */
    val decision: String?,
    val chargeMinor: Long?,
    /** Sketch zone / where on the unit. */
    val location: String?,
)

data class RentalHandover(
    val id: String,
    /** checkout | checkin | readiness */
    val kind: String,
    val at: Instant,
    val orderId: String?,
    val orderLineId: String?,
    val assetId: String?,
    val fuelLevelPct: Int?,
    val chargeLevelPct: Int?,
    val customerSignatureId: String?,
    val notes: String?,
    val damage: List<RentalDamage>,
    val voided: Boolean,
)

data class ReadinessCheckItem(val key: String, val label: String, val done: Boolean, val note: String? = null)

/** A readiness checklist (`rental_handovers` row of kind `readiness`). */
data class ReadinessCheck(
    val id: String,
    val version: Long,
    val assetId: String,
    val orderId: String?,
    /** pending | passed | failed | waived */
    val status: String,
    val items: List<ReadinessCheckItem>,
    val notes: String?,
    val at: Instant,
    val sync: RecordSyncState,
    /** Condition photos (file ids, oldest first). */
    val photoFileIds: List<String> = emptyList(),
    /** The `files` rows of [photoFileIds] known on this device (bytes via [FileRepository]). */
    val photos: List<StoredFile> = emptyList(),
) {
    val open: Boolean get() = status == "pending" || status == "failed"
}

data class RentalBulkReturn(
    val id: String,
    val orderId: String,
    val orderLineId: String,
    val productId: String,
    val returned: BigDecimal,
    val damaged: BigDecimal,
    val lost: BigDecimal,
    val chargeMinor: Long,
    val notes: String?,
    val at: Instant,
)

data class RentalSignature(
    val id: String,
    val orderId: String?,
    val documentId: String?,
    val signerName: String,
    val signerRole: String,
    val method: String,
    val signedAt: Instant,
    val requestId: String?,
    val signerId: String?,
    val handoverId: String?,
)

data class RentalInvoice(
    val id: String,
    val orderId: String?,
    val number: String?,
    /** draft | sent | issued | partially_paid | paid | credited | void */
    val status: String,
    val kind: String,
    val currency: String,
    val totalMinor: Long,
    val paidMinor: Long,
    val dueOn: String?,
    val sync: RecordSyncState,
)

data class RentalCustomer(
    val partyId: String,
    val version: Long,
    /** person | organization */
    val kind: String,
    val name: String,
    val legalName: String?,
    val company: String?,
    val email: String?,
    val phone: String?,
    /** The primary address, one line per field joined by newlines. */
    val address: String?,
    val notes: String?,
    /** active | on_hold | blocked | cash_only */
    val status: String,
    val statusReason: String?,
    val creditLimitMinor: Long?,
    val currency: String?,
    val paymentTermsDays: Int?,
    val poRequired: Boolean,
    val doNotRent: Boolean,
    val doNotRentReason: String?,
    val taxExempt: Boolean,
    val locale: String?,
    val receivableMinor: Long?,
    val archived: Boolean,
    val sync: RecordSyncState,
)

data class RentalClass(
    val id: String,
    val name: String,
    /** serialized | bulk */
    val tracking: String,
    /** distance | hours | none */
    val meterKind: String?,
    val depositMinor: Long?,
    val depositCurrency: String?,
    val active: Boolean,
    val hidden: Boolean,
)

/** A rental delivery / pick-up / transfer task (`rental_delivery_tasks`). */
data class RentalDeliveryTask(
    val id: String,
    val version: Long,
    /** delivery | pickup | transfer | swap */
    val kind: String,
    /** planned | assigned | en_route | arrived | completed | failed | cancelled */
    val status: String,
    val orderId: String?,
    val orderNumber: String?,
    val customerName: String?,
    val transferId: String?,
    val destinationLocationId: String?,
    val destinationLabel: String?,
    /** The destination location's name (job site / branch), when the task targets one. */
    val destinationName: String?,
    /** Navigation target: the snapshot coordinates, else the destination location's. */
    val latitude: Double?,
    val longitude: Double?,
    val contactName: String?,
    val contactPhone: String?,
    val windowStart: Instant?,
    val windowEnd: Instant?,
    val driverUserId: String?,
    val routeSeq: Int?,
    val notes: String?,
    val startedAt: Instant?,
    val arrivedAt: Instant?,
    val completedAt: Instant?,
    val failedReason: String?,
    val etaAt: Instant?,
    val sync: RecordSyncState,
)

data class RentalTransfer(
    val id: String,
    val version: Long,
    val assetId: String,
    val assetName: String?,
    val fromLocationId: String?,
    val toLocationId: String,
    /** planned | in_transit | received | cancelled */
    val status: String,
    val shipAt: Instant,
    val arriveBy: Instant?,
    val shippedAt: Instant?,
    val receivedAt: Instant?,
    val reason: String?,
    val orderId: String?,
    val sync: RecordSyncState,
)

/** A refused rental command, with the gate code the server answered (`signature_required`, …). */
data class RentalFailure(
    val commandId: String,
    val operation: String,
    val orderId: String?,
    val lineId: String?,
    val code: String,
    val message: String?,
    val createdAt: Long,
) {
    val isHandover: Boolean get() = operation == OP_CHECKOUT || operation == OP_CHECKIN

    companion object {
        const val OP_CHECKOUT = "rental.rental_handovers.checkout"
        const val OP_CHECKIN = "rental.rental_handovers.checkin"
    }
}

// ------------------------------------------------------------------------------- drafts

data class RentalCustomerDraft(
    val name: String,
    val kind: String = "person",
    val company: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val dateOfBirth: LocalDate? = null,
    val notes: String? = null,
    val preferredLocale: String? = null,
    val preferredCurrency: String? = null,
)

data class CustomerDocumentDraft(
    /** driver_licence | identity | coi | tax_exempt | operator_cert | credit_app | attachment | other */
    val kind: String,
    val number: String?,
    val issuer: String? = null,
    val issuingCountry: String?,
    val issuingRegion: String?,
    val classCodes: String?,
    val issuedOn: LocalDate?,
    val expiresOn: LocalDate?,
    val notes: String? = null,
    val scans: List<LocalFileRef> = emptyList(),
)

/** A new booking: one rental line for a class or a unit, optionally moved on at once. */
data class RentalBookingDraft(
    val customerPartyId: String?,
    val walkInName: String?,
    val classId: String?,
    val assetId: String?,
    val description: String?,
    val locationId: String?,
    val currency: String?,
    val startAt: Instant,
    val endAt: Instant,
    /** quote | hold | reserve | null (stay draft) */
    val action: String?,
    val notes: String? = null,
)

/** An order transition. Each kind maps to one catalog command. */
sealed interface RentalOrderAction {
    data object Quote : RentalOrderAction
    data class Hold(val reason: String? = null) : RentalOrderAction
    data class Reserve(val overrideReady: Boolean = false, val overrideCredit: Boolean = false, val overrideReason: String? = null) : RentalOrderAction
    data class Cancel(val reason: String) : RentalOrderAction
    data class NoShow(val reason: String? = null) : RentalOrderAction
    data class Extend(val endAt: Instant, val reason: String? = null) : RentalOrderAction
    data class OffRent(val at: Instant, val by: String? = null, val reason: String? = null) : RentalOrderAction
    data object Close : RentalOrderAction
}

/** One sketch / damage finding of a handover. */
data class HandoverDamageDraft(
    val zone: String,
    val severity: String,
    val label: String,
    /** New check-in damage (charge or waive); otherwise `pre_existing`. */
    val isNew: Boolean,
    val chargeMinor: Long? = null,
    val reason: String? = null,
)

data class HandoverDraft(
    val orderId: String,
    val lineId: String,
    val assetId: String,
    /** True for check-out, false for check-in. */
    val checkout: Boolean,
    val at: Instant,
    val meterValue: BigDecimal? = null,
    val meterOverrideReason: String? = null,
    val meterPhoto: LocalFileRef? = null,
    val fuelLevelPct: Int? = null,
    val damage: List<HandoverDamageDraft> = emptyList(),
    val notes: String? = null,
    val inspectionActivityId: String? = null,
    val photos: List<LocalFileRef> = emptyList(),
    /** PNG of the customer's signature drawn on the device. */
    val signaturePng: ByteArray? = null,
    val signerName: String? = null,
    val overrideSignature: Boolean = false,
    /** Credit / document override reason (rental.credit_override). */
    val creditOverrideReason: String? = null,
    val overrideReady: Boolean = false,
    /** Check-in counts of a bulk line. */
    val bulk: BulkCountsDraft? = null,
)

data class BulkCountsDraft(
    val returned: BigDecimal,
    val damaged: BigDecimal,
    val lost: BigDecimal,
    val damageChargeMinor: Long? = null,
    val notes: String? = null,
)

data class SwapDraft(
    val orderId: String,
    val lineId: String,
    val newAssetId: String,
    val reason: String?,
    val inMeter: BigDecimal?,
    val inFuelPct: Int?,
    val outMeter: BigDecimal?,
    val outFuelPct: Int?,
    val at: Instant,
    val overrideReady: Boolean = false,
)

data class PaymentDraft(
    val orderId: String,
    /** payment | refund | deposit_received | deposit_refunded */
    val kind: String,
    /** cash | card_external | bank_transfer | cheque | mobile_money | direct_debit | voucher | other */
    val method: String,
    val amountMinor: Long,
    val currency: String,
    val reference: String?,
    val cardBrand: String?,
    val cardLast4: String?,
    val payerName: String?,
    val isAuthorization: Boolean,
    val notes: String?,
    val receivedAt: Instant,
    /** Where the receipt is e-mailed. */
    val payerEmail: String? = null,
) {
    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /** Whether [email] is acceptable as the payer e-mail (blank means none). */
        fun isValidPayerEmail(email: String?): Boolean = email.isNullOrBlank() || EMAIL.matches(email.trim())
    }
}

data class RentalRates(
    val dayMinor: Long,
    val weekMinor: Long? = null,
    val depositMinor: Long? = null,
    val currency: String? = null,
)

/** The fix for a refused hand-over gate, applied when the user retries it. */
sealed interface HandoverOverride {
    data object Signature : HandoverOverride
    data class Credit(val reason: String) : HandoverOverride
    data class Meter(val reason: String, val value: BigDecimal? = null) : HandoverOverride
    /** `meter_required`: the missing reading is supplied, no override. */
    data class Reading(val value: BigDecimal) : HandoverOverride
    data class Ready(val reason: String) : HandoverOverride
}

// ----------------------------------------------------------------------------- repositories

interface RentalRepository {
    fun observeOrders(customerPartyId: String? = null): Flow<List<RentalOrder>>

    fun observeOrder(id: String): Flow<RentalOrder?>

    suspend fun order(id: String): RentalOrder?

    fun observeLines(orderId: String? = null): Flow<List<RentalOrderLine>>

    /** Active allocations (all, or of one order / unit). */
    fun observeAllocations(orderId: String? = null, assetId: String? = null): Flow<List<RentalAllocation>>

    fun observeHandovers(orderId: String? = null, assetId: String? = null): Flow<List<RentalHandover>>

    fun observeReadinessChecks(assetId: String): Flow<List<ReadinessCheck>>

    fun observeBulkReturns(orderId: String): Flow<List<RentalBulkReturn>>

    fun observeSignatures(orderId: String): Flow<List<RentalSignature>>

    fun observeInvoices(orderId: String): Flow<List<RentalInvoice>>

    fun observeCustomers(): Flow<List<RentalCustomer>>

    fun observeCustomer(partyId: String): Flow<RentalCustomer?>

    fun observeClasses(): Flow<List<RentalClass>>

    /** Rentable class units, blocking unit allocations and class holds for [ClassAvailability.freeUnits]. */
    fun observeClassAvailability(): Flow<ClassAvailability.Facts>

    /** Fleet inputs (units, active allocations, due times, readiness) for [LocalFleet.compute]. */
    fun observeFleet(): Flow<LocalFleet.Inputs>

    /** Refused rental commands (their effects are rolled back) of one order, or all. */
    fun observeFailures(orderId: String? = null): Flow<List<RentalFailure>>

    suspend fun saveCustomer(partyId: String?, draft: RentalCustomerDraft): String

    suspend fun addCustomerDocument(partyId: String, draft: CustomerDocumentDraft): String

    /** Creates the order with its line; [RentalBookingDraft.action] follows as a dependent command. */
    suspend fun createBooking(draft: RentalBookingDraft): String

    suspend fun act(orderId: String, action: RentalOrderAction)

    suspend fun assignUnit(lineId: String, assetId: String, overrideReady: Boolean = false)

    /** Signature (when drawn) → hand-over; returns the hand-over id. */
    suspend fun handover(draft: HandoverDraft): String

    suspend fun swap(draft: SwapDraft): String

    suspend fun bulkReturn(orderId: String, lineId: String, counts: BulkCountsDraft, at: Instant, locationId: String?): String

    suspend fun recordPayment(draft: PaymentDraft): String

    suspend fun createInvoiceDraft(orderId: String): String

    /**
     * Opens an in-person agreement signature request for [signerName]; the server renders the
     * agreement and returns its consent text, after which the signer signs with [signRequest].
     * Returns the request id.
     */
    suspend fun requestAgreementSignature(orderId: String, signerName: String, locale: String?): String

    /**
     * Signs an existing signature request on this device as [signerId] (null = the active
     * in-person signer), with a drawn [signaturePng] and/or a [typedName] (one is required).
     */
    suspend fun signRequest(
        orderId: String,
        requestId: String,
        signerId: String?,
        signerName: String,
        signaturePng: ByteArray?,
        typedName: String?,
        consentTextVersion: String,
    ): String

    suspend fun startReadinessCheck(
        assetId: String,
        items: List<ReadinessCheckItem>,
        notes: String?,
        photos: List<LocalFileRef> = emptyList(),
    ): String

    /** [photos] are added to the check's existing photos. */
    suspend fun decideReadiness(
        checkId: String,
        status: String,
        items: List<ReadinessCheckItem>,
        notes: String?,
        photos: List<LocalFileRef> = emptyList(),
    )

    suspend fun enableRental(assetId: String, rates: RentalRates)

    suspend fun disableRental(assetId: String)

    /**
     * Resends a refused hand-over with [fix] (null resends it unchanged, after the cause was
     * fixed by another change such as signing the agreement); the refused entry is dismissed.
     */
    suspend fun retryHandover(commandId: String, fix: HandoverOverride?)

    suspend fun dismissFailure(commandId: String)
}

interface RentalDispatchRepository {
    fun observeTasks(): Flow<List<RentalDeliveryTask>>

    fun observeTask(id: String): Flow<RentalDeliveryTask?>

    fun observeTransfers(): Flow<List<RentalTransfer>>

    /** Units loaded for the stop (`rental_delivery_task_assets`); empty when none were listed. */
    fun observeTaskAssets(taskId: String): Flow<List<RentalTaskAsset>>

    suspend fun assign(taskId: String, driverUserId: String?)

    /**
     * A driver's route check-in at the stop: moves the task like the matching action (start,
     * arrive, complete, fail) and records the check-in with the device fix and its accuracy.
     * Completing carries the proof (photos staged for upload, PNG signature, signer's name);
     * failing needs a [reason].
     */
    suspend fun checkIn(
        taskId: String,
        status: RouteCheckInStatus,
        at: Instant,
        fix: StopFix?,
        reason: String? = null,
        notes: String? = null,
        photos: List<LocalFileRef> = emptyList(),
        signaturePng: ByteArray? = null,
        signedName: String? = null,
    )

    suspend fun cancel(taskId: String, reason: String?)

    suspend fun reopen(taskId: String, reason: String?)

    suspend fun createTransfer(assetId: String, toLocationId: String, arriveBy: Instant, reason: String?, orderId: String?): String

    suspend fun shipTransfer(id: String, at: Instant)

    suspend fun receiveTransfer(id: String, at: Instant)

    suspend fun cancelTransfer(id: String, reason: String?)
}

/** A driver's route check-in at a stop (`route_checkin_status`). */
enum class RouteCheckInStatus { EN_ROUTE, ARRIVED, COMPLETED, FAILED }

/** The device's position at a stop; [accuracyM] is the fix's radius in metres when known. */
data class StopFix(val lat: BigDecimal, val lng: BigDecimal, val accuracyM: BigDecimal?)

/** A unit the dispatcher loaded for a delivery / pick-up stop. */
data class RentalTaskAsset(val assetId: String, val name: String?)
