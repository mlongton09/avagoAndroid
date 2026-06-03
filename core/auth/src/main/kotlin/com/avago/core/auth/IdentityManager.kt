package com.avago.core.auth

import android.content.Context
import com.avago.core.data.CrashDiagnostics
import com.avago.core.data.DatabaseFactory
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.RefreshFailedHandler
import com.avago.core.network.getOrThrow
import com.avago.core.network.NetworkException
import com.avago.core.network.UnauthorizedException
import com.avago.core.network.model.DeviceUpdateRequest
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Owns user identity, authentication tokens, and the active account.
 *
 * ─────────────────────────────────────────────────────────────────────
 * Sign-in contract (mirrors iOS IdentityManager.swift)
 * ─────────────────────────────────────────────────────────────────────
 * The sequence in [signInWithFirebase] is brittle and order-sensitive.
 * Each step exists because a prior bug required it:
 *
 *   1. POST /auth/firebase → receive access + refresh tokens.
 *   2. tokenStore.storeTokens(...)  — persist to encrypted prefs.
 *   3. client.clearBearerTokenCache() — REQUIRED. Ktor's bearer
 *      AuthProvider caches the most-recent token in memory; without
 *      this, the first post-sign-in request reuses the prior session's
 *      token (often anonymous or another account's), causing 403s and
 *      "Farmer mark not found" symptoms. See AvagoServiceClient
 *      .clearBearerTokenCache for the public-API implementation —
 *      NEVER use reflection here (lint guard #2).
 *   4. fetchAndStoreAllAccounts() — GET /accounts. This MUST hit the
 *      server's write pool (enforced server-side by lint in
 *      avagosvc/.github/workflows/deploy.yml) because read replicas
 *      can lag by hundreds of ms and miss memberships that were
 *      created seconds ago during onboarding.
 *   5. For each returned account, AccountManifest.upsertFromServer(...)
 *      — preserves client-side fields (displayName, email) and
 *      overwrites server-authoritative fields (accountName, role,
 *      userId). Pure insert would clobber "Farmer mark" → "Unknown"
 *      on re-sign-in. See AccountManifest.upsertFromServer kdoc.
 *   6. Emit _signInEvents — AvagoApplication observes this to start
 *      ChatRealtimeClient and trigger initial sync.
 *
 * Sign-out contract:
 *   1. tokenStore.clearTokens(accountId)
 *   2. client.clearBearerTokenCache()  — again, REQUIRED.
 *   3. AccountManifest.remove(accountId)
 *   4. Emit _signOutEvents — AvagoApplication observes and disconnects
 *      the chat WebSocket.
 *
 * ─────────────────────────────────────────────────────────────────────
 * Hilt injection note
 * ─────────────────────────────────────────────────────────────────────
 * AvagoServiceClient is injected via Provider<> (not direct) to break
 * the cycle: IdentityManager → AvagoServiceClient → HttpClient →
 * TokenProvider (SecureTokenStore). Provider<> defers the .get() until
 * after Hilt has finished constructing the graph.
 */
@Singleton
class IdentityManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val tokenStore: SecureTokenStore,
    private val accountManifest: AccountManifest,
    private val databaseFactory: DatabaseFactory,
    // Use Provider<> to break the Hilt dependency cycle:
    // IdentityManager → AvagoServiceClient → HttpClient → TokenProvider (SecureTokenStore)
    // SecureTokenStore does NOT depend on IdentityManager, so no cycle at injection time.
    private val serviceClientProvider: Provider<AvagoServiceClient>,
    // Provider<> avoids potential circular dependency from AccountMigrationService's own dependencies
    private val migrationService: Provider<AccountMigrationService>,
    // Provider<> breaks the cycle: CrashDiagnostics → StateFlow<String?> → IdentityManager
    private val crashDiagnosticsProvider: Provider<CrashDiagnostics>,
    private val permissionStore: Provider<PermissionStore>,
) : RefreshFailedHandler {

    private val refreshMutex = Mutex()

    private val _activeAccountId = MutableStateFlow<String?>(null)
    val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()

    /**
     * Flips to `true` once [initOnLaunch] has completed (successfully or not).
     * The splash screen and nav-host use this to avoid showing the sign-in screen
     * before the auth state has been determined.
     */
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    /** Emits the account ID of an account that was just signed out. Observed by the app layer to reset sync watermarks. */
    private val _signOutEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val signOutEvents: SharedFlow<String> = _signOutEvents.asSharedFlow()

    /** Emits the account ID on successful sign-in. Observed by the app layer to clear rate-limit backoffs. */
    private val _signInEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val signInEvents: SharedFlow<String> = _signInEvents.asSharedFlow()

    /** Emits Unit whenever the accounts manifest is written. Mirrors iOS AVUserProfileDidChange. */
    private val _accountsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val accountsChanged: SharedFlow<Unit> = _accountsChanged.asSharedFlow()

    /** Emits the new active account ID after an account switch. */
    private val _accountSwitchEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val accountSwitchEvents: SharedFlow<String> = _accountSwitchEvents.asSharedFlow()

    private val _activeUserId = MutableStateFlow<String?>(null)
    val activeUserId: StateFlow<String?> = _activeUserId.asStateFlow()

    val isSignedIn: Boolean get() = _activeAccountId.value != null

    // True when the active account is an anonymous/guest session (no real credentials).
    // UI uses this to show "Sign In" prompts and keep the auth screen visible.
    private val _activeAccountIsAnonymous = MutableStateFlow(false)
    val activeAccountIsAnonymous: StateFlow<Boolean> = _activeAccountIsAnonymous.asStateFlow()

    private val _devRoleOverride = MutableStateFlow<String?>(null)
    val devRoleOverride: StateFlow<String?> = _devRoleOverride

    private val client: AvagoServiceClient get() = serviceClientProvider.get()

    private fun setActiveAccount(accountId: String?, userId: String? = null, isAnonymous: Boolean = false) {
        _activeAccountId.value = accountId
        _activeUserId.value = userId
        _activeAccountIsAnonymous.value = isAnonymous
        // Keep the token store's active pointer in sync so TokenProvider
        // can serve the correct credentials without depending on IdentityManager.
        tokenStore.activeAccountId = accountId
    }

    // ---------------------------------------------------------------------------
    // Initialisation — call once on app start (e.g. from Application.onCreate)
    // ---------------------------------------------------------------------------

    /**
     * Reads the manifest on launch. If accounts exist, restores the most-recently-
     * added one as the active account. If there are none, provisions anonymously.
     *
     * Sets [isInitialized] to `true` when complete so the splash screen and nav-host
     * can gate on auth state being ready.
     */
    suspend fun initOnLaunch() = withContext(Dispatchers.IO) {
        try {
            // Clean up orphaned accounts before restoring the active account
            runCatching { migrationService.get().pruneOrphanedAccounts() }
                .onFailure { Timber.w(it, "IdentityManager: pruneOrphanedAccounts failed") }

            val accounts = AccountManifest.load(appContext)
            if (accounts.isNotEmpty()) {
                val last = accounts.last()
                setActiveAccount(last.accountId, last.userId, isAnonymous = last.isAnonymous)

                // Refresh role from /users/me if:
                //  • role is missing (old install / failed prior fetch), OR
                //  • role is a legacy value ("owner", "tech", "member") that the
                //    server DB has since migrated to the new RBAC equivalents.
                //    Storing the normalised value avoids a mismatch on every launch.
                val isLegacyRole = last.role != null && last.role in setOf("owner", "tech", "member")
                val role = if ((last.role.isNullOrBlank() || isLegacyRole) && !last.isAnonymous) {
                    Timber.d("IdentityManager: role=${last.role} for ${last.accountId} — refreshing from /me")
                    runCatching {
                        val user = client.getMe()
                        val fresh = normalizeLegacyRole(user?.role) ?: normalizeLegacyRole(last.role)
                        if (fresh != null) {
                            accountManifest.upsertFromServer(last.copy(role = fresh))
                        }
                        fresh
                    }.getOrNull() ?: normalizeLegacyRole(last.role)
                } else {
                    normalizeLegacyRole(last.role)
                }

                val isRoot = role == "root" || last.memberships.any { it.accountId == last.accountId && it.isRoot }
                permissionStore.get().activate(last.accountId, role, isRoot)
                permissionStore.get().refresh(last.accountId, role)
                crashDiagnosticsProvider.get().setUserContext()
                accountManifest.deduplicateAnonymousAccounts(last.accountId)
                Timber.d("IdentityManager: restored account ${last.accountId}")
            } else {
                Timber.d("IdentityManager: no accounts on disk, provisioning")
                provisionConnected(appContext)
            }
        } finally {
            // Always mark init as done so the splash and nav-host are unblocked
            // even if provisioning failed.
            _isInitialized.value = true
        }
    }

    // ---------------------------------------------------------------------------
    // Provision (anonymous)
    // ---------------------------------------------------------------------------

    suspend fun provisionConnected(context: Context) = withContext(Dispatchers.IO) {
        val deviceId = tokenStore.getOrCreateDeviceId()
        val response = client.provision(deviceId)
        val accountId = requireNotNull(response.account_id) {
            "Server did not return account_id in provision response"
        }

        tokenStore.storeTokens(accountId, response.access_token, response.refresh_token)
        response.device_id?.let { tokenStore.storeDeviceId(accountId, it) }

        val record = AccountRecord(accountId = accountId, isAnonymous = true)
        AccountManifest.addOrUpdate(context, record)
        setActiveAccount(accountId, isAnonymous = true)
        permissionStore.get().activate(accountId, null, isRoot = false)

        Timber.d("IdentityManager: provisioned as $accountId")
        registerPushTokenAsync()
    }

    // ---------------------------------------------------------------------------
    // Sign-in with Firebase
    // ---------------------------------------------------------------------------

    suspend fun signInWithFirebase(context: Context, firebaseToken: String, provider: String = "firebase") =
        withContext(Dispatchers.IO) {
            if (_activeAccountIsAnonymous.value && _activeAccountId.value != null) {
                try {
                    upgradeAnonymousToNamed(firebaseToken, provider)
                    return@withContext
                } catch (e: NetworkException) {
                    if (e.code != 409) throw e
                    Timber.i("IdentityManager: anonymous upgrade target already exists; falling back to sign-in")
                }
            }

            val deviceId = tokenStore.getOrCreateDeviceId()
            val response = client.signIn(firebaseToken, deviceId, provider)
            val accountId = requireNotNull(response.account_id) {
                "Server did not return account_id in sign-in response"
            }

            tokenStore.storeTokens(accountId, response.access_token, response.refresh_token)
            response.device_id?.let { tokenStore.storeDeviceId(accountId, it) }
            // Switch token provider to the new account before any subsequent API calls
            // so the Ktor bearer plugin sends the new token, not the old anonymous one.
            setActiveAccount(accountId)
            // Ktor caches the bearer token from loadTokens internally. Clear that cache
            // now so the next request re-invokes loadTokens and picks up the new token
            // rather than reusing the previous session's (anonymous) cached token.
            client.clearBearerTokenCache()

            // Fetch profile to fill in the manifest record
            val user = runCatching { client.getMe() }.getOrNull()
            val userRole = user?.role
            val memberships = if (userRole != null) {
                listOf(AccountMembership(accountId = accountId, role = userRole, isRoot = userRole == "root"))
            } else {
                emptyList()
            }
            Timber.d("IdentityManager: sign-in response has ${response.accounts.size} account(s): ${response.accounts.map { "${it.account_id}=${it.name}" }}")
            val accountSummary = response.accounts.find { it.account_id == accountId }
            val record = AccountRecord(
                accountId = accountId,
                userId = user?.user_id,
                displayName = user?.display_name,
                email = user?.email,
                role = user?.role,
                memberships = memberships,
                accountName = accountSummary?.name,
            )
            AccountManifest.addOrUpdate(context, record)
            // Add any other accounts from the sign-in response that aren't already stored.
            // Stamp the canonical user_id so reconcileNamed can scope cleanup to
            // records belonging to THIS user (and leave other users / anonymous
            // records alone).
            response.accounts.filter { it.account_id != accountId }.forEach { acct ->
                Timber.d("IdentityManager: adding secondary account ${acct.account_id} (${acct.name}) to manifest")
            accountManifest.upsertFromServer(
                    AccountRecord(
                        accountId = acct.account_id,
                        userId = user?.user_id,
                        accountName = acct.name,
                        role = acct.role,
                    )
                )
            }
            // Reconcile the manifest against the server's authoritative account
            // list for THIS user. Stale entries from a previous session (e.g.
            // memberships that have since been revoked) must be removed BEFORE
            // sync workers fan out, or each stale account's sync/pull 403 will
            // fire observeAccountGone. Scoped by user_id (mirrors iOS) so a
            // parallel /accounts call returning someone else's data (stale
            // bearer token race) doesn't wipe this user's accounts.
            val canonicalUserId = user?.user_id
            if (canonicalUserId != null) {
                val serverAccountIds = response.accounts.map { it.account_id }.toSet()
                val staleAccountIds = accountManifest.reconcileNamed(
                    forUserId = canonicalUserId,
                    serverAccountIds = serverAccountIds,
                    activeAccountId = accountId,
                )
                staleAccountIds.forEach { staleId ->
                    runCatching { tokenStore.clearTokens(staleId) }
                        .onFailure { Timber.w(it, "IdentityManager: failed to clear tokens for stale $staleId") }
                    runCatching { databaseFactory.deleteDatabase(staleId) }
                        .onFailure { Timber.w(it, "IdentityManager: failed to delete DB for stale $staleId") }
                }
            }
            setActiveAccount(accountId, user?.user_id, isAnonymous = false)
            refreshPermissionStoreForActiveAccount()
            _accountsChanged.tryEmit(Unit)
            crashDiagnosticsProvider.get().setUserContext()

            Timber.d("IdentityManager: signed in as $accountId")
            registerPushTokenAsync()

            // Sequential: validate role from members list (mirrors iOS fetchAndStoreUserProfile)
            val capturedUserId = user?.user_id
            if (capturedUserId != null) {
                runCatching { validateRoleFromMembersList(accountId, capturedUserId) }
                    .onFailure { Timber.w(it, "IdentityManager: validateRoleFromMembersList failed") }
            }

            // Sequential: fetch all accounts (mirrors iOS fetchAndStoreAllAccounts).
            // Use upsertFromServer (not addIfMissing) so a pre-existing record
            // with accountName=null gets upgraded with the server's name —
            // this is how iOS keeps "Farmer mark" visible after sign-in.
            runCatching {
                val result = client.getAllAccounts()
                if (result is NetworkResult.Success) {
                    result.data.forEach { acct ->
                        accountManifest.upsertFromServer(
                            AccountRecord(
                                accountId = acct.account_id,
                                userId = user?.user_id,
                                accountName = acct.name,
                                role = acct.role,
                            )
                        )
                    }
                    _accountsChanged.tryEmit(Unit)
                    Timber.d("IdentityManager: fetched ${result.data.size} account(s) post sign-in")
                }
            }.onFailure { Timber.w(it, "IdentityManager: post sign-in account fetch failed") }

            // Notify observers (e.g. SyncEngine rate-limit reset) before enqueueing sync.
            _signInEvents.emit(accountId)
            // Enqueue sync AFTER all sequential calls complete, so the sync starts with
            // full account/role info already written — mirrors iOS pattern.
            enqueueSyncWork()
        }

    private suspend fun upgradeAnonymousToNamed(firebaseToken: String, provider: String) {
        val accountId = requireNotNull(_activeAccountId.value)
        try {
            client.upgradeAnonymous(firebaseToken, provider)
        } catch (e: UnauthorizedException) {
            val deviceId = tokenStore.getDeviceId(accountId) ?: tokenStore.getOrCreateDeviceId()
            val response = client.upgradeAnonymousDevice(deviceId, firebaseToken, provider)
            tokenStore.storeTokens(accountId, response.access_token, response.refresh_token)
            response.device_id?.let { tokenStore.storeDeviceId(accountId, it) }
            client.clearBearerTokenCache()
        }

        val user = runCatching { client.getMe() }.getOrNull()
        val accountsResult = runCatching { client.getAllAccounts() }.getOrNull()
        val accountSummary = (accountsResult as? NetworkResult.Success)
            ?.data
            ?.find { it.account_id == accountId }
        val role = user?.role ?: accountSummary?.role
        val memberships = if (role != null) {
            listOf(AccountMembership(accountId = accountId, role = role, isRoot = role == "root"))
        } else {
            emptyList()
        }
        accountManifest.upsertFromServer(
            AccountRecord(
                accountId = accountId,
                userId = user?.user_id,
                displayName = user?.display_name,
                email = user?.email,
                role = role,
                memberships = memberships,
                accountName = accountSummary?.name,
                isAnonymous = false,
            )
        )
        setActiveAccount(accountId, user?.user_id, isAnonymous = false)
        refreshPermissionStoreForActiveAccount()
        _accountsChanged.tryEmit(Unit)
        crashDiagnosticsProvider.get().setUserContext()
        registerPushTokenAsync()
        _signInEvents.emit(accountId)
        enqueueSyncWork()
        Timber.d("IdentityManager: upgraded anonymous account $accountId to named")
    }

    // ---------------------------------------------------------------------------
    // Silent re-authentication
    // ---------------------------------------------------------------------------

    suspend fun reAuthenticateSilently(): Boolean {
        return try {
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                ?: return false
            val token = suspendCancellableCoroutine<String?> { cont ->
                firebaseUser.getIdToken(true)
                    .addOnSuccessListener { result -> cont.resumeWith(Result.success(result.token)) }
                    .addOnFailureListener { cont.resumeWith(Result.success(null)) }
            } ?: return false
            signInWithFirebase(appContext, token, firebaseUser.inferProvider())
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // don't swallow — let the cancelled scope propagate
        } catch (e: Exception) {
            Timber.w(e, "Silent re-auth failed")
            false
        }
    }

    override suspend fun onRefreshFailed() {
        try {
            val reAuthed = reAuthenticateSilently()
            if (!reAuthed) {
                val accountId = getActiveAccountId() ?: return
                signOut(accountId)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Timber.d("IdentityManager: onRefreshFailed cancelled — likely SyncWorker replaced, ignoring")
            throw e
        }
    }

    // ---------------------------------------------------------------------------
    // Token refresh
    // ---------------------------------------------------------------------------

    /**
     * If the access token for [accountId] is missing, calls the refresh endpoint.
     * Returns true if tokens are valid (or were successfully refreshed).
     */
    suspend fun refreshTokensIfNeeded(accountId: String): Boolean = withContext(Dispatchers.IO) {
        val access = tokenStore.getAccessToken(accountId)
        if (!access.isNullOrBlank()) return@withContext true

        refreshMutex.withLock {
            val accessAfterLock = tokenStore.getAccessToken(accountId)
            if (!accessAfterLock.isNullOrBlank()) return@withLock true

            val refresh = tokenStore.getRefreshToken(accountId)
            if (refresh.isNullOrBlank()) {
                Timber.w("IdentityManager: no refresh token for $accountId")
                return@withLock false
            }

            try {
                val deviceId = tokenStore.getDeviceId(accountId) ?: tokenStore.getOrCreateDeviceId()
                val response = client.refreshTokens(refresh, deviceId)
                tokenStore.storeTokens(accountId, response.access_token, response.refresh_token)
                Timber.d("IdentityManager: refreshed tokens for $accountId")
                true
            } catch (e: Exception) {
                Timber.e(e, "IdentityManager: token refresh failed for $accountId")
                false
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Account switching
    // ---------------------------------------------------------------------------

    /** Switch to an account that already exists in the manifest. */
    suspend fun switchAccount(context: Context, accountId: String) = withContext(Dispatchers.IO) {
        val accounts = AccountManifest.load(context)
        require(accounts.any { it.accountId == accountId }) {
            "Account $accountId not in manifest"
        }
        val freshResult = client.switchAccount(accountId)
        if (freshResult is NetworkResult.Success) {
            tokenStore.storeTokens(accountId, freshResult.data.access_token, freshResult.data.refresh_token)
        } else {
            Timber.w("IdentityManager: switch-account server call failed, using cached tokens")
        }
        setActiveAccount(accountId)
        refreshPermissionStoreForActiveAccount()
        _accountSwitchEvents.emit(accountId)
        Timber.d("IdentityManager: switched to $accountId")
    }

    /**
     * Switch to an existing account — no [Context] overload for use from ViewModels
     * that don't hold a Context reference.
     */
    suspend fun switchAccount(accountId: String) = switchAccount(appContext, accountId)

    // ---------------------------------------------------------------------------
    // Add account
    // ---------------------------------------------------------------------------

    /**
     * Adds [record] to the manifest and immediately switches to it.
     * Tokens must already be stored in [SecureTokenStore] before calling this.
     */
    suspend fun addAccount(record: AccountRecord) = withContext(Dispatchers.IO) {
        AccountManifest.addOrUpdate(appContext, record)
        setActiveAccount(record.accountId)
        refreshPermissionStoreForActiveAccount()
        _accountsChanged.tryEmit(Unit)
        Timber.d("IdentityManager: addAccount ${record.accountId}")
    }

    // ---------------------------------------------------------------------------
    // Sign-out
    // ---------------------------------------------------------------------------

    /** Sign out of [accountId], removing its tokens, manifest entry, and local database. */
    suspend fun signOut(context: Context, accountId: String) = withContext(Dispatchers.IO) {
        _signOutEvents.emit(accountId)
        permissionStore.get().clear(accountId)
        tokenStore.clearTokens(accountId)
        AccountManifest.remove(context, accountId)
        try { databaseFactory.deleteDatabase(accountId) } catch (e: Exception) {
            Timber.w(e, "IdentityManager: failed to delete DB for $accountId")
        }
        _accountsChanged.tryEmit(Unit)
        if (_activeAccountId.value == accountId) {
            val remaining = AccountManifest.load(context)
            val next = remaining.lastOrNull()
            setActiveAccount(next?.accountId, next?.userId, isAnonymous = next?.isAnonymous ?: false)
            val nextIsRoot = next?.let { it.role == "root" || it.memberships.any { membership -> membership.accountId == it.accountId && membership.isRoot } } == true
            permissionStore.get().activate(next?.accountId, next?.role, nextIsRoot)
        }
        // Clear cached bearer token so the next session's sign-in starts fresh.
        client.clearBearerTokenCache()
        Timber.d("IdentityManager: signed out of $accountId")
    }

    /**
     * Sign out without needing a [Context] — for use from ViewModels.
     */
    suspend fun signOut(accountId: String) = signOut(appContext, accountId)

    /** Sign out of all accounts, clearing all cached tokens and local databases. */
    suspend fun signOutAll() = withContext(Dispatchers.IO) {
        val accounts = AccountManifest.load(appContext)
        accounts.forEach { account ->
            _signOutEvents.emit(account.accountId)
            try { databaseFactory.deleteDatabase(account.accountId) } catch (e: Exception) {
                Timber.w(e, "IdentityManager: failed to delete DB for ${account.accountId}")
            }
        }
        tokenStore.clearAllTokens()
        AccountManifest.save(appContext, emptyList())
        _accountsChanged.tryEmit(Unit)
        permissionStore.get().activate(null, null, isRoot = false)
        setActiveAccount(null)
        Timber.d("IdentityManager: signed out of all accounts")
    }

    // ---------------------------------------------------------------------------
    // Multi-account prefetch
    // ---------------------------------------------------------------------------

    private fun fetchMyAccountsAsync() {
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val result = client.getAllAccounts()
                if (result is NetworkResult.Success) {
                    Timber.d("IdentityManager: fetchMyAccounts returned ${result.data.size} account(s): ${result.data.map { "${it.account_id}=${it.name}" }}")
                    // Determine the user_id this response belongs to. Prefer the
                    // fresh /me lookup so a stale token race (where the bearer
                    // cache is still pointing at the previous identity) doesn't
                    // attribute the response to the wrong user.
                    val canonicalUserId = runCatching { client.getMe() }.getOrNull()?.user_id
                    result.data.forEach { acct ->
                        accountManifest.upsertFromServer(
                            AccountRecord(
                                accountId = acct.account_id,
                                userId = canonicalUserId,
                                accountName = acct.name,
                                role = acct.role,
                            )
                        )
                    }
                    if (canonicalUserId != null) {
                        // Reconcile, scoped by user_id (mirrors iOS) — only drop
                        // entries belonging to THIS user that aren't in the
                        // server's authoritative list. Records owned by other
                        // users (multi-account device) and anonymous records
                        // are preserved.
                        val serverIds = result.data.map { it.account_id }.toSet()
                        val staleIds = accountManifest.reconcileNamed(
                            forUserId = canonicalUserId,
                            serverAccountIds = serverIds,
                            activeAccountId = _activeAccountId.value,
                        )
                        staleIds.forEach { staleId ->
                            runCatching { tokenStore.clearTokens(staleId) }
                                .onFailure { Timber.w(it, "IdentityManager: failed to clear tokens for stale $staleId") }
                            runCatching { databaseFactory.deleteDatabase(staleId) }
                                .onFailure { Timber.w(it, "IdentityManager: failed to delete DB for stale $staleId") }
                        }
                    } else {
                        Timber.w("IdentityManager: fetchMyAccounts skipped reconcile — could not resolve user_id from /me")
                    }
                    _accountsChanged.tryEmit(Unit)
                    Timber.d("IdentityManager: prefetched ${result.data.size} account(s); manifest now has ${accountManifest.allAccounts().size}")
                }
            } catch (e: Exception) {
                Timber.w(e, "IdentityManager: fetchMyAccounts failed")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Role validation
    // ---------------------------------------------------------------------------

    /**
     * Maps legacy DB role values to their new RBAC equivalents.
     * Mirrors the server migration (20260531000001_migrate_legacy_role_values.sql):
     *   owner  → root
     *   tech   → technician
     *   member → reader
     */
    private fun normalizeLegacyRole(role: String?): String? = when (role) {
        "owner"  -> "root"
        "tech"   -> "technician"
        "member" -> "reader"
        else     -> role
    }

    private suspend fun validateRoleFromMembersList(accountId: String, userId: String) {
        try {
            val result = client.getAccountMembers(accountId)
            if (result is NetworkResult.Success) {
                val myMember = result.data.find { it.user_id == userId }
                val memberRole = myMember?.role
                if (memberRole != null) {
                    updateAccountRole(accountId, memberRole)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Role validation from members list failed — using profile role")
        }
    }

    private fun updateAccountRole(accountId: String, role: String) {
        val accounts = AccountManifest.load(appContext).toMutableList()
        val idx = accounts.indexOfFirst { it.accountId == accountId }
        if (idx < 0) return
        val existing = accounts[idx]
        val updatedMemberships = existing.memberships.toMutableList()
        val mIdx = updatedMemberships.indexOfFirst { it.accountId == accountId }
        val newMembership = AccountMembership(accountId = accountId, role = role, isRoot = role == "root")
        if (mIdx >= 0) updatedMemberships[mIdx] = newMembership else updatedMemberships.add(newMembership)
        accounts[idx] = existing.copy(role = role, memberships = updatedMemberships)
        AccountManifest.save(appContext, accounts)
        Timber.d("IdentityManager: updated role for $accountId to $role")
    }

    // ---------------------------------------------------------------------------
    // Push token
    // ---------------------------------------------------------------------------

    private fun registerPushTokenAsync() {
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val fcmToken = suspendCancellableCoroutine<String?> { cont ->
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
                        .addOnFailureListener { cont.resumeWith(Result.success(null)) }
                } ?: return@launch
                storePushToken(fcmToken)
            } catch (e: Exception) {
                Timber.w(e, "IdentityManager: failed to register push token after sign-in")
            }
        }
    }

    suspend fun storePushToken(token: String) = withContext(Dispatchers.IO) {
        tokenStore.storePushToken(token)
        val accountId = _activeAccountId.value ?: return@withContext
        try {
            val deviceId = tokenStore.getDeviceId(accountId) ?: tokenStore.getOrCreateDeviceId()
            client.updateDevice(
                deviceId = deviceId,
                request = DeviceUpdateRequest(
                    push_token = token,
                    platform = "android",
                    os_version = android.os.Build.VERSION.RELEASE,
                ),
            )
            Timber.d("IdentityManager: push token registered for $accountId")
        } catch (e: Exception) {
            Timber.e(e, "IdentityManager: failed to register push token")
        }
    }

    // ---------------------------------------------------------------------------
    // Developer role override
    // ---------------------------------------------------------------------------

    fun setDevRoleOverride(role: String?) {
        if (role != null && !hasRootOnCurrentAccount()) return
        _devRoleOverride.value = role
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) { refreshPermissionStoreForActiveAccount() }
    }

    private suspend fun refreshPermissionStoreForActiveAccount() {
        val accountId = _activeAccountId.value
        val role = getEffectiveRole()
        val isRoot = role == "root" || hasRootOnCurrentAccount()
        permissionStore.get().activate(accountId, role, isRoot)
        permissionStore.get().refresh(accountId, role)
    }

    fun getEffectiveRole(): String? {
        return _devRoleOverride.value ?: run {
            val accountId = _activeAccountId.value ?: return null
            normalizeLegacyRole(AccountManifest.load(appContext).find { it.accountId == accountId }?.role)
        }
    }

    // ---------------------------------------------------------------------------
    // Wipe all for testing
    // ---------------------------------------------------------------------------

    suspend fun wipeAllForTesting() {
        Timber.w("WIPE ALL FOR TESTING — clearing all data")
        val accountIds = AccountManifest.load(appContext).map { it.accountId }
        tokenStore.clearAllTokens()
        accountManifest.clearAll()
        for (accountId in accountIds) {
            try {
                databaseFactory.deleteDatabase(accountId)
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete DB for $accountId")
            }
        }
        permissionStore.get().activate(null, null, isRoot = false)
        _activeAccountId.value = null
        _activeUserId.value = null
        tokenStore.activeAccountId = null
        Timber.w("Wipe complete")
    }

    // ---------------------------------------------------------------------------
    // Named account re-authentication
    // ---------------------------------------------------------------------------

    suspend fun reAuthenticateNamedAccount(accountId: String): Boolean {
        return try {
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                ?: return false
            val firebaseToken = suspendCancellableCoroutine<String?> { cont ->
                firebaseUser.getIdToken(true)
                    .addOnSuccessListener { cont.resumeWith(Result.success(it.token)) }
                    .addOnFailureListener { cont.resumeWith(Result.success(null)) }
            } ?: return false
            signInWithFirebase(appContext, firebaseToken, firebaseUser.inferProvider())
            _activeAccountId.value == accountId
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // don't swallow — let the cancelled scope propagate
        } catch (e: Exception) {
            Timber.w(e, "reAuthenticateNamedAccount failed for $accountId")
            false
        }
    }

    // ---------------------------------------------------------------------------
    // Sync scheduling
    // ---------------------------------------------------------------------------

    private var syncWorkerClass: Class<out androidx.work.ListenableWorker>? = null

    fun registerSyncWorker(cls: Class<out androidx.work.ListenableWorker>) {
        syncWorkerClass = cls
    }

    private fun enqueueSyncWork() {
        val cls = syncWorkerClass ?: return
        val request = OneTimeWorkRequest.Builder(cls).build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork("avago_sync", ExistingWorkPolicy.REPLACE, request)
        Timber.d("IdentityManager: enqueued post-signin sync")
    }


    suspend fun updateDisplayName(displayName: String) = withContext(Dispatchers.IO) {
        val trimmed = displayName.trim()
        if (trimmed.isBlank()) return@withContext
        val user = client.updateMe(trimmed).getOrThrow()
        val accountId = _activeAccountId.value ?: return@withContext
        val existing = AccountManifest.load(appContext).find { it.accountId == accountId }
        accountManifest.upsertFromServer(
            AccountRecord(
                accountId = accountId,
                userId = user.user_id,
                displayName = user.display_name ?: trimmed,
                email = user.email ?: existing?.email,
                role = user.role ?: existing?.role,
                memberships = existing?.memberships.orEmpty(),
                isAnonymous = false,
                accountName = existing?.accountName,
            )
        )
        _accountsChanged.tryEmit(Unit)
    }

    fun getActiveAccountLabel(): String? {
        val accountId = _activeAccountId.value ?: return null
        return AccountManifest.load(appContext).find { it.accountId == accountId }?.displayLabel
    }

    // ---------------------------------------------------------------------------
    // Simple accessors (non-suspend, from memory)
    // ---------------------------------------------------------------------------

    fun getActiveAccountId(): String? = _activeAccountId.value

    fun getActiveUserId(): String? = _activeUserId.value

    fun getAccessToken(accountId: String): String? = tokenStore.getAccessToken(accountId)

    fun getRefreshToken(accountId: String): String? = tokenStore.getRefreshToken(accountId)

    fun hasRootOnCurrentAccount(): Boolean {
        val accountId = _activeAccountId.value ?: return false
        val accounts = AccountManifest.load(appContext)
        val account = accounts.find { it.accountId == accountId } ?: return false
        if (account.role == "root") return true
        return account.memberships
            .find { it.accountId == accountId }
            ?.isRoot ?: false
    }
}

private fun com.google.firebase.auth.FirebaseUser.inferProvider(): String {
    val providers = providerData.map { it.providerId }
    return when {
        "google.com" in providers -> "google"
        "apple.com" in providers -> "apple"
        else -> "firebase"
    }
}
