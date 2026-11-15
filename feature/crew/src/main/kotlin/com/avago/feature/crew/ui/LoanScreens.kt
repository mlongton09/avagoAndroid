package com.avago.feature.crew.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.ui.components.EmptyStateView
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.crew.R
import com.avago.feature.crew.data.CrewService
import com.avago.feature.crew.data.DamageEntry
import com.avago.feature.crew.data.HandoverCheck
import com.avago.feature.crew.data.HandoverInput
import com.avago.feature.crew.data.Load
import com.avago.feature.crew.data.Loan
import com.avago.feature.crew.data.MyLoans
import com.avago.feature.crew.data.toLoad
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoansViewModel @Inject constructor(private val crew: CrewService) : NoticeViewModel() {
    private val _loans = MutableStateFlow<Load<MyLoans>>(Load.Loading)
    val loans: StateFlow<Load<MyLoans>> = _loans.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loans.value = Load.Loading
            _loans.value = crew.myLoans().toLoad()
        }
    }
}

@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    private val crew: CrewService,
    savedState: SavedStateHandle,
) : NoticeViewModel() {
    val loanId: String = checkNotNull(savedState["loanId"])
    private val _loan = MutableStateFlow<Load<Loan>>(Load.Loading)
    val loan: StateFlow<Load<Loan>> = _loan.asStateFlow()
    private val _photoIds = MutableStateFlow<List<String>>(emptyList())
    val photoIds: StateFlow<List<String>> = _photoIds.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loan.value = Load.Loading
            _loan.value = crew.loan(loanId).toLoad()
        }
    }

    private fun applied(updated: Loan?) {
        if (updated != null) _loan.value = Load.Ready(updated) else refresh()
    }

    fun accept(signerName: String) {
        val l = _loan.value.valueOrNull() ?: return
        if (signerName.isBlank()) return
        act(R.string.crew_loan_accepted, { crew.acceptLoan(l, signerName.trim()) }, ::applied)
    }

    /** `decline` or `cancel`. */
    fun action(action: String) {
        val done = if (action == "decline") R.string.crew_loan_declined else R.string.crew_loan_cancelled
        act(done, { crew.loanAction(loanId, action) }, ::applied)
    }

    fun addPhoto(jpeg: ByteArray) {
        act(null, { crew.uploadLoanPhoto(loanId, jpeg) }) { id -> _photoIds.update { it + id } }
    }

    fun handover(input: HandoverInput, isReturn: Boolean) {
        val full = input.copy(photoIds = _photoIds.value)
        act(if (isReturn) R.string.crew_loan_returned else R.string.crew_loan_handed_over, { crew.handover(loanId, full, isReturn) }) {
            _photoIds.value = emptyList()
            applied(it)
        }
    }
}

@Composable
fun LoansScreen(onBack: () -> Unit, onLoan: (String) -> Unit, viewModel: LoansViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val loans by viewModel.loans.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    NoticeEffect(viewModel.notices, snackbar)

    CrewScaffold(stringResource(R.string.crew_loans), onBack, snackbar) { inner ->
        LoadBox(loans, viewModel::refresh, inner) { all ->
            Column(Modifier.padding(top = inner.calculateTopPadding())) {
                TabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.crew_borrowing, all.borrowing.size)) })
                    Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.crew_lending, all.lending.size)) })
                }
                val list = if (tab == 0) all.borrowing else all.lending
                if (list.isEmpty()) {
                    EmptyStateView(icon = Icons.Outlined.Handshake, title = stringResource(R.string.crew_no_loans))
                } else {
                    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                        items(list, key = { it.id }) { l ->
                            Panel(Modifier.clickable { onLoan(l.id) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(l.assetName, style = MaterialTheme.typography.titleSmall)
                                        Caption(listOfNotNull(if (tab == 0) l.accountName else l.borrowerName, l.circleName).joinToString(" · "))
                                        Caption("${Fmt.date(l.startsAt)} – ${Fmt.date(l.endsAt)}")
                                    }
                                    LoanStatusPill(l.status)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LoanStatusPill(status: String) {
    val (label, tone) = when (status) {
        "draft" -> R.string.crew_loan_draft to StatusTone.Warning
        "agreed" -> R.string.crew_loan_agreed to StatusTone.Info
        "out" -> R.string.crew_loan_out to StatusTone.Accent
        "returned" -> R.string.crew_loan_status_returned to StatusTone.Success
        "declined" -> R.string.crew_loan_status_declined to StatusTone.Neutral
        "cancelled" -> R.string.crew_loan_status_cancelled to StatusTone.Neutral
        else -> null to StatusTone.Neutral
    }
    StatusPill(label?.let { stringResource(it) } ?: status, tone)
}

/** Default handover checklist; the server stores whatever keys are sent. */
internal val HANDOVER_KEYS = listOf(
    "clean" to R.string.crew_ho_clean,
    "no_damage" to R.string.crew_ho_no_damage,
    "fluids" to R.string.crew_ho_fluids,
    "accessories" to R.string.crew_ho_accessories,
)

@Composable
fun LoanDetailScreen(onBack: () -> Unit, viewModel: LoanDetailViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val loan by viewModel.loan.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val photos by viewModel.photoIds.collectAsStateWithLifecycle()
    var acceptOpen by rememberSaveable { mutableStateOf(false) }
    var handoverMode by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    val capture = rememberPhotoCapture(viewModel::addPhoto)
    NoticeEffect(viewModel.notices, snackbar)

    CrewScaffold(loan.valueOrNull()?.assetName ?: stringResource(R.string.crew_loan), onBack, snackbar) { inner ->
        LoadBox(loan, viewModel::refresh, inner) { l ->
            LazyColumn(contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item("head") {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(l.assetName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            LoanStatusPill(l.status)
                        }
                        Caption("${Fmt.dateTime(l.startsAt)} – ${Fmt.dateTime(l.endsAt)}")
                        l.accountName?.let { Caption(stringResource(R.string.crew_lender, it)) }
                        l.borrowerName?.let { Caption(stringResource(R.string.crew_borrower, it)) }
                        if (l.photos > 0) Caption(pluralStringResource(R.plurals.crew_photos, l.photos, l.photos))
                        if (l.defects > 0) Caption(pluralStringResource(R.plurals.crew_open_defects, l.defects, l.defects))
                    }
                }
                l.terms?.let { item("terms") { Panel(title = stringResource(R.string.crew_terms)) { Text(it) } } }
                l.agreementText?.let { item("agreement") { Panel(title = stringResource(R.string.crew_agreement)) { Text(it, style = MaterialTheme.typography.bodySmall) } } }
                item("actions") {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (l.can("accept")) {
                            Button(onClick = { acceptOpen = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                                Text(stringResource(R.string.crew_accept_loan))
                            }
                        }
                        if (l.can("handover_out")) {
                            Button(onClick = { handoverMode = "out" }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                                Text(stringResource(R.string.crew_handover_out))
                            }
                        }
                        if (l.can("return")) {
                            Button(onClick = { handoverMode = "return" }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                                Text(stringResource(R.string.crew_record_return))
                            }
                        }
                        if (l.can("decline")) {
                            OutlinedButton(onClick = { confirm = "decline" }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                                Text(stringResource(R.string.crew_decline))
                            }
                        }
                        if (l.can("cancel")) {
                            TextButton(onClick = { confirm = "cancel" }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                                Text(stringResource(R.string.crew_cancel_loan))
                            }
                        }
                    }
                }
            }
            if (acceptOpen) {
                AcceptDialog(l, onDismiss = { acceptOpen = false }, onAccept = {
                    viewModel.accept(it)
                    acceptOpen = false
                })
            }
            handoverMode?.let { mode ->
                HandoverDialog(
                    isReturn = mode == "return",
                    photoCount = photos.size,
                    busy = busy,
                    onPhoto = capture,
                    onDismiss = { handoverMode = null },
                    onSubmit = {
                        viewModel.handover(it, mode == "return")
                        handoverMode = null
                    },
                )
            }
            confirm?.let { a ->
                AlertDialog(
                    onDismissRequest = { confirm = null },
                    title = { Text(stringResource(if (a == "decline") R.string.crew_decline_q else R.string.crew_cancel_loan_q)) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.action(a)
                            confirm = null
                        }) { Text(stringResource(R.string.crew_confirm)) }
                    },
                    dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.crew_keep)) } },
                )
            }
        }
    }
}

@Composable
private fun AcceptDialog(loan: Loan, onDismiss: () -> Unit, onAccept: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crew_accept_loan)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(loan.consentText ?: stringResource(R.string.crew_esign_consent_default), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(agreed, { agreed = it })
                    Text(stringResource(R.string.crew_i_agree))
                }
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.crew_signer_name)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onAccept(name) }, enabled = agreed && name.isNotBlank()) { Text(stringResource(R.string.crew_sign_accept)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.crew_cancel)) } },
    )
}

@Composable
private fun HandoverDialog(
    isReturn: Boolean,
    photoCount: Int,
    busy: Boolean,
    onPhoto: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (HandoverInput) -> Unit,
) {
    val checks = remember { mutableStateListOf<Boolean>().apply { repeat(HANDOVER_KEYS.size) { add(true) } } }
    var meter by rememberSaveable { mutableStateOf("") }
    var fuel by rememberSaveable { mutableStateOf<String?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var damage by rememberSaveable { mutableStateOf("") }
    val labels = HANDOVER_KEYS.map { stringResource(it.second) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isReturn) R.string.crew_record_return else R.string.crew_handover_out)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(HANDOVER_KEYS.size) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { checks[i] = !checks[i] }) {
                        Checkbox(checks[i], { checks[i] = it })
                        Text(labels[i])
                    }
                }
                item {
                    OutlinedTextField(
                        meter, { meter = it },
                        label = { Text(stringResource(R.string.crew_meter_value_optional)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
                item {
                    Caption(stringResource(R.string.crew_fuel_level))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("empty", "1/4", "1/2", "3/4", "full").forEach { f -> FilterChip(fuel == f, { fuel = if (fuel == f) null else f }, { Text(f) }) }
                    }
                }
                if (isReturn) {
                    item {
                        OutlinedTextField(damage, { damage = it }, label = { Text(stringResource(R.string.crew_damage_optional)) }, minLines = 2)
                    }
                }
                item { OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.crew_notes)) }) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onPhoto, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.crew_add_photo)) }
                        Spacer(Modifier.width(8.dp))
                        if (photoCount > 0) Caption(pluralStringResource(R.plurals.crew_photos, photoCount, photoCount))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    onSubmit(
                        HandoverInput(
                            checklist = HANDOVER_KEYS.mapIndexed { i, (k, _) -> HandoverCheck(k, labels[i], checks[i]) },
                            photoIds = emptyList(),
                            meterValue = parseDecimal(meter),
                            fuelLevel = fuel,
                            notes = notes,
                            damage = damageEntries(damage),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.crew_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.crew_cancel)) } },
    )
}

/** One damage entry per non-blank line of the free-text field. */
internal fun damageEntries(text: String): List<DamageEntry> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { DamageEntry(it.take(200)) }
