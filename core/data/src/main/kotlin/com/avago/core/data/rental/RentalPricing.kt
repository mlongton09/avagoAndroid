package com.avago.core.data.rental

import com.avago.core.data.domain.RateCardRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** Rate card values in minor units (server `rental_pricing::RateCard`); any rate may be missing. */
data class RateValues(
    val minCharge: Long? = null,
    val hourly: Long? = null,
    val fourHour: Long? = null,
    val eightHour: Long? = null,
    val day: Long? = null,
    val weekend: Long? = null,
    val week: Long? = null,
    val fourWeek: Long? = null,
    val month: Long? = null,
) {
    companion object {
        /** Folds `rental_rate_tiers` (period code, amount) like the server's `CARD_FROM_TIERS`: the highest amount per code. */
        fun fromTiers(tiers: List<Pair<String, Long>>): RateValues {
            val max = tiers.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.max() }
            return RateValues(
                minCharge = max["min_charge"],
                hourly = max["hour"],
                fourHour = max["four_hour"],
                eightHour = max["eight_hour"],
                day = max["day"],
                weekend = max["weekend"],
                week = max["week"],
                fourWeek = max["four_week"],
                month = max["month"],
            )
        }
    }
}

/** Pricing policy of the account rental settings in the location's [zone] (server `rental_pricing::Policy`). */
data class RatePolicy(
    /** `day_mode` "calendar": days are counted by local date; otherwise 24-hour days with [graceMinutes]. */
    val calendarDays: Boolean = false,
    val graceMinutes: Long = 59,
    /** `month_mode` "calendar": calendar months; otherwise a month rate is the 28-day tier. */
    val calendarMonths: Boolean = false,
    val weekend: WeekendWindow? = null,
    val zone: ZoneId = ZoneId.of("UTC"),
) {
    /** A point in the weekly cycle: [dow] 0 = Sunday … 6 = Saturday. */
    data class WeekPoint(val dow: Int, val time: LocalTime)

    data class WeekendWindow(val start: WeekPoint, val end: WeekPoint) {
        companion object {
            private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

            /** `{start: {dow, time "HH:MM"}, end: {…}}`; null when malformed (the server refuses to price with it). */
            fun parse(o: JsonObject): WeekendWindow? {
                fun point(v: Any?): WeekPoint? {
                    val p = v as? JsonObject ?: return null
                    val dow = (p["dow"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.takeIf { it in 0..6 } ?: return null
                    val time = (p["time"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                    return try {
                        WeekPoint(dow, LocalTime.parse(time, HHMM))
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
                return WeekendWindow(point(o["start"]) ?: return null, point(o["end"]) ?: return null)
            }
        }
    }
}

/**
 * Port of the server pricing engine (`domain/rental_pricing.rs` `price_line`) and rate card
 * resolution (`rentals_api/pricing.rs` `resolve_card`), used for the offline price estimate.
 * Integer arithmetic in minor units; overflow yields no price.
 */
object RentalPricing {

    data class Item(
        val tier: String,
        val qty: Long,
        val unitMinor: Long,
        val amountMinor: Long,
        /** The cost the cap replaced, when a higher tier was cheaper. */
        val cappedFromMinor: Long? = null,
    )

    data class Breakdown(
        val minutes: Long,
        val billedDays: Long,
        val candidate: String,
        val items: List<Item>,
        val subtotalMinor: Long,
        val minChargeApplied: Boolean,
        val totalMinor: Long,
    )

    private class Candidate(val name: String, val items: List<Item>, val total: Long)

    private fun item(tier: String, qty: Long, unit: Long) = Item(tier, qty, unit, Math.multiplyExact(qty, unit))

    private fun sum(items: List<Item>): Long = items.fold(0L) { acc, i -> Math.addExact(acc, i.amountMinor) }

    private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b

    /** Duration in whole minutes, rounded up. */
    fun durationMinutes(start: Instant, end: Instant): Long = ceilDiv(Duration.between(start, end).seconds.coerceAtLeast(0), 60)

    /** Billed days for `[start, end)` under the day mode (minimum 1). */
    fun billedDays(start: Instant, end: Instant, policy: RatePolicy): Long {
        val days = if (policy.calendarDays) {
            ChronoUnit.DAYS.between(start.atZone(policy.zone).toLocalDate(), end.atZone(policy.zone).toLocalDate())
        } else {
            days24h(durationMinutes(start, end), policy.graceMinutes)
        }
        return days.coerceAtLeast(1)
    }

    private fun days24h(minutes: Long, grace: Long): Long = minutes / 1440 + if (minutes % 1440 > grace) 1 else 0

    /** Greedy 4-week / week / day split where no part costs more than the next tier; null without a usable rate. */
    private fun dayTiers(n: Long, card: RateValues, four: Long?): List<Item>? {
        val day = card.day
        val week = card.week
        if (n <= 0) return emptyList()
        if (day == null && week == null && four == null) return null
        val f = if (four != null) n / 28 else 0
        val rem = n - 28 * f
        val w = if (week != null) rem / 7 else 0
        val d = rem - 7 * w

        var tail = mutableListOf<Item>()
        var tailCost: Long? = 0
        if (w > 0 && week != null) tail += item("week", w, week)
        if (d > 0) {
            when {
                day != null && week != null -> {
                    val c = Math.multiplyExact(d, day)
                    tail += if (c > week) item("week", 1, week).copy(cappedFromMinor = c) else item("day", d, day)
                }
                day != null -> tail += item("day", d, day)
                week != null -> tail += item("week", 1, week)
                else -> tailCost = null
            }
        }
        if (tailCost != null) tailCost = sum(tail)

        if (four != null) {
            val replace = tailCost?.let { it > four } ?: true
            if (replace && rem > 0) {
                tail = mutableListOf(item("4week", 1, four).copy(cappedFromMinor = tailCost))
                tailCost = four
            }
        }
        if (tailCost == null) return null
        val items = mutableListOf<Item>()
        if (f > 0 && four != null) items += item("4week", f, four)
        items += tail
        return merge(items)
    }

    /** Combines adjacent uncapped items of the same tier and unit. */
    private fun merge(items: List<Item>): List<Item> {
        val out = mutableListOf<Item>()
        for (it in items) {
            val last = out.lastOrNull()
            if (last != null && last.tier == it.tier && last.unitMinor == it.unitMinor && last.cappedFromMinor == null && it.cappedFromMinor == null) {
                out[out.lastIndex] = last.copy(qty = last.qty + it.qty, amountMinor = Math.addExact(last.amountMinor, it.amountMinor))
            } else {
                out += it
            }
        }
        return out
    }

    /** Sunday = 0 … Saturday = 6. */
    private fun DayOfWeek.fromSunday(): Int = value % 7

    /** Does local `[ls, le]` fit inside one weekend window? */
    internal fun fitsWeekend(ls: LocalDateTime, le: LocalDateTime, w: RatePolicy.WeekendWindow): Boolean {
        val back = Math.floorMod(ls.dayOfWeek.fromSunday() - w.start.dow, 7).toLong()
        var ws = ls.toLocalDate().minusDays(back).atTime(w.start.time)
        if (ws.isAfter(ls)) ws = ws.minusDays(7)
        var ahead = Math.floorMod(w.end.dow - w.start.dow, 7).toLong()
        if (ahead == 0L && !w.end.time.isAfter(w.start.time)) ahead = 7
        val we = ws.toLocalDate().plusDays(ahead).atTime(w.end.time)
        return !ls.isBefore(ws) && !le.isAfter(we)
    }

    /** Price of one unit over `[start, end)`; null for an empty range, no applicable rate or overflow. */
    fun priceLine(card: RateValues, start: Instant, end: Instant, policy: RatePolicy): Breakdown? {
        if (!end.isAfter(start)) return null
        return try {
            price(card, start, end, policy)
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun price(card: RateValues, start: Instant, end: Instant, policy: RatePolicy): Breakdown? {
        val minutes = durationMinutes(start, end)
        val days = billedDays(start, end, policy)
        val cands = mutableListOf<Candidate>()

        if (minutes <= 240) card.fourHour?.let { cands += Candidate("4h", listOf(item("4h", 1, it)), it) }
        if (minutes <= 480) card.eightHour?.let { cands += Candidate("8h", listOf(item("8h", 1, it)), it) }
        card.hourly?.let { r ->
            val it = item("hour", ceilDiv(minutes, 60).coerceAtLeast(1), r)
            cands += Candidate("hourly", listOf(it), it.amountMinor)
        }

        // In 4-week month mode a month rate without an explicit 4-week rate is the 28-day tier.
        val four = card.fourWeek ?: if (policy.calendarMonths) null else card.month
        dayTiers(days, card, four)?.let { cands += Candidate("days", it, sum(it)) }

        if (policy.calendarMonths && card.month != null) {
            val mr = card.month
            val ls = start.atZone(policy.zone).toLocalDateTime()
            val le = end.atZone(policy.zone).toLocalDateTime()
            var m = 0L
            while (!ls.plusMonths(m + 1).isAfter(le)) m++
            if (m > 0) {
                val anchor = ls.plusMonths(m)
                val remDays = if (policy.calendarDays) {
                    ChronoUnit.DAYS.between(anchor.toLocalDate(), le.toLocalDate())
                } else {
                    days24h(ceilDiv(Duration.between(anchor, le).seconds.coerceAtLeast(0), 60), policy.graceMinutes)
                }
                val items = mutableListOf(item("month", m, mr))
                val tail = dayTiers(remDays, card, card.fourWeek)
                when {
                    tail != null -> {
                        val tc = sum(tail)
                        if (tc > mr) items += item("month", 1, mr).copy(cappedFromMinor = tc) else items += tail
                    }
                    remDays > 0 -> items += item("month", 1, mr)
                }
                cands += Candidate("months", items, sum(items))
            }
            cands += Candidate("months_up", listOf(item("month", m + 1, mr)), Math.multiplyExact(m + 1, mr))
        }

        val weekendRate = card.weekend
        val window = policy.weekend
        if (weekendRate != null && window != null &&
            fitsWeekend(start.atZone(policy.zone).toLocalDateTime(), end.atZone(policy.zone).toLocalDateTime(), window)
        ) {
            cands += Candidate("weekend", listOf(item("weekend", 1, weekendRate)), weekendRate)
        }

        val best = cands.minWithOrNull(compareBy<Candidate>({ it.total }, { it.items.size })) ?: return null
        val min = card.minCharge
        val minApplied = min != null && min > best.total
        return Breakdown(
            minutes = minutes,
            billedDays = days,
            candidate = best.name,
            items = best.items,
            subtotalMinor = best.total,
            minChargeApplied = minApplied,
            totalMinor = if (minApplied) min!! else best.total,
        )
    }

    /**
     * The most specific active card in [currency] for the asset or class: asset cards first, then
     * customer and location matches, priority, the latest validity start and the oldest card.
     */
    fun resolveCard(
        cards: List<RateCardRecord>,
        currency: String,
        assetId: String?,
        classId: String?,
        customerPartyId: String?,
        locationId: String?,
        on: Instant,
        zone: ZoneId,
    ): RateCardRecord? {
        val day = on.atZone(zone).toLocalDate()
        return cards
            .filter { c ->
                c.active && c.currency == currency &&
                    ((assetId != null && c.assetId == assetId) || (classId != null && c.classId == classId)) &&
                    (c.customerPartyId == null || c.customerPartyId == customerPartyId) &&
                    (c.locationId == null || c.locationId == locationId) &&
                    (c.validFrom == null || !day.isBefore(c.validFrom)) &&
                    (c.validUntil == null || day.isBefore(c.validUntil))
            }
            .sortedWith(
                compareByDescending<RateCardRecord> { it.assetId != null }
                    .thenByDescending { it.customerPartyId != null }
                    .thenByDescending { it.locationId != null }
                    .thenByDescending { it.priority }
                    // Postgres `DESC NULLS LAST` on lower(valid_during).
                    .thenBy(nullsLast(reverseOrder())) { it.validFrom }
                    .thenBy { it.createdAtMillis },
            )
            .firstOrNull()
    }
}
