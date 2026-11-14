package com.avago.feature.rentalsops.sync

import com.avago.feature.rentalsops.data.ApiResult

/**
 * How a queued rentals action is settled after one push attempt.
 *
 * - [Done]: server accepted (or an idempotent replay already applied) → remove from queue.
 * - [Rejected]: the server refused on business grounds (validation, conflict, permission) →
 *   keep the row as `conflict`, tell the user, never auto-retry.
 * - [Retry]: transport/server/auth-refresh trouble or the endpoint is not deployed yet
 *   (stage C rollout) → `error` with attempts+1; bounded by [MAX_ATTEMPTS].
 */
sealed class DrainOutcome {
    data object Done : DrainOutcome()
    data class Rejected(val reason: String) : DrainOutcome()
    data class Retry(val reason: String) : DrainOutcome()

    companion object {
        /** After this many transient failures a row is parked until the user retries it. */
        const val MAX_ATTEMPTS = 10L

        fun classify(r: ApiResult): DrainOutcome = when (r) {
            is ApiResult.Ok -> Done
            is ApiResult.Transport -> Retry(r.message)
            is ApiResult.Http -> when {
                r.status == 401 -> Retry("unauthorized")
                r.status == 404 || r.status == 405 || r.status == 501 -> Retry("endpoint unavailable (${r.status})")
                r.status == 408 || r.status == 425 || r.status == 429 -> Retry("HTTP ${r.status}")
                r.status >= 500 -> Retry("HTTP ${r.status}")
                else -> Rejected(r.code?.let { "$it: ${r.message}" } ?: r.message.ifBlank { "HTTP ${r.status}" })
            }
        }
    }
}
