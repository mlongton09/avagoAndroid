package com.avago.core.sync

import com.avago.core.data.db.entity.SyncQueueEntity

/**
 * A feature-owned outbox drained by [SyncEngine] alongside `/sync/push` (additive hook).
 *
 * Rows of [entityTypes] stay in the shared `sync_queue` (so pending-sync badges and
 * retry bookkeeping work as for every other entity) but are **not** sent to
 * `/sync/push`; instead [drain] is called after the main push with those rows (FIFO by
 * `created_at`), and [pull] is called after the main pull. Implementations are bound
 * into a Hilt `Set<ExternalOutbox>` with `@IntoSet` from their feature module.
 *
 * Both calls must be bounded (no endless retries) and must not throw for ordinary
 * per-row failures — mark rows `error` / `conflict` in `sync_queue` instead.
 */
interface ExternalOutbox {
    /** `sync_queue.entity_type` values owned by this outbox. */
    val entityTypes: Set<String>

    /** Push [items] (already filtered to [entityTypes]); returns how many succeeded. */
    suspend fun drain(accountId: String, items: List<SyncQueueEntity>): Int

    /** Incremental pull of the feature's feeds (non-fatal for the main sync). */
    suspend fun pull(accountId: String)
}
