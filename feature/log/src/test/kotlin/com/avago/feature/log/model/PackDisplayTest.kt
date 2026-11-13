package com.avago.feature.log.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class PackDisplayTest {

    private val t = PackDisplay.Translator { key, count -> if (count != null) "$key($count)" else key }

    private fun build(json: String, skip: Set<String> = emptySet()) =
        PackDisplay.build(LogPacks.parse(json), t, Locale.US, formatCurrency = { "$" + it }, skipKeys = skip)

    @Test
    fun `fuel section formats volume price and partial fill`() {
        val s = build("""{"fuel_volume":12.5,"fuel_volume_unit":"gallon","fuel_price_per_unit":3.5,"fuel_full_fill":false,"fuel_card_last4":"1234","fuel_grade":"full_synthetic"}""")
        assertEquals(1, s.size)
        val fuel = s.single()
        assertEquals(PackDisplay.SectionId.FUEL, fuel.id)
        assertEquals("pack_fuel", fuel.title)
        val byKey = fuel.rows.associate { it.key to it.value }
        assertEquals("12.5 gal", byKey["fuel_volume"])
        assertEquals("$3.5 / gal", byKey["fuel_price_per_unit"])
        assertEquals("partial_fill", byKey["fuel_full_fill"])
        assertEquals("•••• 1234", byKey["fuel_card_last4"])
        assertEquals("Full synthetic", byKey["fuel_grade"])
    }

    @Test
    fun `full fill is not listed and web unit spellings map`() {
        val s = build("""{"fuel_volume":40,"fuel_volume_unit":"L","fuel_full_fill":true}""")
        val rows = s.single().rows
        assertEquals(listOf("fuel_volume"), rows.map { it.key })
        assertEquals("40 L", rows.single().value)
    }

    @Test
    fun `tire position maps are ordered and localized`() {
        val s = build("""{"tire_positions":["fr","fl"],"tire_tread":{"rr":4,"fl":6.25},"tire_psi":{"fl":35},"tire_pressure_unit":"bar"}""")
        val tire = s.single()
        val byKey = tire.rows.associate { it.key to it.value }
        assertEquals("pos_fr, pos_fl", byKey["tire_positions"])
        assertEquals("pos_fl 6.2 /32\" · pos_rr 4 /32\"", byKey["tire_tread"])
        assertEquals("pos_fl 35 bar", byKey["tire_psi"])
    }

    @Test
    fun `battery brake and common sections`() {
        val s = build("""{"battery_voltage":"12.64","battery_rated_cca":650,"brake_pad_mm":{"fl":8},"brake_fluid":"ok","vendor_name":"Shop","pm_plan_ids":["a","b"],"vendor_id":"v1"}""")
        assertEquals(listOf(PackDisplay.SectionId.BATTERY, PackDisplay.SectionId.BRAKE, PackDisplay.SectionId.COMMON), s.map { it.id })
        val all = s.flatMap { it.rows }.associate { it.key to it.value }
        assertEquals("12.64 V", all["battery_voltage"])
        assertEquals("650 CCA", all["battery_rated_cca"])
        assertEquals("pos_fl 8 mm", all["brake_pad_mm"])
        assertEquals("Ok", all["brake_fluid"])
        assertEquals("pm_plans_count(2)", all["pm_plan_ids"])
        assertNull(all["vendor_id"])
    }

    @Test
    fun `skip keys and empty attributes`() {
        assertTrue(build("{}").isEmpty())
        assertTrue(build("""{"fuel_volume":5}""", skip = setOf("fuel_volume")).isEmpty())
        assertTrue(PackDisplay.build(null, t).isEmpty())
    }

    @Test
    fun `extra rows exclude pack keys and false values`() {
        val extra = PackDisplay.extraRows(LogPacks.parse("""{"fuel_volume":5,"serial":"X1","flag":false,"blank":"","count":3}"""))
        assertEquals(listOf("serial" to "X1", "count" to "3"), extra)
    }
}
