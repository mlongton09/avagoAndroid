@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.avago.core.data

import android.content.Context
import com.avago.core.data.db.entity.ConfigEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central feature-flag registry backed by the per-account config DB table.
 *
 * Config rows follow the scope/key convention:
 *   scope = the prefix before the first dot in the key ("feature", "limit")
 *   key   = the full dotted key string (e.g. "feature.scout_enabled")
 *
 * Boolean flags are stored as "true"/"false". Int flags as decimal strings.
 *
 * [activeAccountId] is a [StateFlow] sourced from [IdentityManager] and
 * provided via Hilt so that core:data does not depend on core:auth.
 */
@Singleton
class FeatureFlags @Inject constructor(
    private val databaseFactory: DatabaseFactory,
    private val activeAccountId: StateFlow<String?>,
    @ApplicationContext private val context: Context,
) {

    // ---------------------------------------------------------------------------
    // Boolean feature flags
    // ---------------------------------------------------------------------------

    /** Whether the AI Scout feature is enabled for this account. */
    val scoutEnabled: Boolean get() = getFlag("feature.scout_enabled", default = false)

    /** Whether the inspection checklist feature is enabled. */
    val inspectionEnabled: Boolean get() = getFlag("feature.inspection_enabled", default = false)

    /** Whether the dispatch board is visible. */
    val dispatchEnabled: Boolean get() = getFlag("feature.dispatch_enabled", default = true)

    /** Whether multi-account switching is enabled. */
    val multiAccountEnabled: Boolean get() = getFlag("feature.multi_account_enabled", default = true)

    /** Whether the label printing / label template feature is enabled. */
    val labelPrintingEnabled: Boolean get() = getFlag("feature.label_printing_enabled", default = false)

    /** Whether cycle count floor mode is enabled. */
    val cycleCountFloorEnabled: Boolean get() = getFlag("feature.cycle_count_floor_enabled", default = false)

    // ---------------------------------------------------------------------------
    // Integer limit flags
    // ---------------------------------------------------------------------------

    /** Max assets allowed for this account tier. -1 = unlimited. */
    val maxAssets: Int get() = getIntFlag("limit.max_assets", default = -1)

    /** Max work orders allowed per month. -1 = unlimited. */
    val maxWorkOrdersPerMonth: Int get() = getIntFlag("limit.max_wo_per_month", default = -1)

    // ---------------------------------------------------------------------------
    // Reactive observation
    // ---------------------------------------------------------------------------

    /**
     * Observe a boolean flag as a [Flow]. Re-emits whenever the DB row changes
     * (e.g. after a server config sync) or the active account switches.
     */
    @Suppress("OPT_IN_USAGE")
    fun observeFlag(key: String, default: Boolean): Flow<Boolean> {
        val scope = scopeForKey(key)
        return activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) {
                    flowOf(default)
                } else {
                    runBlocking { databaseFactory.get(accountId) }
                        .configDao()
                        .observeByKey(scope, key)
                        .map { entity -> entity?.value?.toBooleanStrictOrNull() ?: default }
                }
            }
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun getFlag(key: String, default: Boolean): Boolean {
        val entity = queryConfig(key) ?: return default
        return entity.value.toBooleanStrictOrNull() ?: default
    }

    private fun getIntFlag(key: String, default: Int): Int {
        val entity = queryConfig(key) ?: return default
        return entity.value.toIntOrNull().also { parsed ->
            if (parsed == null) {
                Timber.w("FeatureFlags: could not parse int for key=%s value=%s", key, entity.value)
            }
        } ?: default
    }

    /**
     * Synchronous config lookup. Uses [runBlocking] — FeatureFlags property
     * accessors are intentionally non-suspend. Room's WAL mode keeps reads fast.
     */
    private fun queryConfig(key: String): ConfigEntity? {
        val accountId = activeAccountId.value ?: return null
        val scope = scopeForKey(key)
        return try {
            runBlocking { databaseFactory.get(accountId).configDao().getByKey(scope, key) }
        } catch (e: Exception) {
            Timber.e(e, "FeatureFlags: error reading key=%s", key)
            null
        }
    }

    /**
     * Derives the Room [scope] from the key's prefix.
     *   "feature.scout_enabled" → "feature"
     *   "limit.max_assets"      → "limit"
     *   "plain_key"             → "system"  (fallback)
     */
    private fun scopeForKey(key: String): String =
        key.substringBefore('.', missingDelimiterValue = "system")
}
