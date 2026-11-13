package com.avago.feature.log.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.feature.log.model.InspectionDraft
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val Context.inspectionDraftStore by preferencesDataStore(name = "inspection_drafts")

/**
 * Crash / process-death safe inspection drafts: a local DataStore copy (written on every debounced
 * change) plus a best-effort mirror to the service's `inspection-drafts` API so another device or
 * the web app can resume. Server calls degrade to no-ops when the endpoint isn't deployed (404/405).
 */
@Singleton
class InspectionDraftStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceClient: AvagoServiceClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val store get() = context.inspectionDraftStore

    @Volatile private var serverMissing = false
    private val serverIds = ConcurrentHashMap<String, String>()

    suspend fun load(key: String): InspectionDraft? = runCatching {
        val raw = store.data.first()[stringPreferencesKey(key)] ?: return null
        InspectionDraft.fromJson(json.parseToJsonElement(raw) as? JsonObject)
    }.onFailure { Timber.w(it, "[InspectionDraftStore] load failed") }.getOrNull()

    suspend fun save(draft: InspectionDraft) {
        runCatching { store.edit { it[stringPreferencesKey(draft.key)] = draft.toJson().toString() } }
            .onFailure { Timber.w(it, "[InspectionDraftStore] save failed") }
    }

    suspend fun remove(key: String) {
        runCatching { store.edit { it.remove(stringPreferencesKey(key)) } }
        val id = serverIds.remove(key) ?: return
        val acct = key.substringBefore(':')
        if (!serverMissing) runCatching { serviceClient.deleteInspectionDraft(acct, id) }
    }

    /** Newest server draft for this asset / log, or null (endpoint missing, offline, none). */
    suspend fun fetchServer(acct: String, assetId: String, logId: String?): InspectionDraft? {
        if (serverMissing) return null
        val res = runCatching { serviceClient.getInspectionDrafts(acct, assetId, logId) }.getOrNull() ?: return null
        if (res is NetworkResult.Error && (res.code == 404 || res.code == 405)) { serverMissing = true; return null }
        val rows = (res as? NetworkResult.Success)?.data ?: return null
        val match = rows.mapNotNull { it as? JsonObject }
            .filter { (it["asset_id"] as? JsonPrimitive)?.content == assetId }
            .filter { row -> (row["log_id"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content == logId }
            .maxByOrNull { (it["updated_at"] as? JsonPrimitive)?.content.orEmpty() } ?: return null
        val draftId = (match["draft_id"] as? JsonPrimitive)?.content
        val updatedMs = runCatching { Instant.parse((match["updated_at"] as JsonPrimitive).content).toEpochMilli() }.getOrNull()
        val draft = InspectionDraft.fromJson(match["payload"] as? JsonObject, acct)
            ?.copy(acct = acct, assetId = assetId, logId = logId)
            ?.let { d -> if (updatedMs != null) d.copy(savedAt = updatedMs) else d }
            ?: return null
        if (draftId != null) serverIds[draft.key] = draftId
        return draft
    }

    suspend fun pushServer(draft: InspectionDraft) {
        if (serverMissing || draft.assetId == null) return
        val body = JsonObject(
            mapOf(
                "asset_id" to JsonPrimitive(draft.assetId),
                "log_id" to (draft.logId?.let { JsonPrimitive(it) } ?: JsonNull),
                "config_id" to (draft.configId?.let { JsonPrimitive(it) } ?: JsonNull),
                "payload" to draft.toJson(),
            ),
        )
        when (val res = runCatching { serviceClient.putInspectionDraft(draft.acct, body) }.getOrNull()) {
            is NetworkResult.Success -> (res.data["draft_id"] as? JsonPrimitive)?.content?.let { serverIds[draft.key] = it }
            is NetworkResult.Error -> if (res.code == 404 || res.code == 405) serverMissing = true
            else -> Unit
        }
    }
}
