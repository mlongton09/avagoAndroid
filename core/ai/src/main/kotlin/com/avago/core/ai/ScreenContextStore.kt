package com.avago.core.ai

import android.content.Context
import android.content.SharedPreferences
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
 * Process-wide owner of the current [ScreenContext].
 *
 * Per-screen hooks call [setAssetScope] / [setWorkOrderScope] / [setPartScope] /
 * [setListScreen] with their scope. [snapshotForRequest] stamps now/locale/tz and is
 * the single source of truth at AI-request time.
 *
 * The recent-entities ring (max 3, most-recent-first) is persisted to SharedPreferences
 * so it survives backgrounding — matching iOS ScreenContextStore.swift.
 *
 * Mirrors iOS ScreenContextStore.swift.
 */
@Singleton
class ScreenContextStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityManager: IdentityManager,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("screen_context", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val KEY_RECENT_ENTITIES = "recent_entities"
        const val MAX_RECENT = 3
        const val ENVELOPE_BYTE_CAP = 8 * 1024
    }

    private var _snapshot: ScreenContext = emptyContext()

    // ── Scope setters ─────────────────────────────────────────────────────────

    fun setAssetScope(assetId: String?, label: String? = null) {
        _snapshot = emptyContext().copy(screen = "asset_detail", currentAssetId = assetId)
        if (assetId != null) pushRecentEntity(RecentEntity(kind = "asset", id = assetId, label = label))
    }

    fun setLogEntryScope(assetId: String?, label: String? = null) {
        _snapshot = emptyContext().copy(screen = "log_entry", currentAssetId = assetId)
        if (assetId != null) pushRecentEntity(RecentEntity(kind = "asset", id = assetId, label = label))
    }

    fun setWorkOrderScope(woId: String?, label: String? = null) {
        _snapshot = emptyContext().copy(screen = "wo_detail", currentWoId = woId)
        if (woId != null) pushRecentEntity(RecentEntity(kind = "wo", id = woId, label = label))
    }

    fun setPartScope(partId: String?, label: String? = null) {
        _snapshot = emptyContext().copy(screen = "part_detail", currentPartId = partId)
        if (partId != null) pushRecentEntity(RecentEntity(kind = "part", id = partId, label = label))
    }

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

    fun pushRecentEntity(entity: RecentEntity) {
        val current = loadRecentEntities().toMutableList()
        current.removeAll { it.kind == entity.kind && it.id == entity.id }
        current.add(0, entity)
        saveRecentEntities(current.take(MAX_RECENT))
    }

    fun currentContext(): ScreenContext = snapshotForRequest()

    /**
     * Stamp now/locale/tz and return the value-typed context for the AI request.
     * Enforces the 8 KB cap; sets [ScreenContext.truncated] if over the cap.
     */
    fun snapshotForRequest(): ScreenContext {
        val accountId = identityManager.activeAccountId.value ?: ""
        var s = _snapshot.copy(
            accountId = accountId.ifEmpty { _snapshot.accountId },
            now = Instant.now().toString(),
            locale = Locale.getDefault().toLanguageTag().lowercase(),
            tz = ZoneId.systemDefault().id,
            recentEntities = emptyList(), // omitted per server-side audit
        )
        try {
            if (json.encodeToString(s).toByteArray().size > ENVELOPE_BYTE_CAP) {
                s = s.copy(truncated = true)
                Timber.w("[ScreenContextStore] envelope over 8 KB cap — marked truncated")
            }
        } catch (_: Exception) { }
        return s
    }

    fun clear() {
        _snapshot = emptyContext()
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun emptyContext() = ScreenContext(
        accountId = identityManager.activeAccountId.value ?: "",
        client = ScreenContext.ClientInfo(platform = "android"),
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
