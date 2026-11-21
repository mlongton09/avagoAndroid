package com.avago.feature.rentals.ops.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RoutePingsTest {
    private val t0 = Instant.parse("2026-11-15T10:00:00Z").toEpochMilli()

    private fun ping(at: Long, lat: Double = 52.5, lng: Double = 13.4) = RoutePing(lat, lng, at)

    @Test
    fun `ping json uses the server field names and drops out-of-range optionals`() {
        val o = RoutePing(52.5, 13.4, t0, accuracyM = 8f, speedMps = -1f, heading = 90f, batteryPct = 140, taskId = "t1").toJson()
        assertEquals("2026-11-15T10:00:00Z", o["recorded_at"]!!.jsonPrimitive.content)
        assertEquals("t1", o["task_id"]!!.jsonPrimitive.content)
        assertTrue("accuracy_m" in o && "heading" in o)
        assertFalse("speed_mps" in o)
        assertFalse("battery_pct" in o)
        val body = RoutePing.batchBody(listOf(ping(t0), ping(t0 + 20_000)))
        assertEquals(2, (body["pings"] as JsonArray).size)
    }

    @Test
    fun `invalid coordinates are rejected`() {
        assertFalse(ping(t0, lat = 91.0).valid)
        assertFalse(ping(t0, lng = Double.NaN).valid)
        assertFalse(ping(t0, 0.0, 0.0).valid)
        assertTrue(ping(t0).valid)
    }

    @Test
    fun `battery lowers the cadence unless charging`() {
        assertEquals(60, PingPolicy.intervalSeconds(null, 80, false))
        assertEquals(60, PingPolicy.intervalSeconds(60, 25, true))
        assertEquals(120, PingPolicy.intervalSeconds(60, 25, false))
        assertEquals(240, PingPolicy.intervalSeconds(60, 10, false))
        assertEquals(15, PingPolicy.intervalSeconds(5, null, false))
        assertEquals(900, PingPolicy.intervalSeconds(5_000, 90, false))
    }

    @Test
    fun `flushes at most every 15 s and honours retry_after`() {
        assertFalse(PingPolicy.shouldFlush(0, t0, null, null))
        assertTrue(PingPolicy.shouldFlush(1, t0, null, null))
        assertFalse(PingPolicy.shouldFlush(1, t0 + 10_000, t0, null))
        assertTrue(PingPolicy.shouldFlush(1, t0 + 15_000, t0, null))
        assertFalse(PingPolicy.shouldFlush(1, t0 + 60_000, t0, t0 + 90_000))
        val details = Json.parseToJsonElement("""{"retry_after":30}""").jsonObject
        assertEquals(30, PingPolicy.retryAfterSeconds(details))
        assertEquals(60, PingPolicy.retryAfterSeconds(null))
    }

    @Test
    fun `buffer deduplicates within 10 s, caps its size and batches 50 oldest first`() {
        val b = PingBuffer()
        assertTrue(b.add(ping(t0)))
        assertFalse(b.add(ping(t0 + 5_000)))
        assertFalse(b.add(ping(t0 + 20_000, lat = 100.0)))
        assertTrue(b.add(ping(t0 + 10_000)))
        assertFalse(PingBuffer().add(ping(t0 + 3_000), lastSentMs = t0))

        val big = PingBuffer()
        repeat(PingPolicy.MAX_BUFFER + 5) { big.add(ping(t0 + it * 10_000L)) }
        assertEquals(PingPolicy.MAX_BUFFER, big.size)
        assertEquals(t0 + 5 * 10_000L, big.snapshot().first().recordedAtMs)
        val batch = big.nextBatch()
        assertEquals(PingPolicy.MAX_BATCH, batch.size)
        big.ack(batch)
        assertEquals(PingPolicy.MAX_BUFFER - PingPolicy.MAX_BATCH, big.size)
        assertEquals(batch.last().recordedAtMs + 10_000L, big.snapshot().first().recordedAtMs)
    }

    @Test
    fun `expired pings are dropped and ack ignores a changed head`() {
        val b = PingBuffer(listOf(ping(t0 + 60_000), ping(t0)))
        assertEquals(t0, b.snapshot().first().recordedAtMs)
        assertEquals(1, b.dropExpired(t0 + PingPolicy.MAX_AGE_MS + 1))
        assertEquals(1, b.size)
        b.ack(listOf(ping(t0)))
        assertEquals(1, b.size)
    }

    @Test
    fun `stationary after two slow minutes, reset by movement`() {
        val d = StationaryDetector()
        assertFalse(d.update(0.2f, t0))
        assertFalse(d.update(0.3f, t0 + 60_000))
        assertTrue(d.update(0.1f, t0 + 120_000))
        assertFalse(d.update(5f, t0 + 130_000))
        assertFalse(d.update(null, t0 + 140_000))
    }

    @Test
    fun `tracking needs opt-in, a live route and the same day`() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = Instant.parse("2026-11-15T12:00:00Z")
        val today = LocalDate.of(2026, 11, 15)
        assertTrue(TrackingRules.shouldTrack(true, true, today, now, zone))
        assertFalse(TrackingRules.shouldTrack(false, true, today, now, zone))
        assertFalse(TrackingRules.shouldTrack(true, false, today, now, zone))
        assertFalse(TrackingRules.shouldTrack(true, true, today.minusDays(1), now, zone))
        assertEquals(11L, TrackingRules.untilEndOfDay(now, zone).toHours())
    }

    @Test
    fun `pings are attributed to the stop being driven to`() {
        fun stop(id: String, status: TaskStatus) = RouteStop(
            id, null, TaskKind.DELIVERY, status, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        )
        val route = DriverRoute(null, listOf(stop("a", TaskStatus.COMPLETED), stop("b", TaskStatus.EN_ROUTE), stop("c", TaskStatus.ASSIGNED)), true, 60)
        assertEquals("b", TrackingRules.activeTaskId(route))
        assertNull(TrackingRules.activeTaskId(route.copy(stops = listOf(stop("c", TaskStatus.ASSIGNED)))))
    }
}
