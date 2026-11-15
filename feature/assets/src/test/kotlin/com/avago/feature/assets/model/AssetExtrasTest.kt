package com.avago.feature.assets.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssetExtrasTest {
    private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `picker groups follow server entries, order and keep an unlisted current type`() {
        val catalog = AssetTypeCatalog.from(
            json(
                """{"data":{"real_estate_enabled":false,"types":[
                {"id":"power_tools","group":"tools","kind":"tool","usage_meter":"days"},
                {"id":"car","group":"road","kind":"vehicle","usage_meter":"distance","vin_supported":true},
                {"id":"hovercraft","group":"space","kind":"equipment"}]}}""",
            ),
        )
        val groups = AssetScope.pickerGroups(catalog, currentType = "office")
        assertEquals(listOf("road", "tools", "real_estate", "space"), groups.map { it.id })
        assertEquals(listOf("office"), groups.first { it.id == "real_estate" }.types.map { it.key })
        val unknown = groups.last().types.single()
        assertEquals(0, unknown.labelResId)
        assertEquals("Hovercraft", unknown.fallbackLabel)
        assertTrue(AssetScope.isTool("power_tools", catalog))
        assertTrue(AssetScope.showsVin("car", catalog))
        assertFalse(AssetScope.isRoadVehicle("hovercraft", catalog))
    }

    @Test
    fun `picker falls back to the built-in list without server entries`() {
        val groups = AssetScope.pickerGroups(AssetTypeCatalog.Default)
        assertTrue(groups.isNotEmpty())
        assertTrue(groups.none { g -> g.types.any { AssetScope.isRealEstate(it.key) } })
    }

    @Test
    fun `parseMinor accepts amounts with up to two decimals`() {
        assertEquals(123450L, AssetForms.parseMinor("1,234.50"))
        assertEquals(500L, AssetForms.parseMinor("5"))
        assertNull(AssetForms.parseMinor("1.234"))
        assertNull(AssetForms.parseMinor("-1"))
        assertNull(AssetForms.parseMinor("abc"))
        assertEquals("1234.50", AssetForms.formatMinor(123450))
    }

    @Test
    fun `tool form validates fields`() {
        val errs = ToolForm(
            calibratedOn = "2026-13-01", intervalDays = "0", usageValue = "-3",
            replacementValue = "1.999", currency = "EURO", trackerTagId = "x".repeat(129),
        ).errors()
        assertEquals(
            mapOf(
                "calibrated_on" to "date", "calibration_interval_days" to "range", "usage_value" to "number",
                "replacement_value_minor" to "number", "currency" to "currency", "tracker_tag_id" to "length",
            ),
            errs,
        )
        assertTrue(ToolForm(calibratedOn = "2026-01-31", intervalDays = "365").errors().isEmpty())
    }

    @Test
    fun `tool form body clears blanks and omits a blank due date`() {
        val body = ToolForm(calibratedOn = "2026-01-31", intervalDays = "365", replacementValue = "250", currency = "eur").toBody()
        assertEquals("2026-01-31", body["calibrated_on"]!!.jsonPrimitive.content)
        assertEquals(25000L, body["replacement_value_minor"]!!.jsonPrimitive.long)
        assertEquals("EUR", body["currency"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, body["tracker_tag_id"])
        assertFalse(body.containsKey("calibration_due_on"))
    }

    @Test
    fun `tool details round-trip into the form`() {
        val t = ToolDetails.from(
            json(
                """{"calibrated_on":"2026-01-01","calibration_interval_days":180,"usage_unit":"cycles","usage_value":42,
                "replacement_value_minor":129900,"currency":"USD","tracker_kind":"gps","calibration_status":"due_soon",
                "verified_renter_required":true}""",
            ),
        )
        assertEquals(ExpiryStatus.DueSoon, t.calibrationStatus)
        assertTrue(t.verifiedRenterRequired)
        val f = ToolForm.of(t)
        assertEquals("1299.00", f.replacementValue)
        assertEquals("cycles", f.usageUnit)
        assertEquals("42", f.usageValue)
    }

    @Test
    fun `vehicle odometer is only sent when the server returns it`() {
        val without = VehicleDetails.from(json("""{"data":{"registration_status":"overdue","vin_supported":true}}"""))
        assertFalse(without.odometerSupported)
        assertEquals(ExpiryStatus.Overdue, without.registrationStatus)
        val with = VehicleDetails.from(json("""{"odometer":null}"""))
        assertTrue(with.odometerSupported)
        val form = VehicleForm(odometer = "12000", registrationNumber = " ")
        assertFalse(form.toBody(sendOdometer = false).containsKey("odometer"))
        assertEquals(12000L, form.toBody(sendOdometer = true)["odometer"]!!.jsonPrimitive.long)
        assertEquals(JsonNull, form.toBody(false)["registration_number"])
        assertEquals(mapOf("insurance_expires_on" to "date"), VehicleForm(insuranceExpiresOn = "31/01/2026").errors())
    }

    @Test
    fun `recall list parses and counts open recalls`() {
        val list = RecallList.from(
            json(
                """{"supported":true,"stale":true,"block_unremedied_recalls":true,"recalls":[
                {"campaign_number":"24V001","component":"AIR BAGS","park_it":true},
                {"campaign_number":"23V900","remedied_at":"2026-02-01T00:00:00Z","remedy_note":"done"},
                {"component":"no campaign"}]}""",
            ),
        )
        assertTrue(list.supported && list.stale && list.blocksRentals)
        assertEquals(2, list.recalls.size)
        assertEquals(1, list.openCount)
        assertTrue(list.recalls[0].parkIt)
        assertFalse(list.recalls[1].open)
    }
}
