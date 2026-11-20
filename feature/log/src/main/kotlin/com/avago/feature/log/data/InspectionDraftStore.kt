package com.avago.feature.log.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.InspectionDraftRow
import com.avago.core.network.model.PutInspectionDraftRequest
import com.avago.feature.log.model.InspectionDraft
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val Context.inspectionDraftStore by preferencesDataStore(name = "inspection_drafts")

/**
 * Crash / process-death safe inspection drafts: a device copy written on every debounced change,
 * plus the caller's server draft (`/accounts/{a}/inspection-drafts`, one per asset and log) so
 * another device or the web app can resume. Server drafts are online-only; offline the device
 * copy is all there is and the next push catches up.
 */
@Singleton
class InspectionDraftStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceClient: AvagoServiceClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val store get() = context.inspectionDraftStore
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
            .onFailure { Timber.w(it, "[InspectionDraftStore] remove failed") }
    }

    /** The caller's newest server draft for this asset and log, or null (none, or offline). */
    suspend fun fetchServer(acct: String, assetId: String, logId: String?): InspectionDraft? {
        val rows = when (val r = serviceClient.getInspectionDrafts(acct, assetId, logId)) {
            is NetworkResult.Success -> r.data
            else -> {
                Timber.w("[InspectionDraftStore] server drafts unavailable: %s", r)
                return null
            }
        }
        val row = newestFor(rows, assetId, logId) ?: return null
        val draft = fromServer(row, acct) ?: return null
        serverIds[draft.key] = row.draftId
        return draft
    }

    /** Upserts [draft] as the caller's server draft; a failure leaves the device copy as the only one. */
    suspend fun pushServer(draft: InspectionDraft) {
        val assetId = draft.assetId ?: return
        val body = PutInspectionDraftRequest(assetId = assetId, logId = draft.logId, configId = draft.configId, payload = draft.toJson())
        when (val r = serviceClient.putInspectionDraft(draft.acct, body)) {
            is NetworkResult.Success -> serverIds[draft.key] = r.data.draftId
            else -> Timber.w("[InspectionDraftStore] server draft push failed: %s", r)
        }
    }

    /** Deletes the caller's server drafts for this asset and log (looked up when this device has not seen them). */
    suspend fun removeServer(acct: String, assetId: String, logId: String?) {
        val key = InspectionDraft.draftKey(acct, assetId, logId)
        val ids = serverIds.remove(key)?.let(::listOf)
            ?: (serviceClient.getInspectionDrafts(acct, assetId, logId) as? NetworkResult.Success)?.data
                ?.filter { it.assetId == assetId && it.logId == logId }?.map { it.draftId }
            ?: return
        ids.forEach { id ->
            val r = serviceClient.deleteInspectionDraft(acct, id)
            if (r !is NetworkResult.Success) Timber.w("[InspectionDraftStore] server draft delete failed: %s", r)
        }
    }

    internal companion object {
        /** The newest row for exactly this asset and log (a new-entry draft has no log). */
        fun newestFor(rows: List<InspectionDraftRow>, assetId: String, logId: String?): InspectionDraftRow? =
            rows.filter { it.assetId == assetId && it.logId == logId }.maxByOrNull { epochMillis(it.updatedAt) ?: 0L }

        /** The row's payload as a draft for this account, saved at the server's `updated_at`. */
        fun fromServer(row: InspectionDraftRow, acct: String): InspectionDraft? {
            val draft = InspectionDraft.fromJson(row.payload, acct) ?: return null
            val saved = epochMillis(row.updatedAt) ?: draft.savedAt
            return draft.copy(acct = acct, assetId = row.assetId, logId = row.logId, savedAt = saved)
        }

        private fun epochMillis(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
    }
}
