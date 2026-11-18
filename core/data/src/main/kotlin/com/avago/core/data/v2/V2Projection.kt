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
)
