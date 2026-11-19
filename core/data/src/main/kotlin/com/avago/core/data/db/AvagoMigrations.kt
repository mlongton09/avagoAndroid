package com.avago.core.data.db

import androidx.room.migration.Migration

/** Current [AvagoDatabase] schema version. Bump together with a new entry in [AVAGO_MIGRATIONS]. */
const val AVAGO_DB_VERSION = 21

/**
 * Every explicit [AvagoDatabase] migration, one per version step from 4 to [AVAGO_DB_VERSION].
 * `AvagoMigrationsTest` fails if a step is missing, so a version bump without a migration is
 * caught in CI instead of reaching users.
 */
internal val AVAGO_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
    MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
    MIGRATION_19_20, MIGRATION_20_21,
)

/**
 * The only versions that may still be recreated destructively: v1–v3 existed solely in
 * development builds of 2026-05-21/22 and have no migration. Any other upgrade must run
 * [AVAGO_MIGRATIONS], so pending `sync_queue` (outbox) rows are never silently dropped.
 */
internal val AVAGO_DESTRUCTIVE_FROM_VERSIONS: IntArray = intArrayOf(1, 2, 3)
