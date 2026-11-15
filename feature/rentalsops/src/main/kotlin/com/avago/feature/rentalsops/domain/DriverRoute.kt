package com.avago.feature.rentalsops.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/**
 * Wave 2 G5 driver route (`GET routing/my-route`, AvagoSvc
 * docs/rentals-api-wave2-routing-api-webhooks.md §3/§7): the driver's stops for one day in
 * `route_seq` order. When the route cannot be fetched (offline, older server) the list is
 * rebuilt from the stage C `my-tasks` feed already on the device.
 */
data class RouteStop(
    val taskId: String,
    val seq: Int?,
    val kind: TaskKind,
    val status: TaskStatus,
    val etaAt: Instant?,
    val scheduledAt: Instant?,
    val windowEnd: Instant?,
    val lat: Double?,
    val lng: Double?,
    /** `task`, `job_site` or `customer`: where the coordinates came from. */
    val geoFrom: String?,
    val orderNumber: String?,
    val customerName: String?,
    val jobSiteName: String?,
    val address: String?,
    val contactName: String?,
    val contactPhone: String?,
    /** `geo:lat,lng?q=lat,lng` (Android navigation intent); null without coordinates. */
    val geoUri: String?,
    val mapsUrl: String?,
) {
    /** Navigation is offered only for a stop with coordinates (§7 "hide when lat is null"). */
    val canNavigate: Boolean get() = lat != null && lng != null

    companion object {
        fun from(o: JsonObject): RouteStop? {
            val id = o.str("task_id") ?: o.str("id") ?: return null
            val lat = o.dbl("lat")
            val lng = o.dbl("lng")
            val placed = lat != null && lng != null
            return RouteStop(
                taskId = id,
                seq = o.int("route_seq"),
                kind = TaskKind.parse(o.str("kind")) ?: TaskKind.DELIVERY,
                status = TaskStatus.parse(o.str("status")) ?: TaskStatus.PENDING,
                etaAt = o.instant("eta_at"),
                scheduledAt = o.instant("scheduled_at"),
                windowEnd = o.instant("window_end"),
                lat = lat,
                lng = lng,
                geoFrom = o.str("geo_from"),
                orderNumber = o.str("order_number"),
                customerName = o.str("customer_name") ?: o.str("customer_display_name"),
                jobSiteName = o.str("job_site_name"),
                address = DeliveryTask.formatAddress(o["address"]),
                contactName = o.str("contact_name"),
                contactPhone = o.str("contact_phone") ?: o.str("customer_phone"),
                geoUri = o.str("geo_uri")?.takeIf { placed } ?: if (placed) "geo:$lat,$lng?q=$lat,$lng" else null,
                mapsUrl = o.str("maps_url")?.takeIf { placed },
            )
        }

        /** Offline fallback: a stage C task row as a stop (no ETA; the planned window instead). */
        fun fromTask(t: DeliveryTask, status: TaskStatus): RouteStop = RouteStop(
            taskId = t.id,
            seq = t.routeSeq,
            kind = t.kind,
            status = status,
            etaAt = null,
            scheduledAt = t.scheduledAt,
            windowEnd = t.windowEnd,
            lat = t.lat,
            lng = t.lng,
            geoFrom = null,
            orderNumber = t.orderNumber,
            customerName = t.customerName,
            jobSiteName = t.jobSiteName,
            address = t.address,
            contactName = t.contactName,
            contactPhone = t.phone,
            geoUri = if (t.lat != null && t.lng != null) "geo:${t.lat},${t.lng}?q=${t.lat},${t.lng}" else null,
            mapsUrl = null,
        )
    }
}

data class DriverRoute(
    val date: String?,
    val stops: List<RouteStop>,
    /** True while a stop is assigned, en route or arrived (location sharing would run). */
    val tracking: Boolean,
    val pingIntervalSeconds: Int,
) {
    /** The first open stop in route order: the one the driver works on next. */
    val next: RouteStop? get() = stops.firstOrNull { it.status.isOpen }

    companion object {
        const val DEFAULT_PING_SECONDS = 60

        fun parse(o: JsonObject): DriverRoute = DriverRoute(
            date = o.str("date"),
            stops = order((o["stops"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(RouteStop::from) }),
            tracking = o.bool("tracking") == true,
            pingIntervalSeconds = o.int("ping_interval_seconds") ?: DEFAULT_PING_SECONDS,
        )

        /**
         * Offline route from the driver's cached tasks: their own, not cancelled, scheduled on
         * [day] (start/end of the local day) or already in progress.
         */
        fun fromTasks(
            tasks: List<Pair<DeliveryTask, TaskStatus>>,
            me: String?,
            dayStart: Instant,
            dayEnd: Instant,
        ): DriverRoute {
            val stops = tasks
                .filter { (t, s) -> me != null && t.driverUserId == me && s != TaskStatus.CANCELLED }
                .filter { (t, s) ->
                    val at = t.scheduledAt
                    s == TaskStatus.EN_ROUTE || s == TaskStatus.ARRIVED || (at != null && !at.isBefore(dayStart) && at.isBefore(dayEnd))
                }
                .map { (t, s) -> RouteStop.fromTask(t, s) }
            val active = stops.any { it.status == TaskStatus.ASSIGNED || it.status == TaskStatus.EN_ROUTE || it.status == TaskStatus.ARRIVED }
            return DriverRoute(null, order(stops), active, DEFAULT_PING_SECONDS)
        }

        /** `route_seq` first (unsequenced last), then ETA / window start, then id for stability. */
        fun order(stops: List<RouteStop>): List<RouteStop> = stops.sortedWith(
            compareBy<RouteStop> { it.seq ?: Int.MAX_VALUE }
                .thenBy { it.etaAt ?: it.scheduledAt ?: Instant.MAX }
                .thenBy { it.taskId },
        )

        /**
         * Overlays the device's optimistic status (queued check-ins not yet synced) on the
         * server route so the list never jumps back after an offline check-in.
         */
        fun withLocalStatus(route: DriverRoute, local: Map<String, TaskStatus>): DriverRoute =
            route.copy(stops = route.stops.map { s -> local[s.taskId]?.let { s.copy(status = it) } ?: s })
    }
}

/**
 * The single next step the route list offers for a stop (§7 check-in buttons). "On my way" and
 * "Arrived" are queued straight from the list; completing or failing needs proof / a reason
 * and opens the stop.
 */
enum class RouteStep { ON_MY_WAY, ARRIVED, COMPLETE, NONE;

    companion object {
        fun of(status: TaskStatus): RouteStep = when (status) {
            TaskStatus.ASSIGNED -> ON_MY_WAY
            TaskStatus.EN_ROUTE -> ARRIVED
            TaskStatus.ARRIVED -> COMPLETE
            else -> NONE
        }
    }
}
