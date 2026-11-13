package com.avago.feature.workorders.repository

import com.avago.core.data.FeatureFlags
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.feature.workorders.model.DEFAULT_SLA_POLICY
import com.avago.feature.workorders.model.DEFAULT_WO_CODES
import com.avago.feature.workorders.model.SlaWindow
import com.avago.feature.workorders.model.WO_KEY_CLOSEOUT
import com.avago.feature.workorders.model.WO_KEY_HOLD
import com.avago.feature.workorders.model.WoCloseout
import com.avago.feature.workorders.model.WoCloseoutOptions
import com.avago.feature.workorders.model.WoCode
import com.avago.feature.workorders.model.WoCodeKind
import com.avago.feature.workorders.model.WoTimerState
import com.avago.feature.workorders.model.mergeWoAttributesJson
import com.avago.feature.workorders.model.parseSlaPolicy
import com.avago.feature.workorders.model.parseWoAttributes
import com.avago.feature.workorders.model.parseWoCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Work-order v3 helpers (contract §2/§3): close-out codes, SLA policy, server timer and
 * attribute writes. Every optional endpoint falls back to local defaults when the service
 * doesn't support it (404/405/501) or the device is offline.
 */
@Singleton
class WoV3Service @Inject constructor(
    private val client: AvagoServiceClient,
    private val repository: WorkOrderRepository,
    private val featureFlags: FeatureFlags,
) {
    private val codesCache = ConcurrentHashMap<String, Map<WoCodeKind, List<WoCode>>>()
    private val slaCache = ConcurrentHashMap<String, Map<String, SlaWindow>>()
    private val closeoutCache = ConcurrentHashMap<String, WoCloseoutOptions>()

    suspend fun codes(accountId: String): Map<WoCodeKind, List<WoCode>> {
        codesCache[accountId]?.let { return it }
        val body = when (val r = runCatching { client.getWoCodes(accountId) }.getOrNull()) {
            is NetworkResult.Success -> r.data
            else -> null
        }
        val out = WoCodeKind.entries.associateWith { parseWoCodes(body, it) }
        if (body != null) codesCache[accountId] = out
        return out.ifEmpty { DEFAULT_WO_CODES }
    }

    suspend fun slaPolicy(accountId: String): Map<String, SlaWindow> {
        slaCache[accountId]?.let { return it }
        val r = runCatching { client.getWoSlaPolicy(accountId) }.getOrNull()
        return if (r is NetworkResult.Success) parseSlaPolicy(r.data).also { slaCache[accountId] = it }
        else DEFAULT_SLA_POLICY
    }

    /**
     * Close-out opt-ins from account features `wo_closeout_require_signature` /
     * `wo_closeout_require_downtime` (default false). Falls back to the synced config flags offline.
     */
    suspend fun closeoutOptions(accountId: String): WoCloseoutOptions {
        closeoutCache[accountId]?.let { return it }
        val r = runCatching { client.getAccountFeatures(accountId) }.getOrNull()
        if (r is NetworkResult.Success) {
            return WoCloseoutOptions(
                requireSignature = r.data.woCloseoutRequireSignature,
                requireDowntime = r.data.woCloseoutRequireDowntime,
            ).also { closeoutCache[accountId] = it }
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                WoCloseoutOptions(
                    requireSignature = featureFlags.woCloseoutRequireSignature,
                    requireDowntime = featureFlags.woCloseoutRequireDowntime,
                )
            }.getOrDefault(WoCloseoutOptions())
        }
    }

    /** Server timer for the calling user, or null when the endpoint is unavailable. */
    suspend fun timer(accountId: String, woId: String): WoTimerState? =
        timerResult(runCatching { client.getWoTimer(accountId, woId) }.getOrNull())

    /** Starts/stops the server timer; null means "use the local fallback". */
    suspend fun timerAction(accountId: String, woId: String, action: String): WoTimerState? =
        timerResult(runCatching { client.postWoTimer(accountId, woId, action) }.getOrNull())

    private fun timerResult(r: NetworkResult<JsonElement>?): WoTimerState? = when (r) {
        is NetworkResult.Success -> WoTimerState.fromJson(r.data)
        is NetworkResult.Error -> {
            if (r.code !in UNSUPPORTED) Timber.w("[WoV3Service] timer call failed (${r.code})")
            null
        }
        else -> null
    }

    /**
     * Merges [patch] into the WO's attributes (unknown keys preserved), stores locally and
     * PATCHes `attributes` to the service. Returns the updated entity.
     */
    suspend fun patchAttributes(
        accountId: String,
        wo: WorkOrderEntity,
        patch: Map<String, JsonElement?>,
        transform: (WorkOrderEntity) -> WorkOrderEntity = { it },
    ): WorkOrderEntity {
        val now = System.currentTimeMillis()
        val merged = mergeWoAttributesJson(wo.attributes, patch)
        val updated = transform(wo.copy(attributes = merged, updatedAt = now))
        repository.upsert(accountId, updated)
        val body = JsonObject(mapOf("attributes" to parseWoAttributes(merged)))
        when (val r = runCatching { client.patchWorkOrder(accountId, wo.woId, body) }.getOrNull()) {
            is NetworkResult.Success -> Unit
            else -> Timber.w("[WoV3Service] attributes PATCH deferred to sync ($r)")
        }
        return updated
    }

    /** Stores the close-out (and clears any hold), then marks the WO completed. */
    suspend fun complete(
        accountId: String,
        wo: WorkOrderEntity,
        closeout: WoCloseout,
        transform: (WorkOrderEntity) -> WorkOrderEntity = { it },
    ): WorkOrderEntity {
        val now = System.currentTimeMillis()
        val updated = patchAttributes(
            accountId,
            wo,
            mapOf(WO_KEY_CLOSEOUT to closeout.toJson(), WO_KEY_HOLD to null),
        ) {
            transform(
                it.copy(
                    status = STATUS_COMPLETED,
                    completedAt = now,
                    timerStartedAt = null,
                    failureCode = closeout.failureCode ?: it.failureCode,
                    completionNotes = closeout.resolutionNotes ?: it.completionNotes,
                ),
            )
        }
        runCatching { client.transitionWorkOrderStatus(accountId, wo.woId, STATUS_COMPLETED) }
            .onFailure { Timber.w(it, "[WoV3Service] complete transition deferred to sync") }
        return updated
    }

    companion object {
        const val STATUS_COMPLETED = "completed"
        val UNSUPPORTED = setOf(404, 405, 501, -1)
    }
}
