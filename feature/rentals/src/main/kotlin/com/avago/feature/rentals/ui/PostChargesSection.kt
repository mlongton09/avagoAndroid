package com.avago.feature.rentals.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.cardListItemColors
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.PostChargeService
import com.avago.feature.rentals.domain.PostCharge
import com.avago.feature.rentals.domain.PostChargeForm
import com.avago.feature.rentals.domain.PostChargeList
import com.avago.feature.rentals.domain.PostCharges
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** Post-charges section state; [Hidden] until the order is synced and in a chargeable status. */
sealed interface PostChargesState {
    data object Hidden : PostChargesState
    data object Loading : PostChargesState
    data class Ready(val list: PostChargeList) : PostChargesState
    data class Failed(val error: ApiError) : PostChargesState
}

sealed interface PostChargeEvent {
    data class Done(val res: Int) : PostChargeEvent
    data class Failed(val error: ApiError) : PostChargeEvent
    data class OpenUrl(val url: String) : PostChargeEvent
}

@HiltViewModel
class PostChargesViewModel @Inject constructor(private val service: PostChargeService) : ViewModel() {
    private val _state = MutableStateFlow<PostChargesState>(PostChargesState.Hidden)
    val state: StateFlow<PostChargesState> = _state.asStateFlow()
    private val _form = MutableStateFlow<PostChargeForm?>(null)
    /** Open add-charge dialog, or null. */
    val form: StateFlow<PostChargeForm?> = _form.asStateFlow()
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()
    private val _events = MutableSharedFlow<PostChargeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PostChargeEvent> = _events

    private var orderId: String? = null
    private var currency: String = ""
    /** Idempotency key for the open form, so a retried create can't double-bill. */
    private var clientUuid: String = UUID.randomUUID().toString()

    /** Shows the section for a synced order whose status accepts post-charges; [currency] is the order's. */
    fun bind(orderId: String?, orderStatus: String, currency: String) {
        this.currency = currency
        if (orderId == null || !PostCharges.allowedFor(orderStatus)) {
            this.orderId = null
            _state.value = PostChargesState.Hidden
            return
        }
        if (this.orderId == orderId && _state.value !is PostChargesState.Hidden) return
        this.orderId = orderId
        refresh()
    }

    fun refresh() {
        val id = orderId ?: return
        viewModelScope.launch {
            if (_state.value !is PostChargesState.Ready) _state.value = PostChargesState.Loading
            when (val r = service.list(id)) {
                is ApiResult.Ok -> _state.value = PostChargesState.Ready(r.value)
                is ApiResult.Err -> if (_state.value is PostChargesState.Ready) {
                    _events.emit(PostChargeEvent.Failed(r.error))
                } else {
                    _state.value = PostChargesState.Failed(r.error)
                }
            }
        }
    }

    fun openForm(now: Instant = Instant.now()) {
        clientUuid = UUID.randomUUID().toString()
        _errors.value = emptyMap()
        _form.value = PostChargeForm(currency = currency, incurredAt = now)
    }

    fun closeForm() {
        _form.value = null
    }

    fun update(f: PostChargeForm) {
        _form.value = f
        if (_errors.value.isNotEmpty()) _errors.value = f.errors(Instant.now())
    }

    fun attachEvidence(filename: String, contentType: String, bytes: ByteArray) {
        val id = orderId ?: return
        if (_uploading.value) return
        _uploading.value = true
        viewModelScope.launch {
            try {
                when (val r = service.uploadEvidence(id, filename, contentType, bytes)) {
                    is ApiResult.Ok -> _form.value?.let { update(it.copy(evidenceFileId = r.value, evidenceName = filename)) }
                    is ApiResult.Err -> _events.emit(PostChargeEvent.Failed(r.error))
                }
            } finally {
                _uploading.value = false
            }
        }
    }

    fun submit(now: Instant = Instant.now()) {
        val id = orderId ?: return
        val f = _form.value ?: return
        val errs = f.errors(now)
        _errors.value = errs
        if (errs.isNotEmpty()) return
        mutate(R.string.rentals_pc_added, { service.create(id, f.toBody(clientUuid)) }) { _form.value = null }
    }

    fun charge(chargeId: String) {
        val id = orderId ?: return
        mutate(R.string.rentals_pc_charged, { service.charge(id, chargeId) })
    }

    fun void(chargeId: String, reason: String?) {
        val id = orderId ?: return
        mutate(R.string.rentals_pc_voided, { service.void(id, chargeId, reason) })
    }

    private fun mutate(doneRes: Int, call: suspend () -> ApiResult<PostCharge>, onOk: () -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                when (val r = call()) {
                    is ApiResult.Ok -> {
                        onOk()
                        val pc = r.value
                        when {
                            pc.payUrl != null -> _events.emit(PostChargeEvent.OpenUrl(pc.payUrl))
                            pc.status == "charge_failed" -> _events.emit(PostChargeEvent.Done(R.string.rentals_pc_charge_failed))
                            else -> _events.emit(PostChargeEvent.Done(doneRes))
                        }
                        refresh()
                    }
                    is ApiResult.Err -> _events.emit(PostChargeEvent.Failed(r.error))
                }
            } finally {
                _busy.value = false
            }
        }
    }
}

internal fun postChargeErrorRes(e: ApiError): Int? = when {
    e.offline -> R.string.rentals_pc_err_offline
    else -> when (e.code) {
        "invalid_order_status" -> R.string.rentals_pc_err_order_status
        "order_closed" -> R.string.rentals_pc_err_order_closed
        "post_charge_window_closed" -> R.string.rentals_pc_err_window
        "no_customer" -> R.string.rentals_pc_err_no_customer
        "invalid_post_charge_status" -> R.string.rentals_pc_err_not_chargeable
        "post_charge_charged" -> R.string.rentals_pc_err_charged
        "post_charge_invoiced" -> R.string.rentals_pc_err_invoiced
        "online_payments_disabled" -> R.string.rentals_pc_err_payments_disabled
        else -> if (e.status == 403) R.string.rentals_pc_err_forbidden else null
    }
}

internal fun postChargeErrorText(ctx: Context, e: ApiError): String =
    postChargeErrorRes(e)?.let(ctx::getString) ?: e.message.ifBlank { ctx.getString(R.string.rentals_pc_err_generic) }

private fun kindLabel(kind: String): Int = when (kind) {
    "toll" -> R.string.rentals_pc_kind_toll
    "ticket" -> R.string.rentals_pc_kind_ticket
    else -> R.string.rentals_pc_kind_citation
}

private fun statusLabel(status: String): Pair<Int, StatusTone> = when (status) {
    "charged" -> R.string.rentals_pc_status_charged to StatusTone.Success
    "requires_action" -> R.string.rentals_pc_status_action to StatusTone.Warning
    "charge_failed" -> R.string.rentals_pc_status_failed to StatusTone.Danger
    "void" -> R.string.rentals_pc_status_void to StatusTone.Neutral
    else -> R.string.rentals_pc_status_billed to StatusTone.Info
}

private const val MAX_EVIDENCE_BYTES = 15 * 1024 * 1024

private suspend fun readEvidence(ctx: Context, uri: Uri): Triple<String, String, ByteArray>? = withContext(Dispatchers.IO) {
    runCatching {
        val cr = ctx.contentResolver
        val type = cr.getType(uri) ?: "application/octet-stream"
        val name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "evidence"
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        if (bytes.size > MAX_EVIDENCE_BYTES) null else Triple(name, type, bytes)
    }.getOrNull()
}

/** Tolls / tickets billed to the renter after the rental (feature I). Hidden for statuses the server rejects. */
@Composable
internal fun PostChargesSection(
    orderId: String?,
    orderStatus: String,
    currency: String,
    zone: ZoneId,
    onMessage: (String) -> Unit,
    viewModel: PostChargesViewModel = hiltViewModel(),
) {
    LaunchedEffect(orderId, orderStatus, currency) { viewModel.bind(orderId, orderStatus, currency) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var voiding by remember { mutableStateOf<PostCharge?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is PostChargeEvent.Done -> onMessage(ctx.getString(e.res))
                is PostChargeEvent.Failed -> onMessage(postChargeErrorText(ctx, e.error))
                is PostChargeEvent.OpenUrl -> {
                    onMessage(ctx.getString(R.string.rentals_pc_needs_action))
                    runCatching {
                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(e.url)))
                    }
                }
            }
        }
    }
    val s = state
    if (s is PostChargesState.Hidden) return
    SectionCard(
        title = stringResource(R.string.rentals_pc_title),
        trailing = {
            TextButton(onClick = { viewModel.openForm() }, enabled = s is PostChargesState.Ready, modifier = Modifier.heightIn(min = MinTouch)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Text(stringResource(R.string.rentals_pc_add), Modifier.padding(start = 4.dp))
            }
        },
    ) {
        when (s) {
            PostChargesState.Hidden, PostChargesState.Loading ->
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            is PostChargesState.Failed -> {
                CardNote(postChargeErrorText(ctx, s.error))
                TextButton(onClick = viewModel::refresh, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.rentals_pc_retry))
                }
            }
            is PostChargesState.Ready -> {
                if (s.list.items.isEmpty()) CardNote(stringResource(R.string.rentals_pc_empty))
                s.list.items.forEachIndexed { i, pc ->
                    if (i > 0) CardDivider()
                    PostChargeRow(pc, zone, busy, onCharge = { viewModel.charge(pc.id) }, onVoid = { voiding = pc })
                }
                s.list.windowDays?.let {
                    Text(
                        stringResource(R.string.rentals_pc_window, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
    form?.let { f ->
        val kinds = (s as? PostChargesState.Ready)?.list?.kinds ?: PostCharges.KINDS
        AddPostChargeDialog(f, kinds, zone, viewModel)
    }
    voiding?.let { pc ->
        ReasonDialog(stringResource(R.string.rentals_pc_void), onDismiss = { voiding = null }) { reason ->
            viewModel.void(pc.id, reason)
            voiding = null
        }
    }
}

@Composable
private fun PostChargeRow(pc: PostCharge, zone: ZoneId, busy: Boolean, onCharge: () -> Unit, onVoid: () -> Unit) {
    val (statusRes, tone) = statusLabel(pc.status)
    val at = pc.incurredAt?.let { runCatching { Instant.parse(it) }.getOrNull() }?.let { formatDateTime(it, zone) }
    ListItem(
        colors = cardListItemColors(),
        leadingContent = { LeadingBadge(Icons.Default.Receipt, tone) },
        overlineContent = { Text(stringResource(kindLabel(pc.kind))) },
        headlineContent = { Text(listOfNotNull(pc.reference, pc.authority).joinToString(" · ").ifEmpty { pc.description ?: "—" }) },
        supportingContent = {
            Column {
                Text(listOfNotNull(at, pc.evidenceName).joinToString(" · "))
                if (pc.adminFee.minor > 0) Text(stringResource(R.string.rentals_pc_admin_fee_line, formatMoney(pc.adminFee)))
                pc.chargeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                pc.voidReason?.let { Text(it) }
                if (pc.canCharge || pc.canVoid) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pc.canCharge) {
                            OutlinedButton(onClick = onCharge, enabled = !busy) { Text(stringResource(R.string.rentals_pc_charge)) }
                        }
                        if (pc.canVoid) TextButton(onClick = onVoid, enabled = !busy) { Text(stringResource(R.string.rentals_pc_void)) }
                    }
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(pc.total), style = MaterialTheme.typography.bodyMedium)
                StatusPill(stringResource(statusRes), tone)
            }
        },
    )
}

@Composable
private fun AddPostChargeDialog(f: PostChargeForm, kinds: List<String>, zone: ZoneId, viewModel: PostChargesViewModel) {
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val uploading by viewModel.uploading.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickError by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = readEvidence(ctx, uri)
                pickError = file == null
                file?.let { (name, type, bytes) -> viewModel.attachEvidence(name, type, bytes) }
            }
        }
    }
    fun err(key: String): String? = when (errors[key]) {
        null -> null
        "required" -> ctx.getString(R.string.rentals_pc_err_required)
        "amount" -> ctx.getString(R.string.rentals_pc_err_amount)
        "future" -> ctx.getString(R.string.rentals_pc_err_future)
        else -> ctx.getString(R.string.rentals_pc_err_evidence)
    }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    AlertDialog(
        onDismissRequest = { if (!busy) viewModel.closeForm() },
        title = { Text(stringResource(R.string.rentals_pc_add_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow {
                    kinds.forEach { k ->
                        FilterChip(selected = f.kind == k, onClick = { viewModel.update(f.copy(kind = k)) }, label = { Text(stringResource(kindLabel(k))) })
                    }
                }
                OutlinedTextField(
                    f.amount, { viewModel.update(f.copy(amount = it)) },
                    label = { Text(stringResource(R.string.rentals_pc_amount)) },
                    isError = err("amount") != null, supportingText = err("amount")?.let { { Text(it) } },
                    keyboardOptions = decimal, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    f.adminFee, { viewModel.update(f.copy(adminFee = it)) },
                    label = { Text(stringResource(R.string.rentals_pc_admin_fee)) },
                    isError = err("admin_fee") != null, supportingText = err("admin_fee")?.let { { Text(it) } },
                    keyboardOptions = decimal, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                DateTimeField(stringResource(R.string.rentals_pc_incurred), f.incurredAt, zone, { viewModel.update(f.copy(incurredAt = it)) })
                err("incurred_at")?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(
                    f.reference, { viewModel.update(f.copy(reference = it.take(120))) },
                    label = { Text(stringResource(R.string.rentals_pc_reference)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    f.authority, { viewModel.update(f.copy(authority = it.take(200))) },
                    label = { Text(stringResource(R.string.rentals_pc_authority)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    f.description, { viewModel.update(f.copy(description = it.take(500))) },
                    label = { Text(stringResource(R.string.rentals_pc_description)) }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
                    enabled = !uploading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uploading) CircularProgressIndicator(Modifier.size(18.dp)) else Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp))
                    Text(f.evidenceName ?: stringResource(R.string.rentals_pc_attach_evidence), Modifier.padding(start = 8.dp))
                }
                (err("evidence") ?: if (pickError) stringResource(R.string.rentals_pc_err_file) else null)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rentals_pc_charge_now), Modifier.weight(1f))
                    Switch(checked = f.chargeNow, onCheckedChange = { viewModel.update(f.copy(chargeNow = it)) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.submit() }, enabled = !busy && !uploading) { Text(stringResource(R.string.rentals_pc_save)) }
        },
        dismissButton = { TextButton(onClick = viewModel::closeForm, enabled = !busy) { Text(stringResource(R.string.rentals_cancel)) } },
    )
}
