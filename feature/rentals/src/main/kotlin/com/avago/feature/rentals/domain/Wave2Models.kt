package com.avago.feature.rentals.domain

import com.avago.core.data.domain.RentalBulkReturn
import com.avago.core.data.rental.Money
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.time.Instant

// Pure models of rentals wave 2: G3 kits / bulk counts, and the server-computed read answers
// (G4 KPI, G6 online payment status, G2 credit dry run). Parsers are tolerant of absent fields.

/** G3 kits: lines grouped by `kit_instance_id`; the `kind: "kit"` line is the header. */
object Kits {

    data class Group<T>(val header: T?, val components: List<T>)

    /**
     * Groups [lines] for display: standalone lines stay single-item groups in their order; kit
     * components follow their header (or are grouped headless when the header is missing).
     */
    fun <T> group(lines: List<T>, kitInstanceOf: (T) -> String?, isHeader: (T) -> Boolean): List<Group<T>> {
        val out = ArrayList<Group<T>>()
        val seen = HashSet<String>()
        for (l in lines) {
            val kit = kitInstanceOf(l)
            if (kit == null) {
                out += Group(null, listOf(l))
                continue
            }
            if (!seen.add(kit)) continue
            val members = lines.filter { kitInstanceOf(it) == kit }
            out += Group(members.firstOrNull(isHeader), members.filterNot(isHeader))
        }
        return out
    }

    /** Hand-overs are per component line: a kit header is never handed over itself. */
    fun isHandoverLine(kind: String?): Boolean = kind != "kit"
}

/** G3 bulk (non-serialized) lines: returned / damaged / lost counts at check-in. */
object Bulk {

    data class Counts(val returned: BigDecimal, val damaged: BigDecimal, val lost: BigDecimal) {
        val total: BigDecimal get() = returned + damaged + lost
    }

    /** Line qty − Σ(returned + damaged + lost) over the line's recorded bulk returns. */
    fun outstanding(qty: BigDecimal, returns: List<RentalBulkReturn>): BigDecimal {
        val done = returns.fold(BigDecimal.ZERO) { acc, r -> acc + r.returned + r.damaged + r.lost }
        return (qty - done).max(BigDecimal.ZERO)
    }

    enum class Check { OK, EMPTY, NEGATIVE, EXCEEDS }

    /** Mirrors the server's 400 codes (`qty_exceeds_outstanding`) so a bad count is never sent. */
    fun check(c: Counts, outstanding: BigDecimal): Check = when {
        c.returned.signum() < 0 || c.damaged.signum() < 0 || c.lost.signum() < 0 -> Check.NEGATIVE
        c.total.signum() == 0 -> Check.EMPTY
        c.total > outstanding -> Check.EXCEEDS
        else -> Check.OK
    }

    fun parseQty(s: String): BigDecimal? = s.replace(',', '.').trim().ifEmpty { "0" }.toBigDecimalOrNull()
}

/** G4 KPI card (`GET /analytics/kpi-summary?days=30`); any value may be null while backfilling. */
data class KpiSummary(
    val days: Int,
    val currency: String?,
    val utilizationBp: Long?,
    val prevUtilizationBp: Long?,
    val revenueMinor: Long?,
    val prevRevenueMinor: Long?,
    val checkouts: Long?,
    val prevCheckouts: Long?,
    val winRateBp: Long?,
    val unitsOnRent: Long?,
    val units: Long?,
    val openQuotes: Long?,
    val quotesExpiring: Long?,
    val arOverdueMinor: Long?,
    val refreshedThrough: String?,
) {
    val isEmpty: Boolean get() = listOf(utilizationBp, revenueMinor, checkouts, unitsOnRent, openQuotes, arOverdueMinor).all { it == null }

    companion object {
        fun parse(o: JsonObject?): KpiSummary? {
            o ?: return null
            val cur = o.obj("current")
            val prev = o.obj("previous")
            return KpiSummary(
                days = o.long("days")?.toInt() ?: 30,
                currency = o.str("currency")?.trim(),
                utilizationBp = cur?.long("time_utilization_bp"),
                prevUtilizationBp = prev?.long("time_utilization_bp"),
                revenueMinor = cur?.long("revenue_minor"),
                prevRevenueMinor = prev?.long("revenue_minor"),
                checkouts = cur?.long("checkouts"),
                prevCheckouts = prev?.long("checkouts"),
                winRateBp = cur?.long("quote_win_rate_bp"),
                unitsOnRent = o.obj("fleet")?.long("on_rent"),
                units = o.obj("fleet")?.long("units"),
                openQuotes = o.long("open_quotes"),
                quotesExpiring = o.long("quotes_expiring_3d"),
                arOverdueMinor = o.obj("ar")?.long("overdue_minor"),
                refreshedThrough = o.obj("stats")?.str("refreshed_through"),
            )
        }

        /** Signed change in percent (null without both values or a zero base). */
        fun trendPercent(now: Long?, before: Long?): Int? {
            if (now == null || before == null || before == 0L) return null
            return Math.round((now - before) * 100.0 / before).toInt()
        }

        fun percent(bp: Long?): String? = bp?.let { BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString() + " %" }
    }
}

/** G6: online payments (`GET /payments/status`, `GET /orders/{id}/payments`). */
object OnlinePayments {
    enum class Unavailable { NOT_CONFIGURED, DISABLED }

    /** Unknown/offline is not evidence that the account is unconfigured. */
    fun unavailable(status: JsonObject?): Unavailable? {
        if (status?.bool("online_payments_enabled") != false) return null
        return if (status.str("reason") == "not_configured") Unavailable.NOT_CONFIGURED else Unavailable.DISABLED
    }

    data class PayRequest(
        val id: String,
        val status: String,
        val providerStatus: String?,
        val kind: String?,
        val amount: Money?,
        val payUrl: String?,
        val linkAvailable: Boolean,
        val expiresAt: Instant?,
    ) {
        val isOpen: Boolean get() = status == "open"
        val needsAction: Boolean get() = providerStatus == "requires_action"
        val shareable: Boolean get() = isOpen && linkAvailable && !payUrl.isNullOrBlank()
    }

    data class OrderPayments(
        val enabled: Boolean,
        /** `can.request` (rental.payment_record): a pay link may be requested. */
        val canRequest: Boolean,
        val requests: List<PayRequest>,
        /** Saved card, holds and refundable gateway payments ([GatewayPayments.Gateway.NONE] while off/unknown). */
        val gateway: GatewayPayments.Gateway = GatewayPayments.Gateway.NONE,
    )

    /** `online_payments_enabled` of `/payments/status` (false when unknown — never require Stripe). */
    fun enabled(status: JsonObject?): Boolean = status?.bool("online_payments_enabled") == true

    fun parseRequest(r: JsonObject, currency: String?): PayRequest? {
        val id = r.str("id") ?: return null
        val cur = r.str("currency")?.trim() ?: currency
        val url = r.str("pay_url")
        return PayRequest(
            id = id,
            status = r.str("status") ?: "open",
            providerStatus = r.str("provider_status"),
            kind = r.str("kind"),
            amount = r.money("amount_minor", cur),
            payUrl = url,
            linkAvailable = r.bool("link_available") ?: !url.isNullOrBlank(),
            expiresAt = r.instant("expires_at"),
        )
    }

    fun parseOrder(o: JsonObject?, currency: String?): OrderPayments? {
        o ?: return null
        val enabled = o.bool("online_payments_enabled") == true
        return OrderPayments(
            enabled = enabled,
            canRequest = enabled && o.obj("can")?.bool("request") != false,
            requests = o.objs("payment_requests").mapNotNull { parseRequest(it, currency) },
            gateway = GatewayPayments.parse(o, currency, enabled),
        )
    }

    /**
     * What the pay-links card shows: nothing while online payments are off (or unknown);
     * otherwise the order's requests (an empty, requestable list until they load).
     */
    fun visible(enabled: Boolean, orderPayments: JsonObject?, currency: String?): OrderPayments? {
        if (!enabled) return null
        val parsed = parseOrder(orderPayments, currency)
        return when {
            parsed == null -> OrderPayments(enabled = true, canRequest = true, requests = emptyList())
            // The order answer carries its own flag; a later "off" wins over the cached status.
            orderPayments?.containsKey("online_payments_enabled") == true && !parsed.enabled -> null
            else -> parsed.copy(enabled = true, canRequest = orderPayments?.obj("can")?.let { parsed.canRequest } ?: true)
        }
    }

    /** `POST /payment-requests` for the order balance (the amount defaults server-side). */
    fun requestBody(orderId: String, kind: String = "balance"): JsonObject = buildJsonObject {
        put("order_id", orderId)
        put("kind", kind)
    }

    /** The pay URL of a create / link answer (`pay_url`, or nested under `payment_request`). */
    fun payUrlOf(o: JsonObject): String? =
        (o.str("pay_url") ?: o.obj("payment_request")?.str("pay_url"))?.takeIf { it.isNotBlank() }
}

/** Buy-out quote of a rent-to-own contract (`GET /contracts/{id}/buyout-quote`). */
data class BuyoutQuote(val quote: Money?, val equity: Money?, val paidRent: Money?, val floor: Money?, val asOf: Instant?) {
    companion object {
        fun parse(o: JsonObject?): BuyoutQuote? {
            o ?: return null
            val cur = o.str("currency")?.trim()
            return BuyoutQuote(
                quote = o.money("quote_minor", cur),
                equity = o.money("equity_minor", cur),
                paidRent = o.money("paid_rent_minor", cur),
                floor = o.money("buyout_floor_minor", cur),
                asOf = o.instant("as_of"),
            )
        }
    }
}

/** Server outcome of a post-charge REST call (`charge_now` create or charge). */
data class PostChargeOutcome(val status: String?, val payUrl: String?, val chargeError: String?) {
    val failed: Boolean get() = status == "charge_failed" || status == "failed"

    companion object {
        fun parse(root: JsonObject): PostChargeOutcome {
            val o = root.obj("data") ?: root
            return PostChargeOutcome(o.str("status"), o.str("pay_url")?.takeIf { it.isNotBlank() }, o.str("charge_error"))
        }
    }
}

/** G2 credit / documents dry run (`GET /orders/{id}/credit-check?stage=checkout`). */
data class CreditCheck(
    val allowed: Boolean,
    val canOverride: Boolean,
    val accountStatus: String?,
    val accountStatusReason: String?,
    val blocks: List<Block>,
    val documents: List<Doc>,
    val availableMinor: Long?,
    val afterOrderMinor: Long?,
    val creditLimitMinor: Long?,
) {
    data class Block(val code: String, val message: String?, val kind: String)
    data class Doc(val kind: String, val status: String, val expiresOn: String?)

    val hasDocumentBlock: Boolean get() = blocks.any { it.kind == "documents" }
    val missingDocuments: List<Doc> get() = documents.filter { it.status == "missing" || it.status == "expired" }

    companion object {
        fun parse(o: JsonObject?): CreditCheck? {
            o ?: return null
            return CreditCheck(
                allowed = o.bool("allowed") != false,
                canOverride = o.bool("can_override") == true,
                accountStatus = o.str("account_status"),
                accountStatusReason = o.str("account_status_reason"),
                blocks = o.objs("blocks").mapNotNull { b ->
                    Block(b.str("code") ?: return@mapNotNull null, b.str("message"), b.str("kind") ?: "credit")
                },
                documents = o.objs("documents").mapNotNull { d ->
                    Doc(d.str("kind") ?: return@mapNotNull null, d.str("status") ?: "missing", d.str("expires_on"))
                },
                availableMinor = o.obj("exposure")?.long("available_minor"),
                afterOrderMinor = o.obj("exposure")?.long("after_order_minor"),
                creditLimitMinor = o.long("credit_limit_minor"),
            )
        }

        /** Server warning returned when a queued (offline) checkout skipped the gate. */
        const val WARNING_BYPASSED_OFFLINE = "credit_gate_bypassed_offline"
        const val WARNING_OVERRIDDEN = "credit_gate_overridden"
    }
}
