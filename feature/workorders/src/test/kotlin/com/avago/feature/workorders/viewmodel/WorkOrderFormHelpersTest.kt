package com.avago.feature.workorders.viewmodel

import com.avago.core.data.domain.Worker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WorkOrderFormHelpersTest {

    private fun worker(id: String, userId: String?, name: String) = Worker(
        id = id, userId = userId, displayName = name, kind = "employee", role = null, status = "active",
        skills = emptyList(), maxActiveWorkOrders = null, available = true, homeLocationId = null, notes = null, version = 1,
    )

    @Test
    fun `hours text converts to whole minutes and rejects non-positive input`() {
        assertEquals(90, hoursTextToMinutes("1.5"))
        assertEquals(45, hoursTextToMinutes(" 0,75 "))
        assertEquals(1, hoursTextToMinutes("0.01"))
        assertNull(hoursTextToMinutes("0"))
        assertNull(hoursTextToMinutes("-2"))
        assertNull(hoursTextToMinutes("abc"))
        assertEquals("1.5", minutesToHoursText(90))
        assertEquals("2", minutesToHoursText(120))
    }

    @Test
    fun `recent categories are newest first, distinct and capped at four`() {
        val history = listOf(
            1L to "Brakes", 5L to "Tyres", 3L to null, 4L to "Brakes", 2L to " ",
            6L to "Electrical", 7L to "Hydraulics", 8L to "Body", 0L to "Old",
        )
        assertEquals(listOf("Body", "Hydraulics", "Electrical", "Tyres"), recentCategories(history))
    }

    @Test
    fun `worker match prefers user id then a unique name`() {
        val all = listOf(worker("w1", "u1", "Ann Lee"), worker("w2", "u2", "Bob"), worker("w3", "u3", "bob"), worker("w4", null, "Cy"))
        assertEquals("u2", matchWorker(all, "u2"))
        assertEquals("u1", matchWorker(all, " ann lee "))
        assertNull(matchWorker(all, "Bob"))
        assertNull(matchWorker(all, "Cy"))
        assertNull(matchWorker(all, "nobody"))
    }

    @Test
    fun `asset groups count assets per type sorted by label`() {
        val groups = assetGroupsOf(listOf("forklift", "scissor_lift", "forklift", ""))
        assertEquals(listOf("Forklift" to 2, "Scissor lift" to 1), groups.map { it.label to it.assetCount })
        assertEquals("scissor_lift", groups[1].typeCode)
    }
}
