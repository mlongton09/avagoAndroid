package com.avago.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.AnonymousMigrationCandidate
import com.avago.core.auth.AccountMigrationService
import com.avago.core.auth.IdentityManager
import com.avago.core.network.NetworkException
import com.avago.feature.auth.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.OAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class SignInState {
    object Idle : SignInState()
    data class Loading(val isCreating: Boolean = false) : SignInState()
    object Success : SignInState()
    data class PendingMigration(
        val sourceAccountId: String,
        val targetAccountId: String,
        val accountLabel: String,
    ) : SignInState()
    data class Error(val message: String) : SignInState()
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val identityManager: IdentityManager,
    private val migrationService: AccountMigrationService,
) : ViewModel() {

    private val _state = MutableStateFlow<SignInState>(SignInState.Idle)
    val state: StateFlow<SignInState> = _state.asStateFlow()

    // Email/password fields
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()
    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    fun setEmail(v: String) { _email.value = v }
    fun setPassword(v: String) { _password.value = v }
    fun setDisplayName(v: String) { _displayName.value = v }

    fun signInWithGoogle(context: android.app.Activity) {
        if (_state.value is SignInState.Loading) return
        viewModelScope.launch {
            _state.value = SignInState.Loading()
            try {
                val pendingMigration = migrationService.pendingAnonymousMigration()
                val auth = FirebaseAuth.getInstance()
                // Resume an interrupted OAuth flow (Activity was destroyed while Chrome Custom Tab
                // was open — Firebase stored the PKCE state but it gets lost on Activity recreation).
                val authResult = auth.pendingAuthResult?.await()
                    ?: auth.startActivityForSignInWithProvider(
                        context,
                        OAuthProvider.newBuilder("google.com").build(),
                    ).await()
                val idToken = authResult.user?.getIdToken(false)?.await()?.token
                    ?: throw Exception(appContext.getString(R.string.auth_error_token_unavailable))
                identityManager.signInWithFirebase(context, idToken, "google")
                completeSignIn(pendingMigration)
            } catch (e: FirebaseNetworkException) {
                Timber.w(e, "Sign-in network error")
                _state.value = SignInState.Error(appContext.getString(R.string.auth_error_network))
            } catch (e: NetworkException) {
                Timber.e(e, "Sign-in error")
                val msg = if (e.code == 429) {
                    appContext.getString(R.string.auth_error_too_many_requests)
                } else {
                    e.message?.takeIf { it.isNotBlank() }
                        ?: appContext.getString(R.string.auth_error_sign_in_failed)
                }
                _state.value = SignInState.Error(msg)
            } catch (e: Exception) {
                Timber.e(e, "Sign-in error")
                _state.value = SignInState.Error(e.message ?: appContext.getString(R.string.auth_error_sign_in_failed))
            }
        }
    }

    fun signInWithEmail(context: Context) {
        val email = _email.value.trim()
        val password = _password.value
        if (email.isBlank() || password.isBlank()) {
            _state.value = SignInState.Error(appContext.getString(R.string.auth_error_email_password_required))
            return
        }
        signInWithEmail(email, password)
    }

    fun signInWithEmail(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            _state.value = SignInState.Error(appContext.getString(R.string.auth_error_email_password_required))
            return
        }
        if (_state.value is SignInState.Loading) return
        viewModelScope.launch {
            _state.value = SignInState.Loading()
            try {
                val pendingMigration = migrationService.pendingAnonymousMigration()
                val authResult = FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(trimmedEmail, password).await()
                val idToken = authResult.user?.getIdToken(false)?.await()?.token
                    ?: throw Exception(appContext.getString(R.string.auth_error_token_unavailable))
                identityManager.signInWithFirebase(appContext, idToken, "firebase")
                completeSignIn(pendingMigration)
            } catch (e: FirebaseNetworkException) {
                Timber.w(e, "Email sign-in network error")
                _state.value = SignInState.Error(appContext.getString(R.string.auth_error_network))
            } catch (_: FirebaseAuthInvalidCredentialsException) {
                _state.value = SignInState.Error(appContext.getString(R.string.auth_error_wrong_password))
            } catch (_: FirebaseAuthInvalidUserException) {
                _state.value = SignInState.Error(appContext.getString(R.string.auth_error_user_not_found))
            } catch (e: NetworkException) {
                Timber.e(e, "Email sign-in error")
                val msg = if (e.code == 429) {
                    appContext.getString(R.string.auth_error_too_many_requests)
                } else {
                    e.message?.takeIf { it.isNotBlank() }
                        ?: appContext.getString(R.string.auth_error_sign_in_failed)
                }
                _state.value = SignInState.Error(msg)
            } catch (e: Exception) {
                Timber.e(e, "Email sign-in error")
                _state.value = SignInState.Error(e.message ?: appContext.getString(R.string.auth_error_sign_in_failed))
            }
        }
    }



    fun createAccountWithEmail(email: String, password: String, displayName: String) {
        val trimmedEmail = email.trim()
        val trimmedDisplayName = displayName.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            _state.value = SignInState.Error(appContext.getString(R.string.auth_error_email_password_required))
            return
        }
        if (trimmedDisplayName.isBlank()) {
            _state.value = SignInState.Error(appContext.getString(R.string.auth_error_email_password_required))
            return
        }
        if (_state.value is SignInState.Loading) return
        viewModelScope.launch {
            _state.value = SignInState.Loading(isCreating = true)
            try {
                val pendingMigration = migrationService.pendingAnonymousMigration()
                val authResult = FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(trimmedEmail, password).await()
                val idToken = authResult.user?.getIdToken(false)?.await()?.token
                    ?: throw Exception(appContext.getString(R.string.auth_error_token_unavailable))
                identityManager.signInWithFirebase(appContext, idToken, "firebase")
                // Best-effort: if PUT /users/me fails, the user IS signed in and tokens are persisted —
                // surfacing an error would leave them at the sign-in screen with a confusing
                // "user_not_found" on retry. Display name will be picked up by the next /users/me GET.
                runCatching { identityManager.updateDisplayName(trimmedDisplayName) }
                    .onFailure { Timber.w(it, "createAccountWithEmail: updateDisplayName failed (best-effort)") }
                completeSignIn(pendingMigration)
            } catch (e: FirebaseNetworkException) {
                Timber.w(e, "Email create-account network error")
                _state.value = SignInState.Error(appContext.getString(R.string.auth_error_network))
            } catch (_: FirebaseAuthUserCollisionException) {
                _state.value = SignInState.Error(appContext.getString(R.string.auth_error_user_not_found))
            } catch (e: NetworkException) {
                Timber.e(e, "Email create-account error")
                val msg = if (e.code == 429) {
                    appContext.getString(R.string.auth_error_too_many_requests)
                } else {
                    e.message?.takeIf { it.isNotBlank() }
                        ?: appContext.getString(R.string.auth_error_sign_in_failed)
                }
                _state.value = SignInState.Error(msg)
            } catch (e: Exception) {
                Timber.e(e, "Email create-account error")
                _state.value = SignInState.Error(e.message ?: appContext.getString(R.string.auth_error_sign_in_failed))
            }
        }
    }

    fun continueAnonymously(context: Context) {
        viewModelScope.launch {
            _state.value = SignInState.Loading()
            try {
                identityManager.provisionConnected(context)
                _state.value = SignInState.Success
            } catch (e: Exception) {
                Timber.e(e, "Anonymous sign-in error")
                _state.value = SignInState.Error(e.message ?: appContext.getString(R.string.auth_error_anonymous_failed))
            }
        }
    }



    fun movePendingData(sourceAccountId: String, targetAccountId: String) {
        if (_state.value is SignInState.Loading) return
        viewModelScope.launch {
            _state.value = SignInState.Loading()
            val moved = migrationService.migrateAnonymousToAuthenticated(sourceAccountId, targetAccountId)
            _state.value = if (moved) SignInState.Success
            else SignInState.Error(appContext.getString(R.string.auth_error_sign_in_failed))
        }
    }

    fun startFresh(sourceAccountId: String) {
        if (_state.value is SignInState.Loading) return
        viewModelScope.launch {
            _state.value = SignInState.Loading()
            migrationService.discardAnonymousAccount(sourceAccountId)
            _state.value = SignInState.Success
        }
    }

    private fun completeSignIn(pendingMigration: AnonymousMigrationCandidate?) {
        val targetAccountId = identityManager.activeAccountId.value
        if (pendingMigration != null && targetAccountId != null && targetAccountId != pendingMigration.sourceAccountId) {
            _state.value = SignInState.PendingMigration(
                sourceAccountId = pendingMigration.sourceAccountId,
                targetAccountId = targetAccountId,
                accountLabel = identityManager.getActiveAccountLabel() ?: targetAccountId,
            )
        } else {
            _state.value = SignInState.Success
        }
    }

    fun clearError() { _state.value = SignInState.Idle }

}

// Extension to convert a Firebase Task to a suspend function with cancellation support.
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resumeWith(Result.success(it)) }
        addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    }
}
