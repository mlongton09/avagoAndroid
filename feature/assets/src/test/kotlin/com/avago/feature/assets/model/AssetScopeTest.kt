package com.avago.feature.assets.model

import com.avago.feature.assets.viewmodel.normalizeMileage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssetScopeTest {
    private fun parse(s: String) = AssetTypeCatalog.from(Json.parseToJsonElement(s).jsonObject)

    private val hiddenRealEstate = parse(
        """{"data":{"real_estate_enabled":false,
            "types":[{"id":"car","usage_meter":"distance"},{"id":"atv_utv","usage_meter":"hours"},{"id":"drone","usage_meter":"cycles"}],
            "hidden_types":["residential","multifamily","office","healthcare","restaurant"]}}""",
    )

    @Test
    fun `catalog parses the data envelope`() {
        assertFalse(hiddenRealEstate.realEstateEnabled)
        assertEquals(listOf("car", "atv_utv", "drone"), hiddenRealEstate.types)
        assertEquals("cycles", hiddenRealEstate.usageMeters["drone"])
        assertTrue("office" in hiddenRealEstate.hidden)
    }

    @Test
    fun `real estate is hidden unless enabled but the current type is kept`() {
        val groups = AssetScope.visibleGroups(AssetTypes.grouped, hiddenRealEstate)
        assertFalse(groups.any { it.id == "real_estate" })
        assertTrue(groups.first { it.id == "tools" }.types.any { it.key == "power_tools" })
        assertTrue(groups.first { it.id == "equipment" }.types.any { it.key == "industrial" })

        val kept = AssetScope.visibleGroups(AssetTypes.grouped, hiddenRealEstate, currentType = "office")
        assertEquals(listOf("office"), kept.first { it.id == "real_estate" }.types.map { it.key })

        val enabled = parse("""{"real_estate_enabled":true,"types":[],"hidden_types":[]}""")
        assertEquals(5, AssetScope.visibleGroups(AssetTypes.grouped, enabled).first { it.id == "real_estate" }.types.size)
        assertFalse(AssetTypeCatalog.Default.isVisible("residential"))
    }

    @Test
    fun `vin, plate and mileage follow the asset kind`() {
        assertTrue(AssetScope.showsVin("truck"))
        assertTrue(AssetScope.showsVin(null))
        assertFalse(AssetScope.showsVin("drone"))
        assertFalse(AssetScope.showsLicensePlate("welding_cutting"))
        assertTrue(AssetScope.tracksMileage("car"))
        assertFalse(AssetScope.tracksMileage("trailer"))
        assertFalse(AssetScope.tracksMileage("atv_utv"))
        assertFalse(AssetScope.tracksMileage("car", parse("""{"types":[{"id":"car","usage_meter":"hours"}]}""")))
    }

    @Test
    fun `mileage input is normalised to whole digits`() {
        assertEquals("12345", normalizeMileage("12,345 mi"))
        assertEquals("0", normalizeMileage("000"))
        assertEquals("", normalizeMileage("km"))
        assertEquals("123456789", normalizeMileage("1234567890"))
    }
}
