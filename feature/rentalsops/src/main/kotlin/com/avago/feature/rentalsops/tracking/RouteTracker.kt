package com.avago.feature.rentalsops.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.avago.core.auth.IdentityManager
import com.avago.feature.rentalsops.data.ApiResult
import com.avago.feature.rentalsops.data.RentalOpsApi
import com.avago.feature.rentalsops.domain.PingBuffer
import com.avago.feature.rentalsops.domain.PingPolicy
import com.avago.feature.rentalsops.domain.RoutePing
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.routeTrackingStore: DataStore<Preferences> by preferencesDataStore(name = "rentalops_route_tracking")

/**
 * Wave 2 G5 driver location sharing (AvagoSvc docs/rentals-api-wave2-routing-api-webhooks.md §7):
 * the driver's opt-in, the offline ping queue (one file per account, survives process death)
 * and batch upload to `POST routing/pings`. [RouteTrackingService] feeds it fixes while a route
 * is active.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RouteTracker @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val identity: IdentityManager,
    private val api: RentalOpsApi,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var buffer: PingBuffer? = null
    private var bufferAccount: String? = null
    private var lastFlushMs: Long? = null
    private var retryAtMs: Long? = null
    private var lastSentMs: Long? = null

    private val _running = MutableStateFlow(false)

    /** True while [RouteTrackingService] is in the foreground. */
    val running: StateFlow<Boolean> = _running

    /** Server cadence, updated from `my-route` and `next_interval_seconds`. */
    @Volatile
    var baseIntervalSeconds: Int? = null

    internal fun setRunning(on: Boolean) {
        _running.value = on
    }

    private fun key(accountId: String) = booleanPreferencesKey("share:$accountId")

    /** The driver's opt-in for the active account (default off). */
    val sharingEnabled: Flow<Boolean> = identity.activeAccountId.flatMapLatest { a ->
        if (a == null) flowOf(false) else ctx.routeTrackingStore.data.map { it[key(a)] == true }
    }

    suspend fun isSharingEnabled(): Boolean = sharingEnabled.first()

    suspend fun setSharing(on: Boolean) {
        val a = identity.activeAccountId.value ?: return
        ctx.routeTrackingStore.edit { it[key(a)] = on }
    }

    fun hasForegroundLocation(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** "Allow all the time": lets Android restart sharing after it stopped the app mid-route. */
    fun hasBackgroundLocation(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    /** Starts the foreground service; call from a visible screen (Android 12+ start rules). */
    fun start(): Boolean {
        if (!hasForegroundLocation()) return false
        return runCatching {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RouteTrackingService::class.java))
            true
        }.onFailure { Timber.w(it, "[RouteTracker] start failed") }.getOrDefault(false)
    }

    /** Stops the service (it sends what is queued first). */
    fun stop() {
        if (!_running.value) return
        runCatching { ctx.startService(Intent(ctx, RouteTrackingService::class.java).setAction(RouteTrackingService.ACTION_STOP)) }
            .onFailure { ctx.stopService(Intent(ctx, RouteTrackingService::class.java)) }
    }

    // ---- offline queue -------------------------------------------------------------------

    private fun file(accountId: String): File {
        val safe = accountId.filter { it.isLetterOrDigit() || it == '-' }.take(64)
        return File(ctx.filesDir, "rentalops_route_pings_$safe.json")
    }

    private suspend fun load(accountId: String): PingBuffer {
        buffer?.takeIf { bufferAccount == accountId }?.let { return it }
        val list = withContext(Dispatchers.IO) {
            val f = file(accountId)
            if (!f.exists()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString(ListSerializer(RoutePing.serializer()), f.readText()) }.getOrDefault(emptyList())
            }
        }
        return PingBuffer(list).also {
            buffer = it
            bufferAccount = accountId
        }
    }

    private suspend fun persist(accountId: String, b: PingBuffer) = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(accountId)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(RoutePing.serializer()), b.snapshot()))
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }.onFailure { Timber.w(it, "[RouteTracker] persist failed") }
    }

    /** Queues a fix; false when it is invalid or within 10 s of the previous one. */
    suspend fun record(p: RoutePing): Boolean = mutex.withLock {
        val a = identity.activeAccountId.value ?: return false
        val b = load(a)
        val added = b.add(p, lastSentMs)
        if (added) persist(a, b)
        added
    }

    enum class Flush { SENT, IDLE, OFFLINE, FORBIDDEN }

    /**
     * Sends the oldest queued pings (≤ 50), at most every 15 s and never before a 429's
     * `retry_after`. A rejected (4xx) batch is dropped so one bad ping cannot block the queue.
     */
    suspend fun flush(nowMs: Long = System.currentTimeMillis()): Flush = mutex.withLock {
        val a = identity.activeAccountId.value ?: return Flush.IDLE
        val b = load(a)
        if (b.dropExpired(nowMs) > 0) persist(a, b)
        if (!PingPolicy.shouldFlush(b.size, nowMs, lastFlushMs, retryAtMs)) return Flush.IDLE
        val batch = b.nextBatch()
        lastFlushMs = nowMs
        when (val r = api.postPings(a, RoutePing.batchBody(batch))) {
            is ApiResult.Ok -> {
                b.ack(batch)
                persist(a, b)
                lastSentMs = batch.last().recordedAtMs
                retryAtMs = null
                (r.body["next_interval_seconds"] as? JsonPrimitive)?.intOrNull?.let { baseIntervalSeconds = it }
                Flush.SENT
            }
            is ApiResult.Http -> when {
                r.status == 429 -> {
                    retryAtMs = nowMs + PingPolicy.retryAfterSeconds(r.details) * 1000
                    Flush.IDLE
                }
                r.status == 401 || r.status == 403 -> Flush.FORBIDDEN
                r.status in 400..499 -> {
                    Timber.w("[RouteTracker] pings rejected (${r.status} ${r.code}); dropping ${batch.size}")
                    b.ack(batch)
                    persist(a, b)
                    Flush.IDLE
                }
                else -> Flush.OFFLINE
            }
            is ApiResult.Transport -> Flush.OFFLINE
        }
    }
}
