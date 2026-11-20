package com.avago.core.data.v2.commands

import com.avago.core.data.domain.AssetToolDetails
import com.avago.core.data.domain.AssetVehicleDetails
import com.avago.core.data.domain.Attachment
import com.avago.core.data.domain.ToolDetailsDraft
import com.avago.core.data.domain.VehicleDetailsDraft
import com.avago.core.data.v2.V2OverlayPatch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class AssetSubtypeCommandBuildersTest {

    private val assetId = "00000000-0000-7000-8000-000000000001"

    private val tool = AssetToolDetails(
        assetId = assetId, calibrationFileId = null, calibratedOn = "2026-01-01", calibrationIntervalDays = 90,
        calibrationDueOn = "2026-04-01", usageUnit = "days", usageValue = null, replacementValue = BigDecimal("120.00"),
        currency = "USD", trackerTagId = null, trackerKind = null,
    )

    private fun draftOf(t: AssetToolDetails) = ToolDetailsDraft(
        calibratedOn = t.calibratedOn?.let(LocalDate::parse), calibrationIntervalDays = t.calibrationIntervalDays,
        calibrationDueOn = t.calibrationDueOn?.let(LocalDate::parse), usageUnit = t.usageUnit,
        usageValue = t.usageValue, replacementValue = t.replacementValue, currency = t.currency,
        trackerTagId = t.trackerTagId, trackerKind = t.trackerKind,
    )

    private fun set(command: com.avago.core.data.v2.V2NewCommand) = command.payload.jsonObject["set"]!!.jsonObject

    @Test
    fun `an unchanged tool form queues nothing`() {
        assertNull(MaintCommands.updateToolDetails(assetId, tool, draftOf(tool), 1, "u"))
    }

    @Test
    fun `a blank due date is sent as the date the server derives from the new calibration`() {
        val draft = draftOf(tool).copy(calibratedOn = LocalDate.of(2026, 2, 1), calibrationDueOn = null)
        val command = MaintCommands.updateToolDetails(assetId, tool, draft, 1, "u")!!
        assertEquals("maint.asset_tool_details.update", command.operation)
        assertNull(command.baseVersion)
        assertEquals("2026-02-01", set(command)["calibrated_on"]!!.jsonPrimitive.content)
        assertEquals("2026-05-02", set(command)["calibration_due_on"]!!.jsonPrimitive.content)
        val overlay = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single()
        assertEquals("asset_tool_details", overlay.table)
        assertEquals("2026-05-02", overlay.set["calibration_due_on"])
    }

    @Test
    fun `a replacement value is sent in minor units and needs a currency`() {
        val command = MaintCommands.updateToolDetails(assetId, tool, draftOf(tool).copy(replacementValue = BigDecimal("99.5")), 1, "u")!!
        assertEquals(9950L, set(command)["replacement_value_minor"]!!.jsonPrimitive.content.toLong())
        assertThrows(IllegalArgumentException::class.java) {
            MaintCommands.updateToolDetails(assetId, tool, draftOf(tool).copy(currency = null), 1, "u")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MaintCommands.updateToolDetails(assetId, tool, draftOf(tool).copy(calibrationIntervalDays = 0), 1, "u")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MaintCommands.updateToolDetails(assetId, tool, draftOf(tool).copy(trackerKind = "nfc"), 1, "u")
        }
    }

    @Test
    fun `the first tool save creates the row in the overlay`() {
        val draft = ToolDetailsDraft(usageUnit = "cycles", usageValue = BigDecimal("12"), currency = "EUR")
        val command = MaintCommands.updateToolDetails(assetId, null, draft, 5, "u")!!
        assertEquals("cycles", set(command)["usage_unit"]!!.jsonPrimitive.content)
        assertEquals(0, BigDecimal("12").compareTo(BigDecimal(set(command)["usage_value"]!!.jsonPrimitive.content)))
        val row = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single().set
        assertEquals("cycles", row["usage_unit"])
        assertEquals(12_000_000L, (row["usage_value_micros"] as Number).toLong())
        assertTrue(row.containsKey("created_at"))
    }

    @Test
    fun `an emptied usage counter and unit are cleared and the counter resets to zero`() {
        val counted = tool.copy(usageValue = BigDecimal("40"))
        val command = MaintCommands.updateToolDetails(assetId, counted, draftOf(counted).copy(usageUnit = null, usageValue = null), 1, "u")!!
        val clear = command.payload.jsonObject["clear"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(setOf("usage_unit", "usage_value"), clear)
        val row = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single().set
        assertEquals(0L, (row["usage_value_micros"] as Number).toLong())
        assertTrue(row.containsKey("usage_unit") && row["usage_unit"] == null)
    }

    @Test
    fun `emptied vehicle fields are cleared`() {
        val stored = AssetVehicleDetails(
            assetId = assetId, bodyClass = "Van", fuelType = "diesel", gvwr = null, registrationNumber = "AB-1",
            registrationExpiresOn = "2026-06-30", registrationFileId = null, insurancePolicyNumber = "P-9",
            insuranceExpiresOn = null, insuranceFileId = null,
        )
        val draft = VehicleDetailsDraft(
            bodyClass = "Van", fuelType = "diesel", registrationNumber = "AB-2", registrationExpiresOn = null,
            insurancePolicyNumber = "P-9", insuranceExpiresOn = LocalDate.of(2027, 1, 1),
        )
        val command = MaintCommands.updateVehicleDetails(assetId, stored, draft, 1, "u")!!
        val payload = command.payload.jsonObject
        assertEquals(setOf("registration_number", "insurance_expires_on"), payload["set"]!!.jsonObject.keys)
        assertEquals(listOf("registration_expires_on"), payload["clear"]!!.jsonArray.map { it.jsonPrimitive.content })
        val overlay = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single().set
        assertTrue(overlay.containsKey("registration_expires_on") && overlay["registration_expires_on"] == null)
    }

    @Test
    fun `only an asset's first meter is sent as its default`() {
        val first = MaintCommands.createMeter("m1", assetId, "odometer", "km", isFirst = true, nowMillis = 1, userId = "u")
        assertEquals("maint.asset_meters.create", first.operation)
        assertEquals(true, first.payload.jsonObject["is_default"]!!.jsonPrimitive.content.toBoolean())
        val second = MaintCommands.createMeter("m2", assetId, "hours", "hours", isFirst = false, nowMillis = 1, userId = "u")
        assertTrue(second.payload.jsonObject["is_default"].let { it == null || it == JsonNull })
        assertThrows(IllegalArgumentException::class.java) {
            MaintCommands.createMeter("m3", assetId, "Odometer", "km", isFirst = false, nowMillis = 1, userId = "u")
        }
    }

    private fun attachment(id: String, sort: Int) = Attachment(
        id = id, file = null, fileId = "f-$id", documentId = null, assetId = assetId, activityId = null, workOrderId = null,
        sortOrder = sort, createdAt = 0,
    )

    @Test
    fun `a new cover photo moves to the front and only moved rows are updated`() {
        val ordered = listOf(attachment("a", 0), attachment("b", 1), attachment("c", 2), attachment("d", 3))
        val commands = MaintCommands.reorderAttachments(ordered, "c", 1)
        val moved = commands.associate {
            it.entityKey to it.payload.jsonObject["set"]!!.jsonObject["sort_order"]!!.jsonPrimitive.content.toInt()
        }
        assertEquals(mapOf("c" to 0, "a" to 1, "b" to 2), moved)
        assertFalse(moved.containsKey("d"))
        assertTrue(MaintCommands.reorderAttachments(ordered, "a", 1).isEmpty())
    }
}
