package com.avago.feature.rentals.domain

import com.avago.core.data.rental.Money
import kotlinx.serialization.json.JsonObject

/** Instant-book preview for a quick booking (feature A). */
data class InstantBookCheck(val eligible: Boolean, val outcome: String, val reasons: List<String>) {
    companion object {
        fun from(o: JsonObject): InstantBookCheck {
            val d = o.obj("data") ?: o
            return InstantBookCheck(d.bool("eligible") == true, d.str("outcome") ?: "request", d.strings("reasons"))
        }
    }
}

/** Owner-facing fee breakdown of an order (feature K). Amounts share [currency]. */
data class FeeBreakdown(
    val connect: Boolean,
    val orderTotal: Money?,
    val renterFee: Money?,
    val renterFeeBp: Long,
    val renterTotal: Money?,
    val depositHold: Money?,
    val commission: Money?,
    val commissionBp: Long,
    val laborCommission: Money?,
    val absorbedRenterFee: Money?,
    val netPayout: Money?,
) {
    companion object {
        fun from(o: JsonObject): FeeBreakdown? {
            val d = o.obj("data") ?: o
            val cur = d.str("currency")?.uppercase() ?: return null
            val owner = d.obj("owner") ?: JsonObject(emptyMap())
            fun nz(m: Money?) = m?.takeIf { it.minor != 0L }
            return FeeBreakdown(
                connect = d.str("mode") == "connect",
                orderTotal = d.money("order_total_minor", cur),
                renterFee = nz(d.money("renter_fee_minor", cur)),
                renterFeeBp = d.long("renter_fee_bp") ?: 0,
                renterTotal = d.money("renter_total_minor", cur),
                depositHold = nz(d.money("deposit_hold_minor", cur)),
                commission = owner.money("commission_minor", cur),
                commissionBp = owner.long("commission_bp") ?: 0,
                laborCommission = nz(owner.money("labor_commission_minor", cur)),
                absorbedRenterFee = nz(owner.money("absorbed_renter_fee_minor", cur)),
                netPayout = owner.money("net_payout_minor", cur),
            )
        }

        /** Basis points as a percentage label, e.g. 750 → "7.5". */
        fun percent(bp: Long): String = java.math.BigDecimal.valueOf(bp, 2).stripTrailingZeros().toPlainString()
    }
}
