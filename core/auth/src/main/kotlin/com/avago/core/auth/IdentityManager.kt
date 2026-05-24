package com.avago.core.auth

import android.content.Context
import com.avago.core.data.CrashDiagnostics
import com.avago.core.data.DatabaseFactory
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.RefreshFailedHandler
import com.avago.core.network.model.DeviceUpdateRequest
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

    private val _activeUserId = MutableStateFlow<String?>(null)
    val activeUserId: StateFlow<String?> = _activeUserId.asStateFlow()

    val isSignedIn: Boolean get() = _activeAccountId.value != null

    private val _devRoleOverride = MutableStateFlow<String?>(null)
    val devRoleOverride: StateFlow<String?> = _devRoleOverride

    private val client: AvagoServiceClient get() = serviceClientProvider.get()

    private fun setActiveAccount(accountId: String?, userId: String? = null) {
        _activeAccountId.value = accountId
        _activeUserId.value = userId
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
                setActiveAccount(last.accountId, last.userId)
                crashDiagnosticsProvider.get().setUserContext()
                accountManifest.deduplicateAnonymousAccounts(last.accountId)
                Timber.d("IdentityManager: restored account ${last.accountId}")
                val userId = last.userId
                if (userId != null) {
                    @Suppress("OPT_IN_USAGE")
                    GlobalScope.launch(Dispatchers.IO) {
                        validateRoleFromMembersList(last.accountId, userId)
                    }
                }
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
        setActiveAccount(accountId)

        Timber.d("IdentityManager: provisioned as $accountId")
        registerPushTokenAsync()
    }

    // ---------------------------------------------------------------------------
    // Sign-in with Firebase
    // ---------------------------------------------------------------------------

    suspend fun signInWithFirebase(context: Context, firebaseToken: String) =
        withContext(Dispatchers.IO) {
            val deviceId = tokenStore.getOrCreateDeviceId()
            val response = client.signIn(firebaseToken, deviceId)
            val accountId = requireNotNull(response.account_id) {
                "Server did not return account_id in sign-in response"
            }

            tokenStore.storeTokens(accountId, response.access_token, response.refresh_token)
            response.device_id?.let { tokenStore.storeDeviceId(accountId, it) }

            // Fetch profile to fill in the manifest record
            val user = runCatching { client.getMe() }.getOrNull()
            val userRole = user?.role
            val memberships = if (userRole != null) {
                listOf(AccountMembership(accountId = accountId, role = userRole, isRoot = userRole == "owner"))
            } else {
                emptyList()
            }
            val record = AccountRecord(
                accountId = accountId,
                userId = user?.user_id,
                displayName = user?.display_name,
                email = user?.email,
                role = user?.role,
                memberships = memberships,
            )
            AccountManifest.addOrUpdate(context, record)
            setActiveAccount(accountId, user?.user_id)
            crashDiagnosticsProvider.get().setUserContext()

            Timber.d("IdentityManager: signed in as $accountId")
            runCatching { migrationService.get().migrateAnonymousToAuthenticated(accountId) }
                .onFailure { Timber.w(it, "IdentityManager: migrateAnonymousToAuthenticated failed") }
            registerPushTokenAsync()
            fetchMyAccountsAsync()
            val capturedUserId = user?.user_id
            if (capturedUserId != null) {
                @Suppress("OPT_IN_USAGE")
                GlobalScope.launch(Dispatchers.IO) {
                    validateRoleFromMembersList(accountId, capturedUserId)
                }
            }
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
            signInWithFirebase(appContext, token)
            true
        } catch (e: Exception) {
            Timber.w(e, "Silent re-auth failed")
            false
        }
    }

    override suspend fun onRefreshFailed() {
        val reAuthed = reAuthenticateSilently()
        if (!reAuthed) {
            val accountId = getActiveAccountId()
            if (accountId != null && !accountId.startsWith("anon_")) {
                val namedReauthed = reAuthenticateNamedAccount(accountId)
                if (!namedReauthed) signOut(accountId)
            } else if (accountId != null) {
                signOut(accountId)
            }
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
        Timber.d("IdentityManager: addAccount ${record.accountId}")
    }

    // ---------------------------------------------------------------------------
    // Sign-out
    // ---------------------------------------------------------------------------

    /** Sign out of [accountId], removing its tokens and manifest entry. */
    suspend fun signOut(context: Context, accountId: String) = withContext(Dispatchers.IO) {
        _signOutEvents.emit(accountId)
        tokenStore.clearTokens(accountId)
        AccountManifest.remove(context, accountId)
        if (_activeAccountId.value == accountId) {
            // Switch to another account if one exists, otherwise go unauthenticated.
            val remaining = AccountManifest.load(context)
            setActiveAccount(remaining.lastOrNull()?.accountId)
        }
        Timber.d("IdentityManager: signed out of $accountId")
    }

    /**
     * Sign out without needing a [Context] — for use from ViewModels.
     */
    suspend fun signOut(accountId: String) = signOut(appContext, accountId)

    /** Sign out of all accounts, clearing all cached tokens. */
    suspend fun signOutAll() = withContext(Dispatchers.IO) {
        val accounts = AccountManifest.load(appContext)
        accounts.forEach { _signOutEvents.emit(it.accountId) }
        tokenStore.clearAllTokens()
        AccountManifest.save(appContext, emptyList())
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
                val result = client.getMyAccounts()
                if (result is NetworkResult.Success) {
                    result.data.forEach { acct ->
                        accountManifest.addIfMissing(
                            AccountRecord(
                                accountId = acct.account_id,
                                displayName = acct.name,
                            )
                        )
                    }
                    Timber.d("IdentityManager: prefetched ${result.data.size} account(s)")
                }
            } catch (e: Exception) {
                Timber.w(e, "IdentityManager: fetchMyAccounts failed")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Role validation
    // ---------------------------------------------------------------------------

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
        val newMembership = AccountMembership(accountId = accountId, role = role, isRoot = role == "owner")
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
    }

    fun getEffectiveRole(): String? {
        return _devRoleOverride.value ?: run {
            val accountId = _activeAccountId.value ?: return null
            AccountManifest.load(appContext).find { it.accountId == accountId }?.role
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
            signInWithFirebase(appContext, firebaseToken)
            _activeAccountId.value == accountId
        } catch (e: Exception) {
            Timber.w(e, "reAuthenticateNamedAccount failed for $accountId")
            false
        }
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
        return accounts
            .find { it.accountId == accountId }
            ?.memberships
            ?.find { it.accountId == accountId }
            ?.isRoot ?: false
    }
}
