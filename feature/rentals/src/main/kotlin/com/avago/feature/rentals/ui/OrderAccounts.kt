package com.avago.feature.rentals.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.FileUploads
import com.avago.core.data.domain.LedgerEntry
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.PostCharge
import com.avago.core.data.domain.PriceAdjustmentDraft
import com.avago.core.data.domain.RentalAccountsRepository
import com.avago.core.data.domain.RentalContract
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.domain.RentalSettingsRepository
import com.avago.core.data.domain.SignatureRequest
import com.avago.core.data.domain.SignatureSigner
import com.avago.core.data.domain.Subrental
import com.avago.core.data.rental.Money
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.permissions.PermissionsManager
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.cardListItemColors
import com.avago.core.ui.openPdfFile
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.RentalsOnline
import com.avago.feature.rentals.domain.BuyoutQuote
import com.avago.feature.rentals.domain.GatewayPayments
import com.avago.feature.rentals.domain.OnlinePayments
import com.avago.feature.rentals.domain.OrderStatus
import com.avago.feature.rentals.domain.OrderView
import com.avago.feature.rentals.domain.PostChargeForm
import com.avago.feature.rentals.domain.PostChargeOutcome
import com.avago.feature.rentals.domain.PostCharges
import com.avago.feature.rentals.domain.PriceAdjustments
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * The order's money and paperwork beyond the lines (v2 projections and L38 online actions):
 * - post charges (tolls, tickets, citations): created and voided through catalog commands;
 *   "charge now", closed orders and card charges are online REST actions;
 * - the order ledger (payments, deposits, refunds) and manual price adjustments (command);
 * - long-term contracts with the rent-to-own buy-out quote, buy-out and equity statement;
 * - signature requests and sub-rented equipment.
 * Online results arrive through sync; nothing here writes the local store directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrderAccountsViewModel @Inject constructor(
    repo: RentalRepository,
    private val accounts: RentalAccountsRepository,
    settings: RentalSettingsRepository,
    private val online: RentalsOnline,
    private val uploads: FileUploads,
    private val permissions: PermissionsManager,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val orderId: String = requireNotNull(savedState["orderId"])

    private val order: StateFlow<OrderView?> = repo.observeOrder(orderId)
        .map { it?.let(OrderView::of) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun <T> Flow<List<T>>.state(): StateFlow<List<T>> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val postCharges: StateFlow<List<PostCharge>> = accounts.observePostCharges(orderId)
        .map { cs -> cs.sortedByDescending { it.incurredAt } }.state()
    val ledger: StateFlow<List<LedgerEntry>> = accounts.observeLedger(orderId)
        .map { es -> es.sortedByDescending { it.receivedAt } }.state()
    val contracts: StateFlow<List<RentalContract>> = accounts.observeContracts(orderId).state()
    val signatureRequests: StateFlow<List<SignatureRequest>> = accounts.observeSignatureRequests(orderId)
        .map { rs -> rs.sortedByDescending { it.createdAt } }.state()
    val subrentals: StateFlow<List<Subrental>> = accounts.observeSubrentals(orderId).state()

    /** Online payments are switched on for the account (unknown counts as off). */
    private val paymentsEnabled: StateFlow<Boolean> = online.observe(online.paymentsStatusKey)
        .map { OnlinePayments.enabled(it?.data) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Buy-out quotes of the order's rent-to-own contracts (contract id → quote). */
    val buyouts: StateFlow<Map<String, BuyoutQuote>> = contracts
        .flatMapLatest { cs ->
            val rto = cs.filter { it.ownsEquity }
            if (rto.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(rto.map { c -> online.observe(online.buyoutKey(c.id)).map { q -> c.id to BuyoutQuote.parse(q?.data) } }) { pairs ->
                    pairs.mapNotNull { (id, q) -> q?.let { id to it } }.toMap()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Whether the owner's post-charge window after the return is still open (scope policy). */
    private val windowOpen: StateFlow<Boolean> = combine(order, repo.observeLines(orderId), settings.observeScopePolicy()) { o, lines, policy ->
        o == null || PostCharges.withinWindow(PostCharges.returnedAt(o.status, lines, o.endAt), policy.postChargeWindowDays, Instant.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    data class Abilities(
        val addPostCharge: Boolean,
        /** The card on file may be charged (permission and online payments). */
        val chargeCard: Boolean,
        val voidPostCharge: Boolean,
        val adjustPrice: Boolean,
        val buyout: Boolean,
        /** The order takes post charges by status but its window after the return has closed. */
        val postChargeWindowClosed: Boolean = false,
    )

    val abilities: StateFlow<Abilities> = combine(order, paymentsEnabled, windowOpen, permissions.permissions, permissions.isRoot) { o, enabled, open, _, _ ->
        val status = o?.status
        val quote = permissions.can(PostCharges.PERMISSION_CREATE)
        val charge = enabled && permissions.can(PostCharges.PERMISSION_CHARGE)
        Abilities(
            // A closed order is billed online with "charge now" only.
            addPostCharge = o != null && !o.isLegacy && status != null && PostCharges.allowedFor(status) && quote && open &&
                (status != OrderStatus.CLOSED || charge),
            chargeCard = o != null && !o.isLegacy && charge,
            voidPostCharge = quote,
            adjustPrice = o != null && !o.isLegacy && status != null && PriceAdjustments.allowedFor(status) && permissions.can(PriceAdjustments.PERMISSION),
            buyout = permissions.can(PERMISSION_BUYOUT),
            postChargeWindowClosed = o != null && status != null && PostCharges.allowedFor(status) && quote && !open,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Abilities(false, false, false, false, false))

    // ── Events ──

    sealed interface Event {
        data class Message(@StringRes val res: Int, val arg: String? = null) : Event
        data class Failed(val detail: String?) : Event
        data object Offline : Event
        data class OpenUrl(val url: String) : Event
        data class OpenPdf(val file: File) : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private fun launchSafe(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "order accounts action failed")
                _events.emit(Event.Failed(e.message))
            } finally {
                _busy.value = false
            }
        }
    }

    /** Reports a refused / offline online answer; true for success. */
    private suspend fun report(r: RentalsOnline.Result<*>, errorRes: (String?, Int) -> Int? = { _, _ -> null }): Boolean = when (r) {
        is RentalsOnline.Result.Ok -> true
        is RentalsOnline.Result.Offline -> { _events.emit(Event.Offline); false }
        is RentalsOnline.Result.Refused -> {
            val res = when (r.code) {
                RentalsOnline.ACCOUNT_CHANGED -> R.string.rentals_gw_account_changed
                else -> errorRes(r.code, r.status)
            }
            _events.emit(if (res != null) Event.Message(res) else Event.Failed(r.message))
            false
        }
    }

    // ── Post charges ──

    private val _form = MutableStateFlow<PostChargeForm?>(null)
    /** The open add-charge dialog, or null. */
    val form: StateFlow<PostChargeForm?> = _form.asStateFlow()
    private val _formErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val formErrors: StateFlow<Map<String, String>> = _formErrors.asStateFlow()

    /** The evidence already uploaded for the open form (uri → file id), so a retry does not upload again. */
    private var uploaded: Pair<String, String>? = null

    fun openPostCharge(now: Instant = Instant.now()) {
        val o = order.value ?: return
        savedState[PC_CLIENT_UUID] = UUID.randomUUID().toString()
        uploaded = null
        _formErrors.value = emptyMap()
        _form.value = PostChargeForm(currency = o.currency, incurredAt = now, chargeNow = o.status == OrderStatus.CLOSED)
    }

    fun updatePostCharge(f: PostChargeForm) {
        _form.value = f
        if (_formErrors.value.isNotEmpty()) _formErrors.value = f.errors(Instant.now())
    }

    fun closePostCharge() {
        _form.value = null
    }

    fun submitPostCharge(now: Instant = Instant.now()) {
        val f = _form.value ?: return
        val o = order.value ?: return
        val errs = f.errors(now)
        _formErrors.value = errs
        if (errs.isNotEmpty()) return
        launchSafe {
            if (!f.online(o.status)) {
                accounts.createPostCharge(f.toDraft(o.id, null))
                _form.value = null
                _events.emit(Event.Message(R.string.rentals_pc_added))
                return@launchSafe
            }
            val acc = online.activeAccountId() ?: run { _events.emit(Event.Offline); return@launchSafe }
            val evidence = requireNotNull(f.evidence)
            val fileId = uploaded?.takeIf { it.first == evidence.uri }?.second
                ?: try {
                    uploads.upload(evidence).also { uploaded = evidence.uri to it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.d(e, "post charge evidence upload failed")
                    _events.emit(Event.Offline)
                    return@launchSafe
                }
            val uuid = savedState.get<String>(PC_CLIENT_UUID) ?: UUID.randomUUID().toString().also { savedState[PC_CLIENT_UUID] = it }
            val r = online.createPostChargeNow(acc, o.id, f.toRestBody(uuid, fileId, null))
            if (report(r, ::postChargeErrorRes)) {
                _form.value = null
                outcome((r as RentalsOnline.Result.Ok).value.let(PostChargeOutcome::parse), R.string.rentals_pc_added)
            }
        }
    }

    private suspend fun outcome(o: PostChargeOutcome, done: Int) {
        when {
            o.payUrl != null -> {
                _events.emit(Event.Message(R.string.rentals_pc_needs_action))
                _events.emit(Event.OpenUrl(o.payUrl))
            }
            o.failed -> _events.emit(Event.Message(R.string.rentals_pc_charge_failed))
            o.status == "charged" -> _events.emit(Event.Message(R.string.rentals_pc_charged))
            else -> _events.emit(Event.Message(done))
        }
    }

    /** Charges a stored charge to the card on file (online; the server replays a retried charge). */
    fun chargePostCharge(pc: PostCharge) = launchSafe {
        if (!abilities.value.chargeCard || !pc.chargeable) return@launchSafe
        val acc = online.activeAccountId() ?: run { _events.emit(Event.Offline); return@launchSafe }
        val r = online.chargePostCharge(acc, pc.orderId, pc.id)
        if (report(r, ::postChargeErrorRes)) outcome(PostChargeOutcome.parse((r as RentalsOnline.Result.Ok).value), R.string.rentals_pc_charged)
    }

    fun voidPostCharge(pc: PostCharge, reason: String?) = launchSafe {
        accounts.voidPostCharge(pc.id, reason)
        _events.emit(Event.Message(R.string.rentals_pc_voided))
    }

    /** Opens the pay page of a charge that waits for the customer (3-D Secure). */
    fun openPostChargePayLink(pc: PostCharge) = launchSafe {
        val id = pc.paymentRequestId ?: return@launchSafe
        val r = online.linkPaymentRequest(id)
        if (report(r)) OnlinePayments.payUrlOf((r as RentalsOnline.Result.Ok).value)?.let { _events.emit(Event.OpenUrl(it)) }
    }

    // ── Price adjustments ──

    fun addPriceAdjustment(draft: PriceAdjustmentDraft) = launchSafe {
        accounts.addPriceAdjustment(draft)
        _events.emit(Event.Message(R.string.rentals_adjust_saved))
    }

    // ── Contracts ──

    private val attempts = GatewayPayments.Attempts.decode(savedState.get<String>(ATTEMPTS_STATE))

    fun loadBuyout(contractId: String) = launchSafe { report(online.refreshBuyout(contractId)) }

    /** Exercises the buy-out at the quote the user confirmed; a changed quote is re-read and shown. */
    fun buyout(contract: RentalContract, quote: BuyoutQuote) = launchSafe {
        if (!abilities.value.buyout || !contract.buyable) return@launchSafe
        val quoted = quote.quote ?: return@launchSafe
        val expected = quoted.minor
        val acc = online.activeAccountId() ?: run { _events.emit(Event.Offline); return@launchSafe }
        val scope = GatewayPayments.Attempts.scope(acc, "buyout", contract.id)
        val key = when (val b = attempts.begin(scope, "$expected", expected) { UUID.randomUUID().toString() }) {
            is GatewayPayments.Attempts.Begin.Go -> b.key
            is GatewayPayments.Attempts.Begin.Blocked -> {
                val pending = b.pending.amountMinor?.let { m -> formatMoney(Money(m, quoted.currency)) }
                _events.emit(Event.Message(R.string.rentals_gw_pending_other, pending))
                return@launchSafe
            }
        }
        savedState[ATTEMPTS_STATE] = attempts.encode()
        val r = online.buyout(acc, contract.id, buildJsonObject { put("expected_quote_minor", expected) }, key)
        val uncertain = when (r) {
            is RentalsOnline.Result.Ok -> false
            is RentalsOnline.Result.Offline -> true
            is RentalsOnline.Result.Refused -> r.code != RentalsOnline.ACCOUNT_CHANGED && GatewayPayments.isUncertain(r.status)
        }
        attempts.settle(scope, key, uncertain)
        savedState[ATTEMPTS_STATE] = attempts.encode()
        when {
            r is RentalsOnline.Result.Ok -> _events.emit(Event.Message(R.string.rentals_contract_buyout_done))
            uncertain -> _events.emit(Event.Message(R.string.rentals_gw_uncertain))
            r is RentalsOnline.Result.Refused && r.code == "quote_changed" -> {
                online.refreshBuyout(contract.id)
                _events.emit(Event.Message(R.string.rentals_contract_quote_changed))
            }
            else -> report(r)
        }
    }

    fun equityStatement(contractId: String) = launchSafe {
        val r = online.equityStatementPdf(contractId)
        if (report(r)) _events.emit(Event.OpenPdf((r as RentalsOnline.Result.Ok).value))
    }

    // ── Signatures ──

    fun signedDocument(requestId: String) = launchSafe {
        val r = online.signaturePdf(requestId)
        if (report(r)) _events.emit(Event.OpenPdf((r as RentalsOnline.Result.Ok).value))
    }

    companion object {
        const val PERMISSION_BUYOUT = "rental.buyout"
        private const val PC_CLIENT_UUID = "post_charge_client_uuid"
        private const val ATTEMPTS_STATE = "accounts_attempts"
    }
}

/** Localised text of a post-charge refusal (null: show the server message). */
internal fun postChargeErrorRes(code: String?, status: Int): Int? = when (code) {
    "invalid_order_status" -> R.string.rentals_pc_err_order_status
    "order_closed" -> R.string.rentals_pc_err_order_closed
    "post_charge_window_closed" -> R.string.rentals_pc_err_window
    "no_customer" -> R.string.rentals_pc_err_no_customer
    "invalid_post_charge_status" -> R.string.rentals_pc_err_not_chargeable
    "post_charge_charged" -> R.string.rentals_pc_err_charged
    "post_charge_invoiced" -> R.string.rentals_pc_err_invoiced
    "online_payments_disabled" -> R.string.rentals_pc_err_payments_disabled
    else -> if (status == 403) R.string.rentals_pc_err_forbidden else null
}

/** Shows the events of [vm]: snackbar texts through [onMessage], pay pages and PDFs opened. */
@Composable
internal fun OrderAccountsEvents(vm: OrderAccountsViewModel, onMessage: suspend (String, Boolean) -> Unit) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val offline = stringResource(R.string.rentals_online_offline)
    val generic = stringResource(R.string.rentals_online_failed_generic)
    val pdfFailed = stringResource(R.string.rentals_sign_pdf_failed)
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is OrderAccountsViewModel.Event.Message ->
                    onMessage(if (e.arg != null) res.getString(e.res, e.arg) else res.getString(e.res), true)
                is OrderAccountsViewModel.Event.Failed ->
                    onMessage(e.detail?.takeIf { it.isNotBlank() }?.let { res.getString(R.string.rentals_online_failed, it) } ?: generic, false)
                OrderAccountsViewModel.Event.Offline -> onMessage(offline, false)
                is OrderAccountsViewModel.Event.OpenUrl ->
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, e.url.toUri())) }
                is OrderAccountsViewModel.Event.OpenPdf -> if (!openPdfFile(ctx, e.file)) onMessage(pdfFailed, false)
            }
        }
    }
}

// ── Post charges ─────────────────────────────────────────────────────────────────────

@StringRes
private fun postChargeKindLabel(kind: String): Int = when (kind) {
    "toll" -> R.string.rentals_pc_kind_toll
    "ticket" -> R.string.rentals_pc_kind_ticket
    else -> R.string.rentals_pc_kind_citation
}

private fun postChargeStatus(pc: PostCharge): Pair<Int, StatusTone> = when {
    pc.status == PostCharge.STATUS_CHARGED -> R.string.rentals_pc_status_charged to StatusTone.Success
    pc.requiresAction -> R.string.rentals_pc_status_action to StatusTone.Warning
    pc.status == PostCharge.STATUS_FAILED -> R.string.rentals_pc_status_failed to StatusTone.Danger
    pc.status == PostCharge.STATUS_VOIDED -> R.string.rentals_pc_status_void to StatusTone.Neutral
    else -> R.string.rentals_pc_status_billed to StatusTone.Info
}

/** Tolls, tickets and citations of the order; hidden for statuses that take none and while empty without rights. */
@Composable
internal fun PostChargesCard(
    charges: List<PostCharge>,
    orderStatus: String,
    abilities: OrderAccountsViewModel.Abilities,
    busy: Boolean,
    zone: ZoneId,
    onAdd: () -> Unit,
    onCharge: (PostCharge) -> Unit,
    onVoid: (PostCharge, String?) -> Unit,
    onOpenPayLink: (PostCharge) -> Unit,
) {
    if (charges.isEmpty() && !(abilities.addPostCharge && PostCharges.allowedFor(orderStatus)) && !abilities.postChargeWindowClosed) return
    var voiding by remember { mutableStateOf<PostCharge?>(null) }
    SectionCard(
        title = stringResource(R.string.rentals_pc_title),
        trailing = if (abilities.addPostCharge) {
            {
                TextButton(onClick = onAdd, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.rentals_pc_add), Modifier.padding(start = AvagoSpacing.xs))
                }
            }
        } else {
            null
        },
    ) {
        if (abilities.postChargeWindowClosed) CardNote(stringResource(R.string.rentals_pc_err_window))
        if (charges.isEmpty()) CardNote(stringResource(R.string.rentals_pc_empty))
        charges.forEachIndexed { i, pc ->
            if (i > 0) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
            PostChargeRow(
                pc, zone, busy,
                onCharge = if (abilities.chargeCard && pc.chargeable) ({ onCharge(pc) }) else null,
                onVoid = if (abilities.voidPostCharge && pc.voidable) ({ voiding = pc }) else null,
                onOpenPayLink = if (pc.requiresAction) ({ onOpenPayLink(pc) }) else null,
            )
        }
    }
    voiding?.let { pc ->
        ReasonDialog(
            title = stringResource(R.string.rentals_pc_void),
            destructive = true,
            onDismiss = { voiding = null },
        ) { reason ->
            voiding = null
            onVoid(pc, reason)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostChargeRow(
    pc: PostCharge,
    zone: ZoneId,
    busy: Boolean,
    onCharge: (() -> Unit)?,
    onVoid: (() -> Unit)?,
    onOpenPayLink: (() -> Unit)?,
) {
    val (statusRes, tone) = postChargeStatus(pc)
    ListItem(
        colors = cardListItemColors(),
        leadingContent = { LeadingBadge(Icons.Default.Receipt, tone) },
        overlineContent = { Text(stringResource(postChargeKindLabel(pc.kind))) },
        headlineContent = {
            Text(listOfNotNull(pc.reference, pc.authority).joinToString(" \u00b7 ").ifEmpty { pc.description ?: "\u2014" })
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                Text(formatDateTime(pc.incurredAt, zone))
                if (pc.adminFeeMinor > 0) {
                    Text(stringResource(R.string.rentals_pc_admin_fee_line, formatMoney(Money.of(pc.adminFeeMinor, pc.currency))))
                }
                pc.chargeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                pc.voidReason?.let { Text(it) }
                if (onCharge != null || onVoid != null || onOpenPayLink != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                        onCharge?.let {
                            OutlinedButton(onClick = it, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                                Text(stringResource(R.string.rentals_pc_charge))
                            }
                        }
                        onOpenPayLink?.let {
                            OutlinedButton(onClick = it, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                                Text(stringResource(R.string.rentals_pc_open_pay_link))
                            }
                        }
                        onVoid?.let {
                            TextButton(onClick = it, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                                Text(stringResource(R.string.rentals_pc_void), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(Money.of(pc.totalMinor, pc.currency)), style = MaterialTheme.typography.bodyMedium)
                StatusPill(stringResource(statusRes), tone)
                SyncStateBadge(pc.sync)
            }
        },
    )
}

private const val MAX_EVIDENCE_BYTES = 15L * 1024 * 1024

/** Display name and media type of a picked file, or null when unreadable or larger than 15 MB. */
private suspend fun evidenceInfo(ctx: Context, uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
    runCatching {
        val cr = ctx.contentResolver
        val type = cr.getType(uri) ?: return@runCatching null
        if (!type.startsWith("image/") && type != "application/pdf") return@runCatching null
        var name: String? = null
        var size: Long? = null
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)
                size = if (c.isNull(1)) null else c.getLong(1)
            }
        }
        if ((size ?: 0L) > MAX_EVIDENCE_BYTES) null else (name ?: "evidence") to type
    }.getOrNull()
}

/** Add a post charge: kind, amount, fee, date, reference, evidence (photo / PDF) and "charge now". */
@Composable
internal fun AddPostChargeDialog(
    form: PostChargeForm,
    errors: Map<String, String>,
    orderStatus: String,
    canChargeNow: Boolean,
    busy: Boolean,
    zone: ZoneId,
    onChange: (PostChargeForm) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    var pickError by rememberSaveable { mutableStateOf(false) }
    val closed = orderStatus == OrderStatus.CLOSED
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val info = evidenceInfo(ctx, uri)
                pickError = info == null
                info?.let { (name, type) -> onChange(form.copy(evidence = LocalFileRef(uri.toString(), type), evidenceName = name)) }
            }
        }
    }
    fun err(key: String): String? = when (errors[key]) {
        null -> null
        "required" -> res.getString(R.string.rentals_pc_err_required)
        "amount" -> res.getString(R.string.rentals_pc_err_amount)
        "future" -> res.getString(R.string.rentals_pc_err_future)
        else -> res.getString(R.string.rentals_pc_err_evidence)
    }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.rentals_pc_add_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                ChipRow {
                    PostCharge.KINDS.forEach { k ->
                        FilterChip(selected = form.kind == k, onClick = { onChange(form.copy(kind = k)) }, label = { Text(stringResource(postChargeKindLabel(k))) })
                    }
                }
                OutlinedTextField(
                    form.amount, { onChange(form.copy(amount = it)) },
                    label = { Text(stringResource(R.string.rentals_pc_amount)) },
                    isError = err("amount") != null, supportingText = err("amount")?.let { { Text(it) } },
                    keyboardOptions = decimal, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    form.adminFee, { onChange(form.copy(adminFee = it)) },
                    label = { Text(stringResource(R.string.rentals_pc_admin_fee)) },
                    isError = err("admin_fee") != null, supportingText = err("admin_fee")?.let { { Text(it) } },
                    keyboardOptions = decimal, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                DateTimeField(stringResource(R.string.rentals_pc_incurred), form.incurredAt, zone, { onChange(form.copy(incurredAt = it)) })
                err("incurred_at")?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(
                    form.reference, { onChange(form.copy(reference = it.take(120))) },
                    label = { Text(stringResource(R.string.rentals_pc_reference)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    form.authority, { onChange(form.copy(authority = it.take(200))) },
                    label = { Text(stringResource(R.string.rentals_pc_authority)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    form.description, { onChange(form.copy(description = it.take(500))) },
                    label = { Text(stringResource(R.string.rentals_pc_description)) }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                ) {
                    Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp))
                    Text(form.evidenceName ?: stringResource(R.string.rentals_pc_attach_evidence), Modifier.padding(start = AvagoSpacing.sm))
                }
                (err("evidence") ?: if (pickError) stringResource(R.string.rentals_pc_err_file) else null)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (canChargeNow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.rentals_pc_charge_now), Modifier.weight(1f))
                        Switch(checked = form.chargeNow || closed, enabled = !closed, onCheckedChange = { onChange(form.copy(chargeNow = it)) })
                    }
                }
                if (closed) {
                    Text(
                        stringResource(R.string.rentals_pc_closed_online),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(stringResource(R.string.rentals_pc_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.rentals_cancel)) } },
    )
}

// ── Ledger ───────────────────────────────────────────────────────────────────────────


private fun ledgerTone(e: LedgerEntry): StatusTone = when (e.state) {
    LedgerEntry.STATE_FAILED, LedgerEntry.STATE_CANCELED -> StatusTone.Neutral
    LedgerEntry.STATE_PENDING, LedgerEntry.STATE_AUTHORIZED -> StatusTone.Info
    else -> if (e.signedMinor < 0) StatusTone.Warning else StatusTone.Success
}

@Composable
private fun ledgerStateText(state: String): String? = when (state) {
    LedgerEntry.STATE_PENDING -> stringResource(R.string.rentals_ledger_state_pending)
    LedgerEntry.STATE_AUTHORIZED -> stringResource(R.string.rentals_ledger_state_authorized)
    LedgerEntry.STATE_FAILED -> stringResource(R.string.rentals_ledger_state_failed)
    LedgerEntry.STATE_CANCELED -> stringResource(R.string.rentals_ledger_state_canceled)
    else -> null
}

/**
 * Payments, deposits and refunds (`customer_ledger_entries`) of an order or a customer; the net
 * covers every entry while only the first [maxRows] are listed above the optional [footer].
 */
@Composable
internal fun OrderLedgerCard(
    entries: List<LedgerEntry>,
    zone: ZoneId,
    maxRows: Int = Int.MAX_VALUE,
    footer: (@Composable () -> Unit)? = null,
) {
    if (entries.isEmpty()) return
    val settled = entries.filter { it.state == LedgerEntry.STATE_SETTLED && it.voidReason == null }
    val currency = entries.first().currency
    SectionCard(
        title = stringResource(R.string.rentals_ledger_title),
        trailing = if (settled.isNotEmpty() && settled.all { it.currency == currency }) {
            { Text(stringResource(R.string.rentals_ledger_net, formatMoney(Money.of(settled.sumOf { it.signedMinor }, currency))), style = MaterialTheme.typography.labelLarge) }
        } else {
            null
        },
    ) {
        entries.take(maxRows).forEachIndexed { i, e ->
            if (i > 0) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
            LedgerRow(e, zone)
        }
        footer?.invoke()
    }
}

@Composable
private fun LedgerRow(e: LedgerEntry, zone: ZoneId) {
    val stateText = ledgerStateText(e.state)
    ListItem(
        colors = cardListItemColors(),
        leadingContent = { LeadingBadge(Icons.Default.Payments, ledgerTone(e)) },
        overlineContent = e.receivedAt?.let { at -> { Text(formatDateTime(at, zone)) } },
        headlineContent = { Text(listOfNotNull(stringResource(ledgerKindLabel(e.kind)), e.method?.let { stringResource(methodLabel(it)) }).joinToString(" \u00b7 ")) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                val details = listOfNotNull(
                    stateText,
                    ledgerCardText(e)?.let { card -> e.cardExpiry?.let { stringResource(R.string.rentals_ledger_card, card, it) } ?: card },
                    e.payerName?.let { stringResource(R.string.rentals_ledger_payer, it) } ?: e.payerEmail?.let { stringResource(R.string.rentals_ledger_payer, it) },
                    e.receiptNumber?.let { stringResource(R.string.rentals_ledger_receipt, it) },
                    e.reference,
                )
                if (details.isNotEmpty()) Text(details.joinToString(" \u00b7 "))
                e.failureMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                e.voidReason?.let { Text(stringResource(R.string.rentals_ledger_voided, it)) }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(Money.of(e.signedMinor, e.currency)), style = MaterialTheme.typography.bodyMedium)
                SyncStateBadge(e.sync)
            }
        },
    )
}

// ── Price adjustment ─────────────────────────────────────────────────────────────────

@StringRes
private fun adjustKindLabel(kind: String): Int = when (kind) {
    "discount" -> R.string.rentals_adjust_kind_discount
    "fee" -> R.string.rentals_adjust_kind_fee
    "damage" -> R.string.rentals_adjust_kind_damage
    "fuel" -> R.string.rentals_adjust_kind_fuel
    "late" -> R.string.rentals_adjust_kind_late
    "overage" -> R.string.rentals_adjust_kind_overage
    else -> R.string.rentals_adjust_kind_adjustment
}

/** A manual price line with a reason (queued as `rental.rental_order_lines.create`). */
@Composable
internal fun PriceAdjustmentDialog(orderId: String, currency: String, onDismiss: () -> Unit, onSave: (PriceAdjustmentDraft) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(PriceAdjustmentDraft.KINDS.first()) }
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    val draft = PriceAdjustments.draft(orderId, kind, description, amount, reason, currency)
    val amountInvalid = amount.isNotBlank() && PriceAdjustments.amountMinor(kind, amount, currency) == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rentals_adjust_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                ChipRow {
                    PriceAdjustmentDraft.KINDS.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(stringResource(adjustKindLabel(k))) })
                    }
                }
                OutlinedTextField(
                    description, { description = it.take(200) },
                    label = { Text(stringResource(R.string.rentals_adjust_description)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    amount, { amount = it },
                    label = { Text(stringResource(R.string.rentals_adjust_amount, currency)) },
                    isError = amountInvalid,
                    supportingText = {
                        Text(
                            stringResource(
                                when {
                                    amountInvalid -> R.string.rentals_adjust_err_amount
                                    kind == PriceAdjustments.KIND_ADJUSTMENT -> R.string.rentals_adjust_negative_hint
                                    else -> R.string.rentals_pc_err_amount
                                },
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = if (kind == PriceAdjustments.KIND_ADJUSTMENT) KeyboardType.Text else KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    reason, { reason = it.take(500) },
                    label = { Text(stringResource(R.string.rentals_adjust_reason)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { draft?.let(onSave) }, enabled = draft != null) { Text(stringResource(R.string.rentals_adjust_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rentals_cancel)) } },
    )
}

// ── Contracts ────────────────────────────────────────────────────────────────────────

@Composable
internal fun contractStatus(c: RentalContract): Pair<String, StatusTone> = when {
    c.boughtOutAt != null || c.status == "bought_out" -> stringResource(R.string.rentals_contract_bought_out) to StatusTone.Info
    c.terminatedAt != null || c.status == "terminated" -> stringResource(R.string.rentals_contract_terminated) to StatusTone.Neutral
    c.status == "notice_given" -> stringResource(R.string.rentals_contract_notice) to StatusTone.Warning
    else -> stringResource(R.string.rentals_contract_active) to StatusTone.Success
}

/** A long-term or rent-to-own contract of the order: terms, buy-out quote, buy-out and equity statement. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContractCard(
    contract: RentalContract,
    quote: BuyoutQuote?,
    canBuyout: Boolean,
    busy: Boolean,
    zone: ZoneId,
    onLoadQuote: () -> Unit,
    onBuyout: (BuyoutQuote) -> Unit,
    onEquityStatement: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val (statusText, tone) = contractStatus(contract)
    SectionCard(
        title = stringResource(if (contract.ownsEquity) R.string.rentals_contract_rto else R.string.rentals_contract_long_term),
        trailing = { StatusPill(statusText, tone, icon = Icons.Default.Handshake) },
    ) {
        Column(Modifier.padding(vertical = AvagoSpacing.sm)) {
            contract.number?.let { LabelValue(stringResource(R.string.rentals_contract_number), it) }
            contract.monthlyRateMinor?.let { LabelValue(stringResource(R.string.rentals_contract_monthly), formatMoney(Money.of(it, contract.currency))) }
            contract.endAt?.let { LabelValue(stringResource(R.string.rentals_contract_ends), formatDate(it, zone)) }
            contract.purchasePriceMinor?.let { LabelValue(stringResource(R.string.rentals_contract_purchase_price), formatMoney(Money.of(it, contract.currency))) }
            if (contract.ownsEquity && quote != null) {
                quote.equity?.let { LabelValue(stringResource(R.string.rentals_contract_equity), formatMoney(it)) }
                quote.quote?.let { LabelValue(stringResource(R.string.rentals_contract_buyout), formatMoney(it), emphasise = true) }
                quote.asOf?.let {
                    Text(
                        stringResource(R.string.rentals_contract_quote_as_of, formatDateTime(it, zone)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AvagoSpacing.md),
                    )
                }
            }
        }
        if (contract.ownsEquity) {
            CardDivider()
            FlowRow(
                Modifier.padding(AvagoSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs),
            ) {
                if (contract.live) {
                    OutlinedButton(onClick = onLoadQuote, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.rentals_contract_show_quote))
                    }
                }
                OutlinedButton(onClick = onEquityStatement, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                    Icon(Icons.Default.Description, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.rentals_contract_equity_pdf), Modifier.padding(start = AvagoSpacing.sm))
                }
                if (canBuyout && contract.buyable && quote?.quote != null) {
                    OutlinedButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.rentals_contract_buyout_action))
                    }
                }
            }
        }
    }
    val q = quote?.quote
    if (confirm && quote != null && q != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.rentals_contract_buyout_title)) },
            text = { Text(stringResource(R.string.rentals_contract_buyout_body, formatMoney(q))) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onBuyout(quote) }) { Text(stringResource(R.string.rentals_contract_buyout_action)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.rentals_cancel)) } },
        )
    }
}

// ── Signature requests ───────────────────────────────────────────────────────────────

private fun sigStatus(status: String): Pair<Int, StatusTone> = when (status) {
    "completed" -> R.string.rentals_sigreq_status_completed to StatusTone.Success
    "declined" -> R.string.rentals_sigreq_status_declined to StatusTone.Danger
    "expired" -> R.string.rentals_sigreq_status_expired to StatusTone.Neutral
    "voided" -> R.string.rentals_sigreq_status_voided to StatusTone.Neutral
    else -> R.string.rentals_sigreq_status_pending to StatusTone.Warning
}

@Composable
private fun signerText(s: SignatureSigner): String = when (s.status) {
    "signed" -> stringResource(R.string.rentals_sigreq_signer_signed, s.name)
    "declined" -> stringResource(R.string.rentals_sigreq_signer_declined, s.name)
    else -> stringResource(R.string.rentals_sigreq_signer_waiting, s.name)
}

/** Signature requests of the order with their signers; a completed one opens its signed document. */
@Composable
internal fun SignatureRequestsCard(requests: List<SignatureRequest>, busy: Boolean, zone: ZoneId, onOpen: (SignatureRequest) -> Unit) {
    if (requests.isEmpty()) return
    SectionCard(title = stringResource(R.string.rentals_sigreq_title)) {
        requests.forEachIndexed { i, r ->
            if (i > 0) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
            val (res, tone) = sigStatus(r.status)
            ListItem(
                colors = cardListItemColors(),
                leadingContent = { LeadingBadge(Icons.Default.Draw, tone) },
                overlineContent = r.createdAt?.let { at -> { Text(formatDateTime(at, zone)) } },
                headlineContent = { Text(r.docType ?: r.purpose) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                        r.signers.sortedBy { it.order }.forEach { Text(signerText(it)) }
                        r.voidReason?.let { Text(it) }
                        if (r.status == "completed") {
                            TextButton(onClick = { onOpen(r) }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                                Icon(Icons.Default.Description, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.rentals_sign_view_document), Modifier.padding(start = AvagoSpacing.sm))
                            }
                        }
                    }
                },
                trailingContent = { StatusPill(stringResource(res), tone) },
            )
        }
    }
}

// ── Sub-rentals ──────────────────────────────────────────────────────────────────────

private fun subrentalStatus(status: String): Pair<Int, StatusTone>? = when (status) {
    "planned" -> R.string.rentals_subrental_status_planned to StatusTone.Neutral
    "ordered" -> R.string.rentals_subrental_status_ordered to StatusTone.Info
    "received" -> R.string.rentals_subrental_status_received to StatusTone.Accent
    "returned" -> R.string.rentals_subrental_status_returned to StatusTone.Success
    "cancelled", "canceled" -> R.string.rentals_subrental_status_cancelled to StatusTone.Neutral
    else -> null
}

/** Equipment hired in from suppliers for this order. */
@Composable
internal fun SubrentalsCard(subrentals: List<Subrental>, zone: ZoneId) {
    if (subrentals.isEmpty()) return
    SectionCard(title = stringResource(R.string.rentals_subrentals_title)) {
        subrentals.forEachIndexed { i, s ->
            if (i > 0) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
            val status = subrentalStatus(s.status)
            ListItem(
                colors = cardListItemColors(),
                leadingContent = { LeadingBadge(Icons.Default.LocalShipping, status?.second ?: StatusTone.Neutral) },
                overlineContent = s.number?.let { { Text(it) } },
                headlineContent = { Text(s.description ?: stringResource(R.string.rentals_subrented)) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            s.vendorName?.let { stringResource(R.string.rentals_subrental_vendor, it) },
                            s.plannedEnd?.takeIf { s.returnedAt == null }?.let { stringResource(R.string.rentals_subrental_due, formatDateTime(it, zone)) },
                            s.costRateMinor?.let { m ->
                                s.currency?.let { cur ->
                                    val money = formatMoney(Money.of(m, cur))
                                    s.costPeriod?.let { stringResource(R.string.rentals_subrental_cost, money, it) } ?: money
                                }
                            },
                            s.poReference?.let { stringResource(R.string.rentals_subrental_po, it) },
                        ).joinToString(" \u00b7 "),
                    )
                },
                trailingContent = { StatusPill(status?.let { stringResource(it.first) } ?: s.status, status?.second ?: StatusTone.Neutral) },
            )
        }
    }
}

private fun ledgerCardText(e: LedgerEntry): String? =
    listOfNotNull(e.cardBrand, e.cardLast4?.let { "\u2022\u2022\u2022\u2022 $it" }).joinToString(" ").ifBlank { null }
