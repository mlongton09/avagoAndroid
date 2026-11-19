package com.avago.feature.log.model

import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class LogListFilterTest {

    private fun log(id: String, title: String, notes: String? = null, category: String? = null) = Activity(
        id = id, assetId = "a", assetName = null, workOrderId = null, parentActivityId = null,
        type = ActivityType.SERVICE, categoryLabel = category, title = title, occurredAt = 0L,
        performedByUserId = null, performedByName = null, providerName = null, currency = null,
        overtimeMultiplier = null, notes = notes, payload = "{}", attributes = "{}", meterValue = null,
        totalCost = BigDecimal.ZERO, createdAt = 0L, version = 1L,
    )

    @Test
    fun `all terms must match title or notes, diacritic insensitive`() {
        assertTrue(LogListFilter.matches("Ölwechsel", "Filter getauscht", "olwechsel filter"))
        assertTrue(LogListFilter.matches("Brake pads", null, "  BRAKE  "))
        assertFalse(LogListFilter.matches("Brake pads", "front", "brake rear"))
        assertTrue(LogListFilter.matches("anything", null, "   "))
    }

    @Test
    fun `apply combines category and query`() {
        val list = listOf(
            log("1", "Oil change", category = "oil"),
            log("2", "Oil top-up", category = "fluids"),
            log("3", "Tires", notes = "oil leak spotted", category = "tire"),
        )
        assertEquals(listOf("1", "2", "3"), LogListFilter.apply(list, null, "oil").map { it.id })
        assertEquals(listOf("1"), LogListFilter.apply(list, "oil", "oil").map { it.id })
        assertEquals(emptyList<String>(), LogListFilter.apply(list, "tire", "change").map { it.id })
    }

    @Test
    fun `category counts sort by usage then name and skip blanks`() {
        val list = listOf(
            log("1", "a", category = "tire"),
            log("2", "b", category = "oil"),
            log("3", "c", category = "oil"),
            log("4", "d", category = ""),
            log("5", "e", category = "brake"),
        )
        assertEquals(listOf("oil" to 2, "brake" to 1, "tire" to 1), LogListFilter.categoryCounts(list))
    }
}
