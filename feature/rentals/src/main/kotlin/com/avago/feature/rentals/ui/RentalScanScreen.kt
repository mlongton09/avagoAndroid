package com.avago.feature.rentals.ui

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.RentalRepository
import com.avago.feature.rentals.domain.Aamva
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.ScanCode
import com.avago.feature.rentals.domain.str
import com.avago.feature.rentals.domain.obj
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface ScanResult {
    data class Asset(val assetId: String, val name: String, val rentable: Boolean) : ScanResult
    data class Order(val orderId: String, val number: String) : ScanResult
    data class Licence(val licence: Aamva.License) : ScanResult
    data class NotFound(val raw: String) : ScanResult
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
        suspend fun asset(code: String): ScanResult? {
            val a = db.assetDao().getById(code)?.takeIf { it.deletedAt == null }
                ?: db.assetDao().findByIdentifierCandidates(acc, code).firstOrNull()
                ?: return fleet.values.firstOrNull { it.name.equals(code, true) }?.let { ScanResult.Asset(it.id, it.name ?: code, true) }
            return ScanResult.Asset(a.assetId, a.name, fleet.containsKey(a.assetId))
        }
        return when (val k = ScanCode.classify(raw)) {
            is ScanCode.Kind.Licence -> Aamva.parse(k.raw)?.let { LicenceScanBuffer.put(it); ScanResult.Licence(it) } ?: ScanResult.NotFound(raw)
            is ScanCode.Kind.AssetId -> asset(k.id) ?: ScanResult.NotFound(raw)
            is ScanCode.Kind.OrderNumber -> repo.records(RentalEntity.ORDER).first()
                .firstOrNull { it.obj.str("number").equals(k.number, true) }
                ?.let { ScanResult.Order(it.id, k.number) } ?: asset(k.number) ?: ScanResult.NotFound(raw)
            is ScanCode.Kind.Text -> asset(k.value) ?: ScanResult.NotFound(raw)
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
    viewModel: RentalScanViewModel = hiltViewModel(),
) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    var textMode by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    val analyzer = remember(textMode) { if (textMode) MlKitTextAnalyzer() else MlKitBarcodeAnalyzer() }
    NfcReader(onText = viewModel::resolve)

    Scaffold(topBar = { RentalsTopBar(stringResource(R.string.rentals_scan), onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!textMode, { textMode = false }, { Text(stringResource(R.string.rentals_scan_barcode)) }, Modifier.heightIn(min = MinTouch))
                FilterChip(textMode, { textMode = true }, { Text(stringResource(R.string.rentals_scan_text)) }, Modifier.heightIn(min = MinTouch))
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                CameraScanner(analyzer, active = result == null, onCode = viewModel::resolve, modifier = Modifier.fillMaxSize())
            }
            Text(
                stringResource(R.string.rentals_scan_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(manual, { manual = it }, label = { Text(stringResource(R.string.rentals_enter_code)) }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { viewModel.resolve(manual) }, enabled = manual.isNotBlank(), modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentals_search))
                }
            }
            result?.let { r ->
                Card(Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (r) {
                            is ScanResult.Asset -> {
                                Text(r.name, style = MaterialTheme.typography.titleMedium)
                                if (!r.rentable) Text(stringResource(R.string.rentals_not_rentable), style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { onOpenAsset(r.assetId) }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_open)) }
                            }
                            is ScanResult.Order -> {
                                Text(r.number, style = MaterialTheme.typography.titleMedium)
                                Button(onClick = { onOpenOrder(r.orderId) }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_open_order)) }
                            }
                            is ScanResult.Licence -> {
                                Text(r.licence.fullName, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.rentals_licence_read), style = MaterialTheme.typography.bodySmall)
                                Button(onClick = onLicence, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_new_customer)) }
                            }
                            is ScanResult.NotFound -> Text(stringResource(R.string.rentals_scan_not_found, r.raw.take(64)))
                        }
                        TextButton(onClick = viewModel::clear, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_scan_again)) }
                    }
                }
            }
        }
    }
}

/** NFC reader mode while the scan screen is visible (the app's NDEF intent handling stays as is). */
@Composable
private fun NfcReader(onText: (String) -> Unit) {
    val activity = LocalContext.current as? Activity ?: return
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
