package com.avago.core.data.v2

/**
 * Hook for the schema-v2 contract generator (plan §13.1, §15.6).
 *
 * The generator in AvagoSvc (`schema/contract/gen/`, run with
 * `python schema\contract\gen\contract.py generate`) emits, per mobile database:
 *  - `schema/contract/out/sqlite/business.sql` (per area: `out/sqlite/business/NNN_<area>.sql`) →
 *    [V2StorePaths.V2_BUSINESS_DB]; `out/sqlite/chat.sql` → [V2StorePaths.V2_CHAT_DB]. STRICT
 *    SQLite DDL, applied with `foreign_keys=ON`, WAL, one transaction, then
 *    `PRAGMA user_version = 1` (fresh store, no legacy migration; L12);
 *  - `schema/contract/out/kotlin/<Area>.kt` and `ContractCommon.kt` — kotlinx-serialization DTOs
 *    in package `com.avago.contract.v2`.
 *
 * When this scaffold was written the generator had just landed with only the `core` and
 * `places_parties` areas, so nothing is imported yet and [AvagoV2Database] carries only its
 * marker entity. STRICT tables need SQLite ≥ 3.37, newer than the platform SQLite on minSdk 26,
 * so the import requires the bundled driver first (see [AvagoV2DatabaseFactory]). Then add the
 * generated tables to [AvagoV2Database] (schema version stays 1 until the first v2 release) and
 * bump [CLIENT_CONTRACT_VERSION] only together with the contract.
 */
object V2Contract {
    /** Contract generation this client speaks; sent as `X-Contract-Version` and probed via E1. */
    const val CLIENT_CONTRACT_VERSION = 1

    /** Room/`PRAGMA user_version` of the v2 business database. */
    const val V2_DB_SCHEMA_VERSION = 1

    /** Relative location of generated outputs in the AvagoSvc repository. */
    const val GENERATED_SQLITE_DIR = "schema/contract/out/sqlite"
    const val GENERATED_KOTLIN_DIR = "schema/contract/out/kotlin"
}
