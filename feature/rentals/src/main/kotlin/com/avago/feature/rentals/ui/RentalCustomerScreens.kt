package com.avago.feature.rentals.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.RentalRepository
import com.avago.feature.rentals.data.RentalRequests
import com.avago.feature.rentals.domain.Aamva
import com.avago.feature.rentals.domain.CustomerView
import com.avago.feature.rentals.domain.OrderView
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.str
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ── List ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class RentalCustomersViewModel @Inject constructor(repo: RentalRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val customers: StateFlow<List<CustomerView>> = combine(repo.records(RentalEntity.CUSTOMER), query) { rows, q ->
        rows.map(CustomerView::of)
            .filter { q.isBlank() || it.name.contains(q, true) || it.company?.contains(q, true) == true || it.email?.contains(q, true) == true || it.phone?.contains(q) == true }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun RentalCustomersScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    viewModel: RentalCustomersViewModel = hiltViewModel(),
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { RentalsTopBar(stringResource(R.string.rentals_customers), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.rentals_new_customer)) })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 96.dp)) {
            item {
                OutlinedTextField(
                    query, { viewModel.query.value = it },
                    label = { Text(stringResource(R.string.rentals_search_customers)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (customers.isEmpty()) item { EmptyState(stringResource(R.string.rentals_customers_empty)) }
            items(customers, key = { it.id }) { c ->
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { onOpen(c.id) },
                    headlineContent = { Text(c.name) },
                    supportingContent = { listOfNotNull(c.company, c.email, c.phone).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ")) } },
                    trailingContent = {
                        Column {
                            if (c.doNotRent) Text(stringResource(R.string.rentals_do_not_rent), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            SyncStateBadge(c.localState)
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

// ── Edit / detail ────────────────────────────────────────────────────────────────────

data class CustomerFormState(
    val name: String = "",
    val company: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val dateOfBirth: String = "",
    val notes: String = "",
)

data class DocumentRow(val id: String, val kind: String, val number: String?, val expiresOn: String?, val localState: String)

@HiltViewModel
class RentalCustomerViewModel @Inject constructor(
    private val repo: RentalRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val customerId: String? = savedState.get<String>("customerId")?.takeIf { it.isNotBlank() && it != "new" }
    val form = MutableStateFlow(CustomerFormState())
    val view: StateFlow<CustomerView?> = (customerId?.let { repo.record(RentalEntity.CUSTOMER, it) } ?: flowOf(null))
        .map { it?.let(CustomerView::of) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val documents: StateFlow<List<DocumentRow>> = (customerId?.let { repo.byCustomer(RentalEntity.CUSTOMER_DOCUMENT, it) } ?: flowOf(emptyList()))
        .map { rows -> rows.map { r -> val o = r.obj; DocumentRow(r.id, o.str("kind") ?: "other", o.str("number"), o.str("expires_on"), r.localState) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val orders: StateFlow<List<OrderView>> = (customerId?.let { repo.byCustomer(RentalEntity.ORDER, it) } ?: flowOf(emptyList()))
        .map { rows -> rows.map(OrderView::of).sortedByDescending { it.startAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saved: SharedFlow<String> = _saved
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val errors: SharedFlow<String> = _errors

    init {
        customerId?.let { id ->
            viewModelScope.launch {
                val r = repo.record(RentalEntity.CUSTOMER, id).first() ?: return@launch
                val o = r.obj
                form.value = CustomerFormState(
                    name = o.str("name") ?: "",
                    company = o.str("company") ?: "",
                    email = o.str("email") ?: "",
                    phone = o.str("phone") ?: "",
                    address = o.str("address") ?: "",
                    dateOfBirth = o.str("date_of_birth") ?: "",
                    notes = o.str("notes") ?: "",
                )
            }
        }
    }

    /** Fills empty fields from a licence scanned on this device (AAMVA). */
    fun applyLicence(l: Aamva.License) {
        val f = form.value
        form.value = f.copy(
            name = f.name.ifBlank { l.fullName },
            address = f.address.ifBlank { l.address },
            dateOfBirth = f.dateOfBirth.ifBlank { l.dateOfBirth?.toString() ?: "" },
        )
    }

    fun save() = viewModelScope.launch {
        val f = form.value
        try {
            val id = repo.saveCustomer(
                customerId,
                RentalRepository.CustomerInput(
                    name = f.name, company = f.company, email = f.email, phone = f.phone,
                    address = f.address, dateOfBirth = f.dateOfBirth.takeIf { DATE.matches(it) }, notes = f.notes,
                ),
            )
            _saved.emit(id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "save customer failed")
            _errors.emit(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}

@Composable
fun RentalCustomerScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onScanLicence: () -> Unit,
    onAddDocument: (customerId: String) -> Unit,
    onOpenOrder: (String) -> Unit,
    viewModel: RentalCustomerViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val docs by viewModel.documents.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.saved.collect(onSaved) }
    LaunchedEffect(Unit) { viewModel.errors.collect { snackbar.showSnackbar(it) } }
    LifecycleResumeEffect(Unit) {
        LicenceScanBuffer.take()?.let(viewModel::applyLicence)
        onPauseOrDispose { }
    }
    val dobOk = form.dateOfBirth.isBlank() || RentalCustomerViewModel.DATE.matches(form.dateOfBirth)

    Scaffold(
        topBar = {
            RentalsTopBar(
                if (viewModel.customerId == null) stringResource(R.string.rentals_new_customer) else form.name.ifBlank { stringResource(R.string.rentals_customer) },
                onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            view?.let { v ->
                SyncStateBadge(v.localState)
                if (v.doNotRent) {
                    Text(
                        stringResource(R.string.rentals_do_not_rent) + (v.doNotRentReason?.let { ": $it" } ?: ""),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            OutlinedButton(onClick = onScanLicence, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_scan_licence)) }
            Field(form.name, stringResource(R.string.rentals_name)) { viewModel.form.value = form.copy(name = it) }
            Field(form.company, stringResource(R.string.rentals_company)) { viewModel.form.value = form.copy(company = it) }
            Field(form.email, stringResource(R.string.rentals_email), KeyboardType.Email) { viewModel.form.value = form.copy(email = it) }
            Field(form.phone, stringResource(R.string.rentals_phone), KeyboardType.Phone) { viewModel.form.value = form.copy(phone = it) }
            Field(form.address, stringResource(R.string.rentals_address), singleLine = false) { viewModel.form.value = form.copy(address = it) }
            OutlinedTextField(
                form.dateOfBirth, { viewModel.form.value = form.copy(dateOfBirth = it) },
                label = { Text(stringResource(R.string.rentals_date_of_birth)) },
                placeholder = { Text(stringResource(R.string.rentals_date_placeholder)) },
                isError = !dobOk,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Field(form.notes, stringResource(R.string.rentals_notes), singleLine = false) { viewModel.form.value = form.copy(notes = it) }
            Button(
                onClick = viewModel::save,
                enabled = form.name.isNotBlank() && dobOk,
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.rentals_save)) }

            viewModel.customerId?.let { id ->
                SectionHeader(stringResource(R.string.rentals_documents)) {
                    OutlinedButton(onClick = { onAddDocument(id) }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_add_document)) }
                }
                docs.forEach { d ->
                    ListItem(
                        headlineContent = { Text(stringResource(documentKindLabel(d.kind))) },
                        supportingContent = { Text(listOfNotNull(d.number, d.expiresOn?.let { stringResource(R.string.rentals_expires_on, it) }).joinToString(" · ")) },
                        trailingContent = { SyncStateBadge(d.localState) },
                    )
                }
                if (orders.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.rentals_orders))
                    orders.forEach { o ->
                        ListItem(
                            modifier = Modifier.clickable(role = Role.Button) { onOpenOrder(o.id) },
                            headlineContent = { Text(o.number ?: stringResource(R.string.rentals_draft_order)) },
                            supportingContent = { Text(stringResource(orderStatusLabel(o.status))) },
                            trailingContent = { Text(formatMoney(o.total)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, label: String, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

internal val DOCUMENT_KINDS = listOf("licence", "id", "coi", "tax_exempt", "operator_cert", "credit_app", "other")

@StringRes
internal fun documentKindLabel(kind: String): Int = when (kind) {
    "licence" -> R.string.rentals_doc_licence
    "id" -> R.string.rentals_doc_id
    "coi" -> R.string.rentals_doc_coi
    "tax_exempt" -> R.string.rentals_doc_tax_exempt
    "operator_cert" -> R.string.rentals_doc_operator_cert
    "credit_app" -> R.string.rentals_doc_credit_app
    else -> R.string.rentals_doc_other
}

// ── Document capture ─────────────────────────────────────────────────────────────────

@HiltViewModel
class DocumentCaptureViewModel @Inject constructor(
    private val repo: RentalRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    /** null → "prefill" mode: the licence is handed back to the customer form. */
    val customerId: String? = savedState.get<String>("customerId")?.takeIf { it.isNotBlank() && it != "new" }
    val kind = MutableStateFlow("licence")
    val number = MutableStateFlow("")
    val country = MutableStateFlow("")
    val region = MutableStateFlow("")
    val classes = MutableStateFlow("")
    val issued = MutableStateFlow("")
    val expires = MutableStateFlow("")
    val scanned = MutableStateFlow<Aamva.License?>(null)
    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done

    fun onBarcode(raw: String) {
        val l = Aamva.parse(raw) ?: return
        if (scanned.value != null) return
        scanned.value = l
        kind.value = "licence"
        number.value = l.number ?: ""
        country.value = RentalRequests.countryAlpha2(l.country) ?: ""
        region.value = l.region ?: ""
        classes.value = l.classCodes ?: ""
        issued.value = l.issuedOn?.toString() ?: ""
        expires.value = l.expiresOn?.toString() ?: ""
        if (customerId == null) {
            LicenceScanBuffer.put(l)
            _done.tryEmit(Unit)
        }
    }

    fun save() = viewModelScope.launch {
        val id = customerId ?: return@launch
        try {
            repo.addCustomerDocument(
                id,
                RentalRepository.DocumentInput(
                    kind = kind.value,
                    number = number.value,
                    issuingCountry = country.value,
                    issuingRegion = region.value,
                    classCodes = classes.value,
                    issuedOn = issued.value.takeIf { RentalCustomerViewModel.DATE.matches(it) },
                    expiresOn = expires.value.takeIf { RentalCustomerViewModel.DATE.matches(it) },
                ),
            )
            _done.emit(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "add document failed")
        }
    }
}

/** Document capture (§14): PDF417 driver-licence barcode (AAMVA) or manual entry. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DocumentCaptureScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: DocumentCaptureViewModel = hiltViewModel(),
) {
    val scanned by viewModel.scanned.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val number by viewModel.number.collectAsStateWithLifecycle()
    val country by viewModel.country.collectAsStateWithLifecycle()
    val region by viewModel.region.collectAsStateWithLifecycle()
    val classes by viewModel.classes.collectAsStateWithLifecycle()
    val issued by viewModel.issued.collectAsStateWithLifecycle()
    val expires by viewModel.expires.collectAsStateWithLifecycle()
    val analyzer = remember { MlKitBarcodeAnalyzer(pdf417Only = true) }
    var manual by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.done.collect { onDone() } }

    Scaffold(topBar = { RentalsTopBar(stringResource(R.string.rentals_scan_licence), onBack) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (scanned == null && !manual) {
                Text(stringResource(R.string.rentals_licence_hint), style = MaterialTheme.typography.bodyMedium)
                CameraScanner(analyzer, active = true, onCode = viewModel::onBarcode, modifier = Modifier.fillMaxWidth().height(280.dp))
                if (viewModel.customerId != null) {
                    OutlinedButton(onClick = { manual = true }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_enter_manually)) }
                }
            } else {
                scanned?.let { Text(it.fullName, style = MaterialTheme.typography.titleMedium) }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DOCUMENT_KINDS.forEach { k ->
                        FilterChip(kind == k, { viewModel.kind.value = k }, { Text(stringResource(documentKindLabel(k))) }, Modifier.heightIn(min = MinTouch))
                    }
                }
                Field(number, stringResource(R.string.rentals_doc_number)) { viewModel.number.value = it }
                Field(country, stringResource(R.string.rentals_issuing_country)) { viewModel.country.value = it.uppercase().take(2) }
                Field(region, stringResource(R.string.rentals_issuing_region)) { viewModel.region.value = it }
                Field(classes, stringResource(R.string.rentals_class_codes)) { viewModel.classes.value = it }
                Field(issued, stringResource(R.string.rentals_issued_on)) { viewModel.issued.value = it }
                Field(expires, stringResource(R.string.rentals_expires)) { viewModel.expires.value = it }
                Button(
                    onClick = viewModel::save,
                    enabled = viewModel.customerId != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentals_save)) }
            }
        }
    }
}
