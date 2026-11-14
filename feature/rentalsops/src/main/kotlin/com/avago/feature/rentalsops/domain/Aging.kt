package com.avago.feature.rentalsops.domain

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * AR aging buckets (stage B `GET /reports/ar-aging`, stage C `rental_aging.rs`).
 * Stage B keys are `current, 1_30, 31_60, 61_90, over_90`; stage C's domain uses
 * `d1_30, d31_60, d61_90` — both spellings are accepted.
 */
enum class AgingBucket(val keys: List<String>) {
    CURRENT(listOf("current")),
    D1_30(listOf("1_30", "d1_30")),
    D31_60(listOf("31_60", "d31_60")),
    D61_90(listOf("61_90", "d61_90")),
    OVER_90(listOf("over_90", "d90_plus", "90_plus"));

    companion object {
        /** Same rule as the server: whole days past due; ≤ 0 is current. */
        fun forDaysPastDue(days: Long): AgingBucket = when {
            days <= 0 -> CURRENT
            days <= 30 -> D1_30
            days <= 60 -> D31_60
            days <= 90 -> D61_90
            else -> OVER_90
        }

        fun of(dueAt: Instant?, issuedAt: Instant?, asOf: Instant): AgingBucket {
            val due = dueAt ?: issuedAt ?: return CURRENT
            return forDaysPastDue(ChronoUnit.DAYS.between(due, asOf))
        }
    }
}

/** Bucket sums in minor units for one currency. */
data class AgingRow(val currency: String, val amounts: Map<AgingBucket, Long>) {
    val total: Long get() = amounts.values.sum()
    operator fun get(b: AgingBucket): Long = amounts[b] ?: 0L

    companion object {
        fun fromJson(currency: String, buckets: JsonObject?): AgingRow =
            AgingRow(
                currency,
                AgingBucket.entries.associateWith { b ->
                    b.keys.firstNotNullOfOrNull { k -> MinorMoney.parse(buckets?.get(k)) } ?: 0L
                },
            )
    }
}

/**
 * Money arrives as integer minor units, sometimes serialised as a string (BIGINT safety).
 * Decimal strings are rejected rather than guessed (the API never sends major units here).
 */
object MinorMoney {
    fun parse(e: JsonElement?): Long? {
        if (e == null || e is JsonNull || e !is JsonPrimitive) return null
        e.longOrNull?.let { return it }
        val s = e.content.trim()
        if (s.isEmpty()) return null
        return s.toLongOrNull() ?: runCatching { BigDecimal(s).longValueExact() }.getOrNull()
    }

    /** Minor → major for display, honouring the currency's fraction digits. */
    fun toMajor(minor: Long, currency: String): Double {
        val digits = runCatching { java.util.Currency.getInstance(currency.trim().uppercase()).defaultFractionDigits }
            .getOrNull()?.takeIf { it >= 0 } ?: 2
        return BigDecimal.valueOf(minor).movePointLeft(digits).toDouble()
    }
}
