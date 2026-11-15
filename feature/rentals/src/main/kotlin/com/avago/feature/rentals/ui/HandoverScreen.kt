package com.avago.feature.rentals.ui

import android.Manifest
import android.content.pm.PackageManager
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.core.design.theme.AvagoSpacing
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.LocalFleet
import com.avago.core.data.rental.Money
import com.avago.core.data.rental.MoneyFormatter
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.cardListItemColors
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.rememberHaptics
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.StepIndicator
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.VehicleSketchModel.SketchMark
import com.avago.feature.log.ui.inspectionLocationLabel
import com.avago.feature.log.ui.SignaturePad
import com.avago.feature.log.ui.VehicleSketchLegend
import com.avago.feature.log.ui.VehicleSketchView
import com.avago.feature.rentals.data.RentalRepository
import com.avago.feature.rentals.data.RentalsOnline
import com.avago.feature.rentals.domain.CreditCheck
import com.avago.feature.rentals.domain.Esign
import com.avago.feature.rentals.domain.HandoverChecks
import com.avago.feature.rentals.domain.HandoverGates
import com.avago.feature.rentals.domain.MeterInfo
import com.avago.core.permissions.PermissionsManager
import kotlinx.coroutines.flow.distinctUntilChanged
import com.avago.feature.rentals.domain.bool
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.objs
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.str
import com.avago.feature.rentals.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

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
    private val online: RentalsOnline,
    permissions: PermissionsManager,
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

    /** Condition photos on the selected inspection log (uploaded with the handover). */
    val photos: StateFlow<List<PhotoEntity>> = logId.flatMapLatest { id ->
        val acc = identity.getActiveAccountId()
        if (id == null || acc == null) flowOf(emptyList())
        else dbFactory.get(acc).photoDao().observeByEntity(id, "log").map { ps -> ps.filter { it.deletedAt == null }.sortedBy { it.sortOrder } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Adds a camera photo to inspection log [forLogId]; the rental outbox uploads it before the handover. */
    fun addPhoto(forLogId: String, file: java.io.File) = viewModelScope.launch {
        try {
            val acc = identity.getActiveAccountId() ?: return@launch
            val now = System.currentTimeMillis()
            dbFactory.get(acc).photoDao().upsert(
                PhotoEntity(
                    photoId = UUID.randomUUID().toString(),
                    entityId = forLogId,
                    entityType = "log",
                    accountId = acc,
                    storageKey = null,
                    downloadUrl = null,
                    sortOrder = photos.value.size.toLong(),
                    isPrimary = false,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    serverVersion = 0,
                    localPath = file.absolutePath,
                ),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "add handover photo failed")
            _errors.emit(e.message ?: e.javaClass.simpleName)
        }
    }

    val marks = MutableStateFlow<List<SketchMark>>(emptyList())
    /** Charge / waive decision per new check-in mark id. */
    val decisions = MutableStateFlow<Map<String, DamageDecision>>(emptyMap())
    val meter = MutableStateFlow("")
    val meterKind = MutableStateFlow("hours")
    val fuel = MutableStateFlow<Int?>(null)
    val notes = MutableStateFlow("")
    val signature = MutableStateFlow<String?>(null)
    val signer = MutableStateFlow("")

    // ── Wave 2: meter rules, e-signature and credit gates ───────────────────────────

    val canOverrideMeter: Boolean = permissions.can(HandoverGates.PERMISSION_METER_OVERRIDE)
    val canOverrideSignature: Boolean = permissions.can(Esign.PERMISSION_OVERRIDE)
    val canOverrideCredit: Boolean = permissions.can(HandoverGates.PERMISSION_CREDIT_OVERRIDE)
    val meterOverrideReason = MutableStateFlow("")
    val meterPhotoPath = MutableStateFlow<String?>(null)
    val overrideSignature = MutableStateFlow(false)
    val creditOverrideReason = MutableStateFlow("")

    /** Meter of the selected unit (last cached answer; refreshed when the unit changes). */
    val meterInfo: StateFlow<MeterInfo?> = unit.flatMapLatest { u ->
        if (u == null) flowOf(null) else online.observe(online.meterKey(u.assetId)).map { MeterInfo.parse(it?.data) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val esignCached = online.observe(online.signaturesKey(orderId))
    /** Signature status with queued in-person captures counted as signed; null when unknown. */
    val esign: StateFlow<Esign.Status?> = combine(esignCached, repo.pendingSignatureCaptures()) { c, pending ->
        c?.let { Esign.parse(it.data) }?.let { Esign.withLocalCaptures(it, pending) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val esignFresh = esignCached.map { HandoverChecks.fresh(it?.at, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val creditCached = online.observe(online.creditKey(orderId))
    val credit: StateFlow<CreditCheck?> = creditCached.map { CreditCheck.parse(it?.data) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val creditFresh = creditCached.map { HandoverChecks.fresh(it?.at, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    /** True after a failed / offline credit refresh (the screen says the status is unknown). */
    val creditUnknown = MutableStateFlow(false)

    private val checksInput: StateFlow<HandoverChecks.Input> = combine(
        combine(meterInfo, meter, meterOverrideReason) { m, t, r -> Triple(m, t, r) },
        combine(esign, esignFresh, overrideSignature) { e, f, o -> e.takeIf { f } to o },
        combine(credit, creditFresh, creditOverrideReason) { c, f, r -> c.takeIf { f } to r },
    ) { (m, t, r), (e, o), (c, cr) ->
        HandoverChecks.Input(direction, m, t, r, canOverrideMeter, e, o, c, cr, canOverrideCredit)
    }.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        HandoverChecks.Input(direction, null, "", "", canOverrideMeter, null, false, null, "", canOverrideCredit),
    )

    val blockers: StateFlow<List<HandoverChecks.Blocker>> = checksInput.map(HandoverChecks::blockers)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Check-out below the last reading (meter replaced?) — a warning, not a block. */
    val meterBelowPrevious: StateFlow<Boolean> = checksInput.map {
        it.meter?.check(it.meterText, direction) == MeterInfo.Check.BELOW_PREVIOUS
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            unit.map { it?.assetId }.distinctUntilChanged().collect { id ->
                id ?: return@collect
                meterPhotoPath.value = null
                (online.refreshMeter(id) as? RentalsOnline.Result.Ok)?.value?.let(MeterInfo::parse)?.let { m ->
                    // The unit's meter decides the kind (distance meters count km / mi).
                    when (m.kind) {
                        "hours" -> meterKind.value = "hours"
                        "distance", "odometer" -> meterKind.value = "distance"
                    }
                }
            }
        }
        if (direction == "out") refreshGates()
    }

    /** Refreshes the signature status and the credit check (check-out only). */
    fun refreshGates() = viewModelScope.launch {
        online.refreshSignatures(orderId)
        val lineIds = units.value.mapNotNull { it.lineId }
        creditUnknown.value = online.refreshCreditCheck(orderId, lineIds) !is RentalsOnline.Result.Ok
    }

    /** Currency of the order (damage charges are in the order currency). */
    val currency: StateFlow<String?> = repo.record(RentalEntity.ORDER, orderId).map { it?.obj?.str("currency")?.trim()?.takeIf { c -> c.length == 3 } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The order's customer (missing documents are added on the customer). */
    val customerId: StateFlow<String?> = repo.record(RentalEntity.ORDER, orderId).map { it?.customerId ?: it?.obj?.str("customer_id") }
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
                    meterOverrideReason = meterOverrideReason.value.takeIf { HandoverChecks.meterOverrideNeeded(checksInput.value) },
                    meterPhotoPath = meterPhotoPath.value,
                    overrideSignature = direction == "out" && overrideSignature.value,
                    creditOverrideReason = creditOverrideReason.value.takeIf {
                        direction == "out" && credit.value?.allowed == false && canOverrideCredit
                    },
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
 * Check-out / check-in (§14) as a four-step flow — unit & inspection → condition (vehicle sketch
 * with baseline comparison) → meter, fuel & notes → signature & review — with a sticky
 * Back / Next bar. Event time is the device clock; saved offline and uploaded in order
 * (photos → inspection log → signature → handover → ledger).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HandoverScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onStartInspection: (assetId: String) -> Unit,
    /** After a check-in: open the unit's readiness checklist instead of just going back. */
    onCheckedIn: ((assetId: String) -> Unit)? = null,
    /** Wave 2: capture the agreement signature in person (signing screen). */
    onSign: ((orderId: String) -> Unit)? = null,
    /** Wave 2 / G2: add a missing / expired customer document (photo capture). */
    onAddDocument: ((customerId: String) -> Unit)? = null,
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
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val meterInfo by viewModel.meterInfo.collectAsStateWithLifecycle()
    val meterReason by viewModel.meterOverrideReason.collectAsStateWithLifecycle()
    val meterPhoto by viewModel.meterPhotoPath.collectAsStateWithLifecycle()
    val belowPrevious by viewModel.meterBelowPrevious.collectAsStateWithLifecycle()
    val esign by viewModel.esign.collectAsStateWithLifecycle()
    val overrideSignature by viewModel.overrideSignature.collectAsStateWithLifecycle()
    val credit by viewModel.credit.collectAsStateWithLifecycle()
    val creditUnknown by viewModel.creditUnknown.collectAsStateWithLifecycle()
    val creditReason by viewModel.creditOverrideReason.collectAsStateWithLifecycle()
    val blockers by viewModel.blockers.collectAsStateWithLifecycle()
    val customerId by viewModel.customerId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraDenied = stringResource(R.string.rentals_readiness_camera_denied)
    // Pending capture (log id + file) survives the camera activity / process death.
    var captureLog by rememberSaveable { mutableStateOf<String?>(null) }
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val defaultSigner = stringResource(R.string.rentals_customer)
    var addZone by remember { mutableStateOf<String?>(null) }
    var editMark by remember { mutableStateOf<SketchMark?>(null) }
    var customerMode by remember { mutableStateOf(false) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    val scroll = rememberScrollState()
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val forLog = captureLog
        val file = capturePath?.let(::File)
        captureLog = null
        capturePath = null
        if (ok && forLog != null && file != null && file.length() > 0) {
            haptics.tick()
            viewModel.addPhoto(forLog, file)
        } else {
            file?.delete()
        }
    }
    fun launchCamera(forLog: String) {
        val dir = File(context.filesDir, "rental_photos").apply { mkdirs() }
        val file = File(dir, "handover_${UUID.randomUUID()}.jpg")
        captureLog = forLog
        capturePath = file.absolutePath
        takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.provider", file))
    }
    var permissionFor by rememberSaveable { mutableStateOf<String?>(null) }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val forLog = permissionFor
        permissionFor = null
        if (granted && forLog != null) launchCamera(forLog)
        else if (!granted) scope.launch { snackbar.showSnackbar(cameraDenied) }
    }
    fun takePhoto(forLog: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera(forLog)
        } else {
            permissionFor = forLog
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }
    // Wave 2 meter photo (its own file; uploaded by the outbox as `meter_photo_file_id`).
    var meterCapturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val takeMeterPicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = meterCapturePath?.let(::File)
        meterCapturePath = null
        if (ok && file != null && file.length() > 0) {
            haptics.tick()
            viewModel.meterPhotoPath.value?.let { File(it).delete() }
            viewModel.meterPhotoPath.value = file.absolutePath
        } else {
            file?.delete()
        }
    }
    fun launchMeterCamera() {
        val dir = File(context.filesDir, "rental_photos").apply { mkdirs() }
        val file = File(dir, "meter_${UUID.randomUUID()}.jpg")
        meterCapturePath = file.absolutePath
        takeMeterPicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.provider", file))
    }
    val requestMeterCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchMeterCamera() else scope.launch { snackbar.showSnackbar(cameraDenied) }
    }
    fun takeMeterPhoto() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchMeterCamera()
        } else {
            requestMeterCamera.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.done.collect {
            haptics.success()
            val asset = viewModel.currentUnit.value?.assetId
            if (viewModel.direction == "in" && asset != null && onCheckedIn != null) onCheckedIn(asset) else onDone()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.errors.collect {
            saving = false
            haptics.error()
            snackbar.showSnackbar(it)
        }
    }
    LaunchedEffect(step) { scroll.scrollTo(0) }
    val out = viewModel.direction == "out"
    val zone = ZoneId.systemDefault()
    val steps = listOf(
        stringResource(R.string.rentals_inspection),
        stringResource(R.string.rentals_handover_step_condition),
        stringResource(R.string.rentals_meter),
        stringResource(R.string.rentals_handover_step_review),
    )
    val last = steps.lastIndex
    val goBack: () -> Unit = { if (step > 0) step-- else onBack() }
    BackHandler(enabled = step > 0) { step-- }

    Scaffold(
        topBar = { RentalsTopBar(stringResource(if (out) R.string.rentals_action_checkout else R.string.rentals_action_checkin), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomActionBar {
                OutlinedButton(onClick = goBack, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                    Text(stringResource(if (step == 0) R.string.rentals_cancel else R.string.rentals_back))
                }
                if (step < last) {
                    Button(
                        onClick = { step++ },
                        enabled = unit != null,
                        modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                    ) {
                        Text(stringResource(R.string.rentals_next_step))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 8.dp).size(18.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            saving = true
                            viewModel.save(defaultSigner)
                        },
                        enabled = unit != null && undecided == 0 && blockers.isEmpty() && !saving,
                        modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        }
                        Text(
                            stringResource(if (out) R.string.rentals_complete_checkout else R.string.rentals_complete_checkin),
                            Modifier.padding(start = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding(),
        ) {
            StepIndicator(steps, step, onStepClick = { step = it })
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(bottom = 16.dp),
            ) {
                if (units.isEmpty()) {
                    ErrorBanner(
                        stringResource(R.string.rentals_handover_no_unit),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        tone = StatusTone.Warning,
                    )
                }
                when (step) {
                    0 -> {
                        SectionCard(title = stringResource(R.string.rentals_unit), contentPadding = units.size > 1) {
                            if (units.size > 1) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    units.forEach { u ->
                                        FilterChip(
                                            unit?.assetId == u.assetId, { viewModel.selected.value = u.assetId }, { Text(u.name) },
                                            Modifier.heightIn(min = MinTouch),
                                        )
                                    }
                                }
                            } else {
                                unit?.let {
                                    ListItem(
                                        colors = cardListItemColors(),
                                        leadingContent = { UnitThumbnail(it.assetId, Icons.Default.DirectionsCar, if (out) StatusTone.Accent else StatusTone.Success) },
                                        headlineContent = { Text(it.name, style = MaterialTheme.typography.titleMedium) },
                                    )
                                }
                            }
                        }
                        SectionCard(title = stringResource(R.string.rentals_inspection)) {
                            inspections.forEachIndexed { i, opt ->
                                if (i > 0) CardDivider()
                                ListItem(
                                    colors = cardListItemColors(),
                                    modifier = Modifier.selectable(selected = logId == opt.logId, role = Role.RadioButton) {
                                        viewModel.logId.value = if (logId == opt.logId) null else opt.logId
                                    },
                                    leadingContent = { RadioButton(selected = logId == opt.logId, onClick = null) },
                                    headlineContent = { Text(opt.title) },
                                    supportingContent = { Text(formatDateTime(opt.at, zone)) },
                                )
                            }
                            if (inspections.isEmpty()) CardNote(stringResource(R.string.rentals_no_recent_inspection))
                            unit?.let { u ->
                                CardDivider()
                                TextButton(
                                    onClick = { onStartInspection(u.assetId) },
                                    modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = MinTouch),
                                ) {
                                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                    Text(stringResource(R.string.rentals_start_inspection), Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                    1 -> {
                        SectionCard(title = stringResource(R.string.rentals_damage), contentPadding = true) {
                            Text(
                                stringResource(if (baseline.isEmpty()) R.string.rentals_sketch_hint else R.string.rentals_sketch_hint_baseline),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            VehicleSketchView(
                                assetType = unit?.assetType,
                                marks = marks,
                                baseline = baseline,
                                onZoneTap = { addZone = it },
                                onMarkMove = viewModel::moveMark,
                                onMarkEdit = { editMark = it },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(320.dp),
                            )
                            VehicleSketchLegend(showBaseline = baseline.isNotEmpty())
                            val newCount = marks.count { it.isNew }
                            if (!out && baseline.isNotEmpty()) {
                                StatusPill(
                                    stringResource(R.string.rentals_new_damage_count, newCount),
                                    if (newCount > 0) StatusTone.Danger else StatusTone.Success,
                                    Modifier.padding(top = 8.dp),
                                    icon = if (newCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                                )
                            }
                        }
                        ConditionPhotoStrip(
                            photos = photos,
                            logId = logId,
                            onAdd = { takePhoto(it) },
                            onStartInspection = unit?.let { u -> { onStartInspection(u.assetId) } },
                        )
                        if (marks.isNotEmpty()) {
                            SectionCard {
                                marks.forEachIndexed { i, m ->
                                    if (i > 0) CardDivider()
                                    val needs = m.isNew && decisions[m.id]?.isValid != true
                                    ListItem(
                                        colors = cardListItemColors(),
                                        modifier = Modifier.clickable(role = Role.Button) { editMark = m },
                                        leadingContent = {
                                            LeadingBadge(
                                                if (m.isNew) Icons.Default.Warning else Icons.Default.History,
                                                if (m.isNew) StatusTone.Danger else StatusTone.Neutral,
                                            )
                                        },
                                        headlineContent = { Text("${m.number}. ${inspectionLocationLabel(m.zone) ?: m.zone}") },
                                        supportingContent = { Text(listOf(severityLabel(m.severity), m.label).filter { it.isNotBlank() }.joinToString(" · ")) },
                                        trailingContent = if (needs) {
                                            { StatusPill(stringResource(R.string.rentals_decision_needed), StatusTone.Warning, icon = Icons.Default.Error) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        SectionCard(title = stringResource(R.string.rentals_meter), contentPadding = true) {
                            OutlinedTextField(
                                meter, { viewModel.meter.value = it },
                                label = { Text(stringResource(if (meterKind == "hours") R.string.rentals_meter_hours else R.string.rentals_meter_distance)) },
                                suffix = meterInfo?.takeIf { it.hasMeter }?.let { m -> { Text(meterUnitLabel(m.unit)) } },
                                isError = blockers.any { blockerStep(it) == 2 },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // The unit's meter fixes the kind; the chips stay for units without meter rules.
                            if (meterInfo?.hasMeter != true) ChipRow(Modifier.padding(top = 8.dp)) {
                                FilterChip(
                                    meterKind == "hours", { viewModel.meterKind.value = "hours" },
                                    { Text(stringResource(R.string.rentals_meter_hours)) }, Modifier.heightIn(min = MinTouch),
                                )
                                FilterChip(
                                    meterKind == "distance", { viewModel.meterKind.value = "distance" },
                                    { Text(stringResource(R.string.rentals_meter_distance)) }, Modifier.heightIn(min = MinTouch),
                                )
                            }
                            MeterRules(
                                info = meterInfo,
                                check = meterInfo?.takeIf { it.hasMeter }?.check(meter, viewModel.direction)
                                    ?: if (blockers.contains(HandoverChecks.Blocker.METER_INVALID)) MeterInfo.Check.INVALID else null,
                                belowPrevious = belowPrevious,
                                canOverride = viewModel.canOverrideMeter,
                                reason = meterReason,
                                onReason = { viewModel.meterOverrideReason.value = it },
                                photoPath = meterPhoto,
                                onTakePhoto = { takeMeterPhoto() },
                                onRemovePhoto = {
                                    meterPhoto?.let { File(it).delete() }
                                    viewModel.meterPhotoPath.value = null
                                },
                            )
                        }
                        SectionCard(title = stringResource(R.string.rentals_meter_fuel), contentPadding = true) {
                            val fuelText = fuel?.let { "$it %" } ?: stringResource(R.string.rentals_not_recorded)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalGasStation, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    stringResource(R.string.rentals_fuel_level, fuelText),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            Slider(
                                value = (fuel ?: 0).toFloat(),
                                onValueChange = { viewModel.fuel.value = (it / 5).toInt() * 5 },
                                valueRange = 0f..100f,
                                steps = 19,
                                modifier = Modifier.heightIn(min = MinTouch).semantics { stateDescription = fuelText },
                            )
                        }
                        SectionCard(title = stringResource(R.string.rentals_notes), contentPadding = true) {
                            OutlinedTextField(
                                notes, { viewModel.notes.value = it },
                                label = { Text(stringResource(R.string.rentals_notes)) },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    else -> {
                        SectionCard(title = stringResource(R.string.rentals_signature), contentPadding = true) {
                            StatusPill(
                                stringResource(if (signature != null) R.string.rentals_signed else R.string.rentals_not_signed),
                                if (signature != null) StatusTone.Success else StatusTone.Neutral,
                                icon = if (signature != null) Icons.Default.CheckCircle else Icons.Default.Draw,
                            )
                            if (signer.isNotBlank()) {
                                Text(signer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                            }
                            FilledTonalButton(
                                onClick = { customerMode = true },
                                modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = MinTouch),
                            ) {
                                Icon(Icons.Default.Draw, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.rentals_hand_to_customer), Modifier.padding(start = 8.dp))
                            }
                        }
                        SectionCard(title = stringResource(R.string.rentals_review_summary)) {
                            Column(Modifier.padding(vertical = 8.dp)) {
                                LabelValue(stringResource(R.string.rentals_unit), unit?.name ?: "—", emphasise = true)
                                LabelValue(
                                    stringResource(R.string.rentals_inspection),
                                    inspections.firstOrNull { it.logId == logId }?.title ?: stringResource(R.string.rentals_not_recorded),
                                )
                                LabelValue(stringResource(R.string.rentals_damage), stringResource(R.string.rentals_marks_count, marks.size, marks.count { it.isNew }))
                                if (photos.isNotEmpty()) LabelValue(stringResource(R.string.rentals_condition_photos), photos.size.toString())
                                LabelValue(
                                    stringResource(if (meterKind == "hours") R.string.rentals_meter_hours else R.string.rentals_meter_distance),
                                    meter.ifBlank { stringResource(R.string.rentals_not_recorded) },
                                )
                                LabelValue(
                                    stringResource(R.string.rentals_meter_fuel),
                                    fuel?.let { "$it %" } ?: stringResource(R.string.rentals_not_recorded),
                                )
                                if (notes.isNotBlank()) LabelValue(stringResource(R.string.rentals_notes), notes)
                            }
                        }
                        if (out) {
                            CheckoutGates(
                                esign = esign,
                                canOverrideSignature = viewModel.canOverrideSignature,
                                overrideSignature = overrideSignature,
                                onOverrideSignature = { viewModel.overrideSignature.value = it },
                                onSignNow = onSign?.let { f -> { f(viewModel.orderId) } },
                                credit = credit,
                                creditUnknown = creditUnknown,
                                canOverrideCredit = viewModel.canOverrideCredit,
                                creditReason = creditReason,
                                onCreditReason = { viewModel.creditOverrideReason.value = it },
                                onAddDocument = customerId?.let { c -> onAddDocument?.let { f -> { f(c) } } },
                            )
                        }
                        blockers.firstOrNull()?.let { b ->
                            ErrorBanner(
                                blockerText(b),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                onRetry = if (blockerStep(b) != step) ({ step = blockerStep(b) }) else null,
                                retryLabel = stringResource(R.string.rentals_meter),
                                tone = StatusTone.Danger,
                            )
                        }
                        if (undecided > 0) {
                            ErrorBanner(
                                stringResource(R.string.rentals_damage_needs_decision),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                onRetry = { step = 1 },
                                retryLabel = stringResource(R.string.rentals_damage),
                                tone = StatusTone.Danger,
                            )
                        }
                        Text(
                            stringResource(R.string.rentals_handover_offline_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
            }
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
                haptics.tick()
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
            Column(Modifier.systemBarsPadding().imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

/**
 * Condition photo strip (72dp tiles + "Add photo"). Photos belong to the selected inspection
 * log and are uploaded with the handover; with no inspection selected the add tile starts one.
 */
@Composable
private fun ConditionPhotoStrip(
    photos: List<PhotoEntity>,
    logId: String?,
    onAdd: (logId: String) -> Unit,
    onStartInspection: (() -> Unit)?,
) {
    SectionCard(
        title = stringResource(R.string.rentals_condition_photos),
        trailing = if (photos.isNotEmpty()) {
            { StatusPill(photos.size.toString(), StatusTone.Neutral) }
        } else {
            null
        },
        contentPadding = true,
    ) {
        if (logId == null) {
            Text(
                stringResource(R.string.rentals_condition_photos_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = AvagoSpacing.sm),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
            items(photos, key = { it.photoId }) { p ->
                val model = remember(p.photoId, p.localPath, p.downloadUrl) { photoModel(p) }
                Box(
                    Modifier
                        .size(AvagoSpacing.photoTile)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (model != null) {
                        AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            item(key = "add") {
                val label = stringResource(if (logId == null) R.string.rentals_start_inspection else R.string.rentals_add_photo)
                val enabled = logId != null || onStartInspection != null
                Surface(
                    onClick = { if (logId != null) onAdd(logId) else onStartInspection?.invoke() },
                    enabled = enabled,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(AvagoSpacing.photoTile).semantics { contentDescription = label },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (logId == null) Icons.Default.Add else Icons.Default.AddAPhoto, contentDescription = null)
                    }
                }
            }
        }
    }
}