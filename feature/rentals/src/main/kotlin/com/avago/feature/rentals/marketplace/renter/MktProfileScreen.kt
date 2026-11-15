package com.avago.feature.rentals.marketplace.renter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.ui.components.ErrorBanner
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.data.MarketplaceRepository
import com.avago.feature.rentals.marketplace.data.MarketplaceRepositoryImpl.Companion.renterLocale
import com.avago.feature.rentals.marketplace.data.MktResult
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.ui.MktPreviews
import com.avago.feature.rentals.marketplace.ui.mktErrorText
import com.avago.feature.rentals.ui.MinTouch
import com.avago.feature.rentals.ui.RentalsTopBar
import com.avago.feature.rentals.ui.readableWidth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MktProfileState(
    val signedIn: Boolean = false,
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: MktError? = null,
)

/** Renter account (§3 /me): name + phone used on bookings, sign out. */
@HiltViewModel
class MktProfileViewModel @Inject constructor(private val repo: MarketplaceRepository) : ViewModel() {
    private val _state = MutableStateFlow(MktProfileState())
    val state: StateFlow<MktProfileState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.session.collect { s ->
                when {
                    s == null -> _state.value = MktProfileState()
                    !_state.value.signedIn -> {
                        _state.value = fromSession()
                        loadMe()
                    }
                }
            }
        }
    }

    private suspend fun loadMe() {
        when (val r = repo.me()) {
            is MktResult.Ok -> _state.update { it.copy(email = r.value.email, name = r.value.name.orEmpty(), phone = r.value.phone.orEmpty()) }
            is MktResult.Err -> Unit // 401 clears the session (collected above); other errors keep the cached profile.
        }
    }

    private fun fromSession(): MktProfileState {
        val r = repo.session.value ?: return MktProfileState()
        return MktProfileState(true, r.renter?.email.orEmpty(), r.renter?.name.orEmpty(), r.renter?.phone.orEmpty())
    }

    fun setName(n: String) = _state.update { it.copy(name = n.take(120), saved = false, error = null) }
    fun setPhone(p: String) = _state.update { it.copy(phone = p.take(40), saved = false, error = null) }
    fun savedShown() = _state.update { it.copy(saved = false) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.name.isBlank()) {
            _state.update { it.copy(error = MktError.NameRequired) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.updateMe(s.name.trim(), s.phone.trim(), renterLocale())) {
                is MktResult.Ok -> _state.update { it.copy(saving = false, saved = true) }
                is MktResult.Err -> _state.update {
                    if (r.error.needsSignIn) MktProfileState() else it.copy(saving = false, error = r.error)
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repo.signOut()
            _state.value = MktProfileState()
        }
    }
}

@Composable
fun MktProfileScreen(onBack: () -> Unit, onSignIn: () -> Unit, vm: MktProfileViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MktProfileContent(state, onBack, onSignIn, vm::setName, vm::setPhone, vm::save, vm::signOut, vm::savedShown)
}

@Composable
internal fun MktProfileContent(
    state: MktProfileState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onSave: () -> Unit,
    onSignOut: () -> Unit,
    onSavedShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.mkt_profile_saved)
    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbar.showSnackbar(savedText)
            onSavedShown()
        }
    }
    Scaffold(topBar = { RentalsTopBar(stringResource(R.string.mkt_account), onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (!state.signedIn) {
                SignInPrompt(stringResource(R.string.mkt_sign_in_requests), onSignIn)
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).readableWidth().padding(AvagoSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AvagoSpacing.md),
                ) {
                    Text(stringResource(R.string.mkt_signed_in_as, state.email), style = MaterialTheme.typography.bodyLarge)
                    state.error?.let { ErrorBanner(mktErrorText(it)) }
                    OutlinedTextField(
                        value = state.name, onValueChange = onName, label = { Text(stringResource(R.string.mkt_your_name)) },
                        isError = state.error == MktError.NameRequired, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.phone, onValueChange = onPhone, label = { Text(stringResource(R.string.mkt_phone)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.mkt_admin_save))
                    }
                    TextButton(onClick = onSignOut, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.mkt_sign_out), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@MktPreviews
@Composable
private fun MktProfilePreview() {
    AvagoTheme { MktProfileContent(MktProfileState(true, "sam@example.com", "Sam Lee", "+1 250 555 0101"), {}, {}, {}, {}, {}, {}, {}) }
}
