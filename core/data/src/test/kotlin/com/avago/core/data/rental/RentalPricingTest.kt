package com.avago.core.data.rental

import com.avago.core.data.domain.CollectionSettings
import com.avago.core.data.domain.RateCardRecord
import com.avago.core.data.domain.RentalSettings
import com.avago.core.data.domain.ScopePolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Vectors of the server `rental_pricing.rs` tests, so the offline estimate prices like the server. */
class RentalPricingTest {

    private val p = RatePolicy()
    private fun t(s: String) = Instant.parse(s)
    private fun days(start: String, n: Long) = t(start).let { it to it.plus(Duration.ofDays(n)) }
    private fun dw4(d: Long, w: Long, f: Long) = RateValues(day = d, week = w, fourWeek = f)
    private fun total(card: RateValues, s: Instant, e: Instant, policy: RatePolicy = p) = RentalPricing.priceLine(card, s, e, policy)!!.totalMinor

    @Test fun `best rate table`() {
        val cases = listOf(
            arrayOf(1L, 10_000L, 30_000L, 80_000L, 10_000L),
            arrayOf(4L, 10_000L, 30_000L, 80_000L, 30_000L),
            arrayOf(8L, 10_000L, 30_000L, 80_000L, 40_000L),
            arrayOf(10L, 10_000L, 30_000L, 80_000L, 60_000L),
            arrayOf(21L, 10_000L, 30_000L, 80_000L, 80_000L),
            arrayOf(37L, 12_000L, 40_000L, 125_000L, 189_000L),
            arrayOf(36L, 10_000L, 30_000L, 80_000L, 120_000L),
            arrayOf(60L, 10_000L, 30_000L, 80_000L, 190_000L),
        )
        for ((n, d, w, f, want) in cases) {
            val (s, e) = days("2026-01-05T09:00:00Z", n)
            assertEquals(want, total(dw4(d, w, f), s, e), "$n days")
        }
    }

    @Test fun `day remainder is capped at the week rate`() {
        val (s, e) = days("2026-01-05T09:00:00Z", 37)
        val b = RentalPricing.priceLine(RateValues(day = 12_000, week = 20_000, fourWeek = 125_000), s, e, p)!!
        assertEquals(165_000L, b.totalMinor)
        assertTrue(b.items.any { it.cappedFromMinor == 24_000L })
    }

    @Test fun `grace minutes in 24h mode`() {
        val card = RateValues(day = 5_000)
        val s = t("2026-05-01T08:00:00Z")
        listOf("2026-05-02T08:59:00Z" to 1L, "2026-05-02T09:00:00Z" to 2L, "2026-05-01T08:30:00Z" to 1L).forEach { (end, d) ->
            val b = RentalPricing.priceLine(card, s, t(end), p)!!
            assertEquals(d, b.billedDays, end)
            assertEquals(5_000 * d, b.totalMinor)
        }
    }

    @Test fun `calendar days use the location zone`() {
        val ny = ZoneId.of("America/New_York")
        val s = LocalDateTime.of(2026, 6, 1, 9, 0).atZone(ny).toInstant()
        val e = LocalDateTime.of(2026, 6, 2, 17, 0).atZone(ny).toInstant()
        assertEquals(1L, RentalPricing.billedDays(s, e, RatePolicy(calendarDays = true, zone = ny)))
        assertEquals(2L, RentalPricing.billedDays(s, e, p))
    }

    @Test fun `short rates and minimum charge`() {
        val card = RateValues(fourHour = 4_000, eightHour = 6_000, hourly = 1_500, day = 8_000)
        val s = t("2026-05-01T08:00:00Z")
        listOf(
            Triple("2026-05-01T09:00:00Z", 1_500L, "hourly"),
            Triple("2026-05-01T11:00:00Z", 4_000L, "4h"),
            Triple("2026-05-01T14:00:00Z", 6_000L, "8h"),
            Triple("2026-05-01T18:00:00Z", 8_000L, "days"),
        ).forEach { (end, want, cand) ->
            val b = RentalPricing.priceLine(card, s, t(end), p)!!
            assertEquals(want to cand, b.totalMinor to b.candidate, end)
        }
        val m = RentalPricing.priceLine(RateValues(hourly = 1_000, minCharge = 2_500), s, t("2026-05-01T09:00:00Z"), p)!!
        assertEquals(1_000L, m.subtotalMinor)
        assertEquals(2_500L, m.totalMinor)
        assertTrue(m.minChargeApplied)
    }

    @Test fun `weekend window`() {
        val policy = p.copy(
            weekend = RatePolicy.WeekendWindow(RatePolicy.WeekPoint(5, LocalTime.NOON), RatePolicy.WeekPoint(1, LocalTime.of(9, 0))),
        )
        val card = RateValues(day = 10_000, weekend = 15_000)
        val b = RentalPricing.priceLine(card, t("2026-05-01T13:00:00Z"), t("2026-05-04T08:30:00Z"), policy)!!
        assertEquals(15_000L to "weekend", b.totalMinor to b.candidate)
        assertEquals(30_000L, total(card, t("2026-05-01T13:00:00Z"), t("2026-05-04T10:00:00Z"), policy))
        assertEquals(20_000L, total(card, t("2026-05-01T11:00:00Z"), t("2026-05-03T11:00:00Z"), policy))
        assertEquals(10_000L, total(card, t("2026-05-02T09:00:00Z"), t("2026-05-03T09:00:00Z"), policy))
    }

    @Test fun `calendar months`() {
        val policy = p.copy(calendarMonths = true)
        val card = RateValues(day = 10_000, week = 40_000, month = 120_000)
        assertEquals(120_000L, total(card, t("2026-01-15T08:00:00Z"), t("2026-02-15T08:00:00Z"), policy))
        assertEquals(140_000L, total(card, t("2026-01-15T08:00:00Z"), t("2026-02-17T08:00:00Z"), policy))
        assertEquals(240_000L, total(card, t("2026-01-15T08:00:00Z"), t("2026-03-07T08:00:00Z"), policy))
    }

    @Test fun `month rate is the 4-week tier in 4-week mode and missing day rounds up to a week`() {
        val (s, e) = days("2026-01-05T09:00:00Z", 30)
        assertEquals(170_000L, total(RateValues(day = 10_000, month = 150_000), s, e))
        val (s9, e9) = days("2026-01-05T09:00:00Z", 9)
        assertEquals(60_000L, total(RateValues(week = 30_000), s9, e9))
    }

    @Test fun `no price for an empty range or a card without rates`() {
        val s = t("2026-01-05T09:00:00Z")
        assertNull(RentalPricing.priceLine(RateValues(day = 1), s, s, p))
        assertNull(RentalPricing.priceLine(RateValues(), s, s.plus(Duration.ofDays(1)), p))
    }

    @Test fun `tiers fold to the highest amount per period`() {
        val v = RateValues.fromTiers(listOf("day" to 100L, "day" to 120L, "four_week" to 900L, "unit" to 5L))
        assertEquals(RateValues(day = 120, fourWeek = 900), v)
    }

    private fun card(
        id: String,
        assetId: String? = null,
        classId: String? = "c",
        customer: String? = null,
        location: String? = null,
        priority: Int = 0,
        from: LocalDate? = null,
        until: LocalDate? = null,
        currency: String = "USD",
        active: Boolean = true,
        created: Long = 0,
    ) = RateCardRecord(id, classId, assetId, customer, location, currency, active, from, until, priority, created, RateValues(day = 1))

    private fun resolve(cards: List<RateCardRecord>, customer: String? = "p", location: String? = "l", on: String = "2026-03-10T12:00:00Z") =
        RentalPricing.resolveCard(cards, "USD", "a", "c", customer, location, t(on), ZoneId.of("UTC"))?.id

    @Test fun `card resolution prefers asset, customer, location, priority, latest start, oldest`() {
        assertEquals("asset", resolve(listOf(card("class", customer = "p", location = "l", priority = 9), card("asset", assetId = "a", classId = null))))
        assertEquals("cust", resolve(listOf(card("loc", location = "l", priority = 9), card("cust", customer = "p"))))
        assertEquals("loc", resolve(listOf(card("plain", priority = 9), card("loc", location = "l"))))
        assertEquals("hi", resolve(listOf(card("lo"), card("hi", priority = 1))))
        assertEquals("late", resolve(listOf(card("open"), card("early", from = LocalDate.of(2026, 1, 1)), card("late", from = LocalDate.of(2026, 3, 1)))))
        assertEquals("old", resolve(listOf(card("new", created = 2), card("old", created = 1))))
    }

    @Test fun `card resolution filters currency, activity, owner and validity`() {
        assertNull(resolve(listOf(card("eur", currency = "EUR"), card("off", active = false), card("other", classId = "x"))))
        assertNull(resolve(listOf(card("someone", customer = "q"), card("elsewhere", location = "m"))))
        assertNull(resolve(listOf(card("ended", until = LocalDate.of(2026, 3, 10)), card("future", from = LocalDate.of(2026, 3, 11)))))
        assertEquals("last", resolve(listOf(card("last", until = LocalDate.of(2026, 3, 11)))))
        assertNull(resolve(listOf(card("cust", customer = "p")), customer = null))
    }

    @Test fun `settings payloads take server defaults`() {
        val s = RentalSettings.parse(
            """{"default_currency":"eur","day_mode":"calendar","grace_minutes":30,"month_mode":"calendar",
               "weekend_window":{"start":{"dow":5,"time":"12:00"},"end":{"dow":1,"time":"09:00"}}}""",
        )
        assertEquals("EUR", s.defaultCurrency)
        assertEquals(RatePolicy.WeekPoint(5, LocalTime.NOON), s.weekend?.start)
        val policy = s.pricingPolicy(ZoneId.of("UTC"))
        assertTrue(policy.calendarDays && policy.calendarMonths)
        assertEquals(30L, policy.graceMinutes)
        assertEquals(RentalSettings(), RentalSettings.parse(null))
        assertEquals(RentalSettings(), RentalSettings.parse("""{"default_currency":"  ","grace_minutes":"x","weekend_window":{"start":{"dow":9}}}"""))
        assertEquals(ScopePolicy(postChargeWindowDays = 30), ScopePolicy.parse("""{"post_charge_window_days":30}"""))
        val c = CollectionSettings.parse("""{"doc_expiry_alert_days":10,"required_doc_kinds":["license"]}""")
        assertEquals(listOf("license"), c.requiredDocKinds)
        val today = LocalDate.of(2026, 3, 1)
        assertTrue(c.expiringSoon(LocalDate.of(2026, 3, 11), today))
        assertFalse(c.expiringSoon(LocalDate.of(2026, 3, 12), today))
        assertFalse(c.expiringSoon(null, today))
    }
}
