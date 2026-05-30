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

    suspend fun addIfMissing(account: AccountRecord) = withContext(Dispatchers.IO) {
        lock.write {
            val current = load(appContext)
            if (current.none { it.accountId == account.accountId }) {
                save(appContext, current + account)
            }
        }
    }

    suspend fun remove(accountId: String) = withContext(Dispatchers.IO) {
        lock.write { remove(appContext, accountId) }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        lock.write { save(appContext, emptyList()) }
    }

    /**
     * Reconcile the manifest against the server-authoritative set of account IDs.
     *
     * Removes any non-anonymous account whose id isn't in [serverAccountIds] and
     * isn't the [activeAccountId] (we never yank the live session out from under
     * the user — sign-out is the caller's job if the active account is stale).
     *
     * Anonymous accounts are preserved unconditionally — they were never on the
     * server, so the server can't tell us about them.
     *
     * Returns the list of accountIds that were removed so the caller can clear
     * their tokens / databases.
     */
    suspend fun reconcileNamed(
        serverAccountIds: Set<String>,
        activeAccountId: String?,
    ): List<String> = withContext(Dispatchers.IO) {
        lock.write {
            val current = load(appContext)
            val (keep, drop) = current.partition { record ->
                record.isAnonymous ||
                    record.accountId == activeAccountId ||
                    record.accountId in serverAccountIds
            }
            if (drop.isEmpty()) {
                emptyList()
            } else {
                save(appContext, keep)
                Timber.i(
                    "AccountManifest: reconcile dropped ${drop.size} stale account(s): " +
                        drop.joinToString { it.accountId }
                )
                drop.map { it.accountId }
            }
        }
    }

    fun deduplicateAnonymousAccounts(activeAccountId: String) {
        lock.write {
            val current = load(appContext)
            val nonAnon = current.filter { !it.isAnonymous }
            val activeAnon = current.filter { it.isAnonymous && it.accountId == activeAccountId }
            val deduplicated = nonAnon + activeAnon
            if (deduplicated.size < current.size) {
                save(appContext, deduplicated)
            }
        }
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
