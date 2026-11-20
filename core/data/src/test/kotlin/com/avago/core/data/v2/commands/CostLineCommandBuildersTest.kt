package com.avago.core.data.v2.commands

import com.avago.core.data.domain.AttachmentTarget
import com.avago.core.data.domain.CostLine
import com.avago.core.data.domain.CostLineDraft
import com.avago.core.data.domain.CostLineType
import com.avago.core.data.domain.PartIdentity
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2StagedFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigDecimal

class CostLineCommandBuildersTest {

    private val gtin = "4006381333931"
    private val stored = CostLine(
        id = "cl1", activityId = "a1", workOrderId = null, kind = CostLineType.PART, sortOrder = 0, productId = null,
        performerWorkerId = null, description = "Filter", quantity = BigDecimal.ONE, uomCode = null, unitCost = BigDecimal("12.50"),
        taxAmountMinor = 120, glAccountId = null, costStatus = "actual",
        identity = PartIdentity(partNumber = "P1", gtin = gtin, country = "DE"),
        partSnapshot = """{"gtin":"$gtin","country":"DE","supplier_sku":"S9"}""", notes = null, version = 4,
    )

    private fun draftOf(line: CostLine) = CostLineDraft(
        kind = line.kind, description = line.description, quantity = line.quantity, unitCost = line.unitCost,
        taxAmountMinor = line.taxAmountMinor, identity = line.identity,
    )

    @Test
    fun `a new line sends its identity and mirrors the snapshot keys`() {
        val draft = draftOf(stored).copy(identity = PartIdentity(partNumber = " P1 ", gtin = gtin, country = "de", revision = ""))
        val command = MaintCommands.createCostLine("cl2", "a1", null, draft, 0, 10, "u1")
        val payload = command.payload.jsonObject
        assertEquals("P1", payload["part_number"]!!.jsonPrimitive.content)
        assertEquals(gtin, payload["gtin"]!!.jsonPrimitive.content)
        assertEquals("DE", payload["country"]!!.jsonPrimitive.content)
        assertFalse("revision" in payload)
        val row = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single { it.table == "activity_cost_lines" }.set
        assertEquals("P1", row["part_number"])
        assertEquals(Json.parseToJsonElement("""{"gtin":"$gtin","country":"DE"}"""), Json.parseToJsonElement(row["part_snapshot"] as String))
    }

    @Test
    fun `emptied identity fields and tax are cleared and other snapshot keys survive`() {
        val draft = draftOf(stored).copy(taxAmountMinor = null, identity = stored.identity.copy(partNumber = "P2", gtin = " "))
        val command = MaintCommands.updateCostLine(stored, draft, 10, null, null)!!
        val payload = command.payload.jsonObject
        assertEquals(setOf("gtin", "tax_amount_minor"), payload["clear"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
        assertEquals(setOf("part_number"), payload["set"]!!.jsonObject.keys)
        assertEquals("P2", payload["set"]!!.jsonObject["part_number"]!!.jsonPrimitive.content)
        val row = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single { it.table == "activity_cost_lines" }.set
        assertEquals("P2", row["part_number"])
        assertTrue(row.containsKey("tax_amount_minor") && row["tax_amount_minor"] == null)
        assertEquals(
            Json.parseToJsonElement("""{"supplier_sku":"S9","country":"DE"}"""),
            Json.parseToJsonElement(row["part_snapshot"] as String),
        )
    }

    @Test
    fun `an invalid GTIN or country is rejected`() {
        val badGtin = draftOf(stored).copy(identity = PartIdentity(gtin = "4006381333932"))
        assertThrows(IllegalArgumentException::class.java) { MaintCommands.createCostLine("cl2", "a1", null, badGtin, 0, 10, "u1") }
        val badCountry = draftOf(stored).copy(identity = stored.identity.copy(country = "DEU"))
        assertThrows(IllegalArgumentException::class.java) { MaintCommands.updateCostLine(stored, badCountry, 10, null, null) }
    }

    @Test
    fun `an attachment can target a cost line`() {
        val file = V2StagedFile("00000000-0000-7000-8000-00000000000f", File("receipt.jpg"), "ab", 10, "image/jpeg", "receipt.jpg")
        val command = MaintCommands.attach("at1", AttachmentTarget.ToCostLine("cl1"), file, null, 10, "u1")
        assertEquals("cl1", command.payload.jsonObject["cost_line_id"]!!.jsonPrimitive.content)
        assertFalse("activity_id" in command.payload.jsonObject)
        val row = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single { it.table == "attachments" }.set
        assertEquals("cl1", row["cost_line_id"])
    }
}
