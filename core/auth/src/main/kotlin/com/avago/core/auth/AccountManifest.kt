package com.avago.core.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Reads and writes accounts_manifest.json in the app's filesDir.
 *
 * Plain-text JSON (not encrypted) — tokens are stored separately in SecureTokenStore.
 *
 * Available both as a Hilt-injected singleton (preferred) and as a static object
 * via the legacy companion-style helpers used by IdentityManager before Phase 13.
 */
@Singleton
class AccountManifest @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val lock = ReentrantReadWriteLock()

    // ---------------------------------------------------------------------------
    // Injected API (Phase 13+)
    // ---------------------------------------------------------------------------

    fun allAccounts(): List<AccountRecord> = lock.read { load(appContext) }

    fun activeAccountId(): String? = lock.read {
        // The active account is tracked by IdentityManager; the manifest does not
        // store it separately.  Callers that need it should read IdentityManager.
        // Returning null here is intentional — don't add state that IdentityManager owns.
        null
    }

    suspend fun add(account: AccountRecord) = withContext(Dispatchers.IO) {
        lock.write { addOrUpdate(appContext, account) }
    }

    suspend fun remove(accountId: String) = withContext(Dispatchers.IO) {
        lock.write { remove(appContext, accountId) }
    }

    // ---------------------------------------------------------------------------
    // Static-style helpers (kept for IdentityManager backward compat)
    // ---------------------------------------------------------------------------

    companion object {
        private const val FILE_NAME = "accounts_manifest.json"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

        fun load(context: Context): List<AccountRecord> {
            val f = file(context)
            if (!f.exists()) return emptyList()
            return try {
                json.decodeFromString(f.readText())
            } catch (e: Exception) {
                Timber.w(e, "AccountManifest: failed to parse manifest, returning empty list")
                emptyList()
            }
        }

        fun save(context: Context, accounts: List<AccountRecord>) {
            file(context).writeText(json.encodeToString(accounts))
        }

        fun addOrUpdate(context: Context, record: AccountRecord) {
            val current = load(context).toMutableList()
            val idx = current.indexOfFirst { it.accountId == record.accountId }
            if (idx >= 0) {
                current[idx] = record
            } else {
                current.add(record)
            }
            save(context, current)
        }

        fun remove(context: Context, accountId: String) {
            val updated = load(context).filter { it.accountId != accountId }
            save(context, updated)
        }
    }
}
