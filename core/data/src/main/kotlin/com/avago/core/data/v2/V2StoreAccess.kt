package com.avago.core.data.v2

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The v2 stores of the signed-in user/account, published by the sync coordinator (core/sync) once
 * the [V2StoreGate] is open and the stores are open. Null while nobody is signed in.
 */
@Singleton
class V2ActiveStores @Inject constructor() {
    private val _current = MutableStateFlow<V2Stores?>(null)
    val current: StateFlow<V2Stores?> = _current

    fun publish(stores: V2Stores?) {
        _current.value = stores
    }

    fun forAccount(accountId: String): V2Stores? = _current.value?.takeIf { it.identity.accountId == accountId }
}

/**
 * Repository access to the business store of the signed-in account ([V2ActiveStores]). Every
 * observation also watches `local_overlays` so optimistic command effects and their rollback show
 * immediately. Before the stores are published (sign-in, first launch) observers get [empty].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class V2StoreAccess @Inject constructor(private val active: V2ActiveStores) {

    fun <T> observe(tables: Set<String>, empty: T, query: (V2Connection) -> T): Flow<T> =
        active.current.flatMapLatest { stores ->
            stores?.business?.observe(tables + OVERLAYS, query) ?: flowOf(empty)
        }.distinctUntilChanged()

    suspend fun <T> read(empty: T, block: (V2Connection) -> T): T =
        active.current.value?.business?.read(block) ?: empty

    /** The open stores, or null when nobody is signed in or v2 has not opened them yet. */
    fun current(): V2Stores? = active.current.value

    private companion object {
        const val OVERLAYS = "local_overlays"
    }
}
