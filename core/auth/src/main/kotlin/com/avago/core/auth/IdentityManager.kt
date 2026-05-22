package com.avago.core.auth

import android.content.Context
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
    // Use Provider<> to break the Hilt dependency cycle:
    // IdentityManager → AvagoServiceClient → HttpClient → TokenProvider (SecureTokenStore)
    // SecureTokenStore does NOT depend on IdentityManager, so no cycle at injection time.
    private val serviceClientProvider: Provider<AvagoServiceClient>,
) : RefreshFailedHandler {

    private val refreshMutex = Mutex()

    private val _activeAccountId = MutableStateFlow<String?>(null)
    val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()

    /** Emits the account ID of an account that was just signed out. Observed by the app layer to reset sync watermarks. */
    private val _signOutEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val signOutEvents: SharedFlow<String> = _signOutEvents.asSharedFlow()

    private val _activeUserId = MutableStateFlow<String?>(null)
    val activeUserId: StateFlow<String?> = _activeUserId.asStateFlow()

    val isSignedIn: Boolean get() = _activeAccountId.value != null

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
     */
    suspend fun initOnLaunch() = withContext(Dispatchers.IO) {
        val accounts = AccountManifest.load(appContext)
        if (accounts.isNotEmpty()) {
            val last = accounts.last()
            setActiveAccount(last.accountId, last.userId)
            Timber.d("IdentityManager: restored account ${last.accountId}")
        } else {
            Timber.d("IdentityManager: no accounts on disk, provisioning")
            provisionConnected(appContext)
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

        val record = AccountRecord(accountId = accountId)
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

            Timber.d("IdentityManager: signed in as $accountId")
            registerPushTokenAsync()
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
            if (accountId != null) {
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
                val deviceId = tokenStore.getOrCreateDeviceId()
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
            val deviceId = tokenStore.getOrCreateDeviceId()
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
