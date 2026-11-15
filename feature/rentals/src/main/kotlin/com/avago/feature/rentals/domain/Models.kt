package com.avago.feature.rentals.domain

import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.Money
import java.time.Instant

/** Sync feed / local entity names (spec §15). */
object RentalEntity {
    const val SETTINGS = "rental_settings"
    const val LOCATION_SETTING = "rental_location_setting"
    const val CLASS = "rental_class"
    const val RATE_CARD = "rental_rate_card"
    const val FEE = "rental_fee"
    const val TAX_RATE = "rental_tax_rate"
    const val TAX_PROFILE = "rental_tax_profile"
    const val CUSTOMER = "rental_customer"
    const val CUSTOMER_CONTACT = "rental_customer_contact"
    const val CUSTOMER_DOCUMENT = "rental_customer_document"
    const val JOB_SITE = "rental_job_site"
    const val ORDER = "rental_order"
    const val ORDER_LINE = "rental_order_line"
    const val ALLOCATION = "rental_allocation"
    const val BULK_ALLOCATION = "rental_bulk_allocation"
    const val ASSET = "rental_asset"
    const val FLEET = "rental_fleet"

    // Stage B feeds (sync.rs FEEDS / rentals-api-stage-b.md §9; 400 unknown_entity on stage A servers).
    const val HANDOVER = "rental_handover"
    const val LEDGER = "rental_ledger_entry"
    const val SIGNATURE = "rental_signature"
    const val INVOICE = "rental_invoice"
    const val PAYMENT_REQUEST = "rental_payment_request"
    const val DOCUMENT = "rental_document"
    const val TERMS_TEMPLATE = "rental_terms_template"
    const val READINESS_CHECK = "rental_readiness_check"
    // Wave 2 / G3 feeds (docs/rentals-api-wave2-kits-subrent-longterm.md).
    const val BULK_RETURN = "rental_bulk_return"
    const val SUBRENTAL = "rental_subrental"
    const val CONTRACT = "rental_contract"
    const val META = "rental_meta"
    const val ID_MAP = "rental_id_map"

    /** Server feeds pulled by the sync worker, parents before children. */
    val FEEDS = listOf(
        SETTINGS, LOCATION_SETTING, TAX_RATE, TAX_PROFILE, CLASS, RATE_CARD, FEE,
        CUSTOMER, CUSTOMER_CONTACT, CUSTOMER_DOCUMENT, JOB_SITE,
        ORDER, ORDER_LINE, ALLOCATION, BULK_ALLOCATION, ASSET,
        // Stage B — skipped while the server answers 400 unknown_entity.
        TERMS_TEMPLATE, READINESS_CHECK, DOCUMENT, SIGNATURE, HANDOVER, PAYMENT_REQUEST, LEDGER, INVOICE,
        // Wave 2 — skipped (404 / unknown_entity) on servers without G3.
        BULK_RETURN, SUBRENTAL, CONTRACT,
    )

    /** Wave 2 / G3 feeds (absent on older servers; skipped on 404 / unknown_entity). */
    val WAVE2_FEEDS = setOf(BULK_RETURN, SUBRENTAL, CONTRACT)

    /** Feeds not served by stage A servers (all eight stage B feeds). */
    val STAGE_B_FEEDS = setOf(
        TERMS_TEMPLATE, READINESS_CHECK, DOCUMENT, SIGNATURE, HANDOVER, PAYMENT_REQUEST, LEDGER, INVOICE,
    )

    /** Primary-key field of each feed (tombstones carry `id` and this key). */
    fun pkOf(entity: String): String = when (entity) {
        SETTINGS -> "account_id"
        LOCATION_SETTING -> "location_id"
        CUSTOMER -> "rental_customer_id"
        INVOICE -> "rental_invoice_id"
        ASSET, FLEET -> "asset_id"
        else -> "id"
    }
}

/** Order lifecycle (server `rental_orders.status`). */
object OrderStatus {
    const val DRAFT = "draft"
    const val QUOTED = "quoted"
    const val HELD = "held"
    const val RESERVED = "reserved"
    const val ON_RENT = "on_rent"
    const val OFF_RENT_REQUESTED = "off_rent_requested"
    const val RETURNED = "returned"
    const val CLOSED = "closed"
    const val CANCELLED = "cancelled"

    /** Transitions offered per status (server enforces; this only drives the UI). */
    fun actions(status: String): List<String> = when (status) {
        DRAFT -> listOf("quote", "hold", "reserve", "cancel")
        QUOTED -> listOf("hold", "reserve", "cancel")
        HELD -> listOf("reserve", "cancel")
        RESERVED -> listOf("checkout", "no-show", "cancel")
        ON_RENT -> listOf("extend", "swap", "off-rent", "checkin")
        OFF_RENT_REQUESTED -> listOf("checkin")
        RETURNED -> listOf("close")
        else -> emptyList()
    }
}

data class OrderView(
    val id: String,
    val number: String?,
    val status: String,
    val currency: String,
    val customerId: String?,
    val customerName: String?,
    val locationId: String?,
    val startAt: Instant?,
    val endAt: Instant?,
    val holdExpiresAt: Instant?,
    val total: Money?,
    val paid: Money?,
    val balance: Money?,
    val depositRequired: Money?,
    val version: Int?,
    val localState: String,
    val notice: String?,
) {
    val isDraft: Boolean get() = localState == RentalRecordEntity.STATE_DRAFT
    val isPending: Boolean get() = localState != RentalRecordEntity.STATE_SYNCED

    companion object {
        fun of(r: RentalRecordEntity): OrderView {
            val o = r.obj
            val cur = o.str("currency")?.trim() ?: "USD"
            return OrderView(
                id = r.id,
                number = o.str("number"),
                status = o.str("status") ?: r.status ?: OrderStatus.DRAFT,
                currency = cur,
                customerId = o.str("customer_id") ?: r.customerId,
                customerName = o.obj("customer")?.str("name") ?: o.str("customer_name") ?: r.name,
                locationId = o.str("location_id") ?: r.locationId,
                startAt = o.instant("start_at"),
                endAt = o.instant("end_at"),
                holdExpiresAt = o.instant("hold_expires_at"),
                total = o.money("total_minor", cur),
                paid = o.money("paid_minor", cur),
                balance = o.money("balance_minor", cur),
                depositRequired = o.money("deposit_required_minor", cur),
                version = o.long("version")?.toInt(),
                localState = r.localState,
                notice = r.notice,
            )
        }
    }
}

data class LineView(
    val id: String,
    val orderId: String?,
    val kind: String,
    val classId: String?,
    val assetId: String?,
    val description: String?,
    val qty: Double,
    val startAt: Instant?,
    val endAt: Instant?,
    val amount: Money?,
    val tax: Money?,
    /** Line status (open | out | returned | swapped | cancelled); null on old rows. */
    val status: String? = null,
    val localState: String = RentalRecordEntity.STATE_SYNCED,
    /** G3: kit grouping (`kind: "kit"` is the header) and the part of bulk / accessory lines. */
    val kitInstanceId: String? = null,
    val partId: String? = null,
    val rentalKind: String? = null,
    /** G4: rule adjustments applied to the line price. */
    val adjustments: List<PriceAdjustment> = emptyList(),
) {
    val isKitHeader: Boolean get() = kind == "kit"

    companion object {
        fun of(r: RentalRecordEntity, currency: String): LineView {
            val o = r.obj
            return LineView(
                id = r.id,
                orderId = o.str("order_id") ?: r.orderId,
                kind = o.str("kind") ?: "rental",
                classId = o.str("class_id") ?: r.classId,
                assetId = o.str("asset_id") ?: r.assetId,
                description = o.str("description"),
                qty = o.double("qty") ?: 1.0,
                startAt = o.instant("start_at"),
                endAt = o.instant("end_at"),
                amount = o.money("amount_minor", currency),
                tax = o.money("tax_minor", currency),
                status = o.str("status"),
                localState = r.localState,
                kitInstanceId = o.str("kit_instance_id"),
                partId = o.str("part_id"),
                rentalKind = o.str("rental_kind") ?: o.str("part_rental_kind"),
                adjustments = PriceAdjustment.of(o, currency),
            )
        }
    }
}

data class CustomerView(
    val id: String,
    val name: String,
    val company: String?,
    val email: String?,
    val phone: String?,
    val doNotRent: Boolean,
    val doNotRentReason: String?,
    val localState: String,
) {
    companion object {
        fun of(r: RentalRecordEntity): CustomerView {
            val o = r.obj
            return CustomerView(
                id = r.id,
                name = o.str("name") ?: r.name ?: "",
                company = o.str("company"),
                email = o.str("email"),
                phone = o.str("phone"),
                doNotRent = o.bool("do_not_rent") ?: false,
                doNotRentReason = o.str("do_not_rent_reason"),
                localState = r.localState,
            )
        }
    }
}

data class ClassView(val id: String, val name: String, val currency: String?) {
    companion object {
        fun of(r: RentalRecordEntity): ClassView {
            val o = r.obj
            return ClassView(r.id, o.str("name") ?: r.name ?: r.id.take(8), o.str("currency"))
        }
    }
}

/** One agenda row (`GET /rentals/schedule` event or the same derived from local rows). */
data class ScheduleEvent(
    /** pickup | hold | return | maintenance */
    val type: String,
    val at: Instant,
    val overdue: Boolean,
    val allocationId: String?,
    val kind: String?,
    val assetId: String?,
    val assetName: String?,
    val orderId: String?,
    val orderNumber: String?,
    val customerName: String?,
    val startAt: Instant?,
    val endAt: Instant?,
) {
    companion object {
        /** One `GET /rentals/schedule` event (rentals-api.md §10.3); null when malformed. */
        fun of(o: kotlinx.serialization.json.JsonObject): ScheduleEvent? = ScheduleEvent(
            type = o.str("type") ?: return null,
            at = o.instant("at") ?: return null,
            overdue = o.bool("overdue") ?: false,
            allocationId = o.str("allocation_id"),
            kind = o.str("kind"),
            assetId = o.str("asset_id"),
            assetName = o.str("asset_name"),
            orderId = o.str("order_id"),
            orderNumber = o.str("order_number"),
            customerName = o.str("customer_name"),
            startAt = o.instant("start_at"),
            endAt = o.instant("end_at"),
        )

        /** All events of a schedule response, in server order. */
        fun fromResponse(res: kotlinx.serialization.json.JsonObject): List<ScheduleEvent> = res.objs("events").mapNotNull(::of)
    }
}