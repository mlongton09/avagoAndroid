package com.avago.feature.rentals.ops

import com.avago.feature.rentals.ops.push.RentalOpsRoutes
import com.avago.feature.rentals.ops.push.RentalPushRouter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Push payload checks against the examples in AvagoSvc `docs/rentals-api-stage-c.md` §7. */
class RentalOpsContractTest {

    // §7 push payload `data` (FCM flattens values to strings; loc_args is a JSON string).
    @Test
    fun `push payload from the doc`() {
        val data = mapOf(
            "type" to "rental.task_assigned", "account_id" to "acc-1", "order_id" to "9f000000-0000-4000-8000-000000000001",
            "entity_type" to "rental_delivery_task", "entity_id" to "7d000000-0000-4000-8000-000000000001",
            "task_id" to "7d000000-0000-4000-8000-000000000001", "kind" to "pickup", "number" to "R-1042",
            "title" to "New pickup task", "body" to "Scheduled 2026-03-02 09:00 UTC",
            "category" to "RENTAL_PICKUP",
            "title_loc_key" to "rental_task_assigned_pickup_title", "body_loc_key" to "rental_task_assigned_pickup_body",
            "loc_args" to """["R-1042", "pickup"]""",
        )
        val s = RentalPushRouter.route(data)!!
        assertEquals(RentalPushRouter.Category.PICKUP, s.category)
        assertEquals("rental_pickup", s.category.channelId)
        assertEquals(RentalOpsRoutes.task("7d000000-0000-4000-8000-000000000001"), s.route)
        assertTrue(RentalOpsRoutes.isSafe(s.route))
        assertEquals("R-1042", s.orderNumber)
        assertEquals("acc-1", s.accountId)
        assertEquals(listOf("R-1042", "pickup"), s.locArgs)
        assertEquals("rental_task_assigned_pickup_title", s.titleLocKey)
        // Every documented category maps to a channel.
        listOf(
            "RENTAL_BOOKING_REQUEST", "RENTAL_PICKUP_REQUESTED", "RENTAL_DELIVERY", "RENTAL_PICKUP",
            "RENTAL_TRANSFER", "RENTAL_RETURN_DUE", "RENTAL_INVOICE",
        ).forEach { assertEquals(it, RentalPushRouter.Category.fromWire(it)?.wire) }
    }
}
