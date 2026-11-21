package com.avago.feature.rentals.ops.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/**
 * Read-only finance views (stage B): `GET /rentals/reports/ar-aging` and
 * `GET /rentals/customers/{id}/statement`. Money stays in minor units until display.
 */
data class AgingInvoice(
    val invoiceId: String,
    val number: String?,
    val dueAt: Instant?,
    val daysPastDue: Long?,
    val bucket: AgingBucket,
    val balanceMinor: Long,
)

data class AgingCustomer(
    val customerId: String,
    val name: String,
    val currency: String,
    val row: AgingRow,
    val invoices: List<AgingInvoice>,
)

data class AgingReport(
    val asOf: String?,
    val totals: List<AgingRow>,
    val customers: List<AgingCustomer>,
) {
    /** Share of the receivable that is over 60 days past due (61–90 + 90+), per currency. */
    fun over60Share(currency: String): Double? {
        val r = totals.firstOrNull { it.currency == currency } ?: return null
        if (r.total <= 0L) return null
        return (r[AgingBucket.D61_90] + r[AgingBucket.OVER_90]).toDouble() / r.total
    }

    companion object {
        fun parse(o: JsonObject): AgingReport {
            val customers = (o["customers"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { c ->
                val id = c.str("customer_id") ?: return@mapNotNull null
                val cur = c.str("currency") ?: return@mapNotNull null
                AgingCustomer(
                    customerId = id,
                    name = c.str("name") ?: id.take(8),
                    currency = cur,
                    row = AgingRow.fromJson(cur, c.obj("buckets")),
                    invoices = (c["invoices"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { i ->
                        val iid = i.str("invoice_id") ?: i.str("id") ?: return@mapNotNull null
                        val days = MinorMoney.parse(i["days_past_due"])
                        AgingInvoice(
                            iid,
                            i.str("number"),
                            i.instant("due_at"),
                            days,
                            AgingBucket.entries.firstOrNull { b -> i.str("bucket") in b.keys }
                                ?: AgingBucket.forDaysPastDue(days ?: 0),
                            MinorMoney.parse(i["balance_minor"]) ?: 0L,
                        )
                    },
                )
            }
            val explicit = (o["totals"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { t ->
                t.str("currency")?.let { AgingRow.fromJson(it, t.obj("buckets")) }
            }
            // Fall back to summing customers when the server omits totals.
            val totals = explicit.ifEmpty {
                customers.groupBy { it.currency }.map { (cur, list) ->
                    AgingRow(cur, AgingBucket.entries.associateWith { b -> list.sumOf { it.row[b] } })
                }
            }
            return AgingReport(o.str("as_of"), totals.sortedBy { it.currency }, customers.sortedByDescending { it.row.total })
        }
    }
}

data class StatementInvoice(
    val id: String,
    val number: String?,
    val docType: String?,
    val status: String?,
    val issuedAt: Instant?,
    val dueAt: Instant?,
    val currency: String,
    val totalMinor: Long,
    val balanceMinor: Long,
)

data class Statement(
    val invoices: List<StatementInvoice>,
    val balanceByCurrency: Map<String, Long>,
    val creditMinor: Long?,
) {
    companion object {
        fun parse(o: JsonObject): Statement {
            val inv = (o["invoices"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { i ->
                val id = i.str("id") ?: return@mapNotNull null
                StatementInvoice(
                    id, i.str("number"), i.str("doc_type"), i.str("status"),
                    i.instant("issued_at"), i.instant("due_at"),
                    i.str("currency") ?: "USD",
                    MinorMoney.parse(i["total_minor"]) ?: 0L,
                    MinorMoney.parse(i["balance_minor"]) ?: 0L,
                )
            }.sortedByDescending { it.issuedAt ?: Instant.EPOCH }
            val bal = o.obj("balance_by_currency")?.mapNotNull { (k, v) -> MinorMoney.parse(v)?.let { k to it } }?.toMap().orEmpty()
            return Statement(inv, bal, MinorMoney.parse(o["customer_credit_minor"]))
        }
    }
}
