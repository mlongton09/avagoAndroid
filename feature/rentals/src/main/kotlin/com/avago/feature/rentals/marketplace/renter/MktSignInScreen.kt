package com.avago.feature.rentals.marketplace.renter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.ui.components.ErrorBanner
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.data.MarketplaceRepository
import com.avago.feature.rentals.marketplace.data.MktResult
import com.avago.feature.rentals.marketplace.data.MarketplaceRepositoryImpl.Companion.renterLocale
import com.avago.feature.rentals.marketplace.domain.MktEmail
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktNext
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

enum class MktSignInStep { Form, Sent, Completing, LinkInvalid, Done }

data class MktSignInState(
    val step: MktSignInStep = MktSignInStep.Form,
    val email: String = "",
    val name: String = "",
    val sending: Boolean = false,
    val error: MktError? = null,
    /** Web path to continue at after sign-in (sanitised, `/marketplace…` only). */
    val next: String? = null,
)

/**
 * Renter email sign-in (§3): the server mails a magic link to https://<site>/marketplace/auth,
 * which the app opens (App Link) and exchanges for a renter session here.
 */
@HiltViewModel
class MktSignInViewModel @Inject constructor(
    private val repo: MarketplaceRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    private val token: String? = saved.get<String>("token")?.takeIf { it.isNotBlank() }
    private val _state = MutableStateFlow(MktSignInState(next = MktNext.sanitize(saved.get<String>("next"))))
    val state: StateFlow<MktSignInState> = _state.asStateFlow()

    init {
        if (token != null) complete(token)
        else if (repo.session.value != null) _state.update { it.copy(step = MktSignInStep.Done) }
    }

    fun setEmail(e: String) = _state.update { it.copy(email = e.take(254), error = null) }
    fun setName(n: String) = _state.update { it.copy(name = n.take(120), error = null) }

    fun send() {
        val s = _state.value
        if (s.sending) return
        if (!MktEmail.valid(s.email)) {
            _state.update { it.copy(error = MktError.InvalidEmail) }
            return
        }
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.requestLink(s.email.trim(), s.name.trim().ifBlank { null }, renterLocale(), s.next)) {
                is MktResult.Ok -> _state.update { it.copy(sending = false, step = MktSignInStep.Sent) }
                is MktResult.Err -> _state.update { it.copy(sending = false, error = r.error) }
            }
        }
    }

    /** Back to the form (wrong address / request a new link). */
    fun restart() = _state.update { it.copy(step = MktSignInStep.Form, error = null) }

    private fun complete(t: String) {
        _state.update { it.copy(step = MktSignInStep.Completing) }
        viewModelScope.launch {
            when (val r = repo.completeSignIn(t)) {
                is MktResult.Ok -> _state.update { it.copy(step = MktSignInStep.Done) }
                is MktResult.Err -> _state.update {
                    // Network trouble is retryable from the form; a bad / used / expired link is not.
                    if (r.error == MktError.Network) it.copy(step = MktSignInStep.Form, error = r.error)
                    else it.copy(step = MktSignInStep.LinkInvalid)
                }
            }
        }
    }
}

@Composable
fun MktSignInScreen(
    onBack: () -> Unit,
    /** Called once signed in with the sanitised web path to continue at (null = search). */
    onSignedIn: (next: String?) -> Unit,
    vm: MktSignInViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.step) { if (state.step == MktSignInStep.Done) onSignedIn(state.next) }
    MktSignInContent(state, onBack, vm::setEmail, vm::setName, vm::send, vm::restart)
}

@Composable
internal fun MktSignInContent(
    state: MktSignInState,
    onBack: () -> Unit,
    onEmail: (String) -> Unit,
    onName: (String) -> Unit,
    onSend: () -> Unit,
    onRestart: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(topBar = { RentalsTopBar(stringResource(R.string.mkt_sign_in), onBack) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).readableWidth().padding(AvagoSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AvagoSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state.step) {
                    MktSignInStep.Completing, MktSignInStep.Done -> {
                        CircularProgressIndicator(Modifier.padding(top = AvagoSpacing.xxl))
                        Text(stringResource(R.string.mkt_signing_in), style = MaterialTheme.typography.bodyLarge)
                    }
                    MktSignInStep.LinkInvalid -> {
                        Icon(Icons.Outlined.LinkOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.mkt_link_invalid_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.mkt_link_invalid_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_request_new_link)) }
                    }
                    MktSignInStep.Sent -> {
                        Icon(Icons.Outlined.MarkEmailRead, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.mkt_check_email_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.mkt_check_email_body, state.email.trim()), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Button(onClick = { openEmailApp(context) }, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_open_email_app)) }
                        TextButton(onClick = onRestart, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_use_different_email)) }
                    }
                    MktSignInStep.Form -> {
                        Text(stringResource(R.string.mkt_sign_in_intro), style = MaterialTheme.typography.bodyLarge)
                        state.error?.let { ErrorBanner(mktErrorText(it)) }
                        OutlinedTextField(
                            value = state.email, onValueChange = onEmail,
                            label = { Text(stringResource(R.string.mkt_email)) },
                            isError = state.error == MktError.InvalidEmail,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.name, onValueChange = onName,
                            label = { Text(stringResource(R.string.mkt_your_name)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = onSend, enabled = !state.sending, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                            Text(stringResource(if (state.sending) R.string.mkt_sending else R.string.mkt_send_link))
                        }
                        Text(stringResource(R.string.mkt_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun openEmailApp(context: android.content.Context) {
    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        .addCategory(android.content.Intent.CATEGORY_APP_EMAIL)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@MktPreviews
@Composable
private fun MktSignInPreview() {
    AvagoTheme { MktSignInContent(MktSignInState(email = "sam@example.com"), {}, {}, {}, {}, {}) }
}

@MktPreviews
@Composable
private fun MktSignInSentPreview() {
    AvagoTheme { MktSignInContent(MktSignInState(step = MktSignInStep.Sent, email = "sam@example.com"), {}, {}, {}, {}, {}) }
}
