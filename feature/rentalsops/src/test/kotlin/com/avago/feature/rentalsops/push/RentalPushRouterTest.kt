package com.avago.feature.rentalsops.push

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RentalPushRouterTest {

    @Test
    fun `only rental types are claimed`() {
        assertTrue(RentalPushRouter.handles("rental.task_assigned"))
        assertTrue(RentalPushRouter.handles("rental_transfer_shipped"))
        assertFalse(RentalPushRouter.handles("chat_message"))
        assertFalse(RentalPushRouter.handles(null))
        assertNull(RentalPushRouter.route(mapOf("type" to "wo_assignment")))
    }

    @Test
    fun `task events deep link to the task and keep one notification per task`() {
        val a = RentalPushRouter.route(mapOf("type" to "rental.task_assigned", "task_id" to "t-1"))!!
        assertEquals(RentalPushRouter.Category.TASKS, a.category)
        assertEquals("rentalops/task/t-1", a.route)
        val b = RentalPushRouter.route(mapOf("type" to "rental.task_assigned", "task_id" to "t-1", "title" to "New"))!!
        assertEquals(a.notificationKey, b.notificationKey)
        assertEquals("New", b.title)
    }

    @Test
    fun `categories map to their screens`() {
        assertEquals(
            RentalOpsRoutes.TRANSFERS,
            RentalPushRouter.route(mapOf("type" to "rental.transfer_shipped", "transfer_id" to "x"))!!.route,
        )
        assertEquals(
            "rentalops/assign/l-9",
            RentalPushRouter.route(mapOf("type" to "rental.unit_assignment_needed", "line_id" to "l-9"))!!.route,
        )
        val due = RentalPushRouter.route(mapOf("type" to "rental.return_overdue", "order_id" to "o"))!!
        assertEquals(RentalPushRouter.Category.DUE, due.category)
        assertEquals(RentalOpsRoutes.HOME, due.route)
        // `event` refines a generic type.
        val e = RentalPushRouter.route(mapOf("type" to "rental_event", "event" to "rental.task_completed", "task_id" to "t"))!!
        assertEquals(RentalPushRouter.Category.TASKS, e.category)
        assertNotNull(RentalPushRouter.route(mapOf("type" to "rental.something_new")))
    }

    @Test
    fun `deep link guard accepts only module routes with safe ids`() {
        assertTrue(RentalOpsRoutes.isSafe("rentalops/tasks"))
        assertTrue(RentalOpsRoutes.isSafe(RentalOpsRoutes.task("0b4a1d2e-aaaa-4bbb-8ccc-123456789abc")))
        assertFalse(RentalOpsRoutes.isSafe("settings/graph"))
        assertFalse(RentalOpsRoutes.isSafe("rentalops/task/../../auth"))
        assertFalse(RentalOpsRoutes.isSafe("rentalops/task/a?x=1"))
        assertFalse(RentalOpsRoutes.isSafe("rentalops/unknown"))
        assertFalse(RentalOpsRoutes.isSafe(null))
    }
}
