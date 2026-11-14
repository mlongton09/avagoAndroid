package com.avago.feature.rentalsops.sync

import com.avago.feature.rentalsops.data.ApiResult

/**
 * How a queued rentals action is settled after one push attempt.
 *
 * - [Done]: server accepted (or an idempotent replay already applied) → remove from queue.
 * - [Rejected]: the server refused on business grounds (validation, conflict, permission) →
 *   keep the row as `conflict`, tell the user, never auto-retry.
 * - [Reassigned]: `403 not_assigned` — the task was given to another driver while this one
 *   was offline. Per the stage C contract the action must NOT be retried: drop it (and the
 *   later queued events of the same task), tell the driver, and re-pull `my-tasks` (the
 *   reassignment arrives there as a tombstone).
 * - [Retry]: transport/server/auth-refresh trouble → `error` with attempts+1; bounded by
 *   [MAX_ATTEMPTS]. Stage C is deployed, so 404/405 now mean a real "not found" (rejected).
 */
sealed class DrainOutcome {
    data object Done : DrainOutcome()
    data class Rejected(val reason: String) : DrainOutcome()
    data class Reassigned(val reason: String) : DrainOutcome()
    data class Retry(val reason: String) : DrainOutcome()

    companion object {
        /** After this many transient failures a row is parked until the user retries it. */
        const val MAX_ATTEMPTS = 10L

        const val NOT_ASSIGNED = "not_assigned"

        fun classify(r: ApiResult): DrainOutcome = when (r) {
            is ApiResult.Ok -> Done
            is ApiResult.Transport -> Retry(r.message)
            is ApiResult.Http -> when {
                r.status == 401 -> Retry("unauthorized")
                r.status == 403 && r.code == NOT_ASSIGNED -> Reassigned(r.message.ifBlank { NOT_ASSIGNED })
                r.status == 408 || r.status == 425 || r.status == 429 -> Retry("HTTP ${r.status}")
                r.status >= 500 -> Retry("HTTP ${r.status}")
                else -> Rejected(r.code?.let { "$it: ${r.message}" } ?: r.message.ifBlank { "HTTP ${r.status}" })
            }
        }
    }
}
