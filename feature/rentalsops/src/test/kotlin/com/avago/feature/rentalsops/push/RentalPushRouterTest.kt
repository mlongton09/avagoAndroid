package com.avago.feature.rentalsops.push

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RentalPushRouterTest {

    @Test
    fun `only rental dot events are claimed`() {
        assertTrue(RentalPushRouter.handles("rental.task_assigned"))
        assertFalse(RentalPushRouter.handles("rental_transfer_shipped"))
        assertFalse(RentalPushRouter.handles("chat_message"))
        assertFalse(RentalPushRouter.handles(null))
        assertNull(RentalPushRouter.route(mapOf("type" to "wo_assignment")))
        // Unknown rental event without a category: no channel to post on.
        assertNull(RentalPushRouter.route(mapOf("type" to "rental.something_new")))
    }

    @Test
    fun `task events deep link to the task and keep one notification per task`() {
        val a = RentalPushRouter.route(mapOf("type" to "rental.task_assigned", "task_id" to "t-1", "kind" to "pickup"))!!
        assertEquals(RentalPushRouter.Category.PICKUP, a.category)
        assertEquals("rentalops/task/t-1", a.route)
        val b = RentalPushRouter.route(mapOf("type" to "rental.task_assigned", "task_id" to "t-1", "title" to "New"))!!
        assertEquals(a.notificationKey, b.notificationKey)
        assertEquals("New", b.title)
        // entity_id stands in for task_id when entity_type is the task table.
        val c = RentalPushRouter.route(
            mapOf("type" to "rental.task_failed", "entity_type" to "rental_delivery_task", "entity_id" to "t-2", "category" to "RENTAL_DELIVERY"),
        )!!
        assertEquals("rentalops/task/t-2", c.route)
        // No id at all: the task list.
        assertEquals(RentalOpsRoutes.TASKS, RentalPushRouter.route(mapOf("type" to "rental.task_completed"))!!.route)
    }

    @Test
    fun `order events open the P1 order, invoices the aging view`() {
        val due = RentalPushRouter.route(mapOf("type" to "rental.overdue", "order_id" to "o-1", "category" to "RENTAL_RETURN_DUE"))!!
        assertEquals(RentalPushRouter.Category.RETURN_DUE, due.category)
        assertEquals("rentals/order/o-1", due.route)
        val inv = RentalPushRouter.route(mapOf("type" to "rental.cycle_invoiced", "order_id" to "null"))!!
        assertEquals(RentalPushRouter.Category.INVOICE, inv.category)
        assertEquals(RentalOpsRoutes.AGING, inv.route)
        // An unsafe order id is not routed into navigation.
        assertEquals(RentalOpsRoutes.P1_HOME, RentalPushRouter.route(mapOf("type" to "rental.booking_request", "order_id" to "../x"))!!.route)
    }

    @Test
    fun `fallback categories match the server mapping`() {
        fun cat(event: String, kind: String? = null) =
            RentalPushRouter.route(listOfNotNull("type" to event, kind?.let { "kind" to it }).toMap())!!.category
        assertEquals(RentalPushRouter.Category.BOOKING_REQUEST, cat("rental.booking_request"))
        assertEquals(RentalPushRouter.Category.PICKUP_REQUESTED, cat("rental.off_rent_requested"))
        assertEquals(RentalPushRouter.Category.DELIVERY, cat("rental.task_assigned", "delivery"))
        assertEquals(RentalPushRouter.Category.TRANSFER, cat("rental.task_completed", "transfer"))
        assertEquals(RentalPushRouter.Category.RETURN_DUE, cat("rental.overdue"))
        assertEquals(RentalPushRouter.Category.INVOICE, cat("rental.cycle_invoiced"))
    }

    @Test
    fun `flattened FCM nulls and loc_args are normalised`() {
        assertEquals(listOf("R-1042", "pickup"), RentalPushRouter.locArgs("""["R-1042", "pickup"]"""))
        assertEquals(listOf("R-1"), RentalPushRouter.locArgs("R-1"))
        assertEquals(emptyList<String>(), RentalPushRouter.locArgs("null"))
        assertEquals(emptyList<String>(), RentalPushRouter.locArgs("[not json"))
        val s = RentalPushRouter.route(mapOf("type" to "rental.task_assigned", "number" to "null", "loc_args" to """["pickup"]""", "title" to " "))!!
        assertNull(s.orderNumber)
        assertNull(s.title)
        assertEquals(listOf("pickup"), s.locArgs)
    }

    @Test
    fun `every server loc key has an app string`() {
        val events = listOf("booking_request", "off_rent_requested", "overdue", "cycle_invoiced")
        val tasks = listOf("assigned", "completed", "failed").flatMap { a -> listOf("delivery", "pickup", "transfer").map { "task_${a}_$it" } }
        val expected = (events + tasks).flatMap { listOf("rental_${it}_title", "rental_${it}_body") }.toSet()
        assertEquals(26, expected.size)
        assertEquals(expected, PushText.KEYS)
        val cycle = PushText.resolve(spec(title = "rental_cycle_invoiced_title", body = "rental_cycle_invoiced_body"))
        assertFalse(cycle.bodyTakesNumber)
        assertTrue(PushText.resolve(spec(title = "rental_overdue_title", body = "rental_overdue_body")).bodyTakesNumber)
        // Keys from a newer server: no string, so the handler falls back to the server text.
        val unknown = PushText.resolve(spec(title = "rental_new_title", body = "rental_new_body"))
        assertNull(unknown.titleRes)
        assertNull(unknown.bodyRes)
    }

    private fun spec(title: String, body: String) = RentalPushRouter.route(
        mapOf("type" to "rental.overdue", "title_loc_key" to title, "body_loc_key" to body),
    )!!

    @Test
    fun `deep link guard accepts only known routes with safe ids`() {
        assertTrue(RentalOpsRoutes.isSafe("rentalops/tasks"))
        assertTrue(RentalOpsRoutes.isSafe(RentalOpsRoutes.task("0b4a1d2e-aaaa-4bbb-8ccc-123456789abc")))
        assertTrue(RentalOpsRoutes.isSafe("rentals/home"))
        assertTrue(RentalOpsRoutes.isSafe(RentalOpsRoutes.p1Order("o-1")))
        assertFalse(RentalOpsRoutes.isSafe("rentals/order/../../auth"))
        assertFalse(RentalOpsRoutes.isSafe("rentals/settings"))
        assertFalse(RentalOpsRoutes.isSafe("settings/graph"))
        assertFalse(RentalOpsRoutes.isSafe("rentalops/task/../../auth"))
        assertFalse(RentalOpsRoutes.isSafe("rentalops/task/a?x=1"))
        assertFalse(RentalOpsRoutes.isSafe("rentalops/unknown"))
        assertFalse(RentalOpsRoutes.isSafe(null))
    }
}