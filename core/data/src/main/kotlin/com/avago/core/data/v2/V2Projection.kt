package com.avago.core.data.v2

/** Sync feed a projection travels on (sync-wire.md: the global stream is served first). */
enum class V2Stream(val wire: String) {
    GLOBAL("global"),
    ACCOUNT("account"),
    ;

    companion object {
        fun fromWire(raw: String): V2Stream? = entries.firstOrNull { it.wire == raw }
    }
}

/** Which v2 database holds a mobile table (plan §4.1: business and chat are separate files). */
enum class V2StoreKind { BUSINESS, CHAT }

/**
 * One row of `platform.sync_projections` (generated into [V2GeneratedSchema.projections]; the
 * server also returns it from `/v2/capabilities`).
 */
data class V2Projection(
    val entityType: String,
    val stream: V2Stream,
    val perUser: Boolean,
    /** Server key columns, in `entity_key` order. */
    val keyColumns: List<String>,
    /** Mobile table, or null when the projection is server-only (items are ignored). */
    val mobileTable: String?,
    val store: V2StoreKind?,
    val applyRank: Int,
    val schemaVersion: Int,
    /** Windowed history (`history_days` in capabilities); null: every row is kept. */
    val historyWindow: V2HistoryWindow? = null,
)

/**
 * A bounded history projection (sync-wire.md "History windows"): open rows and rows whose
 * [endColumn] (epoch millis) is less than [days] old are delivered. Older rows age out silently,
 * so the client prunes them itself.
 */
data class V2HistoryWindow(val days: Int, val endColumn: String) {
    fun cutoffMillis(nowMillis: Long): Long = nowMillis - days * MILLIS_PER_DAY

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
