package com.avago.feature.log.model

import com.avago.core.data.db.entity.LogEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogListFilterTest {

    private fun log(id: String, title: String, notes: String? = null, category: String? = null) = LogEntity(
        entryId = id, assetId = "a", accountId = "acc", title = title, entryDate = 0L, odometerValue = null,
        category = category, cost = null, performedBy = null, performedByUserId = null, notes = notes,
        data = null, attributes = null, costMode = null, costItems = null, costLabor = null, costTax = null,
        currency = null, baseAmount = null, exchangeRateUsed = null, configId = null, configVersion = null,
        serviceId = null, costMisc = null, parentId = null, createdAt = 0L, updatedAt = 0L, deletedAt = null,
        serverVersion = 1L, seq = 1L,
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
        assertEquals(listOf("1", "2", "3"), LogListFilter.apply(list, null, "oil").map { it.entryId })
        assertEquals(listOf("1"), LogListFilter.apply(list, "oil", "oil").map { it.entryId })
        assertEquals(emptyList<String>(), LogListFilter.apply(list, "tire", "change").map { it.entryId })
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
