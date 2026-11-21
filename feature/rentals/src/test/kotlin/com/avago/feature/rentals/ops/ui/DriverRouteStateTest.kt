package com.avago.feature.rentals.ops.ui

import com.avago.feature.rentals.ops.data.CachedJson
import com.avago.feature.rentals.ops.data.RentalOpsRepository.Fetch
import com.avago.feature.rentals.ops.data.TaskView
import com.avago.feature.rentals.ops.domain.DeliveryTask
import com.avago.feature.rentals.ops.domain.TaskKind
import com.avago.feature.rentals.ops.domain.TaskStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DriverRouteStateTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-06-10")
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private val serverRoute = obj("""{"date":"2026-06-10","stops":[{"task_id":"srv","status":"assigned","route_seq":1}]}""")
    private val local = TaskView(
        DeliveryTask(
            id = "srv", kind = TaskKind.DELIVERY, status = TaskStatus.ASSIGNED, orderId = null, transferId = null, address = null,
            lat = null, lng = null, contactName = null, contactPhone = null, scheduledAt = Instant.parse("2026-06-10T09:00:00Z"),
            windowEnd = null, driverUserId = "me", routeSeq = null, notes = null, failedReason = null, orderNumber = null,
            customerName = null, jobSiteName = null,
        ),
        TaskStatus.EN_ROUTE,
        emptyList(),
    )

    @Test
    fun `server route wins and shows queued local status`() {
        val s = DriverRouteViewModel.build(Fetch.Ok(CachedJson(serverRoute, ms("2026-06-10T07:00:00Z"), true)), listOf(local), false, "me", today, zone)
        assertEquals(RouteSource.SERVER, s.source)
        assertFalse(s.failed)
        assertEquals(TaskStatus.EN_ROUTE, s.route!!.stops.single().status)
    }

    @Test
    fun `failed refresh uses today's cached route but never yesterday's`() {
        val todayCache = CachedJson(serverRoute, ms("2026-06-10T06:00:00Z"), false)
        val cached = DriverRouteViewModel.build(Fetch.Failed(null, "offline", todayCache), listOf(local), false, "me", today, zone)
        assertEquals(RouteSource.CACHED, cached.source)
        assertTrue(cached.failed)
        assertNull(cached.failure)

        val stale = CachedJson(serverRoute, ms("2026-06-09T18:00:00Z"), false)
        val device = DriverRouteViewModel.build(Fetch.Failed(403, "forbidden", stale), listOf(local), false, "me", today, zone)
        assertEquals(RouteSource.DEVICE, device.source)
        assertEquals(403, device.failure)
        assertNull(device.fetchedAt)
        // Rebuilt from the device's tasks.
        assertEquals(listOf("srv"), device.route!!.stops.map { it.taskId })
    }

    @Test
    fun `before the first fetch the device route is shown`() {
        val s = DriverRouteViewModel.build(null, listOf(local), true, "me", today, zone)
        assertEquals(RouteSource.DEVICE, s.source)
        assertTrue(s.loading)
        assertEquals(1, s.route!!.stops.size)
    }
}
