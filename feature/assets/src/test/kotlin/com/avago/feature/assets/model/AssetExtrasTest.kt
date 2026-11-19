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
