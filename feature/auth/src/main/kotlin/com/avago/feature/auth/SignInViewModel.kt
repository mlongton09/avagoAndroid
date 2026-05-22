package com.avago.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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
        viewModelScope.launch {
            _state.value = SignInState.Loading
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(WEB_CLIENT_ID)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(context, request)
                val googleIdToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                val idToken = authResult.user?.getIdToken(false)?.await()?.token
                    ?: throw Exception("Could not get Firebase ID token")
                identityManager.signInWithFirebase(context, idToken)
                _state.value = SignInState.Success
            } catch (e: GetCredentialException) {
                Timber.w(e, "Google sign-in cancelled or failed")
                _state.value = SignInState.Error("Google sign-in failed: ${e.message}")
            } catch (e: Exception) {
                Timber.e(e, "Sign-in error")
                _state.value = SignInState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun signInWithEmail(context: Context) {
        val email = _email.value.trim()
        val password = _password.value
        if (email.isBlank() || password.isBlank()) {
            _state.value = SignInState.Error("Email and password are required")
            return
        }
        viewModelScope.launch {
            _state.value = SignInState.Loading
            try {
                val authResult = FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(email, password).await()
                val idToken = authResult.user?.getIdToken(false)?.await()?.token
                    ?: throw Exception("Could not get Firebase ID token")
                identityManager.signInWithFirebase(context, idToken)
                _state.value = SignInState.Success
            } catch (e: Exception) {
                Timber.e(e, "Email sign-in error")
                _state.value = SignInState.Error(e.message ?: "Sign-in failed")
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
                _state.value = SignInState.Error(e.message ?: "Failed")
            }
        }
    }

    fun clearError() { _state.value = SignInState.Idle }

    companion object {
        // Replace with your actual Web Client ID from google-services.json.
        // This is the client_id of type 3 (WEB) in google-services.json.
        const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID"
    }
}

// Extension to convert a Firebase Task to a suspend function with cancellation support.
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it, null) }
        addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    }
}
