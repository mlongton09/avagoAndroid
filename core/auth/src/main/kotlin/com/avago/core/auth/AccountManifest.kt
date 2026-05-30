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

    /**
     * Upsert from a server response: if the record exists, overwrite the
     * server-authoritative fields (accountName, role, userId) but preserve
     * locally-known fields (displayName, email, memberships). If it doesn't
     * exist, insert as-is.
     *
     * Mirrors iOS fetchAndStoreAllAccounts (lines 1324-1337 of IdentityManager.swift)
     * which starts from any existing record and only overwrites fields the
     * server returned. Critical for fixing stale "Unknown" entries — a prior
     * write that didn't include accountName must be upgradable when the
     * server provides one.
     */
    suspend fun upsertFromServer(account: AccountRecord) = withContext(Dispatchers.IO) {
        lock.write {
            val current = load(appContext).toMutableList()
            val idx = current.indexOfFirst { it.accountId == account.accountId }
            if (idx >= 0) {
                val existing = current[idx]
                current[idx] = existing.copy(
                    userId = account.userId ?: existing.userId,
                    accountName = account.accountName ?: existing.accountName,
                    role = account.role ?: existing.role,
                    // memberships/displayName/email left intact unless server
                    // explicitly supplied non-empty replacements
                    displayName = account.displayName?.takeIf { it.isNotBlank() } ?: existing.displayName,
                    email = account.email?.takeIf { it.isNotBlank() } ?: existing.email,
                    memberships = account.memberships.ifEmpty { existing.memberships },
                )
            } else {
                current.add(account)
            }
            save(appContext, current)
        }
    }

    suspend fun remove(accountId: String) = withContext(Dispatchers.IO) {
        lock.write { remove(appContext, accountId) }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        lock.write { save(appContext, emptyList()) }
    }

    /**
     * Reconcile the manifest against the server-authoritative set of account IDs
     * **for the given user**.
     *
     * Drops any record where `userId == forUserId` and the accountId isn't in
     * [serverAccountIds]. Records belonging to a *different* user (or with no
     * userId set — anonymous-only) and the [activeAccountId] are preserved.
     *
     * Scoping by userId mirrors iOS AccountManifest.reconcileNamedAccounts and
     * is critical when a parallel /accounts call uses a stale bearer token: we
     * must NOT delete the freshly signed-in user's accounts just because the
     * stale-token response listed someone else's account.
     *
     * Returns the list of accountIds that were removed so the caller can clear
     * their tokens / databases.
     */
    suspend fun reconcileNamed(
        forUserId: String,
        serverAccountIds: Set<String>,
        activeAccountId: String?,
    ): List<String> = withContext(Dispatchers.IO) {
        lock.write {
            val current = load(appContext)
            val (keep, drop) = current.partition { record ->
                // Preserve:
                //  • anonymous records (server doesn't know about them)
                //  • the currently active account (don't yank live session)
                //  • records the server confirmed for this user
                //  • records belonging to a *different* known user_id
                // Drop:
                //  • records owned by [forUserId] that the server didn't list
                //  • orphan records with userId == null and not anonymous
                //    (legacy entries from before reconcile was user-scoped —
                //    e.g. an "Unknown" account left behind by a stale-token
                //    /accounts response)
                if (record.isAnonymous) return@partition true
                if (record.accountId == activeAccountId) return@partition true
                if (record.accountId in serverAccountIds) return@partition true
                val owner = record.userId
                when {
                    owner == null -> false
                    owner == forUserId -> false
                    else -> true
                }
            }
            if (drop.isEmpty()) {
                emptyList()
            } else {
                save(appContext, keep)
                Timber.i(
                    "AccountManifest: reconcile (user=$forUserId) dropped ${drop.size} stale account(s): " +
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
