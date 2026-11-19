package com.avago.feature.log.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avago.feature.log.model.InspectionDraft
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.inspectionDraftStore by preferencesDataStore(name = "inspection_drafts")

/**
 * Crash / process-death safe inspection drafts, kept on the device (written on every debounced
 * change). The v2 contract has no draft entity, so drafts are not shared across devices.
 */
@Singleton
class InspectionDraftStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val store get() = context.inspectionDraftStore

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
}
