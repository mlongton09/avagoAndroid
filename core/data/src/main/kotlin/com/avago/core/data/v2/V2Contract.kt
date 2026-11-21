package com.avago.core.data.v2

/**
 * Hook for the schema-v2 contract generator (plan §13.1, §15.6).
 *
 * The generator in AvagoSvc (`schema/contract/gen/`, run with
 * `python schema\contract\gen\contract.py generate`) emits, per mobile database:
 *  - `schema/contract/out/sqlite/business.sql` (per area: `out/sqlite/business/NNN_<area>.sql`) →
 *    [V2StorePaths.V2_BUSINESS_DB]; `out/sqlite/chat.sql` → [V2StorePaths.v2ChatDatabaseName]. STRICT
 *    SQLite DDL, applied with `foreign_keys=ON`, WAL, one transaction, then
 *    `PRAGMA user_version` = [V2_DB_SCHEMA_VERSION] (fresh store; L12). Older released stores are
 *    upgraded in place by the `out/sqlite/migrations/<db>/` scripts (plan 6.4, L43);
 *  - `schema/contract/out/kotlin/<Area>.kt` and `ContractCommon.kt` — kotlinx-serialization DTOs
 *    in package `com.avago.contract.v2`.
 *
 * `scripts/gen_v2_contract.py` copies the SQLite DDL into `core/data` resources and writes
 * [V2GeneratedSchema] (projection registry + DDL hashes). The stores are opened on the bundled
 * SQLite driver (STRICT tables need SQLite ≥ 3.37, newer than the platform SQLite on minSdk 26) by
 * [V2StoreFactory]; the Kotlin DTOs are not vendored yet (the engine applies projection bodies
 * generically). Bump [CLIENT_CONTRACT_VERSION] only together with the contract.
 */
object V2Contract {
    /** Contract generation this client speaks; sent as `X-Contract-Version` and probed via E1. */
    const val CLIENT_CONTRACT_VERSION = 1

    /** `PRAGMA user_version` of the v2 business and chat databases (L43 mobile schema version). */
    const val V2_DB_SCHEMA_VERSION = V2GeneratedSchema.MOBILE_SCHEMA_VERSION

    /** Relative location of generated outputs in the AvagoSvc repository. */
    const val GENERATED_SQLITE_DIR = "schema/contract/out/sqlite"
    const val GENERATED_KOTLIN_DIR = "schema/contract/out/kotlin"
}
