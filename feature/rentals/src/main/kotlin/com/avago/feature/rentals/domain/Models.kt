package com.avago.feature.rentals.domain

import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalClass
import com.avago.core.data.domain.RentalCustomer
import com.avago.core.data.domain.RentalOrder
import com.avago.core.data.domain.RentalOrderLine
import com.avago.core.data.domain.RentalOrderStatus
import com.avago.core.data.rental.Money
import java.math.BigDecimal
import java.time.Instant

/** Order lifecycle (server `rental_orders.status`). */
object OrderStatus {
    const val DRAFT = RentalOrderStatus.DRAFT
    const val QUOTED = RentalOrderStatus.QUOTED
    const val HELD = RentalOrderStatus.HELD
    const val RESERVED = RentalOrderStatus.RESERVED
    const val ON_RENT = RentalOrderStatus.ON_RENT
    const val OFF_RENT_REQUESTED = RentalOrderStatus.OFF_RENT_REQUESTED
    const val RETURNED = RentalOrderStatus.RETURNED
    const val CLOSED = RentalOrderStatus.CLOSED
    const val CANCELLED = RentalOrderStatus.CANCELLED

    fun permission(action: String): String = when (action) {
        "quote" -> "rental.quote"
        "hold", "reserve", "cancel", "no-show", "extend" -> "rental.reserve"
        "checkout", "swap" -> "rental.checkout"
        "checkin", "off-rent" -> "rental.checkin"
        "close" -> "rental.invoice_issue"
        else -> error("Unknown rental action: $action")
    }

    /** Transitions offered per status (server enforces; this only drives the UI). */
    fun actions(status: String): List<String> = when (status) {
        DRAFT -> listOf("quote", "hold", "reserve", "cancel")
        QUOTED -> listOf("hold", "reserve", "cancel")
        HELD -> listOf("reserve", "cancel")
        RESERVED -> listOf("checkout", "extend", "no-show", "cancel")
        ON_RENT -> listOf("extend", "swap", "off-rent", "checkin", "checkout")
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
    /** Paid on the order's invoices (null before the first invoice). */
    val paid: Money?,
    /** Invoiced and not yet paid (null before the first invoice). */
    val balance: Money?,
    val depositRequired: Money?,
    val version: Long,
    val sync: RecordSyncState,
    val source: String? = null,
) {
    val isLegacy: Boolean get() = source == "legacy"
    val actions: List<String> get() = if (isLegacy) emptyList() else OrderStatus.actions(status)
    val isPending: Boolean get() = sync != RecordSyncState.SAVED

    companion object {
        fun of(o: RentalOrder): OrderView {
            val cur = o.currency
            return OrderView(
                id = o.id,
                number = o.number,
                status = o.status,
                currency = cur,
                customerId = o.customerPartyId,
                customerName = o.customerName,
                locationId = o.locationId,
                startAt = o.startAt,
                endAt = o.endAt,
                holdExpiresAt = o.holdExpiresAt,
                total = Money.of(o.totalMinor, cur),
                paid = Money.of(o.invoicePaidMinor, cur),
                balance = o.invoicedMinor?.let { Money.of(it - (o.invoicePaidMinor ?: 0L), cur) },
                depositRequired = Money.of(o.depositRequiredMinor.takeIf { it > 0 }, cur),
                version = o.version,
                sync = o.sync,
                source = o.source,
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
    val qty: BigDecimal,
    val startAt: Instant?,
    val endAt: Instant?,
    val amount: Money?,
    val tax: Money?,
    /** Line status (open | out | returned | swapped | cancelled). */
    val status: String? = null,
    val sync: RecordSyncState = RecordSyncState.SAVED,
    /** Kit grouping (`kind: "kit"` is the header) and the product of bulk lines. */
    val kitInstanceId: String? = null,
    val productId: String? = null,
    val isBulk: Boolean = false,
    /** Line discount (null when none). */
    val discount: Money? = null,
) {
    val isKitHeader: Boolean get() = kind == "kit"

    companion object {
        fun of(l: RentalOrderLine, currency: String): LineView = LineView(
            id = l.id,
            orderId = l.orderId,
            kind = l.kind,
            classId = l.classId,
            assetId = l.assetId,
            description = l.description,
            qty = l.qty,
            startAt = l.startAt,
            endAt = l.endAt,
            amount = Money.of(l.amountMinor, currency),
            tax = Money.of(l.taxMinor, currency),
            status = l.status,
            sync = l.sync,
            kitInstanceId = l.kitInstanceId,
            productId = l.productId,
            isBulk = l.isBulk,
            discount = l.discountMinor.takeIf { it > 0 }?.let { Money.of(it, currency) },
        )
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
    val sync: RecordSyncState,
) {
    companion object {
        fun of(c: RentalCustomer): CustomerView = CustomerView(
            id = c.partyId,
            name = c.name,
            company = c.company,
            email = c.email,
            phone = c.phone,
            doNotRent = c.doNotRent,
            doNotRentReason = c.doNotRentReason,
            sync = c.sync,
        )
    }
}

data class ClassView(val id: String, val name: String, val currency: String?) {
    companion object {
        fun of(c: RentalClass): ClassView = ClassView(c.id, c.name, c.depositCurrency)
    }
}

/** One agenda row (`GET /rentals/schedule` event or the same derived from the local store). */
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