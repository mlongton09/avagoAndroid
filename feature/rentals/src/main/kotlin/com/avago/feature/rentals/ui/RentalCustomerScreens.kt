package com.avago.feature.rentals.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.CollectionSettings
import com.avago.core.data.domain.CustomerDocumentDraft
import com.avago.core.data.domain.CustomerSite
import com.avago.core.data.domain.LedgerEntry
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.PartyDocument
import com.avago.core.data.domain.RentalAccountsRepository
import com.avago.core.data.domain.RentalContract
import com.avago.core.data.domain.RentalCustomer
import com.avago.core.data.domain.RentalCustomerDraft
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.domain.RentalSettingsRepository
import com.avago.core.data.rental.Money
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.StickyDayHeader
import com.avago.core.ui.components.cardListItemColors
import com.avago.core.ui.components.rememberHaptics
import com.avago.core.ui.components.toneColors
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.RentalsOnline
import com.avago.feature.rentals.domain.Aamva
import com.avago.feature.rentals.domain.CustomerView
import com.avago.feature.rentals.domain.OrderView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

// ── List ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class RentalCustomersViewModel @Inject constructor(repo: RentalRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val customers: StateFlow<List<CustomerView>> = combine(repo.observeCustomers(), query) { rows, q ->
        rows.filterNot { it.archived }.map(CustomerView::of)
            .filter { q.isBlank() || it.name.contains(q, true) || it.company?.contains(q, true) == true || it.email?.contains(q, true) == true || it.phone?.contains(q) == true }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RentalCustomersScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onBook: ((customerId: String) -> Unit)? = null,
    viewModel: RentalCustomersViewModel = hiltViewModel(),
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val bookLabel = stringResource(R.string.rentals_book)
    val callLabel = stringResource(R.string.rentals_call)
    val emailLabel = stringResource(R.string.rentals_send_email)
    // A–Z sticky headers (list is sorted by name).
    val groups = remember(customers) { customers.groupBy { c -> c.name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#' } }
    Scaffold(
        topBar = { RentalsTopBar(stringResource(R.string.rentals_customers), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                expanded = !listState.canScrollBackward,
                icon = { Icon(Icons.Default.PersonAdd, null) },
                text = { Text(stringResource(R.string.rentals_new_customer)) },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().readableWidth(),
            state = listState,
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + AvagoSpacing.fabClearance),
        ) {
            item(key = "search", contentType = "search") {
                RentalSearchField(query, { viewModel.query.value = it }, stringResource(R.string.rentals_search_customers))
            }
            if (customers.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    if (query.isNotBlank()) {
                        EmptyState(
                            stringResource(R.string.rentals_no_matches),
                            icon = Icons.Default.SearchOff,
                            actionLabel = stringResource(R.string.rentals_clear_search),
                            onAction = { viewModel.query.value = "" },
                        )
                    } else {
                        EmptyState(
                            stringResource(R.string.rentals_customers_empty),
                            icon = Icons.Default.People,
                            actionLabel = stringResource(R.string.rentals_new_customer),
                            onAction = onNew,
                        )
                    }
                }
            }
            groups.forEach { (letter, rows) ->
                stickyHeader(key = "h:$letter", contentType = "header") { StickyDayHeader(letter.toString()) }
                items(rows, key = { it.id }, contentType = { "customer" }) { c ->
                    val book = if (onBook != null && !c.doNotRent) {
                        SwipeAction(bookLabel, Icons.Default.EventAvailable, StatusTone.Accent) { onBook(c.id) }
                    } else null
                    val call = c.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                        SwipeAction(callLabel, Icons.Default.Call, StatusTone.Info) { launchContact(context, Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)) }
                    }
                    val mail = c.email?.takeIf { it.isNotBlank() }?.let { email ->
                        SwipeAction(emailLabel, Icons.Default.Email, StatusTone.Info) { launchContact(context, Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null)) }
                    }
                    // Swipe right = Book, swipe left = Call (or Email); the rest are TalkBack actions.
                    val end = call ?: mail
                    SwipeActionsRow(
                        start = book,
                        end = end,
                        more = listOfNotNull(mail).filter { it !== end },
                        modifier = Modifier.animateItem(),
                    ) { a11y ->
                        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                            ListItem(
                                modifier = Modifier.clickable(role = Role.Button) { onOpen(c.id) }.then(a11y),
                                leadingContent = { InitialsAvatar(c.name, if (c.doNotRent) StatusTone.Danger else StatusTone.Info) },
                                headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    listOfNotNull(c.company, c.email, c.phone).takeIf { it.isNotEmpty() }?.let {
                                        Text(it.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                },
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                                        if (c.doNotRent) StatusPill(stringResource(R.string.rentals_do_not_rent), StatusTone.Danger, icon = Icons.Default.Block)
                                        SyncStateBadge(c.sync)
                                    }
                                },
                            )
                            HorizontalDivider(Modifier.padding(start = AvagoSpacing.listInset), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

/** Opens the dialer / mail app; silently ignored when no app can handle it. */
private fun launchContact(context: Context, action: String, uri: Uri) {
    try {
        context.startActivity(Intent(action, uri))
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "no app for %s", action)
    }
}

/** 40dp tonal circle with the name's initials (decorative; the name is read from the headline). */
@Composable
internal fun InitialsAvatar(name: String, tone: StatusTone) {
    val c = toneColors(tone)
    val initials = name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(c.container)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = MaterialTheme.typography.titleSmall, color = c.content)
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

@HiltViewModel
class RentalCustomerViewModel @Inject constructor(
    private val repo: RentalRepository,
    accounts: RentalAccountsRepository,
    settings: RentalSettingsRepository,
    private val online: RentalsOnline,
    savedState: SavedStateHandle,
) : ViewModel() {
    val customerId: String? = savedState.get<String>("customerId")?.takeIf { it.isNotBlank() && it != "new" }
    val form = MutableStateFlow(CustomerFormState())
    val view: StateFlow<CustomerView?> = (customerId?.let { repo.observeCustomer(it) } ?: flowOf(null))
        .map { it?.let(CustomerView::of) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val orders: StateFlow<List<OrderView>> = (customerId?.let { repo.observeOrders(it) } ?: flowOf(emptyList()))
        .map { rows -> rows.map(OrderView::of).sortedByDescending { it.startAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun <T> Flow<List<T>>.state(): StateFlow<List<T>> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Licences, IDs and certificates on file, newest expiry first. */
    val documents: StateFlow<List<PartyDocument>> = (customerId?.let { accounts.observeCustomerDocuments(it) } ?: flowOf(emptyList()))
        .map { ds -> ds.sortedWith(compareBy<PartyDocument> { it.kind }.thenByDescending { it.expiresOn }) }.state()
    val sites: StateFlow<List<CustomerSite>> = (customerId?.let { accounts.observeCustomerSites(it) } ?: flowOf(emptyList()))
        .map { ss -> ss.sortedWith(compareByDescending<CustomerSite> { it.isPrimary }.thenBy { it.name.orEmpty() }) }.state()
    val ledger: StateFlow<List<LedgerEntry>> = (customerId?.let { accounts.observeLedger(null, it) } ?: flowOf(emptyList()))
        .map { es -> es.sortedByDescending { it.receivedAt } }.state()
    val contracts: StateFlow<List<RentalContract>> = (customerId?.let { accounts.observeContracts(null, it) } ?: flowOf(emptyList())).state()

    /** The account's document expiry alert window (collection settings). */
    val collection: StateFlow<CollectionSettings> = settings.observeCollectionSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionSettings())

    /** The stored customer; fields this form does not edit are carried over unchanged. */
    private var loaded: RentalCustomer? = null

    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saved: SharedFlow<String> = _saved
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val errors: SharedFlow<String> = _errors

    init {
        customerId?.let { id ->
            viewModelScope.launch {
                val c = repo.observeCustomer(id).first() ?: return@launch
                loaded = c
                form.value = CustomerFormState(
                    name = c.name,
                    company = c.company ?: "",
                    email = c.email ?: "",
                    phone = c.phone ?: "",
                    address = c.address ?: "",
                    notes = c.notes ?: "",
                )
                prefillDateOfBirth(id)
            }
        }
    }

    /**
     * The date of birth is encrypted at rest and never synced: it is read online
     * (`GET /rentals/customers/{id}`) and only fills a field the user has not typed in.
     */
    private suspend fun prefillDateOfBirth(id: String) {
        val r = online.refreshCustomer(id) as? RentalsOnline.Result.Ok ?: return
        val dob = (r.value["date_of_birth"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return
        if (DATE.matches(dob) && form.value.dateOfBirth.isBlank()) form.value = form.value.copy(dateOfBirth = dob)
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
                RentalCustomerDraft(
                    name = f.name.trim(),
                    kind = loaded?.kind ?: "person",
                    preferredLocale = loaded?.locale,
                    preferredCurrency = loaded?.currency,
                    company = f.company.trim().ifBlank { null },
                    email = f.email.trim().ifBlank { null },
                    phone = f.phone.trim().ifBlank { null },
                    address = f.address.trim().ifBlank { null },
                    dateOfBirth = f.dateOfBirth.takeIf { DATE.matches(it) }?.let(LocalDate::parse),
                    notes = f.notes.trim().ifBlank { null },
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
    onBook: ((customerId: String) -> Unit)? = null,
    viewModel: RentalCustomerViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val contracts by viewModel.contracts.collectAsStateWithLifecycle()
    val collection by viewModel.collection.collectAsStateWithLifecycle()
    var showAllOrders by rememberSaveable { mutableStateOf(false) }
    var showAllLedger by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    var attempted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.saved.collect {
            haptics.success()
            onSaved(it)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.errors.collect {
            haptics.error()
            snackbar.showSnackbar(it)
        }
    }
    LifecycleResumeEffect(Unit) {
        LicenceScanBuffer.take()?.let(viewModel::applyLicence)
        onPauseOrDispose { }
    }
    val dobOk = form.dateOfBirth.isBlank() || RentalCustomerViewModel.DATE.matches(form.dateOfBirth)
    val nameError = attempted && form.name.isBlank()
    val zone = ZoneId.systemDefault()
    val save = {
        attempted = true
        if (form.name.isNotBlank() && dobOk) viewModel.save() else haptics.error()
    }

    Scaffold(
        topBar = {
            RentalsTopBar(
                if (viewModel.customerId == null) stringResource(R.string.rentals_new_customer) else form.name.ifBlank { stringResource(R.string.rentals_customer) },
                onBack,
            ) {
                val id = viewModel.customerId
                if (id != null && onBook != null && view?.doNotRent != true) {
                    IconButton(onClick = { onBook(id) }, modifier = Modifier.size(MinTouch)) {
                        Icon(Icons.Default.EventAvailable, contentDescription = stringResource(R.string.rentals_book))
                    }
                }
                IconButton(onClick = onScanLicence, modifier = Modifier.size(MinTouch)) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = stringResource(R.string.rentals_scan_licence))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomActionBar(Modifier.imePadding()) {
                Button(onClick = { save() }, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.rentals_save), Modifier.padding(start = 8.dp))
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = AvagoSpacing.sm),
        ) {
            view?.let { v ->
                if (v.doNotRent) {
                    ErrorBanner(
                        stringResource(R.string.rentals_do_not_rent) + (v.doNotRentReason?.let { ": $it" } ?: ""),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        tone = StatusTone.Danger,
                        icon = Icons.Default.Block,
                    )
                }
                SyncStateBadge(v.sync, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            if (viewModel.customerId == null) {
                OutlinedButton(
                    onClick = onScanLicence,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().heightIn(min = MinTouch),
                ) {
                    Icon(Icons.Default.DocumentScanner, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.rentals_scan_licence), Modifier.padding(start = 8.dp))
                }
            }
            SectionCard(title = stringResource(R.string.rentals_customer), contentPadding = true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(
                        form.name, stringResource(R.string.rentals_name),
                        capitalization = KeyboardCapitalization.Words,
                        error = if (nameError) stringResource(R.string.rentals_name_required) else null,
                    ) { viewModel.form.value = form.copy(name = it) }
                    Field(form.company, stringResource(R.string.rentals_company), capitalization = KeyboardCapitalization.Words) {
                        viewModel.form.value = form.copy(company = it)
                    }
                }
            }
            SectionCard(title = stringResource(R.string.rentals_contact), contentPadding = true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(form.email, stringResource(R.string.rentals_email), KeyboardType.Email) { viewModel.form.value = form.copy(email = it) }
                    Field(form.phone, stringResource(R.string.rentals_phone), KeyboardType.Phone) { viewModel.form.value = form.copy(phone = it) }
                    Field(form.address, stringResource(R.string.rentals_address), singleLine = false) { viewModel.form.value = form.copy(address = it) }
                }
            }
            SectionCard(title = stringResource(R.string.rentals_more_details), contentPadding = true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        form.dateOfBirth, { viewModel.form.value = form.copy(dateOfBirth = it) },
                        label = { Text(stringResource(R.string.rentals_date_of_birth)) },
                        placeholder = { Text(stringResource(R.string.rentals_date_placeholder)) },
                        isError = !dobOk,
                        supportingText = if (!dobOk) {
                            { Text(stringResource(R.string.rentals_date_invalid, stringResource(R.string.rentals_date_placeholder))) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Field(form.notes, stringResource(R.string.rentals_notes), singleLine = false) { viewModel.form.value = form.copy(notes = it) }
                }
            }

            viewModel.customerId?.let { id ->
                SectionCard(
                    title = stringResource(R.string.rentals_documents),
                    trailing = {
                        TextButton(onClick = { onAddDocument(id) }, modifier = Modifier.heightIn(min = MinTouch)) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.rentals_add_document), Modifier.padding(start = 4.dp))
                        }
                    },
                ) {
                    if (documents.isEmpty()) CardNote(stringResource(R.string.rentals_documents_hint))
                    val today = LocalDate.now(zone)
                    documents.forEachIndexed { i, d ->
                        if (i > 0) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
                        DocumentRow(d, today, collection, zone)
                    }
                }
                if (sites.isNotEmpty()) {
                    SectionCard(title = stringResource(R.string.rentals_sites_title)) {
                        sites.forEachIndexed { i, site ->
                            if (i > 0) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
                            ListItem(
                                colors = cardListItemColors(),
                                leadingContent = { LeadingBadge(Icons.Default.Place, if (site.isPrimary) StatusTone.Info else StatusTone.Neutral) },
                                overlineContent = { Text(stringResource(sitePurposeLabel(site.purpose))) },
                                headlineContent = { Text(site.name ?: site.address.orEmpty()) },
                                supportingContent = listOfNotNull(site.address.takeIf { site.name != null }, site.contactNote)
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { lines -> { Text(lines.joinToString("\n")) } },
                            )
                        }
                    }
                }
                if (contracts.isNotEmpty()) {
                    SectionCard(title = stringResource(R.string.rentals_contracts_title)) {
                        contracts.forEachIndexed { i, c ->
                            if (i > 0) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
                            val (statusText, tone) = contractStatus(c)
                            val orderId = c.orderId
                            ListItem(
                                colors = cardListItemColors(),
                                modifier = if (orderId != null) Modifier.clickable(role = Role.Button) { onOpenOrder(orderId) } else Modifier,
                                leadingContent = { LeadingBadge(Icons.Default.Handshake, tone) },
                                headlineContent = {
                                    Text(c.number ?: stringResource(if (c.ownsEquity) R.string.rentals_contract_rto else R.string.rentals_contract_long_term))
                                },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                                        c.endAt?.let { Text(stringResource(R.string.rentals_contract_ends) + " " + formatDate(it, zone)) }
                                        StatusPill(statusText, tone)
                                    }
                                },
                                trailingContent = c.monthlyRateMinor?.let { m -> { Text(formatMoney(Money.of(m, c.currency)), style = MaterialTheme.typography.bodyMedium) } },
                            )
                        }
                    }
                }
                OrderLedgerCard(
                    ledger, zone,
                    maxRows = if (showAllLedger) Int.MAX_VALUE else PREVIEW_ROWS,
                    footer = { ShowAllRow(ledger.size, showAllLedger, { showAllLedger = !showAllLedger }) },
                )
                if (orders.isNotEmpty()) {
                    SectionCard(title = stringResource(R.string.rentals_orders)) {
                        orders.take(if (showAllOrders) orders.size else PREVIEW_ROWS).forEachIndexed { i, o ->
                            if (i > 0) CardDivider()
                            ListItem(
                                colors = cardListItemColors(),
                                modifier = Modifier.clickable(role = Role.Button) { onOpenOrder(o.id) },
                                leadingContent = { LeadingBadge(orderStatusIcon(o.status), orderStatusTone(o.status)) },
                                headlineContent = { Text(o.number ?: stringResource(R.string.rentals_draft_order)) },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(formatDateTime(o.startAt, zone))
                                        OrderStatusPill(o.status)
                                    }
                                },
                                trailingContent = { Text(formatMoney(o.total), style = MaterialTheme.typography.bodyMedium) },
                            )
                        }
                        ShowAllRow(orders.size, showAllOrders, { showAllOrders = !showAllOrders })
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    error: String? = null,
    onChange: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (singleLine) capitalization else KeyboardCapitalization.Sentences,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A document on file with its masked number and the expiry state against the account's alert window. */
@Composable
private fun DocumentRow(d: PartyDocument, today: LocalDate, collection: CollectionSettings, zone: ZoneId) {
    val expired = d.expired(today)
    val expiring = !expired && collection.expiringSoon(d.expiresOn, today)
    val tone = when {
        expired -> StatusTone.Danger
        expiring -> StatusTone.Warning
        d.verifiedAt != null -> StatusTone.Success
        else -> StatusTone.Neutral
    }
    ListItem(
        colors = cardListItemColors(),
        leadingContent = { LeadingBadge(Icons.Default.Badge, tone) },
        headlineContent = { Text(stringResource(documentKindLabel(d.kind))) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                val details = listOfNotNull(
                    d.numberLast4?.let { stringResource(R.string.rentals_doc_ending, it) },
                    d.issuer ?: listOfNotNull(d.issuingRegion, d.issuingCountry).joinToString(", ").ifEmpty { null },
                    d.expiresOn?.let { stringResource(R.string.rentals_doc_expires_on, formatDate(it.atStartOfDay(zone).toInstant(), zone)) },
                )
                if (details.isNotEmpty()) Text(details.joinToString(" \u00b7 "))
                SyncStateBadge(d.sync)
            }
        },
        trailingContent = when {
            expired -> ({ StatusPill(stringResource(R.string.rentals_doc_expired_badge), StatusTone.Danger) })
            expiring -> ({ StatusPill(stringResource(R.string.rentals_doc_expiring), StatusTone.Warning) })
            d.verifiedAt != null -> ({ StatusPill(stringResource(R.string.rentals_doc_verified), StatusTone.Success) })
            else -> null
        },
    )
}

@StringRes
private fun sitePurposeLabel(purpose: String): Int = when (purpose) {
    "primary" -> R.string.rentals_site_primary
    "billing" -> R.string.rentals_site_billing
    "shipping" -> R.string.rentals_site_shipping
    "job_site" -> R.string.rentals_site_job_site
    "pickup" -> R.string.rentals_site_pickup
    else -> R.string.rentals_site_other
}

internal val DOCUMENT_KINDS = listOf("driver_licence", "identity", "coi", "tax_exempt", "operator_cert", "credit_app", "other")

@StringRes
internal fun documentKindLabel(kind: String): Int = when (kind) {
    "driver_licence" -> R.string.rentals_doc_licence
    "identity" -> R.string.rentals_doc_id
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
    val kind = MutableStateFlow("driver_licence")
    val number = MutableStateFlow("")
    val country = MutableStateFlow("")
    val region = MutableStateFlow("")
    val classes = MutableStateFlow("")
    val issued = MutableStateFlow("")
    val expires = MutableStateFlow("")
    val scanned = MutableStateFlow<Aamva.License?>(null)
    /** Photo of the document (ID / insurance), uploaded with the add-document command. */
    val photoPath = MutableStateFlow<String?>(null)
    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done

    fun onBarcode(raw: String) {
        val l = Aamva.parse(raw) ?: return
        if (scanned.value != null) return
        scanned.value = l
        kind.value = "driver_licence"
        number.value = l.number ?: ""
        country.value = Aamva.countryAlpha2(l.country) ?: ""
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
                CustomerDocumentDraft(
                    kind = kind.value,
                    number = number.value.trim().ifBlank { null },
                    issuingCountry = Aamva.countryAlpha2(country.value),
                    issuingRegion = region.value.trim().ifBlank { null },
                    classCodes = classes.value.trim().ifBlank { null },
                    issuedOn = issued.value.takeIf { RentalCustomerViewModel.DATE.matches(it) }?.let(LocalDate::parse),
                    expiresOn = expires.value.takeIf { RentalCustomerViewModel.DATE.matches(it) }?.let(LocalDate::parse),
                    scans = listOfNotNull(photoPath.value?.let { LocalFileRef(Uri.fromFile(File(it)).toString(), "image/jpeg") }),
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
    val photo by viewModel.photoPath.collectAsStateWithLifecycle()
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
                DocumentPhotoRow(
                    photoPath = photo,
                    onPhoto = { viewModel.photoPath.value?.let { java.io.File(it).delete() }; viewModel.photoPath.value = it },
                    onRemove = { viewModel.photoPath.value?.let { java.io.File(it).delete() }; viewModel.photoPath.value = null },
                )
                Button(
                    onClick = viewModel::save,
                    enabled = viewModel.customerId != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentals_save)) }
            }
        }
    }
}
