package com.avago.feature.rentals.ui

import java.time.Instant
import javax.inject.Inject
import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.rental.LocalFleet
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.rememberHaptics
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.data.RentalRepository
import com.avago.feature.rentals.domain.Aamva
import com.avago.feature.rentals.domain.HandoverTarget
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.ScanCode
import com.avago.feature.rentals.domain.ScanResolution
import com.avago.feature.rentals.data.RentalsOnline
import com.avago.feature.rentals.domain.str
import com.avago.feature.rentals.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

sealed interface ScanResult {
    /** [handover] is the check-out / check-in the unit is waiting for (offline fleet), if any. */
    data class Asset(
        val assetId: String,
        val name: String,
        val rentable: Boolean,
        val handover: HandoverTarget? = null,
    ) : ScanResult
    data class Order(val orderId: String, val number: String) : ScanResult
    data class Licence(val licence: Aamva.License) : ScanResult
    /** [offline]: only this device was searched (the server could not be asked). */
    data class NotFound(val raw: String, val offline: Boolean = false) : ScanResult

    /** Wave 2: a bulk part label (`P` code) resolved by the server. */
    data class Part(val partId: String, val name: String, val partNumber: String?) : ScanResult
}

/** Last licence read on the device, handed to the customer form (process memory only). */
object LicenceScanBuffer {
    @Volatile private var last: Aamva.License? = null
    fun put(l: Aamva.License) { last = l }
    fun take(): Aamva.License? = last.also { last = null }
}

@HiltViewModel
class RentalScanViewModel @Inject constructor(
    private val repo: RentalRepository,
    private val online: RentalsOnline,
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
) : ViewModel() {
    private val _result = MutableStateFlow<ScanResult?>(null)
    val result: StateFlow<ScanResult?> = _result.asStateFlow()
    private var lastRaw: String? = null

    fun clear() { _result.value = null; lastRaw = null }

    fun resolve(raw: String) {
        if (raw == lastRaw && _result.value != null) return
        lastRaw = raw
        viewModelScope.launch {
            _result.value = try { lookup(raw) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                Timber.w(e, "scan lookup failed"); ScanResult.NotFound(raw)
            }
        }
    }

    private suspend fun lookup(raw: String): ScanResult {
        val acc = identity.getActiveAccountId() ?: return ScanResult.NotFound(raw)
        val db = dbFactory.get(acc)
        val fleet = repo.records(RentalEntity.FLEET).first().associateBy { it.id }
        // Offline fleet status, so the result can offer the pending check-out / check-in directly.
        suspend fun handoverFor(assetId: String): HandoverTarget? {
            val inputs = repo.fleetInputs().first()
            return withContext(Dispatchers.Default) { LocalFleet.compute(inputs, Instant.now())[assetId] }?.let(HandoverTarget::of)
        }
        suspend fun asset(code: String): ScanResult? {
            val a = db.assetDao().getById(code)?.takeIf { it.deletedAt == null }
                ?: db.assetDao().findByIdentifierCandidates(acc, code).firstOrNull()
            val id = a?.assetId ?: fleet.values.firstOrNull { it.name.equals(code, true) }?.id ?: return null
            val rentable = fleet.containsKey(id)
            val handover = if (rentable) handoverFor(id) else null
            return ScanResult.Asset(id, a?.name ?: fleet[id]?.name ?: code, rentable, handover)
        }
        /** Wave 2: `GET /scan/resolve` for label short codes, serials and part numbers this device doesn't know. */
        suspend fun server(code: String): ScanResult = when (val r = online.scanResolve(code, null)) {
            is RentalsOnline.Result.Ok -> {
                val s = ScanResolution.parse(r.value)
                when {
                    s == null -> ScanResult.NotFound(raw)
                    s.isAsset -> {
                        val id = s.assetId!!
                        asset(id) ?: run {
                            val rentable = fleet.containsKey(id) || s.rentalClassId != null
                            ScanResult.Asset(id, s.assetName ?: code, rentable, if (rentable) handoverFor(id) else null)
                        }
                    }
                    s.isPart -> ScanResult.Part(s.partId!!, s.partName ?: s.partNumber ?: code, s.partNumber)
                    else -> ScanResult.NotFound(raw)
                }
            }
            is RentalsOnline.Result.Refused -> ScanResult.NotFound(raw)
            is RentalsOnline.Result.Offline -> ScanResult.NotFound(raw, offline = true)
        }
        return when (val k = ScanCode.classify(raw)) {
            is ScanCode.Kind.Licence -> Aamva.parse(k.raw)?.let { LicenceScanBuffer.put(it); ScanResult.Licence(it) } ?: ScanResult.NotFound(raw)
            is ScanCode.Kind.AssetId -> asset(k.id) ?: server(k.id)
            is ScanCode.Kind.OrderNumber -> repo.records(RentalEntity.ORDER).first()
                .firstOrNull { it.obj.str("number").equals(k.number, true) }
                ?.let { ScanResult.Order(it.id, k.number) } ?: asset(k.number) ?: ScanResult.NotFound(raw)
            is ScanCode.Kind.ShortCode -> asset(k.code) ?: server(k.code)
            is ScanCode.Kind.Text -> asset(k.value) ?: server(k.value)
        }
    }
}

/** Scan (§14): ML Kit barcode or text via CameraX, plus NFC tags (reader mode while open). */
@Composable
fun RentalScanScreen(
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    onOpenOrder: (String) -> Unit,
    onLicence: () -> Unit,
    onHandover: (orderId: String, direction: String) -> Unit = { _, _ -> },
    /** [HandoverTarget.OUT] / [HandoverTarget.IN] when opened from a home quick action. */
    mode: String? = null,
    viewModel: RentalScanViewModel = hiltViewModel(),
) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    var textMode by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    val analyzer = remember(textMode) { if (textMode) MlKitTextAnalyzer() else MlKitBarcodeAnalyzer() }
    NfcReader(onText = viewModel::resolve)
    val title = when (mode) {
        HandoverTarget.OUT -> R.string.rentals_scan_title_out
        HandoverTarget.IN -> R.string.rentals_scan_title_in
        else -> R.string.rentals_scan
    }

    val haptics = rememberHaptics()
    LaunchedEffect(result) {
        when (result) {
            null -> Unit
            is ScanResult.NotFound -> haptics.error()
            else -> haptics.success()
        }
    }
    val submitManual = { if (manual.isNotBlank()) viewModel.resolve(manual.trim()) }

    Scaffold(topBar = { RentalsTopBar(stringResource(title), onBack) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding(),
        ) {
            ChipRow {
                FilterChip(
                    selected = !textMode,
                    onClick = { textMode = false },
                    label = { Text(stringResource(R.string.rentals_scan_barcode)) },
                    leadingIcon = { Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = MinTouch),
                )
                FilterChip(
                    selected = textMode,
                    onClick = { textMode = true },
                    label = { Text(stringResource(R.string.rentals_scan_text)) },
                    leadingIcon = { Icon(Icons.Default.TextFields, null, Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = MinTouch),
                )
            }
            // Viewfinder: rounded, framed preview; collapses to a compact strip while a result is shown
            // so the result card and its actions stay in thumb reach.
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Color.Black,
                border = BorderStroke(2.dp, if (result == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .then(if (result == null) Modifier.weight(1f) else Modifier.height(120.dp)),
            ) {
                CameraScanner(analyzer, active = result == null, onCode = viewModel::resolve, modifier = Modifier.fillMaxSize())
            }
            Text(
                stringResource(R.string.rentals_scan_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    manual, { manual = it },
                    label = { Text(stringResource(R.string.rentals_enter_code)) },
                    leadingIcon = { Icon(Icons.Default.Keyboard, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, capitalization = KeyboardCapitalization.Characters),
                    keyboardActions = KeyboardActions(onSearch = { submitManual() }),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = submitManual, enabled = manual.isNotBlank(), modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.rentals_search))
                }
            }
            result?.let { r -> ScanResultCard(r, mode, onOpenAsset, onOpenOrder, onLicence, onHandover, onScanAgain = viewModel::clear) }
        }
    }
}

/** Result of a scan: what was found, its state, and the next step as the primary button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanResultCard(
    r: ScanResult,
    mode: String?,
    onOpenAsset: (String) -> Unit,
    onOpenOrder: (String) -> Unit,
    onLicence: () -> Unit,
    onHandover: (orderId: String, direction: String) -> Unit,
    onScanAgain: () -> Unit,
) {
    SectionCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, title = stringResource(R.string.rentals_scan_result)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val (icon, tone) = when (r) {
                is ScanResult.Asset -> Icons.Default.Inventory2 to if (r.rentable) StatusTone.Success else StatusTone.Neutral
                is ScanResult.Order -> Icons.AutoMirrored.Filled.ReceiptLong to StatusTone.Info
                is ScanResult.Licence -> Icons.Default.Badge to StatusTone.Info
                is ScanResult.NotFound -> Icons.Default.SearchOff to StatusTone.Warning
                is ScanResult.Part -> Icons.Default.Widgets to StatusTone.Info
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LeadingBadge(icon, tone)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val headline = when (r) {
                        is ScanResult.Asset -> r.name
                        is ScanResult.Order -> r.number
                        is ScanResult.Licence -> r.licence.fullName
                        is ScanResult.NotFound -> stringResource(R.string.rentals_scan_not_found, r.raw.take(64))
                        is ScanResult.Part -> r.name
                    }
                    Text(headline, style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        when (r) {
                            is ScanResult.Asset -> if (!r.rentable) {
                                StatusPill(stringResource(R.string.rentals_not_rentable), StatusTone.Neutral, icon = Icons.Default.Block)
                            } else {
                                r.handover?.let { h ->
                                    val inbound = h.direction == HandoverTarget.IN
                                    StatusPill(
                                        stringResource(if (inbound) R.string.rentals_action_checkin else R.string.rentals_action_checkout),
                                        if (inbound) StatusTone.Warning else StatusTone.Info,
                                        icon = if (inbound) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                                    )
                                }
                            }
                            is ScanResult.Part -> StatusPill(
                                listOfNotNull(stringResource(R.string.rentals_scan_bulk_item), r.partNumber).joinToString(" · "),
                                StatusTone.Info,
                            )
                            is ScanResult.NotFound -> if (r.offline) {
                                Text(
                                    stringResource(R.string.rentals_scan_offline_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            is ScanResult.Licence -> Text(
                                stringResource(R.string.rentals_licence_read),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> Unit
                        }
                    }
                }
            }
            if (r is ScanResult.Asset && r.rentable && r.handover == null && mode != null) {
                Text(
                    stringResource(R.string.rentals_scan_no_handover),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onScanAgain, modifier = Modifier.heightIn(min = MinTouch)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.rentals_scan_again))
                }
                Spacer(Modifier.weight(1f))
                when (r) {
                    is ScanResult.Asset -> {
                        val h = r.handover
                        if (h != null) {
                            OutlinedButton(onClick = { onOpenAsset(r.assetId) }, modifier = Modifier.heightIn(min = MinTouch)) {
                                Text(stringResource(R.string.rentals_open))
                            }
                            Button(onClick = { onHandover(h.orderId, h.direction) }, modifier = Modifier.heightIn(min = MinTouch)) {
                                Text(stringResource(if (h.direction == HandoverTarget.IN) R.string.rentals_action_checkin else R.string.rentals_action_checkout))
                            }
                        } else {
                            Button(onClick = { onOpenAsset(r.assetId) }, modifier = Modifier.heightIn(min = MinTouch)) {
                                Text(stringResource(R.string.rentals_open))
                            }
                        }
                    }
                    is ScanResult.Order -> Button(onClick = { onOpenOrder(r.orderId) }, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.rentals_open_order))
                    }
                    is ScanResult.Licence -> Button(onClick = onLicence, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.rentals_new_customer))
                    }
                    is ScanResult.NotFound, is ScanResult.Part -> Unit
                }
            }
        }
    }
}

/** NFC reader mode while the scan screen is visible (the app's NDEF intent handling stays as is). */
@Composable
private fun NfcReader(onText: (String) -> Unit) {
    // LocalContext may be a ContextWrapper (theme / locale overrides), so a cast would silently
    // disable NFC; LocalActivity resolves the hosting activity.
    val activity: Activity = LocalActivity.current ?: return
    val emit by rememberUpdatedState(onText)
    DisposableEffect(activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter?.isEnabled == true) {
            adapter.enableReaderMode(
                activity,
                { tag: Tag -> ndefText(tag)?.let { v -> activity.runOnUiThread { emit(v) } } },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                null,
            )
        }
        onDispose { runCatching { adapter?.disableReaderMode(activity) } }
    }
}

private fun ndefText(tag: Tag): String? {
    val ndef = Ndef.get(tag) ?: return null
    return try {
        ndef.connect()
        val msg: NdefMessage = ndef.ndefMessage ?: ndef.cachedNdefMessage ?: return null
        msg.records.firstNotNullOfOrNull(::recordText)
    } catch (e: Exception) {
        Timber.w(e, "NFC read failed"); null
    } finally {
        runCatching { ndef.close() }
    }
}

internal fun recordText(r: NdefRecord): String? {
    r.toUri()?.let { return it.toString() }
    if (r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_TEXT)) {
        val p = r.payload
        if (p.isEmpty()) return null
        val langLen = p[0].toInt() and 0x3F
        val utf16 = (p[0].toInt() and 0x80) != 0
        return String(p, 1 + langLen, p.size - 1 - langLen, if (utf16) Charsets.UTF_16 else Charsets.UTF_8)
    }
    return null
}
