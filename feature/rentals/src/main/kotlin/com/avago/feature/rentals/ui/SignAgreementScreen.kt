package com.avago.feature.rentals.ui

import androidx.compose.runtime.getValue
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.domain.SyncRepository
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.permissions.PermissionsManager
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.log.ui.SignaturePad
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.RentalsOnline
import com.avago.feature.rentals.domain.Esign
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * In-person agreement signing (docs/rentals-api-wave2-esign-scan-meter.md §1, mobile scope):
 * shows the agreement PDF and consent text, captures a drawn signature (or a typed name) with
 * explicit consent and queues the `rental.signatures.sign_request` command (the PNG goes through
 * the upload endpoints first). Offline captures are sent later; a refusal is rolled back and
 * shown as a failure on the order.
 */
@HiltViewModel
class SignAgreementViewModel @Inject constructor(
    private val repo: RentalRepository,
    private val online: RentalsOnline,
    private val sync: SyncRepository,
    permissions: PermissionsManager,
    savedState: SavedStateHandle,
) : ViewModel() {
    val orderId: String = requireNotNull(savedState["orderId"])
    val canCreateRequest: Boolean = permissions.can(Esign.PERMISSION_REQUEST)

    /** Server status merged with the signatures captured on this device. */
    val status: StateFlow<Esign.Status?> = combine(
        online.observe(online.signaturesKey(orderId)).map { Esign.parse(it?.data) },
        repo.observeSignatures(orderId),
    ) { s, local -> s?.let { Esign.withLocal(it, local) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val customerName: StateFlow<String> = repo.observeOrder(orderId)
        .map { it?.customerName.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val loading = MutableStateFlow(true)
    /** Last refresh / create failure: null, [Problem.Offline] or a server refusal message. */
    val problem = MutableStateFlow<Problem?>(null)
    val busy = MutableStateFlow(false)

    val signerName = MutableStateFlow("")
    val typed = MutableStateFlow(false)
    val typedName = MutableStateFlow("")
    val drawn = MutableStateFlow<String?>(null)
    val consent = MutableStateFlow(false)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events

    sealed interface Problem {
        data object Offline : Problem
        data class Refused(val message: String?) : Problem
    }

    sealed interface Event {
        data class Captured(val more: Boolean) : Event
        data class Invalid(val capture: Esign.Capture) : Event
        data class OpenPdf(val file: File) : Event
        data object PdfFailed : Event
    }

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        loading.value = true
        problem.value = when (val r = online.refreshSignatures(orderId)) {
            is RentalsOnline.Result.Ok -> null
            is RentalsOnline.Result.Offline -> Problem.Offline
            is RentalsOnline.Result.Refused -> Problem.Refused(r.message)
        }
        loading.value = false
    }

    /**
     * No open in-person request: queue `rental.rental_orders.request_signatures` for the order's
     * customer, send it and reload the status (the server renders the agreement and its consent
     * text). Offline, the request is sent with the next sync.
     */
    fun createRequest() = viewModelScope.launch {
        val name = customerName.value.ifBlank { signerName.value }.trim()
        if (name.isEmpty()) return@launch
        busy.value = true
        try {
            repo.requestAgreementSignature(orderId, name, Locale.getDefault().language)
            problem.value = if (sync.syncNow()) null else Problem.Offline
            if (problem.value == null) refresh().join()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "signature request failed")
            problem.value = Problem.Refused(e.message)
        } finally {
            busy.value = false
        }
    }

    fun openPdf(requestId: String) = viewModelScope.launch {
        busy.value = true
        when (val r = online.signaturePdf(requestId)) {
            is RentalsOnline.Result.Ok -> _events.emit(Event.OpenPdf(r.value))
            else -> _events.emit(Event.PdfFailed)
        }
        busy.value = false
    }

    /** Validates and queues the capture for the active signer of [request]. */
    fun submit(request: Esign.Request, signer: Esign.Signer) = viewModelScope.launch {
        val png = if (typed.value) null else drawn.value
        val name = if (typed.value) typedName.value else null
        when (val v = Esign.validate(png, name, consent.value)) {
            Esign.Capture.Ok -> Unit
            else -> { _events.emit(Event.Invalid(v)); return@launch }
        }
        busy.value = true
        try {
            repo.signRequest(
                orderId = orderId,
                requestId = request.id,
                signerId = signer.id,
                signerName = signer.name,
                signaturePng = png?.let { requireNotNull(Esign.pngBytes(it)) { "unreadable signature image" } },
                typedName = name,
                consentTextVersion = requireNotNull(request.consentVersion) { "the request has no consent text" },
            )
            val more = request.capturable.count { it.id != signer.id } > 0
            drawn.value = null
            typedName.value = ""
            consent.value = false
            _events.emit(Event.Captured(more))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "queue signature failed")
            problem.value = Problem.Refused(e.message)
        } finally {
            busy.value = false
        }
    }
}

/** Opens a cached PDF with the system viewer (FileProvider `cache-path`). */
internal fun openPdfFile(context: Context, file: File): Boolean = try {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    context.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: IllegalArgumentException) {
    false
}

@Composable
fun SignAgreementScreen(
    onBack: () -> Unit,
    viewModel: SignAgreementViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val customer by viewModel.customerName.collectAsStateWithLifecycle()
    val typed by viewModel.typed.collectAsStateWithLifecycle()
    val typedName by viewModel.typedName.collectAsStateWithLifecycle()
    val drawn by viewModel.drawn.collectAsStateWithLifecycle()
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val msgConsent = stringResource(R.string.rentals_sign_consent_required)
    val msgSignature = stringResource(R.string.rentals_sign_signature_required)
    val msgTooLarge = stringResource(R.string.rentals_sign_too_large)
    val msgQueued = stringResource(R.string.rentals_sign_queued)
    val msgPdf = stringResource(R.string.rentals_sign_pdf_failed)
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is SignAgreementViewModel.Event.Captured -> if (e.more) snackbar.showSnackbar(msgQueued) else onBack()
                is SignAgreementViewModel.Event.Invalid -> snackbar.showSnackbar(
                    when (e.capture) {
                        Esign.Capture.ConsentRequired -> msgConsent
                        Esign.Capture.ImageTooLarge -> msgTooLarge
                        else -> msgSignature
                    },
                )
                is SignAgreementViewModel.Event.OpenPdf -> if (!openPdfFile(context, e.file)) snackbar.showSnackbar(msgPdf)
                SignAgreementViewModel.Event.PdfFailed -> snackbar.showSnackbar(msgPdf)
            }
        }
    }
    val request = status?.signable
    val signer = request?.capturable?.firstOrNull()
    Scaffold(
        topBar = { RentalsTopBar(stringResource(R.string.rentals_sign_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (request != null && signer != null) {
                BottomActionBar {
                    Button(
                        onClick = { viewModel.submit(request, signer) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                    ) { Text(stringResource(R.string.rentals_sign_submit)) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState(), enabled = typed || drawn == null)
                .padding(vertical = AvagoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
        ) {
            when (val p = problem) {
                SignAgreementViewModel.Problem.Offline -> ErrorBanner(
                    stringResource(R.string.rentals_sign_offline),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onRetry = { viewModel.refresh() },
                    retryLabel = stringResource(R.string.rentals_retry),
                )
                is SignAgreementViewModel.Problem.Refused -> ErrorBanner(
                    p.message ?: stringResource(R.string.rentals_sign_failed),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onRetry = { viewModel.refresh() },
                    retryLabel = stringResource(R.string.rentals_retry),
                    tone = StatusTone.Danger,
                )
                null -> Unit
            }
            when {
                status?.agreementSigned == true -> EmptyState(
                    stringResource(R.string.rentals_agreement_signed),
                    message = stringResource(R.string.rentals_sign_done_body),
                    actionLabel = stringResource(R.string.rentals_done),
                    onAction = onBack,
                )
                request == null && loading -> Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
                request == null || signer == null -> {
                    val allLocal = status?.requests?.any { r -> r.signers.any { it.status == Esign.STATUS_SIGNED_LOCAL } } == true
                    EmptyState(
                        stringResource(if (allLocal) R.string.rentals_agreement_signed_syncing else R.string.rentals_sign_none),
                        icon = Icons.Default.Draw,
                        message = when {
                            allLocal -> null
                            viewModel.canCreateRequest && customer.isNotBlank() -> stringResource(R.string.rentals_sign_create_hint, customer)
                            viewModel.canCreateRequest -> null
                            else -> stringResource(R.string.rentals_override_ask_manager)
                        },
                        actionLabel = if (!allLocal && viewModel.canCreateRequest && customer.isNotBlank() && !busy) {
                            stringResource(R.string.rentals_sign_create)
                        } else {
                            null
                        },
                        onAction = { viewModel.createRequest() },
                    )
                }
                else -> {
                    SectionCard(
                        title = stringResource(R.string.rentals_agreement),
                        trailing = { StatusPill(stringResource(R.string.rentals_agreement_unsigned), StatusTone.Warning) },
                        contentPadding = true,
                    ) {
                        request.documentNumber?.let {
                            Text(
                                stringResource(R.string.rentals_sign_document, it),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = AvagoSpacing.sm),
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.openPdf(request.id) },
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = MinTouch),
                        ) {
                            Icon(Icons.Default.Description, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.rentals_sign_view_document), Modifier.padding(start = AvagoSpacing.sm))
                        }
                    }
                    SectionCard(title = stringResource(R.string.rentals_sign_signer, signer.name), contentPadding = true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                            FilterChip(!typed, { viewModel.typed.value = false }, { Text(stringResource(R.string.rentals_sign_draw)) }, Modifier.heightIn(min = MinTouch))
                            FilterChip(typed, { viewModel.typed.value = true }, { Text(stringResource(R.string.rentals_sign_type)) }, Modifier.heightIn(min = MinTouch))
                        }
                        if (typed) {
                            OutlinedTextField(
                                typedName, { viewModel.typedName.value = it },
                                label = { Text(stringResource(R.string.rentals_signer_name)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = AvagoSpacing.sm),
                            )
                        } else {
                            val desc = stringResource(R.string.rentals_signature_pad)
                            SignaturePad(
                                signature = drawn,
                                onSignatureChange = { viewModel.drawn.value = it },
                                modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = AvagoSpacing.sm).semantics { contentDescription = desc },
                            )
                        }
                    }
                    SectionCard(title = stringResource(R.string.rentals_sign_consent), contentPadding = true) {
                        Text(
                            request.consentText ?: stringResource(R.string.rentals_sign_consent_default),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = MinTouch)
                                .toggleable(consent, role = Role.Checkbox) { viewModel.consent.value = it },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(consent, null)
                            Text(stringResource(R.string.rentals_sign_consent_agree), Modifier.padding(start = AvagoSpacing.sm))
                        }
                    }
                }
            }
        }
    }
}