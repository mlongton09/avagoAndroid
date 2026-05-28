package com.avago.core.ai

import android.content.SharedPreferences
import android.content.Context
import androidx.core.content.edit
import com.avago.core.auth.IdentityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide owner of the current [ScoutContext].
 *
 * Per-screen hooks call [setAssetScope] / [setWorkOrderScope] / [setPartScope] /
 * [setListScreen] with their scope. [snapshotForRequest] stamps now/locale/tz and is the
 * single source of truth at AI-request time.
 *
 * The recent-entities ring (max 3, most-recent-first) is persisted to SharedPreferences
 * so it survives backgrounding — matching iOS ScreenContextStore.swift.
 *
 * Mirrors iOS ScreenContextStore.swift.
 */
@Singleton
class ScoutContextHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityManager: IdentityManager,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("scout_context", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val KEY_RECENT_ENTITIES = "recent_entities"
        const val MAX_RECENT = 3
        const val ENVELOPE_BYTE_CAP = 8 * 1024
    }

    private var _snapshot: ScoutContext = emptyContext()

    // ── Scope setters ─────────────────────────────────────────────────────────

    fun setAssetScope(assetId: String?, label: String? = null) {
        _snapshot = emptyContext().copy(
            screen = "asset_detail",
            currentAssetId = assetId,
        )
        if (assetId != null) pushRecentEntity(RecentEntity(kind = "asset", id = assetId, label = label))
    }

    fun setLogEntryScope(assetId: String?, label: String? = null) {
        _snapshot = emptyContext().copy(
            screen = "log_entry",
            currentAssetId = assetId,
        )
        if (assetId != null) pushRecentEntity(RecentEntity(kind = "asset", id = assetId, label = label))
    }

    fun setWorkOrderScope(woId: String?, label: String? = null) {
        _snapshot = emptyContext().copy(
            screen = "wo_detail",
            currentWoId = woId,
        )
        if (woId != null) pushRecentEntity(RecentEntity(kind = "wo", id = woId, label = label))
    }

    fun setPartScope(partId: String?, label: String? = null) {
        _snapshot = emptyContext().copy(
            screen = "part_detail",
            currentPartId = partId,
        )
        if (partId != null) pushRecentEntity(RecentEntity(kind = "part", id = partId, label = label))
    }

    /**
     * Bare-screen contributor for list/dashboard views with no single entity scope.
     * Lets the intent classifier see "wo_list", "asset_list", etc. and bias routing.
     */
    fun setListScreen(
        screen: String,
        currentAssetId: String? = null,
        currentWoId: String? = null,
        currentPartId: String? = null,
    ) {
        _snapshot = emptyContext().copy(
            screen = screen,
            currentAssetId = currentAssetId,
            currentWoId = currentWoId,
            currentPartId = currentPartId,
        )
    }

    fun setCurrentScreen(screen: String) {
        _snapshot = _snapshot.copy(screen = screen)
    }

    /** Push or promote an entity to the front of the MRU ring. */
    fun pushRecentEntity(entity: RecentEntity) {
        val current = loadRecentEntities().toMutableList()
        current.removeAll { it.kind == entity.kind && it.id == entity.id }
        current.add(0, entity)
        val trimmed = current.take(MAX_RECENT)
        saveRecentEntities(trimmed)
    }

    /** Snapshot the current context for inclusion in a Scout request. */
    fun currentContext(): ScoutContext = snapshotForRequest()

    /**
     * Stamp now/locale/tz and return the value-typed context for the AI request.
     * Enforces the 8 KB cap; sets [ScoutContext.truncated] if over the cap.
     */
    fun snapshotForRequest(): ScoutContext {
        val accountId = identityManager.activeAccountId.value ?: ""
        val now = Instant.now().toString()
        val locale = Locale.getDefault().toLanguageTag().lowercase()
        val tz = ZoneId.systemDefault().id

        var s = _snapshot.copy(
            accountId = accountId.ifEmpty { _snapshot.accountId },
            now = now,
            locale = locale,
            tz = tz,
            // Recent entities omitted from AI envelope per server-side audit (nothing reads them).
            recentEntities = emptyList(),
        )

        // 8 KB cap enforcement
        try {
            val encoded = json.encodeToString(s)
            if (encoded.toByteArray().size > ENVELOPE_BYTE_CAP) {
                s = s.copy(truncated = true)
                Timber.w("[ScoutContextHost] envelope over 8 KB cap — marked truncated")
            }
        } catch (_: Exception) { }

        return s
    }

    fun clear() {
        _snapshot = emptyContext()
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun emptyContext() = ScoutContext(
        accountId = identityManager.activeAccountId.value ?: "",
        client = ScoutContext.ClientInfo(platform = "android"),
    )

    private fun loadRecentEntities(): List<RecentEntity> {
        val raw = prefs.getString(KEY_RECENT_ENTITIES, null) ?: return emptyList()
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    private fun saveRecentEntities(entities: List<RecentEntity>) {
        try {
            prefs.edit { putString(KEY_RECENT_ENTITIES, json.encodeToString(entities)) }
        } catch (_: Exception) { }
    }
}
