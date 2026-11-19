package com.avago.core.data.v2

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.BinEntity
import com.avago.core.data.db.entity.LocationEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The v2 stores of the signed-in user/account, published by the sync coordinator (core/sync) once
 * the [V2StoreGate] is open and the stores are open. Null while v2 is off.
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
 * Location and bin reads for feature screens: the v2 store when it is active for the account
 * ([V2LocationReader]), otherwise the legacy Room DAOs. Screens use this instead of
 * `DatabaseFactory.get(..).locationDao()/binDao()` so the v2 switch is one place.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LocationBinReads @Inject constructor(
    private val databases: DatabaseFactory,
    private val active: V2ActiveStores,
) {
    fun observeLocations(accountId: String): Flow<List<LocationEntity>> = active.current.flatMapLatest { stores ->
        if (stores != null && stores.identity.accountId == accountId) {
            stores.locations.observeLocations(accountId)
        } else {
            flow { emitAll(databases.get(accountId).locationDao().observeAll(accountId)) }
        }
    }

    suspend fun location(accountId: String, id: String): LocationEntity? {
        val stores = active.forAccount(accountId) ?: return databases.get(accountId).locationDao().getById(id)
        return stores.locations.location(accountId, id)
    }

    fun observeBins(accountId: String): Flow<List<BinEntity>> = active.current.flatMapLatest { stores ->
        if (stores != null && stores.identity.accountId == accountId) {
            stores.locations.observeBins()
        } else {
            flow { emitAll(databases.get(accountId).binDao().observeAll(accountId)) }
        }
    }

    suspend fun bin(accountId: String, id: String): BinEntity? {
        val stores = active.forAccount(accountId) ?: return databases.get(accountId).binDao().getById(id)
        return stores.locations.bin(id)
    }
}
