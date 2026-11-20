package com.avago.core.sync.v2

import android.content.Context
import com.avago.core.auth.IdentityManager
import com.avago.core.data.v2.ServerV2Capability
import com.avago.core.data.v2.SharedPreferencesV2FlagStorage
import com.avago.core.data.v2.V2ActiveStores
import com.avago.core.data.v2.V2Api
import com.avago.core.data.v2.V2ApiUploader
import com.avago.core.data.v2.V2CommandOutbox
import com.avago.core.data.v2.V2Flag
import com.avago.core.data.v2.V2OutboxException
import com.avago.core.data.v2.V2StoreFactory
import com.avago.core.data.v2.V2StoreGate
import com.avago.core.data.v2.V2StoreIdentity
import com.avago.core.data.v2.V2Stores
import com.avago.core.data.v2.V2SyncEngine
import com.avago.core.data.v2.V2SyncResult
import com.avago.core.data.v2.V2Uuid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives one v2 cycle for the active user/account: open (and publish) the v2 stores, pull
 * ([V2SyncEngine]), then drain the command outbox ([V2CommandOutbox]). Pull and flush are
 * serialised by one mutex, as the outbox requires. Inert unless [V2StoreGate] is open (v2 flag on
 * and the legacy reset complete), so the default build never touches it.
 *
 * A `426` from either side records [ServerV2Capability.INCOMPATIBLE], which raises the M-02
 * update-required screen through [V2Flag.updateRequired].
 */
@Singleton
class V2SyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gate: V2StoreGate,
    private val flag: V2Flag,
    private val factory: V2StoreFactory,
    private val active: V2ActiveStores,
    private val identityManager: IdentityManager,
    private val blobs: V2HttpBlobTransport,
    private val transport: V2HttpTransport,
) {
    private val mutex = Mutex()
    @Volatile private var session: Session? = null

    private class Session(val stores: V2Stores, val api: V2Api, val engine: V2SyncEngine, val outbox: V2CommandOutbox)

    private val prefs by lazy {
        context.getSharedPreferences(SharedPreferencesV2FlagStorage.PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** The outbox of the active session, for feature write paths; null until a cycle opened one. */
    suspend fun outbox(): V2CommandOutbox? = mutex.withLock { session?.outbox }

    /**
     * The API of the active session, for online reads such as file downloads; opens the session
     * when no cycle has run yet. Null when v2 is gated off or nobody is signed in.
     */
    suspend fun api(): V2Api? {
        if (!gate.isOpen()) return null
        val accountId = identityManager.getActiveAccountId() ?: return null
        val userId = identityManager.activeUserId.value ?: return null
        session?.takeIf { it.stores.identity.accountId == accountId && it.stores.identity.userId == userId }
            ?.let { return it.api }
        return mutex.withLock { sessionFor()?.api }
    }

    /** One pull + flush. Returns null when v2 is gated off or nobody is signed in. */
    suspend fun runOnce(): V2SyncResult? = mutex.withLock {
        if (!gate.isOpen()) return@withLock null
        val current = try {
            sessionFor() ?: return@withLock null
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.e(e, "V2SyncCoordinator: could not open the v2 stores")
            return@withLock V2SyncResult.Failed(e)
        }
        var result = current.engine.sync()
        if (result == V2SyncResult.DeviceIdRejected) {
            Timber.w("V2SyncCoordinator: device id owned elsewhere; generating a new one")
            current.api.deviceId = rotateDeviceId()
            result = current.engine.sync()
        }
        when (result) {
            V2SyncResult.UpgradeRequired -> flag.recordServerCapability(ServerV2Capability.INCOMPATIBLE)
            is V2SyncResult.Failed -> Timber.e(result.error, "V2SyncCoordinator: sync failed")
            else -> Timber.d("V2SyncCoordinator: sync → $result")
        }
        if (result is V2SyncResult.Synced || result is V2SyncResult.RetryLater) flush(current)
        result
    }

    /** Drops the local copy and bootstraps again (queued commands are kept). Null when v2 is off. */
    suspend fun resync(): V2SyncResult? {
        val requested = mutex.withLock {
            if (!gate.isOpen()) return@withLock false
            val current = sessionFor() ?: return@withLock false
            current.engine.requestBootstrap()
            true
        }
        return if (requested) runOnce() else null
    }

    /** Sends queued commands now (no pull); used right after a local write. */
    suspend fun flushNow() = mutex.withLock {
        if (!gate.isOpen()) return@withLock
        val current = try {
            sessionFor() ?: return@withLock
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.e(e, "V2SyncCoordinator: could not open the v2 stores")
            return@withLock
        }
        flush(current)
    }

    private suspend fun flush(current: Session) {
        try {
            val flushed = current.outbox.flush()
            Timber.d(
                "V2SyncCoordinator: outbox acked=${flushed.acknowledged.size} rejected=${flushed.rejected.size} " +
                    "deferred=${flushed.deferred} offline=${flushed.offline}",
            )
        } catch (e: V2OutboxException.UpgradeRequired) {
            flag.recordServerCapability(ServerV2Capability.INCOMPATIBLE)
        } catch (e: V2OutboxException.DeviceRevoked) {
            Timber.w("V2SyncCoordinator: device revoked during flush")
        }
    }

    private suspend fun sessionFor(): Session? {
        val accountId = identityManager.getActiveAccountId() ?: return dropSession()
        val userId = identityManager.activeUserId.value ?: return dropSession()
        session?.takeIf { it.stores.identity.accountId == accountId && it.stores.identity.userId == userId }
            ?.let { return it }
        val deviceId = deviceId()
        val stores = factory.get(V2StoreIdentity(accountId = accountId, userId = userId, deviceId = deviceId))
        val api = V2Api(transport, accountId, deviceId)
        val next = Session(
            stores = stores,
            api = api,
            engine = V2SyncEngine(api, stores.business, stores.chat, appVersion = appVersion()),
            outbox = V2CommandOutbox(stores.business, api, stores.identity, uploader = V2ApiUploader(api, blobs)),
        )
        session = next
        active.publish(stores)
        return next
    }

    private fun dropSession(): Session? {
        session = null
        active.publish(null)
        return null
    }

    /** The v2 device id (client-generated UUIDv7, sync-wire.md "Devices"); kept across the reset. */
    private fun deviceId(): String = prefs.getString(KEY_DEVICE_ID, null) ?: rotateDeviceId()

    private fun rotateDeviceId(): String = V2Uuid.v7().also { prefs.edit().putString(KEY_DEVICE_ID, it).commit() }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    private companion object {
        const val KEY_DEVICE_ID = "v2_device_id"
    }
}
