package com.avago.feature.chat.realtime

import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.ChatSyncOp
import com.avago.core.sync.ApplicationScope
import com.avago.feature.chat.data.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundSyncCoordinator @Inject constructor(
    private val client: AvagoServiceClient,
    private val repository: ChatRepository,
    private val identity: IdentityManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    /**
     * Run a full delta sync cycle: page through /chat/sync?cursor= until has_more=false.
     * Idempotent — concurrent calls return immediately without doing double-work.
     * Mirrors iOS BackgroundSyncCoordinator.runDelta().
     */
    suspend fun runDelta() {
        if (!mutex.tryLock()) {
            Timber.d("[ChatSync] already running — skipping")
            return
        }
        try {
            val accountId = identity.activeAccountId.value ?: return
            var cursor = repository.getChatSyncCursor(accountId)
            var hasMore = true
            while (hasMore) {
                when (val result = client.chatSync(cursor)) {
                    is NetworkResult.Success -> {
                        val response = result.data
                        for (op in response.ops) {
                            repository.applyChatSyncOp(accountId, op)
                        }
                        cursor = response.cursor
                        hasMore = response.has_more && response.ops.isNotEmpty()
                        if (cursor != null) {
                            repository.saveChatSyncCursor(accountId, cursor)
                            // Ack the cursor so server knows we consumed these ops
                            client.chatSyncAck(cursor)
                        }
                    }
                    is NetworkResult.Error -> {
                        Timber.w("[ChatSync] delta sync error ${result.code}: ${result.message}")
                        hasMore = false
                    }
                    is NetworkResult.Unauthorized -> {
                        Timber.w("[ChatSync] unauthorized")
                        hasMore = false
                    }
                }
            }
            Timber.d("[ChatSync] delta sync complete, cursor=$cursor")
        } catch (e: Exception) {
            Timber.e(e, "[ChatSync] delta sync exception")
        } finally {
            mutex.unlock()
        }
    }
}
