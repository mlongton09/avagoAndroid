package com.avago.core.data.sync

/**
 * The retry policy shared by every `sync_queue` drainer (`/sync/push`, the defect register and
 * the rentals P1/P2 outboxes), so the outboxes cannot drift apart.
 *
 * Only a failure the server answered counts as an attempt. A row that keeps failing for
 * [MAX_ATTEMPTS] answered attempts is parked until the user retries it. A request that never
 * reached the server (device offline, forced offline mode, DNS/socket/timeout) keeps the row
 * `pending` and does not use up the budget (`SyncQueueDao.markOfflineRetry`). A long offline
 * period therefore never turns queued work into "failed" rows.
 */
object QueueRetryPolicy {
    /** Answered failures after which a row is parked (iOS `resetErrorsToPending(maxAttempts: 10)`). */
    const val MAX_ATTEMPTS = 10L

    /** HTTP statuses worth retrying automatically: 408, 425, 429 and 5xx. */
    fun isTransientHttp(status: Int): Boolean =
        status == 408 || status == 425 || status == 429 || status >= 500

    /**
     * `NetworkResult.Error` codes for a request that got no HTTP answer: 0 = forced offline mode,
     * -1 = transport exception.
     */
    fun isNoResponse(code: Int): Boolean = code <= 0
}
