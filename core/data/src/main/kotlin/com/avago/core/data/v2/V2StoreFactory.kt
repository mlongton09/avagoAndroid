package com.avago.core.data.v2

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Store gate (plan §14.4 step 2): v2 stores open only when v2 is enabled and the legacy reset has
 * completed. The app runs in one process (no `android:process` components), so this process-local
 * check plus running the reset before any store/worker starts is the whole gate.
 */
class V2StoreGate(private val flag: V2Flag, private val marker: V2ResetMarker) {
    fun isOpen(): Boolean = flag.isEnabled() && marker.isComplete
}

/** The business and chat databases of one user/account/device. */
class V2Stores(val identity: V2StoreIdentity, val business: V2Database, val chat: V2Database) {
    fun close() {
        runCatching { business.close() }
        runCatching { chat.close() }
    }
}

/**
 * Opens the per-user/account v2 stores ([V2StorePaths]) on the bundled SQLite driver (STRICT
 * tables need SQLite ≥ 3.37; minSdk 26 ships older). A file owned by another user/account (or
 * with another store layout version) is a disposable cache (L12): it is deleted and rebuilt, then
 * re-bootstraps. A file built from an older contract DDL migrates in place and keeps its outbox.
 */
@Singleton
class V2StoreFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gate: V2StoreGate,
) {
    private val mutex = Mutex()
    private val instances = mutableMapOf<String, V2Stores>()

    suspend fun get(identity: V2StoreIdentity): V2Stores = mutex.withLock {
        check(gate.isOpen()) { "v2 store is gated: v2 disabled or legacy reset not complete" }
        val key = key(identity.accountId, identity.userId)
        instances[key]?.takeIf { it.identity == identity }?.let { return@withLock it }
        instances.remove(key)?.close()
        val dir = V2StorePaths.v2Directory(context.filesDir, identity.accountId, identity.userId)
        val stores = V2Stores(
            identity,
            openOrRebuild(File(dir, V2StorePaths.V2_BUSINESS_DB), V2StoreKind.BUSINESS, identity),
            openOrRebuild(File(dir, V2StorePaths.V2_CHAT_DB), V2StoreKind.CHAT, identity),
        )
        instances[key] = stores
        stores
    }

    /** Returns the already-open stores for the account, if any (never opens). */
    suspend fun peek(accountId: String, userId: String): V2Stores? = mutex.withLock { instances[key(accountId, userId)] }

    suspend fun closeAll() = mutex.withLock {
        instances.values.forEach { it.close() }
        instances.clear()
    }

    private fun openOrRebuild(file: File, kind: V2StoreKind, identity: V2StoreIdentity): V2Database {
        file.parentFile?.mkdirs()
        return try {
            open(file, kind, identity)
        } catch (e: V2SchemaMismatchException) {
            Timber.w("V2StoreFactory: rebuilding ${file.name}: ${e.message}")
            deleteDatabase(file)
            open(file, kind, identity)
        }
    }

    private fun open(file: File, kind: V2StoreKind, identity: V2StoreIdentity): V2Database {
        val conn = BundledV2Connection(file.absolutePath)
        return try {
            V2Database.open(conn, kind, identity)
        } catch (e: Throwable) {
            runCatching { conn.close() }
            throw e
        }
    }

    private fun key(accountId: String, userId: String) = "$accountId/$userId"

    companion object {
        fun deleteDatabase(file: File) {
            V2StorePaths.SQLITE_SIDECARS.forEach { File(file.path + it).delete() }
        }
    }
}
