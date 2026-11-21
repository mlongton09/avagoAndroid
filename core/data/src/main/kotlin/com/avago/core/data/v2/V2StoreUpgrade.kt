package com.avago.core.data.v2

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * In-place upgrade of a store at an older released `user_version` (plan 6.4, L43), run by
 * [V2Database.open]: with `foreign_keys=OFF`, one `BEGIN IMMEDIATE` transaction runs every embedded
 * step up to the current version, then `PRAGMA foreign_key_check` must be empty (else ROLLBACK).
 * Synced rows, the outbox and every durable table are kept. The new DDL hash and the projection
 * versions minus the retired projections are written to meta, so neither a rebuild nor a
 * re-bootstrap follows.
 */
internal object V2StoreUpgrade {

    fun upgrade(conn: V2Connection, kind: V2StoreKind, from: Int, expectedSha: String) {
        val scripts = steps(kind, from).map(::migrationScript)
        conn.execute("PRAGMA foreign_keys = OFF")
        try {
            V2Database.transaction(conn) {
                try {
                    scripts.forEach { conn.executeScript(it) }
                } catch (e: V2SqlException) {
                    throw V2MigrationException("v2 $kind store upgrade from $from failed: ${e.message}", e)
                }
                verify(conn, kind, from)
                conn.execute(V2Database.META_DDL)
                V2Database.metaPut(conn, V2Database.META_DDL_SHA, expectedSha)
                if (kind == V2StoreKind.BUSINESS) {
                    V2Database.metaGet(conn, V2SyncEngine.META_PROJECTION_VERSIONS)?.let { stored ->
                        V2Database.metaPut(conn, V2SyncEngine.META_PROJECTION_VERSIONS, keptProjectionVersions(stored))
                    }
                }
            }
        } finally {
            conn.execute("PRAGMA foreign_keys = ON")
        }
    }

    /**
     * The stored projection versions without the projections that no longer have a mobile
     * table, i.e. what [V2SyncEngine] computes as its desired versions after the upgrade.
     */
    fun keptProjectionVersions(stored: String): String {
        val kept = V2SyncEngine.parseVersions(stored).filterKeys { name ->
            V2GeneratedSchema.projectionsByType[name]?.mobileTable != null
        }.toSortedMap()
        return JsonObject(kept.mapValues { JsonPrimitive(it.value) }).toString()
    }

    /** The embedded steps from [from] up to the current schema version, in order. */
    private fun steps(kind: V2StoreKind, from: Int): List<V2MigrationStep> {
        val steps = mutableListOf<V2MigrationStep>()
        var version = from
        while (version < V2Contract.V2_DB_SCHEMA_VERSION) {
            val step = V2GeneratedSchema.migrationSteps.firstOrNull { it.store == kind && it.from == version }
                ?: throw V2MigrationException("v2 $kind store: no upgrade step from user_version $version")
            steps += step
            version = step.to
        }
        return steps
    }

    /** Inside the upgrade transaction: no dangling references and the target `user_version` reached. */
    private fun verify(conn: V2Connection, kind: V2StoreKind, from: Int) {
        val violations = conn.query("PRAGMA foreign_key_check")
        if (violations.isNotEmpty()) {
            throw V2MigrationException("v2 $kind store upgrade from $from left ${violations.size} dangling references")
        }
        val reached = conn.scalarLong("PRAGMA user_version")
        if (reached != V2Contract.V2_DB_SCHEMA_VERSION.toLong()) {
            throw V2MigrationException("v2 $kind store upgrade from $from ended at user_version $reached")
        }
    }

    private fun migrationScript(step: V2MigrationStep): String {
        val bytes = V2StoreUpgrade::class.java.getResourceAsStream(step.resource)?.use { it.readBytes() }
            ?: error("missing migration resource ${step.resource}")
        check(V2Canonical.sha256Hex(bytes) == step.sha256) {
            "migration resource ${step.resource} does not match V2GeneratedSchema " +
                "(rerun scripts/gen_v2_contract.py)"
        }
        return bytes.toString(Charsets.UTF_8)
    }
}
