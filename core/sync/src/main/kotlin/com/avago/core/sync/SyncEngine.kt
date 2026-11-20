package com.avago.core.sync

import com.avago.core.auth.IdentityManager
import com.avago.core.data.v2.V2SyncResult
import com.avago.core.sync.v2.V2SyncCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's sync cycle: one schema-v2 pull followed by an outbox flush ([V2SyncCoordinator]).
 * Every trigger (the periodic and one-off [SyncWorker], connectivity, foreground, pull-to-refresh,
 * FCM `sync_hint`) ends here. With v2 gated off ([com.avago.core.data.v2.V2StoreGate]) a cycle
 * is a no-op.
 *
 * Retryable outcomes (offline, 5xx, `Retry-After`) map to [SyncResult.Failed] so the worker backs
 * off; terminal ones (426, revoked device, a typed 403 recorded on
 * [com.avago.core.auth.AccessDenialMonitor]) to [SyncResult.Partial] so it does not retry.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val coordinator: V2SyncCoordinator,
    private val identity: IdentityManager,
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _accountGoneEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Account ids whose device the server revoked (`401 device_revoked`): sign out and wipe. */
    val accountGoneEvents: SharedFlow<String> = _accountGoneEvents.asSharedFlow()

    @Volatile private var retryNotBefore = 0L

    /** Time left of the server's `Retry-After` window; other clients (chat WS) wait it out too. */
    val rateLimitedMsRemaining: Long
        get() = (retryNotBefore - System.currentTimeMillis()).coerceAtLeast(0L)

    fun clearRateLimitBackoff() {
        retryNotBefore = 0L
    }

    /** One pull + flush. */
    suspend fun sync(): SyncResult = run { coordinator.runOnce() }

    /** Discards the local copy and bootstraps again; queued changes are kept and still sent. */
    suspend fun resync(): SyncResult = run { coordinator.resync() }

    private suspend fun run(cycle: suspend () -> V2SyncResult?): SyncResult {
        if (rateLimitedMsRemaining > 0) {
            Timber.d("SyncEngine: inside the server retry window; skipping")
            return SyncResult.Partial(0, 0)
        }
        _state.value = SyncState.Pulling
        val result = try {
            cycle()
        } catch (e: CancellationException) {
            _state.value = SyncState.Idle
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.e(e, "SyncEngine: sync cycle failed")
            _state.value = SyncState.Error(e.message ?: e.javaClass.simpleName)
            return SyncResult.Failed(e)
        }
        return map(result)
    }

    private fun map(result: V2SyncResult?): SyncResult {
        _state.value = SyncState.Idle
        return when (result) {
            null -> SyncResult.Partial(0, 0)
            is V2SyncResult.Synced -> SyncResult.Success
            is V2SyncResult.Offline -> SyncResult.Failed(java.io.IOException(result.message))
            is V2SyncResult.RetryLater -> {
                result.retryAfterMs?.let { retryNotBefore = System.currentTimeMillis() + it }
                SyncResult.Failed(IllegalStateException("server busy: ${result.reason}"))
            }
            is V2SyncResult.Failed -> {
                _state.value = SyncState.Error(result.error.message ?: result.error.javaClass.simpleName)
                SyncResult.Failed(result.error)
            }
            V2SyncResult.DeviceRevoked -> {
                identity.getActiveAccountId()?.let { _accountGoneEvents.tryEmit(it) }
                SyncResult.Partial(0, 0)
            }
            // The coordinator recorded the denial; the app asks the user to switch or sign out.
            is V2SyncResult.AccessDenied, V2SyncResult.UpgradeRequired, V2SyncResult.DeviceIdRejected -> SyncResult.Partial(0, 0)
        }
    }
}
