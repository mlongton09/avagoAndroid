package com.avago.feature.rentals.domain

import com.avago.core.data.domain.RentalBulkReturn
import com.avago.core.data.rental.Money
import kotlinx.serialization.json.JsonObject
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
        val requests: List<PayRequest>,
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
            requests = o.objs("payment_requests").mapNotNull { parseRequest(it, currency) },
        )
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
