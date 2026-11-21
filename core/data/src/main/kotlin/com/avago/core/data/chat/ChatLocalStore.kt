package com.avago.core.data.chat

import com.avago.core.data.v2.V2ActiveStores
import com.avago.core.data.v2.V2Database
import com.avago.core.data.v2.V2Stores
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Chat persistence of one user/account: the [cache] and the ordered [outbox] in its chat file. */
class ChatStore internal constructor(val database: V2Database, val userId: String, val accountId: String) {
    val cache = ChatCache(database)
    val outbox = ChatOutboxQueue(database, userId)

    companion object {
        /** Prepares the client-only cache table on [database] (idempotent) and wraps it. */
        suspend fun open(database: V2Database, userId: String, accountId: String): ChatStore {
            database.write { tx -> ChatCache.DDL.forEach { tx.execute(it) } }
            return ChatStore(database, userId, accountId)
        }
    }
}

/**
 * The chat store of the signed-in user/account: the chat file `chat-<user>-<account>.sqlite` of the
 * v2 stores published by [V2ActiveStores] (L42). Null until the stores are open (v2 off, signed
 * out, or still opening), so chat persistence follows the same gate as every other store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ChatLocalStore @Inject constructor(private val active: V2ActiveStores) {
    private val mutex = Mutex()
    private var opened: ChatStore? = null

    /** The open chat store of [accountId], or null when that account's stores are not open. */
    suspend fun forAccount(accountId: String): ChatStore? = active.forAccount(accountId)?.let { of(it) }

    /** The chat store of [accountId] as the stores open, switch and close. */
    fun observe(accountId: String): Flow<ChatStore?> =
        active.current
            .map { stores -> stores?.takeIf { it.identity.accountId == accountId } }
            .distinctUntilChanged()
            .map { stores -> stores?.let { of(it) } }

    /** [observe] flattened: [query] on the open store, [empty] while there is none. */
    fun <T> observeWith(accountId: String?, empty: T, query: (ChatStore) -> Flow<T>): Flow<T> =
        if (accountId == null) {
            flowOf(empty)
        } else {
            observe(accountId).flatMapLatest { store -> store?.let(query) ?: flowOf(empty) }
        }

    private suspend fun of(stores: V2Stores): ChatStore = mutex.withLock {
        opened?.takeIf { it.database === stores.chat }
            ?: ChatStore.open(stores.chat, stores.identity.userId, stores.identity.accountId).also { opened = it }
    }
}
