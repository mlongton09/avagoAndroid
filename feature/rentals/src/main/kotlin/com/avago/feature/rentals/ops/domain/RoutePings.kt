package com.avago.feature.rentals.ops.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One G5 location ping (`POST routing/pings`, AvagoSvc
 * docs/rentals-api-wave2-routing-api-webhooks.md §3/§7). [recordedAtMs] is the fix time.
 */
@Serializable
data class RoutePing(
    val lat: Double,
    val lng: Double,
    val recordedAtMs: Long,
    val accuracyM: Float? = null,
    val speedMps: Float? = null,
    val heading: Float? = null,
    val batteryPct: Int? = null,
    val taskId: String? = null,
) {
    val valid: Boolean
        get() = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)

    fun toJson(): JsonObject = JsonObject(
        buildMap {
            put("lat", JsonPrimitive(lat))
            put("lng", JsonPrimitive(lng))
            put("recorded_at", JsonPrimitive(Instant.ofEpochMilli(recordedAtMs).toString()))
            accuracyM?.takeIf { it.isFinite() && it >= 0f }?.let { put("accuracy_m", JsonPrimitive(it)) }
            speedMps?.takeIf { it.isFinite() && it >= 0f }?.let { put("speed_mps", JsonPrimitive(it)) }
            heading?.takeIf { it.isFinite() }?.let { put("heading", JsonPrimitive(it)) }
            batteryPct?.takeIf { it in 0..100 }?.let { put("battery_pct", JsonPrimitive(it)) }
            taskId?.let { put("task_id", JsonPrimitive(it)) }
        },
    )

    companion object {
        fun batchBody(pings: List<RoutePing>): JsonObject = JsonObject(mapOf("pings" to JsonArray(pings.map { it.toJson() })))
    }
}

/** Battery-aware cadence and batching rules from §7 "Location pings". */
object PingPolicy {
    /** Server bounds for `ping_interval_seconds` (routing settings accept 15–900). */
    const val MIN_INTERVAL_SECONDS = 15
    const val MAX_INTERVAL_SECONDS = 900

    /** The server skips pings closer together than this. */
    const val MIN_GAP_MS = 10_000L

    /** Batches of at most 50 pings, sent at most every 15 s (server limit: 12 batches/min). */
    const val MAX_BATCH = 50
    const val MIN_FLUSH_GAP_MS = 15_000L

    /** Pings older than 2 days are skipped by the server; drop them locally instead of sending. */
    const val MAX_AGE_MS = 2L * 24 * 60 * 60 * 1000

    /** Offline buffer cap (about 8 h at the default 60 s); the oldest pings go first. */
    const val MAX_BUFFER = 1_000

    /** Back-off after a 429 without `retry_after`. */
    const val DEFAULT_RETRY_AFTER_SECONDS = 60L

    /** How often the service re-reads `my-route` to learn whether tracking should continue. */
    const val ROUTE_CHECK_MS = 5L * 60 * 1000

    /**
     * Location request interval: the server's base interval, doubled below 30 % battery and
     * quadrupled below 15 % while not charging.
     */
    fun intervalSeconds(baseSeconds: Int?, batteryPct: Int?, charging: Boolean): Int {
        val base = (baseSeconds ?: DriverRoute.DEFAULT_PING_SECONDS).coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)
        val factor = when {
            charging || batteryPct == null -> 1
            batteryPct < 15 -> 4
            batteryPct < 30 -> 2
            else -> 1
        }
        return base * factor
    }

    /** Whether a batch may be sent now. */
    fun shouldFlush(queued: Int, nowMs: Long, lastFlushMs: Long?, retryAtMs: Long?): Boolean =
        queued > 0 &&
            (lastFlushMs == null || nowMs - lastFlushMs >= MIN_FLUSH_GAP_MS) &&
            (retryAtMs == null || nowMs >= retryAtMs)

    /** `429 rate_limited`: `details.retry_after` seconds (falls back to 60 s). */
    fun retryAfterSeconds(details: JsonObject?): Long {
        val v = details?.get("retry_after") as? JsonPrimitive
        val s = v?.longOrNull ?: v?.intOrNull?.toLong()
        return (s ?: DEFAULT_RETRY_AFTER_SECONDS).coerceIn(1, 15 * 60)
    }
}

/**
 * §7 "low-power modes when stationary": stationary once every fix for 2 minutes reported a
 * speed below 1 m/s; any faster fix (or a fix without speed) resets it.
 */
class StationaryDetector(private val windowMs: Long = 120_000L, private val speedMps: Float = 1f) {
    private var slowSinceMs: Long? = null

    fun update(speed: Float?, atMs: Long): Boolean {
        if (speed == null || !speed.isFinite() || speed >= speedMps) {
            slowSinceMs = null
            return false
        }
        val since = slowSinceMs ?: atMs.also { slowSinceMs = it }
        return atMs - since >= windowMs
    }

    val stationary: Boolean get() = slowSinceMs != null

    fun reset() {
        slowSinceMs = null
    }
}

/**
 * The offline ping queue: FIFO, deduplicated by the server's 10 s rule, bounded to
 * [PingPolicy.MAX_BUFFER]. Not thread-safe; the owner serialises access.
 */
class PingBuffer(initial: List<RoutePing> = emptyList()) {
    private val items = ArrayDeque(initial.sortedBy { it.recordedAtMs })

    val size: Int get() = items.size
    fun snapshot(): List<RoutePing> = items.toList()

    /** False when the ping is invalid or within 10 s of the newest queued one. */
    fun add(p: RoutePing, lastSentMs: Long? = null): Boolean {
        if (!p.valid) return false
        val newest = items.lastOrNull()?.recordedAtMs ?: lastSentMs
        if (newest != null && p.recordedAtMs - newest < PingPolicy.MIN_GAP_MS) return false
        items.addLast(p)
        while (items.size > PingPolicy.MAX_BUFFER) items.removeFirst()
        return true
    }

    /** Drops pings the server would skip as too old; returns how many were dropped. */
    fun dropExpired(nowMs: Long): Int {
        var n = 0
        while (items.isNotEmpty() && nowMs - items.first().recordedAtMs > PingPolicy.MAX_AGE_MS) {
            items.removeFirst()
            n++
        }
        return n
    }

    /** The oldest pings, at most [PingPolicy.MAX_BATCH]. */
    fun nextBatch(): List<RoutePing> = items.take(PingPolicy.MAX_BATCH)

    /** Removes a sent (or rejected) batch from the head of the queue. */
    fun ack(batch: List<RoutePing>) {
        repeat(batch.size.coerceAtMost(items.size)) {
            if (items.first() == batch[it]) items.removeFirst() else return
        }
    }
}

/** Whether the background tracker should keep running (§7 "stop tracking …"). */
object TrackingRules {
    /**
     * Tracking runs only while the driver opted in, the server route says `tracking` (a stop is
     * assigned, en route or arrived) and it is still the day the tracker started on.
     */
    fun shouldTrack(enabled: Boolean, routeTracking: Boolean, startedOn: LocalDate?, now: Instant, zone: ZoneId): Boolean =
        enabled && routeTracking && (startedOn == null || now.atZone(zone).toLocalDate() == startedOn)

    /** The stop pings are attributed to: the stop the driver is driving to or is at. */
    fun activeTaskId(route: DriverRoute?): String? =
        route?.stops?.firstOrNull { it.status == TaskStatus.EN_ROUTE || it.status == TaskStatus.ARRIVED }?.taskId

    /** Delay until the next local midnight, when a day's tracking ends. */
    fun untilEndOfDay(now: Instant, zone: ZoneId): Duration {
        val next = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        return Duration.between(now, next)
    }
}
