package com.avago.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.feature.auth.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
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
    object Loading : SignInState()
    object Success : SignInState()
    data class Error(val message: String) : SignInState()
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow<SignInState>(SignInState.Idle)
    val state: StateFlow<SignInState> = _state.asStateFlow()

    // Email/password fields
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    fun setEmail(v: String) { _email.value = v }
    fun setPassword(v: String) { _password.value = v }

    fun signInWithGoogle(context: android.app.Activity) {
        if (_state.value is SignInState.Loading) return
        viewModelScope.launch {
            _state.value = SignInState.Loading
            try {
                val provider = OAuthProvider.newBuilder("google.com").build()
                val authResult = FirebaseAuth.getInstance()
                    .startActivityForSignInWithProvider(context, provider)
                    .await()
                val idToken = authResult.user?.getIdToken(false)?.await()?.token
                    ?: throw Exception(appContext.getString(R.string.auth_error_token_unavailable))
                identityManager.signInWithFirebase(context, idToken, "google")
                _state.value = SignInState.Success
            } catch (e: FirebaseNetworkException) {
                Timber.w(e, "Sign-in network error")
                _state.value = SignInState.Error(appContext.getString(R.string.auth_error_network))
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
            _state.value = SignInState.Loading
            try {
                val authResult = FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(trimmedEmail, password).await()
                val idToken = authResult.user?.getIdToken(false)?.await()?.token
                    ?: throw Exception(appContext.getString(R.string.auth_error_token_unavailable))
                identityManager.signInWithFirebase(appContext, idToken, "firebase")
                _state.value = SignInState.Success
            } catch (e: FirebaseNetworkException) {
                Timber.w(e, "Email sign-in network error")
                _state.value = SignInState.Error(appContext.getString(R.string.auth_error_network))
            } catch (_: FirebaseAuthInvalidCredentialsException) {
                _state.value = SignInState.Error(appContext.getString(R.string.auth_error_wrong_password))
            } catch (_: FirebaseAuthInvalidUserException) {
                _state.value = SignInState.Error(appContext.getString(R.string.auth_error_user_not_found))
            } catch (e: Exception) {
                Timber.e(e, "Email sign-in error")
                _state.value = SignInState.Error(e.message ?: appContext.getString(R.string.auth_error_sign_in_failed))
            }
        }
    }

    fun continueAnonymously(context: Context) {
        viewModelScope.launch {
            _state.value = SignInState.Loading
            try {
                identityManager.provisionConnected(context)
                _state.value = SignInState.Success
            } catch (e: Exception) {
                Timber.e(e, "Anonymous sign-in error")
                _state.value = SignInState.Error(e.message ?: appContext.getString(R.string.auth_error_anonymous_failed))
            }
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
