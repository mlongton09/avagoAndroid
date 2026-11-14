package com.avago.feature.rentals.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.LocalFleet
import com.avago.core.data.rental.Money
import com.avago.core.data.rental.MoneyFormatter
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.VehicleSketchModel.SketchMark
import com.avago.feature.log.ui.SignaturePad
import com.avago.feature.log.ui.VehicleSketchLegend
import com.avago.feature.log.ui.VehicleSketchView
import com.avago.feature.log.ui.inspectionLocationLabel
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.RentalRepository
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.bool
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.objs
import com.avago.feature.rentals.domain.str
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class InspectionOption(val logId: String, val title: String, val at: Instant)
data class HandoverUnit(val assetId: String, val name: String, val assetType: String?, val lineId: String?)

/** Charge (amount in the order currency, minor units) or waive (with reason) for new damage. */
data class DamageDecision(val charge: Boolean, val chargeMinor: Long?, val reason: String?) {
    val isValid: Boolean get() = if (charge) (chargeMinor ?: 0L) > 0L else !reason.isNullOrBlank()
}

/**
 * Damage marks of a stored handover as sketch pins: the server keeps them as
 * `damage_summary[{label, severity, decision, location, …}]`; a pending local copy holds the
 * request's `damage[]`. `location` is the sketch zone key (`zone` read for older local rows).
 */
internal fun marksOf(rec: RentalRecordEntity?): List<SketchMark> {
    val o = rec?.obj ?: return emptyList()
    val list = o.objs("damage_summary").ifEmpty { o.objs("damage") }
    return list.mapIndexedNotNull { i, m ->
        val zone = m.str("location") ?: m.str("zone") ?: return@mapIndexedNotNull null
        SketchMark(m.str("id") ?: "b$i", zone, m.str("severity") ?: InspectionV2.SEV_MINOR, m.str("label") ?: "", i + 1)
    }
}

/**
 * Baseline for a handover (§14): check-in compares with this order's check-out; check-out
 * compares with the unit's latest check-in. Pure so it can be unit-tested.
 */
internal fun baselineFor(direction: String, orderId: String, handovers: List<RentalRecordEntity>): RentalRecordEntity? {
    val sorted = handovers.sortedByDescending { it.obj.str("at") ?: "" }
    return if (direction == "in") {
        sorted.firstOrNull { it.orderId == orderId && it.obj.str("kind") == "checkout" }
    } else {
        sorted.firstOrNull { it.obj.str("kind") == "checkin" }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HandoverViewModel @Inject constructor(
    private val repo: RentalRepository,
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    savedState: SavedStateHandle,
) : ViewModel() {
    val orderId: String = requireNotNull(savedState["orderId"])
    val direction: String = savedState.get<String>("direction")?.takeIf { it == "in" } ?: "out"

    private val lines = repo.byOrder(RentalEntity.ORDER_LINE, orderId)
    private val allocations = repo.byOrder(RentalEntity.ALLOCATION, orderId)
    private val fleet = repo.records(RentalEntity.FLEET)

    val units: StateFlow<List<HandoverUnit>> = combine(lines, allocations, fleet) { ls, al, fl ->
        val byId = fl.mapNotNull(LocalFleet::unitOf).associateBy { it.assetId }
        val fromLines = ls.mapNotNull { l -> l.assetId?.let { it to l.id } }
        val fromAllocs = al.filter { it.obj.bool("active") != false }.mapNotNull { a -> a.assetId?.let { it to a.obj.str("order_line_id") } }
        (fromLines + fromAllocs).distinctBy { it.first }.map { (id, line) ->
            HandoverUnit(id, byId[id]?.name ?: id, byId[id]?.assetType, line)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selected = MutableStateFlow<String?>(null)

    private val unit: StateFlow<HandoverUnit?> = combine(units, selected) { us, s -> us.firstOrNull { it.assetId == s } ?: us.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val currentUnit: StateFlow<HandoverUnit?> get() = unit

    val baseline: StateFlow<List<SketchMark>> = unit.flatMapLatest { u ->
        if (u == null) flowOf(emptyList()) else repo.byAsset(RentalEntity.HANDOVER, u.assetId).map { marksOf(baselineFor(direction, orderId, it)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Recent inspection logs (last 24 h) for the unit — the inspection v2 form. */
    val inspections: StateFlow<List<InspectionOption>> = unit.flatMapLatest { u ->
        val acc = identity.getActiveAccountId()
        if (u == null || acc == null) flowOf(emptyList())
        else dbFactory.get(acc).logDao().observeAll(acc).map { logs ->
            val since = System.currentTimeMillis() - 24 * 3_600_000L
            logs.filter { it.assetId == u.assetId && it.entryDate >= since && (it.category == "inspection" || it.data?.contains("\"inspection\"") == true) }
                .sortedByDescending { it.entryDate }
                .map { InspectionOption(it.entryId, it.title, Instant.ofEpochMilli(it.entryDate)) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val logId = MutableStateFlow<String?>(null)
    val marks = MutableStateFlow<List<SketchMark>>(emptyList())
    /** Charge / waive decision per new check-in mark id. */
    val decisions = MutableStateFlow<Map<String, DamageDecision>>(emptyMap())
    val meter = MutableStateFlow("")
    val meterKind = MutableStateFlow("hours")
    val fuel = MutableStateFlow<Int?>(null)
    val notes = MutableStateFlow("")
    val signature = MutableStateFlow<String?>(null)
    val signer = MutableStateFlow("")

    /** Currency of the order (damage charges are in the order currency). */
    val currency: StateFlow<String?> = repo.record(RentalEntity.ORDER, orderId).map { it?.obj?.str("currency")?.trim()?.takeIf { c -> c.length == 3 } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** New check-in damage still without a charge or a waive reason (save is blocked). */
    val undecided: StateFlow<Int> = combine(marks, decisions) { ms, ds ->
        ms.count { it.isNew && ds[it.id]?.isValid != true }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _done = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val done: SharedFlow<String> = _done
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val errors: SharedFlow<String> = _errors

    /** Adds a pin; returns its id. */
    fun addMark(zone: String, severity: String, label: String): String {
        val base = baseline.value
        val isNew = direction == "in" && base.none { it.zone == zone }
        val list = marks.value
        val id = "m" + UUID.randomUUID().toString().take(8)
        marks.value = list + SketchMark(id, zone, severity, label, list.size + 1, isNew)
        return id
    }

    /** True when a pin in [zone] is new check-in damage (needs a charge / waive decision). */
    fun isNewDamage(zone: String): Boolean = direction == "in" && baseline.value.none { it.zone == zone }

    fun removeMark(id: String) {
        marks.value = marks.value.filter { it.id != id }.mapIndexed { i, m -> m.copy(number = i + 1) }
        decisions.value = decisions.value - id
    }

    fun setDecision(id: String, d: DamageDecision?) {
        decisions.value = if (d == null) decisions.value - id else decisions.value + (id to d)
    }

    fun moveMark(m: SketchMark, zone: String) {
        marks.value = marks.value.map { if (it.id == m.id) it.copy(zone = zone, isNew = direction == "in" && baseline.value.none { b -> b.zone == zone }) else it }
    }

    /** @param defaultSigner localised name used when the customer leaves the name blank. */
    fun save(defaultSigner: String) = viewModelScope.launch {
        val u = unit.value ?: return@launch
        try {
            val acc = identity.getActiveAccountId()
            val photoIds = logId.value?.let { id -> acc?.let { dbFactory.get(it).photoDao().observeByEntity(id, "log").first() } }
                ?.map { it.photoId }.orEmpty()
            val ds = decisions.value
            val id = repo.saveHandover(
                RentalRepository.HandoverInput(
                    orderId = orderId,
                    orderLineId = u.lineId,
                    assetId = u.assetId,
                    direction = direction,
                    meterReading = meter.value.replace(',', '.').toDoubleOrNull(),
                    meterKind = meterKind.value,
                    fuelLevel = fuel.value,
                    marks = marks.value.map {
                        val d = ds[it.id].takeIf { _ -> it.isNew }
                        RentalRepository.HandoverInput.Mark(
                            it.id, it.zone, it.severity, it.label, it.isNew,
                            chargeMinor = d?.chargeMinor?.takeIf { _ -> d.charge },
                            reason = d?.reason?.takeIf { _ -> !d.charge },
                        )
                    },
                    notes = notes.value,
                    logId = logId.value,
                    photoIds = photoIds,
                    signatureDataUrl = signature.value,
                    signerName = signer.value.takeIf { it.isNotBlank() } ?: defaultSigner,
                    occurredAt = Instant.now(),
                    zoneId = ZoneId.systemDefault().id,
                ),
            )
            _done.emit(id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "handover save failed")
            _errors.emit(e.message ?: e.javaClass.simpleName)
        }
    }
}

/**
 * Check-out / check-in (§14): inspection v2 log, vehicle sketch with baseline comparison,
 * meter + fuel, customer signature. Event time is the device clock; saved offline and uploaded
 * in order (photos → inspection log → signature → handover → ledger).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HandoverScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onStartInspection: (assetId: String) -> Unit,
    /** After a check-in: open the unit's readiness checklist instead of just going back. */
    onCheckedIn: ((assetId: String) -> Unit)? = null,
    viewModel: HandoverViewModel = hiltViewModel(),
) {
    val units by viewModel.units.collectAsStateWithLifecycle()
    val unit by viewModel.currentUnit.collectAsStateWithLifecycle()
    val baseline by viewModel.baseline.collectAsStateWithLifecycle()
    val inspections by viewModel.inspections.collectAsStateWithLifecycle()
    val logId by viewModel.logId.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val meter by viewModel.meter.collectAsStateWithLifecycle()
    val meterKind by viewModel.meterKind.collectAsStateWithLifecycle()
    val fuel by viewModel.fuel.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val signature by viewModel.signature.collectAsStateWithLifecycle()
    val signer by viewModel.signer.collectAsStateWithLifecycle()
    val decisions by viewModel.decisions.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val undecided by viewModel.undecided.collectAsStateWithLifecycle()
    val defaultSigner = stringResource(R.string.rentals_customer)
    var addZone by remember { mutableStateOf<String?>(null) }
    var editMark by remember { mutableStateOf<SketchMark?>(null) }
    var customerMode by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.done.collect {
            val asset = viewModel.currentUnit.value?.assetId
            if (viewModel.direction == "in" && asset != null && onCheckedIn != null) onCheckedIn(asset) else onDone()
        }
    }
    LaunchedEffect(Unit) { viewModel.errors.collect { snackbar.showSnackbar(it) } }
    val out = viewModel.direction == "out"
    val zone = ZoneId.systemDefault()

    Scaffold(
        topBar = { RentalsTopBar(stringResource(if (out) R.string.rentals_action_checkout else R.string.rentals_action_checkin), onBack) },
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
            if (units.isEmpty()) {
                Text(stringResource(R.string.rentals_handover_no_unit), color = MaterialTheme.colorScheme.error)
            }
            if (units.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    units.forEach { u ->
                        FilterChip(unit?.assetId == u.assetId, { viewModel.selected.value = u.assetId }, { Text(u.name) }, Modifier.heightIn(min = MinTouch))
                    }
                }
            } else unit?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }

            SectionHeader(stringResource(R.string.rentals_inspection))
            inspections.forEach { opt ->
                ListItem(
                    modifier = Modifier.selectable(selected = logId == opt.logId, role = Role.RadioButton) {
                        viewModel.logId.value = if (logId == opt.logId) null else opt.logId
                    },
                    leadingContent = { RadioButton(selected = logId == opt.logId, onClick = null) },
                    headlineContent = { Text(opt.title) },
                    supportingContent = { Text(formatDateTime(opt.at, zone)) },
                )
            }
            if (inspections.isEmpty()) Text(stringResource(R.string.rentals_no_recent_inspection), style = MaterialTheme.typography.bodySmall)
            unit?.let { u ->
                OutlinedButton(onClick = { onStartInspection(u.assetId) }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentals_start_inspection))
                }
            }

            SectionHeader(stringResource(R.string.rentals_damage))
            Text(
                stringResource(if (baseline.isEmpty()) R.string.rentals_sketch_hint else R.string.rentals_sketch_hint_baseline),
                style = MaterialTheme.typography.bodySmall,
            )
            VehicleSketchView(
                assetType = unit?.assetType,
                marks = marks,
                baseline = baseline,
                onZoneTap = { addZone = it },
                onMarkMove = viewModel::moveMark,
                onMarkEdit = { editMark = it },
                modifier = Modifier.fillMaxWidth().height(320.dp),
            )
            VehicleSketchLegend(showBaseline = baseline.isNotEmpty())
            val newCount = marks.count { it.isNew }
            if (!out && baseline.isNotEmpty()) {
                Text(
                    stringResource(R.string.rentals_new_damage_count, newCount),
                    color = if (newCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            marks.forEach { m ->
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { editMark = m },
                    headlineContent = { Text("${m.number}. ${inspectionLocationLabel(m.zone) ?: m.zone}") },
                    supportingContent = { Text(listOf(severityLabel(m.severity), m.label).filter { it.isNotBlank() }.joinToString(" · ")) },
                )
            }

            SectionHeader(stringResource(R.string.rentals_meter_fuel))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    meter, { viewModel.meter.value = it },
                    label = { Text(stringResource(R.string.rentals_meter)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(meterKind == "hours", { viewModel.meterKind.value = "hours" }, { Text(stringResource(R.string.rentals_meter_hours)) }, Modifier.heightIn(min = MinTouch))
                FilterChip(meterKind == "distance", { viewModel.meterKind.value = "distance" }, { Text(stringResource(R.string.rentals_meter_distance)) }, Modifier.heightIn(min = MinTouch))
            }
            val fuelText = fuel?.let { "$it %" } ?: stringResource(R.string.rentals_not_recorded)
            Text(stringResource(R.string.rentals_fuel_level, fuelText))
            Slider(
                value = (fuel ?: 0).toFloat(),
                onValueChange = { viewModel.fuel.value = (it / 5).toInt() * 5 },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.semantics { stateDescription = fuelText },
            )
            OutlinedTextField(notes, { viewModel.notes.value = it }, label = { Text(stringResource(R.string.rentals_notes)) }, modifier = Modifier.fillMaxWidth())

            SectionHeader(stringResource(R.string.rentals_signature))
            Text(
                stringResource(if (signature != null) R.string.rentals_signed else R.string.rentals_not_signed),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { customerMode = true }, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(R.string.rentals_hand_to_customer))
            }

            if (undecided > 0) {
                Text(stringResource(R.string.rentals_damage_needs_decision), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { viewModel.save(defaultSigner) },
                enabled = unit != null && undecided == 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
            ) { Text(stringResource(if (out) R.string.rentals_complete_checkout else R.string.rentals_complete_checkin)) }
            Text(stringResource(R.string.rentals_handover_offline_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    addZone?.let { z ->
        MarkDialog(
            title = inspectionLocationLabel(z) ?: z,
            initialSeverity = InspectionV2.SEV_MINOR,
            initialLabel = "",
            chargeable = viewModel.isNewDamage(z),
            currency = currency,
            initialDecision = null,
            onDismiss = { addZone = null },
            onSave = { sev, label, decision ->
                val id = viewModel.addMark(z, sev, label)
                viewModel.setDecision(id, decision)
                addZone = null
            },
            onDelete = null,
        )
    }
    editMark?.let { m ->
        MarkDialog(
            title = inspectionLocationLabel(m.zone) ?: m.zone,
            initialSeverity = m.severity,
            initialLabel = m.label,
            chargeable = m.isNew,
            currency = currency,
            initialDecision = decisions[m.id],
            onDismiss = { editMark = null },
            onSave = { sev, label, decision ->
                viewModel.marks.value = viewModel.marks.value.map { if (it.id == m.id) it.copy(severity = sev, label = label) else it }
                viewModel.setDecision(m.id, decision)
                editMark = null
            },
            onDelete = { viewModel.removeMark(m.id); editMark = null },
        )
    }
    if (customerMode) {
        CustomerSignatureDialog(
            signature = signature,
            signer = signer,
            out = out,
            onSigner = { viewModel.signer.value = it },
            onSignature = { viewModel.signature.value = it },
            onDone = { customerMode = false },
        )
    }
}

@Composable
private fun severityLabel(sev: String): String = stringResource(
    when (sev) {
        InspectionV2.SEV_MAJOR -> R.string.rentals_severity_major
        InspectionV2.SEV_OUT_OF_SERVICE -> R.string.rentals_severity_out_of_service
        else -> R.string.rentals_severity_minor
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkDialog(
    title: String,
    initialSeverity: String,
    initialLabel: String,
    /** New check-in damage: ask for a charge (order currency) or a waive reason. */
    chargeable: Boolean,
    currency: String?,
    initialDecision: DamageDecision?,
    onDismiss: () -> Unit,
    onSave: (String, String, DamageDecision?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var sev by remember { mutableStateOf(initialSeverity) }
    var label by remember { mutableStateOf(initialLabel) }
    var charge by remember { mutableStateOf(initialDecision?.charge ?: true) }
    var amount by remember {
        mutableStateOf(
            initialDecision?.chargeMinor?.let { m -> currency?.let { MoneyFormatter.toInput(Money(m, it)) } }.orEmpty(),
        )
    }
    var reason by remember { mutableStateOf(initialDecision?.reason.orEmpty()) }
    val decision = if (!chargeable) null else DamageDecision(
        charge = charge,
        chargeMinor = if (charge) currency?.let { MoneyFormatter.parse(amount, it)?.minor } else null,
        reason = if (charge) null else reason.trim(),
    )
    val valid = decision?.isValid ?: true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(InspectionV2.SEV_MINOR, InspectionV2.SEV_MAJOR, InspectionV2.SEV_OUT_OF_SERVICE).forEach { s ->
                        FilterChip(sev == s, { sev = s }, { Text(severityLabel(s)) }, Modifier.heightIn(min = MinTouch))
                    }
                }
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.rentals_damage_label)) })
                if (chargeable) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(charge, { charge = true }, { Text(stringResource(R.string.rentals_damage_decision_charge)) }, Modifier.heightIn(min = MinTouch))
                        FilterChip(!charge, { charge = false }, { Text(stringResource(R.string.rentals_damage_decision_waive)) }, Modifier.heightIn(min = MinTouch))
                    }
                    if (charge) {
                        OutlinedTextField(
                            amount, { amount = it },
                            label = { Text(stringResource(R.string.rentals_damage_charge, currency ?: "")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = amount.isNotBlank() && !valid,
                            singleLine = true,
                        )
                    } else {
                        OutlinedTextField(reason, { reason = it }, label = { Text(stringResource(R.string.rentals_damage_waive_reason)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sev, label.trim(), decision) }, enabled = valid) { Text(stringResource(R.string.rentals_ok)) }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.rentals_remove)) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.rentals_cancel)) }
            }
        },
    )
}

/** Customer mode: full-screen, only the terms line, name and the Canvas signature pad. */
@Composable
private fun CustomerSignatureDialog(
    signature: String?,
    signer: String,
    out: Boolean,
    onSigner: (String) -> Unit,
    onSignature: (String?) -> Unit,
    onDone: () -> Unit,
) {
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.rentals_customer_sign_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(if (out) R.string.rentals_customer_sign_terms_out else R.string.rentals_customer_sign_terms_in))
                OutlinedTextField(signer, onSigner, label = { Text(stringResource(R.string.rentals_signer_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val desc = stringResource(R.string.rentals_signature_pad)
                SignaturePad(
                    signature = signature,
                    onSignatureChange = onSignature,
                    modifier = Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = desc },
                )
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_done)) }
            }
        }
    }
}
