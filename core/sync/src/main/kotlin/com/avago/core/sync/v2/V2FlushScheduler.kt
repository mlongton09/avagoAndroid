package com.avago.core.sync.v2

import com.avago.core.data.v2.V2FlushRequester
import com.avago.core.sync.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flushes the v2 outbox shortly after a local write. Requests coalesce (conflated channel), so a
 * burst of writes costs one flush; offline flushes simply leave the commands queued for the next
 * sync trigger.
 */
@Singleton
class V2FlushScheduler @Inject constructor(
    private val coordinator: V2SyncCoordinator,
    @ApplicationScope private val scope: CoroutineScope,
) : V2FlushRequester {
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            requests.consumeAsFlow().collect {
                try {
                    coordinator.flushNow()
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Timber.e(e, "V2FlushScheduler: flush failed")
                }
            }
        }
    }

    override fun requestFlush() {
        requests.trySend(Unit)
    }
}
