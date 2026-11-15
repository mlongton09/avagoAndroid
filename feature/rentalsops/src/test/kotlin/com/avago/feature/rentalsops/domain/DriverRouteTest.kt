package com.avago.feature.rentalsops.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class DriverRouteTest {

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `my-route parses stops in route_seq order and keeps the server geo uri`() {
        val r = DriverRoute.parse(
            obj(
                """{"date":"2026-06-10","timezone":"Europe/Berlin","ping_interval_seconds":30,"tracking":true,
                   "stops":[
                     {"task_id":"b","kind":"pickup","status":"assigned","route_seq":2,"lat":1.5,"lng":2.5,
                      "geo_uri":"geo:1.5,2.5?q=1.5,2.5","maps_url":"https://maps/x","geo_from":"job_site",
                      "address":{"line1":"1 Main St","city":"Town"},"customer_name":"ACME","order_number":"R-1"},
                     {"task_id":"a","kind":"delivery","status":"completed","route_seq":1,"lat":null,"lng":null,
                      "maps_url":"https://maps/y"},
                     {"task_id":"c","kind":"delivery","status":"assigned"},
                     {"kind":"delivery"}
                   ]}""",
            ),
        )
        assertEquals("2026-06-10", r.date)
        assertEquals(30, r.pingIntervalSeconds)
        assertTrue(r.tracking)
        assertEquals(listOf("a", "b", "c"), r.stops.map { it.taskId })
        val b = r.stops[1]
        assertEquals(TaskKind.PICKUP, b.kind)
        assertEquals("geo:1.5,2.5?q=1.5,2.5", b.geoUri)
        assertEquals("https://maps/x", b.mapsUrl)
        assertEquals("1 Main St, Town", b.address)
        assertTrue(b.canNavigate)
        // No pin: navigation hidden, no geo/maps link even if the server sent a maps url.
        val a = r.stops[0]
        assertFalse(a.canNavigate)
        assertNull(a.geoUri)
        assertNull(a.mapsUrl)
        // The next stop skips completed ones.
        assertEquals("b", r.next?.taskId)
    }

    @Test
    fun `missing ping interval falls back to the default`() {
        val r = DriverRoute.parse(obj("""{"stops":[]}"""))
        assertEquals(DriverRoute.DEFAULT_PING_SECONDS, r.pingIntervalSeconds)
        assertFalse(r.tracking)
        assertNull(r.next)
    }

    @Test
    fun `local optimistic status overlays the server route`() {
        val r = DriverRoute.parse(obj("""{"stops":[{"task_id":"a","status":"assigned","route_seq":1}]}"""))
        val o = DriverRoute.withLocalStatus(r, mapOf("a" to TaskStatus.EN_ROUTE, "zz" to TaskStatus.COMPLETED))
        assertEquals(TaskStatus.EN_ROUTE, o.stops.single().status)
    }

    private fun task(id: String, driver: String?, at: String?, seq: Int? = null, status: String = "assigned") =
        DeliveryTask.from(
            obj(
                """{"id":"$id","kind":"delivery","status":"$status","driver_user_id":${driver?.let { "\"$it\"" } ?: "null"},
                   "scheduled_at":${at?.let { "\"$it\"" } ?: "null"},"route_seq":${seq ?: "null"},"lat":1.0,"lng":2.0}""",
            ),
        )!!

    @Test
    fun `offline route keeps my tasks for today and in-progress ones`() {
        val start = Instant.parse("2026-06-10T00:00:00Z")
        val end = Instant.parse("2026-06-11T00:00:00Z")
        val tasks = listOf(
            task("today2", "me", "2026-06-10T09:00:00Z", seq = 2) to TaskStatus.ASSIGNED,
            task("today1", "me", "2026-06-10T15:00:00Z", seq = 1) to TaskStatus.ASSIGNED,
            task("tomorrow", "me", "2026-06-11T08:00:00Z") to TaskStatus.ASSIGNED,
            task("yesterday-open", "me", "2026-06-09T08:00:00Z") to TaskStatus.EN_ROUTE,
            task("other", "someone", "2026-06-10T09:00:00Z") to TaskStatus.ASSIGNED,
            task("cancelled", "me", "2026-06-10T09:00:00Z") to TaskStatus.CANCELLED,
            task("unscheduled", "me", null) to TaskStatus.ASSIGNED,
        )
        val r = DriverRoute.fromTasks(tasks, "me", start, end)
        assertEquals(listOf("today1", "today2", "yesterday-open"), r.stops.map { it.taskId })
        assertTrue(r.tracking)
        assertNull(r.date)
        assertEquals("geo:1.0,2.0?q=1.0,2.0", r.stops[0].geoUri)
        // No signed-in user: nothing is "mine".
        assertTrue(DriverRoute.fromTasks(tasks, null, start, end).stops.isEmpty())
    }

    @Test
    fun `route step offers exactly one next action`() {
        assertEquals(RouteStep.ON_MY_WAY, RouteStep.of(TaskStatus.ASSIGNED))
        assertEquals(RouteStep.ARRIVED, RouteStep.of(TaskStatus.EN_ROUTE))
        assertEquals(RouteStep.COMPLETE, RouteStep.of(TaskStatus.ARRIVED))
        assertEquals(RouteStep.NONE, RouteStep.of(TaskStatus.PENDING))
        assertEquals(RouteStep.NONE, RouteStep.of(TaskStatus.COMPLETED))
        assertEquals(RouteStep.NONE, RouteStep.of(TaskStatus.FAILED))
    }
}
