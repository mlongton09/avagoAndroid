package com.avago.feature.log.model

import com.avago.feature.log.model.LogAnalytics.DistanceUnit
import com.avago.feature.log.model.LogAnalytics.Entry
import com.avago.feature.log.model.LogAnalytics.VolumeUnit
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogAnalyticsTest {

    private val day = 86_400_000L

    private fun fill(
        id: String,
        dayN: Int,
        meter: Double?,
        volume: Double,
        full: Boolean = true,
        unit: String = "gallon",
        cost: Double? = null,
        extra: String = "",
    ): Entry = Entry(
        id = id,
        dateMs = dayN * day,
        category = "fuel",
        meter = meter,
        cost = cost,
        attributes = LogPacks.parse(
            """{"fuel_volume":$volume,"fuel_volume_unit":"$unit","fuel_full_fill":$full$extra}""",
        ),
    )

    @Test
    fun `economy is full-to-full with partials carried forward`() {
        val s = LogAnalytics.economySeries(
            listOf(
                fill("a", 1, 1000.0, 10.0),
                fill("b", 2, 1150.0, 5.0, full = false),
                fill("c", 3, 1300.0, 10.0),
            ),
            DistanceUnit.MI,
        )
        assertEquals("mpg", s.unit)
        assertNull(s.points[0].economy)
        assertTrue(s.points[1].partial)
        assertNull(s.points[1].economy)
        assertEquals(20.0, s.points[2].economy!!, 1e-9) // 300 mi / 15 gal
        assertEquals(20.0, s.average!!, 1e-9)
        assertEquals(15.0, s.totalVolume, 1e-9)
        assertEquals(300.0, s.totalDistance, 1e-9)
    }

    @Test
    fun `web gal and L spellings are accepted`() {
        assertEquals(VolumeUnit.GAL, LogAnalytics.volumeUnitOf("gal"))
        assertEquals(VolumeUnit.L, LogAnalytics.volumeUnitOf("L"))
        assertEquals(VolumeUnit.L, LogAnalytics.volumeUnitOf("liter"))
        assertEquals(VolumeUnit.KWH, LogAnalytics.volumeUnitOf("kwh"))
        assertNull(LogAnalytics.volumeUnitOf("bogus"))
    }

    @Test
    fun `km series converts gallons to litres`() {
        val s = LogAnalytics.economySeries(
            listOf(fill("a", 1, 100.0, 1.0), fill("b", 2, 200.0, 1.0)),
            DistanceUnit.KM,
        )
        assertEquals("km/L", s.unit)
        assertEquals(100.0 / 3.785411784, s.points[1].economy!!, 1e-6)
    }

    @Test
    fun `hour meters report volume per hour`() {
        val s = LogAnalytics.economySeries(
            listOf(fill("a", 1, 10.0, 4.0), fill("b", 2, 20.0, 5.0)),
            DistanceUnit.H,
        )
        assertEquals("gal/h", s.unit)
        assertEquals(0.5, s.points[1].economy!!, 1e-9)
    }

    @Test
    fun `stored economy wins when convertible`() {
        val s = LogAnalytics.economySeries(
            listOf(
                fill("a", 1, 1000.0, 10.0),
                fill("b", 2, 1200.0, 10.0, extra = ""","fuel_economy":25,"fuel_economy_unit":"mpg""""),
            ),
        )
        assertEquals(25.0, s.points[1].economy!!, 1e-9)
        assertTrue(s.points[1].stored)
    }

    @Test
    fun `anomalies are flagged beyond 25 percent of median`() {
        val s = LogAnalytics.economySeries(
            listOf(
                fill("a", 1, 0.1, 10.0),
                fill("b", 2, 200.0, 10.0),
                fill("c", 3, 400.0, 10.0),
                fill("d", 4, 600.0, 10.0),
                fill("e", 5, 1000.0, 10.0),
            ),
        )
        val flagged = s.points.filter { it.anomaly }.map { it.id }
        assertEquals(listOf("e"), flagged)
        assertEquals(1, s.anomalies)
    }

    @Test
    fun `entries without volume are not fills`() {
        val e = Entry("x", day, "fuel", 100.0, 50.0, JsonObject(emptyMap()))
        assertTrue(LogAnalytics.economySeries(listOf(e)).points.isEmpty())
        assertNull(LogAnalytics.fuelVolume(e))
    }

    @Test
    fun `cost per meter uses metered span and window`() {
        val entries = listOf(
            Entry("a", 1 * day, "oil", 1000.0, 50.0),
            Entry("b", 5 * day, "tire", null, 200.0),
            Entry("c", 10 * day, "fuel", 1500.0, 50.0),
            Entry("d", 20 * day, "misc", null, 999.0), // outside metered window
        )
        val c = LogAnalytics.costPerMeter(entries, DistanceUnit.MI)
        assertEquals(500.0, c.distance, 1e-9)
        assertEquals(300.0, c.totalCost, 1e-9)
        assertEquals(0.6, c.perUnit!!, 1e-9)
    }

    @Test
    fun `cost per meter needs two readings`() {
        val c = LogAnalytics.costPerMeter(listOf(Entry("a", day, null, 10.0, 40.0), Entry("b", day, null, null, 2.0)))
        assertNull(c.perUnit)
        assertEquals(42.0, c.totalCost, 1e-9)
    }

    @Test
    fun `spend by category sorts descending and buckets other`() {
        val s = LogAnalytics.spendByCategory(
            listOf(
                Entry("a", day, "oil", null, 30.0),
                Entry("b", day, null, null, 100.0),
                Entry("c", day, "oil", null, 40.0),
                Entry("d", day, "tire", null, 0.0),
                Entry("e", day, "tire", null, null),
            ),
        )
        assertEquals(listOf("other", "oil"), s.map { it.category })
        assertEquals(70.0, s[1].total, 1e-9)
        assertEquals(2, s[1].count)
    }

    @Test
    fun `entry cost fallback and unit helpers`() {
        assertEquals(5.0, LogAnalytics.entryCost(null, 5.0, 7.0))
        assertEquals(7.0, LogAnalytics.entryCost(null, null, 7.0))
        assertNull(LogAnalytics.entryCost(null, null, null))
        assertEquals(DistanceUnit.H, LogAnalytics.distanceUnitFor("engine_hours", "km"))
        assertEquals(DistanceUnit.KM, LogAnalytics.distanceUnitFor(null, "km"))
        assertEquals(DistanceUnit.MI, LogAnalytics.distanceUnitFor("odometer", "mi"))
        assertEquals(10.0, LogAnalytics.convertEconomy(10.0, "km/L", "L/100km")!!, 1e-9)
        assertNull(LogAnalytics.convertEconomy(10.0, "mi/kWh", "mpg"))
        assertFalse(LogAnalytics.median(emptyList()) != null)
    }
}
