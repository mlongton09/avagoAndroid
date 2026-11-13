package com.avago.feature.log.model

import com.avago.feature.log.model.LogPacks.FuelFill
import com.avago.feature.log.model.LogPacks.FuelTriple
import com.avago.feature.log.model.LogPacks.Pack
import com.avago.feature.log.model.LogPacks.readNumMap
import com.avago.feature.log.model.LogPacks.readString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogPacksTest {

    @Test
    fun `category detection`() {
        assertEquals(setOf(Pack.FUEL), LogPacks.packsFor("Refuel"))
        assertEquals(setOf(Pack.FUEL), LogPacks.packsFor("diesel_fill"))
        assertEquals(setOf(Pack.FUEL), LogPacks.packsFor("DEF top-up"))
        assertFalse(LogPacks.isFuel("default"))
        assertEquals(setOf(Pack.OIL), LogPacks.packsFor("oil_change"))
        assertEquals(setOf(Pack.TIRE), LogPacks.packsFor("Wheel balance"))
        assertTrue(LogPacks.packsFor("Wheel alignment").isEmpty())
        assertFalse(LogPacks.isFuel("Fuel filter replacement"))
        assertFalse(LogPacks.isFuel("fuel_system_service"))
        assertFalse(LogPacks.isFuel("Fuel injector cleaning"))
        assertEquals(setOf(Pack.BATTERY), LogPacks.packsFor("battery_test"))
        assertEquals(setOf(Pack.BRAKE), LogPacks.packsFor("brakes"))
        assertTrue(LogPacks.packsFor(null).isEmpty())
    }

    @Test
    fun `any two of price volume total computes the third`() {
        assertEquals(40.0, LogPacks.solveFuel(FuelTriple(4.0, 10.0, null)).total)
        assertEquals(3.5, LogPacks.solveFuel(FuelTriple(null, 10.0, 35.0)).pricePerUnit)
        assertEquals(12.5, LogPacks.solveFuel(FuelTriple(4.0, null, 50.0)).volume)
        assertEquals(FuelTriple(4.0, null, null), LogPacks.solveFuel(FuelTriple(4.0, null, null)))
        // all three present, user edited total → price recomputed
        assertEquals(5.0, LogPacks.solveFuel(FuelTriple(4.0, 10.0, 50.0), lastEdited = "total").pricePerUnit)
    }

    @Test
    fun `economy uses previous full fill and accumulates partials`() {
        val history = listOf(
            FuelFill("a", 1, 1000.0, 10.0, true),
            FuelFill("b", 2, 1100.0, 4.0, false),
            FuelFill("old", 0, 500.0, 12.0, true),
        )
        val current = FuelFill("c", 3, 1300.0, 6.0, true)
        // 300 distance / (6 + 4) = 30
        assertEquals(30.0, LogPacks.computeEconomy(current, history))
        assertEquals(3.33, LogPacks.computeEconomy(current, history, "L/100km"))
    }

    @Test
    fun `economy null for partial fill or no previous full fill`() {
        assertNull(LogPacks.computeEconomy(FuelFill("c", 3, 1300.0, 6.0, false), listOf(FuelFill("a", 1, 1000.0, 10.0, true))))
        assertNull(LogPacks.computeEconomy(FuelFill("c", 3, 1300.0, 6.0, true), listOf(FuelFill("b", 1, 1000.0, 10.0, false))))
        assertNull(LogPacks.computeEconomy(FuelFill("c", 3, null, 6.0, true), listOf(FuelFill("a", 1, 1000.0, 10.0, true))))
    }

    @Test
    fun `merge preserves unknown keys and clears nulls`() {
        val existing = LogPacks.parse("""{"custom_x":{"deep":true},"fuel_grade":"regular","fuel_station":"Shell"}""")
        val merged = LogPacks.merge(
            existing,
            mapOf(
                "fuel_grade" to JsonPrimitive("premium"),
                "fuel_station" to JsonNull,
                "tire_tread" to LogPacks.numMap(mapOf("fl" to "6", "fr" to "")),
            ),
        )
        assertEquals("premium", merged.readString("fuel_grade"))
        assertNull(merged["fuel_station"])
        assertTrue(merged["custom_x"]!!.jsonObject.containsKey("deep"))
        assertEquals(mapOf("fl" to "6"), merged.readNumMap("tire_tread"))
    }

    @Test
    fun `fuel edit derives the missing field and keeps re-deriving it`() {
        var s = LogPacks.FuelInputs("", "", "", null)
        s = LogPacks.applyFuelEdit(s, "price", "4")
        s = LogPacks.applyFuelEdit(s, "volume", "10")
        assertEquals("40", s.total)
        assertEquals("total", s.derived)
        s = LogPacks.applyFuelEdit(s, "volume", "12")
        assertEquals("48", s.total)
        // user takes over the derived field: no recompute, even when cleared
        s = LogPacks.applyFuelEdit(s, "total", "")
        assertEquals("", s.total)
        assertNull(s.derived)
    }

    @Test
    fun `volume unit writes gallon liter and reads legacy gal L`() {
        assertEquals("gallon", LogPacks.normalizeVolumeUnit("gal"))
        assertEquals("liter", LogPacks.normalizeVolumeUnit("L"))
        assertEquals("kWh", LogPacks.normalizeVolumeUnit("kwh"))
        assertEquals("km/L", LogPacks.economyUnitFor("L"))
        assertEquals("km/L", LogPacks.economyUnitFor("liter"))
    }

    @Test
    fun `dot date validation`() {
        assertTrue(LogPacks.isValidDotDate("0723"))
        assertFalse(LogPacks.isValidDotDate("5423"))
        assertFalse(LogPacks.isValidDotDate("723"))
    }
}
