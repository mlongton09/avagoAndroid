package com.avago.feature.workorders.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WoSearchTest {

    @Test
    fun `terms normalize case, diacritics and strip leading hash`() {
        assertEquals(listOf("bremse", "1a2b"), WoSearch.terms("  BREMSE  #1A2B "))
        assertEquals(listOf("cafe"), WoSearch.terms("Café"))
        assertEquals(emptyList<String>(), WoSearch.terms("   "))
        assertEquals(emptyList<String>(), WoSearch.terms("#"))
    }

    @Test
    fun `every term must match some field`() {
        val fields = listOf("Replace brake pads", "wo-123abc", "Ford Transit", "Jürgen")
        assertTrue(WoSearch.matches(fields, WoSearch.terms("brake transit")))
        assertTrue(WoSearch.matches(fields, WoSearch.terms("jurgen #123")))
        assertFalse(WoSearch.matches(fields, WoSearch.terms("brake sprinter")))
    }

    @Test
    fun `blank query returns all rows and null fields are ignored`() {
        val rows = listOf("a" to null, "b" to "Pump")
        assertEquals(rows, WoSearch.filter(rows, "", { listOf(it.first, it.second) }))
        assertEquals(listOf(rows[1]), WoSearch.filter(rows, "pump", { listOf(it.first, it.second) }))
    }
}
