package com.avago.feature.crew.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.crew.R
import com.avago.feature.crew.data.ClaimResult
import com.avago.feature.crew.data.Load
import com.avago.feature.crew.data.Passport
import com.avago.feature.crew.data.PassportLink
import com.avago.feature.crew.data.PassportService
import com.avago.feature.crew.data.TransferPreview
import com.avago.feature.crew.data.toLoad
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Public web origin for passport rent links (same as feature/log). */
internal const val WEB_BASE_URL = "https://www.avagomate.com"

/** The server caps the base64 fault photo at about 2 MB of decoded image. */
internal const val MAX_FAULT_PHOTO_BYTES = 2 * 1024 * 1024

@HiltViewModel
class PassportViewModel @Inject constructor(
    private val svc: PassportService,
    savedState: SavedStateHandle,
) : NoticeViewModel() {
    val tokenId: String = checkNotNull(savedState["tokenId"])
    private val _passport = MutableStateFlow<Load<Passport>>(Load.Loading)
    val passport: StateFlow<Load<Passport>> = _passport.asStateFlow()
    private val _answers = MutableStateFlow<Map<String, String>>(emptyMap())
    val answers: StateFlow<Map<String, String>> = _answers.asStateFlow()
    private val _inspectionResult = MutableStateFlow<String?>(null)
    val inspectionResult: StateFlow<String?> = _inspectionResult.asStateFlow()
    private val _faultPhoto = MutableStateFlow<ByteArray?>(null)
    val faultPhoto: StateFlow<ByteArray?> = _faultPhoto.asStateFlow()
    private val _faultSent = MutableStateFlow(false)
    val faultSent: StateFlow<Boolean> = _faultSent.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _passport.value = Load.Loading
            _passport.value = svc.passport(tokenId).toLoad()
        }
    }

    fun answer(itemId: String, value: String) = _answers.update { it + (itemId to value) }

    /** Select items still unanswered (the server requires every select item). */
    fun unanswered(): List<String> {
        val p = _passport.value.valueOrNull() ?: return emptyList()
        return p.checklist.filter { it.type == "select" && _answers.value[it.id].isNullOrBlank() }.map { it.id }
    }

    fun submitInspection(name: String?) {
        if (unanswered().isNotEmpty()) return
        val p = _passport.value.valueOrNull() ?: return
        val selects = _answers.value.filterKeys { k -> p.checklist.any { it.id == k && it.type == "select" } }
        val notes = _answers.value.filterKeys { k -> p.checklist.any { it.id == k && it.type != "select" } }
        act(null, { svc.submitInspection(tokenId, name, selects, notes) }) { _inspectionResult.value = it ?: "submitted" }
    }

    fun setFaultPhoto(jpeg: ByteArray?) { _faultPhoto.value = jpeg?.takeIf { it.size <= MAX_FAULT_PHOTO_BYTES } }

    fun reportFault(name: String?, description: String, severity: String) {
        if (description.isBlank()) return
        val b64 = _faultPhoto.value?.let { "data:image/jpeg;base64," + Base64.encodeToString(it, Base64.NO_WRAP) }
        act(R.string.crew_fault_sent, { svc.reportFault(tokenId, name, description.trim(), severity, b64) }) {
            _faultSent.value = true
            _faultPhoto.value = null
        }
    }

    fun resetFault() { _faultSent.value = false }
}

@HiltViewModel
class TransferClaimViewModel @Inject constructor(
    private val svc: PassportService,
    savedState: SavedStateHandle,
) : NoticeViewModel() {
    val token: String = checkNotNull(savedState["token"])
    private val _preview = MutableStateFlow<Load<TransferPreview>>(Load.Loading)
    val preview: StateFlow<Load<TransferPreview>> = _preview.asStateFlow()
    private val _claimed = MutableStateFlow<ClaimResult?>(null)
    val claimed: StateFlow<ClaimResult?> = _claimed.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _preview.value = Load.Loading
            _preview.value = svc.transferPreview(token).toLoad()
        }
    }

    fun claim() = act(R.string.crew_transfer_claimed, { svc.claimTransfer(token) }) { _claimed.value = it }
}

@Composable
fun PassportScanScreen(onBack: () -> Unit, onPassport: (String) -> Unit, onClaim: (String) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    var manual by rememberSaveable { mutableStateOf("") }
    var handled by remember { mutableStateOf(false) }
    val invalid = stringResource(R.string.crew_passport_invalid_code)
    var lastBad by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lastBad) { lastBad?.let { snackbar.showSnackbar(invalid) } }

    fun open(raw: String): Boolean = when (val l = PassportLink.parse(raw)) {
        is PassportLink.Passport -> { onPassport(l.tokenId); true }
        is PassportLink.Claim -> { onClaim(l.token); true }
        null -> false
    }

    CrewScaffold(stringResource(R.string.crew_scan_passport), onBack, snackbar) { inner ->
        Column(Modifier.padding(inner).padding(AvagoSpacing.lg), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QrScanner(
                active = !handled,
                onCode = { code ->
                    if (!handled) {
                        if (open(code)) handled = true else if (lastBad != code) lastBad = code
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Caption(stringResource(R.string.crew_passport_scan_hint))
            OutlinedTextField(
                manual, { manual = it },
                label = { Text(stringResource(R.string.crew_passport_manual)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { if (!open(manual)) lastBad = manual + "#" },
                enabled = manual.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.crew_open)) }
        }
    }
}

@Composable
fun PassportScreen(onBack: () -> Unit, viewModel: PassportViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val passport by viewModel.passport.collectAsStateWithLifecycle()
    val answers by viewModel.answers.collectAsStateWithLifecycle()
    val result by viewModel.inspectionResult.collectAsStateWithLifecycle()
    val photo by viewModel.faultPhoto.collectAsStateWithLifecycle()
    val faultSent by viewModel.faultSent.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var showMissing by rememberSaveable { mutableStateOf(false) }
    val capture = rememberPhotoCapture(viewModel::setFaultPhoto)
    NoticeEffect(viewModel.notices, snackbar)
    LaunchedEffect(faultSent) {
        if (faultSent) {
            mode = null
            viewModel.resetFault()
        }
    }

    result?.let { r ->
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(if (r == "fail") R.string.crew_inspection_failed else R.string.crew_inspection_saved)) },
            confirmButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.crew_done)) } },
        )
    }

    CrewScaffold(passport.valueOrNull()?.asset?.title ?: stringResource(R.string.crew_passport), onBack, snackbar) { inner ->
        LoadBox(passport, viewModel::refresh, inner) { p ->
            LazyColumn(contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item("asset") {
                    Panel {
                        val a = p.asset
                        Text(a?.title ?: "—", style = MaterialTheme.typography.titleMedium)
                        Caption(listOfNotNull(a?.make, a?.model, a?.year?.toString()).joinToString(" "))
                        a?.vinMasked?.let { Caption(stringResource(R.string.crew_vin, it)) }
                        p.lastServiceDate?.let { Caption(stringResource(R.string.crew_last_service, Fmt.date(it))) }
                        if ((p.recallOpen ?: 0) > 0) StatusPill(stringResource(R.string.crew_open_recall), StatusTone.Danger, Modifier.padding(top = 8.dp))
                        if (p.visibility == "off") Caption(stringResource(R.string.crew_passport_private))
                    }
                }
                if (mode == null) {
                    item("actions") {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (p.canInspect) {
                                Button(onClick = { mode = "inspect" }, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                                    Text(stringResource(R.string.crew_preuse_inspection))
                                }
                            }
                            if (p.canReportFault) {
                                OutlinedButton(onClick = { mode = "fault" }, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                                    Text(stringResource(R.string.crew_report_fault))
                                }
                            }
                            p.rentUrl?.let { url ->
                                TextButton(
                                    onClick = {
                                        val full = if (url.startsWith("http")) url else WEB_BASE_URL + url
                                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(full))) }
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                                ) { Text(stringResource(R.string.crew_rent_this)) }
                            }
                        }
                    }
                    if (p.serviceEvents.isNotEmpty()) {
                        item("service") {
                            Panel(title = stringResource(R.string.crew_service_history)) {
                                p.serviceEvents.forEach { e -> Text("${Fmt.date(e.date)} · ${e.title ?: e.category ?: "—"}", Modifier.padding(vertical = 2.dp)) }
                            }
                        }
                    }
                    if (p.inspections.isNotEmpty()) {
                        item("insp") {
                            Panel(title = stringResource(R.string.crew_recent_inspections)) {
                                p.inspections.forEach { e ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(Fmt.date(e.date), Modifier.weight(1f))
                                        InspectionPill(e.result)
                                    }
                                }
                            }
                        }
                    }
                }
                if (mode == "inspect") {
                    val missing = if (showMissing) viewModel.unanswered().toSet() else emptySet()
                    item("name") {
                        OutlinedTextField(
                            name, { name = it },
                            label = { Text(stringResource(R.string.crew_your_name_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                    items(p.checklist, key = { it.id }) { item ->
                        Panel(title = item.section?.let(Passport::humanize)) {
                            Text(Passport.humanize(item.text))
                            if (item.type == "select") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item.options.forEach { o ->
                                        FilterChip(answers[item.id] == o, { viewModel.answer(item.id, o) }, { Text(optionLabel(o)) })
                                    }
                                }
                                if (item.id in missing) Caption(stringResource(R.string.crew_answer_required))
                            } else {
                                OutlinedTextField(answers[item.id].orEmpty(), { viewModel.answer(item.id, it) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    item("submit") {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { mode = null }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.crew_cancel)) }
                            Button(
                                onClick = {
                                    showMissing = true
                                    viewModel.submitInspection(name)
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                            ) { Text(stringResource(R.string.crew_submit_inspection)) }
                        }
                    }
                }
                if (mode == "fault") {
                    item("fault") {
                        var desc by rememberSaveable { mutableStateOf("") }
                        var sev by rememberSaveable { mutableStateOf("medium") }
                        Panel(title = stringResource(R.string.crew_report_fault)) {
                            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.crew_your_name_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(desc, { desc = it }, label = { Text(stringResource(R.string.crew_fault_details)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("low", "medium", "high", "critical").forEach { s -> FilterChip(sev == s, { sev = s }, { Text(severityLabel(s)) }) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = capture, modifier = Modifier.heightIn(min = MinTouch)) {
                                    Text(stringResource(if (photo == null) R.string.crew_add_photo else R.string.crew_replace_photo))
                                }
                                if (photo != null) Caption(stringResource(R.string.crew_photo_attached))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { mode = null }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.crew_cancel)) }
                                Button(
                                    onClick = { viewModel.reportFault(name, desc, sev) },
                                    enabled = !busy && desc.isNotBlank(),
                                    modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                                ) { Text(stringResource(R.string.crew_send_fault)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun optionLabel(o: String): String = when (o.substringAfterLast('.')) {
    "normal", "ok", "pass" -> stringResource(R.string.crew_opt_ok)
    "monitor" -> stringResource(R.string.crew_opt_monitor)
    "needs_repair", "fail" -> stringResource(R.string.crew_opt_needs_repair)
    "na", "n_a" -> stringResource(R.string.crew_na)
    else -> Passport.humanize(o)
}

@Composable
fun TransferClaimScreen(onBack: () -> Unit, onClaimed: (String?) -> Unit, viewModel: TransferClaimViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val claimed by viewModel.claimed.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    NoticeEffect(viewModel.notices, snackbar)

    claimed?.let { c ->
        AlertDialog(
            onDismissRequest = { onClaimed(c.assetId) },
            title = { Text(stringResource(R.string.crew_transfer_claimed)) },
            text = { Text(stringResource(R.string.crew_transfer_copied, c.logsCopied)) },
            confirmButton = { TextButton(onClick = { onClaimed(c.assetId) }) { Text(stringResource(R.string.crew_done)) } },
        )
    }

    CrewScaffold(stringResource(R.string.crew_transfer_title), onBack, snackbar) { inner ->
        LoadBox(preview, viewModel::refresh, inner) { t ->
            Column(Modifier.padding(screenPadding(inner)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel {
                    Text(t.asset?.title ?: "—", style = MaterialTheme.typography.titleMedium)
                    t.sellerName?.let { Caption(stringResource(R.string.crew_transfer_from, it)) }
                    Caption(stringResource(R.string.crew_transfer_counts, t.serviceEvents, t.inspections))
                    t.expiresAt?.let { Caption(stringResource(R.string.crew_expires, Fmt.date(it))) }
                }
                if (t.claimable) {
                    Caption(stringResource(R.string.crew_transfer_hint), Modifier.padding(horizontal = 20.dp))
                    Button(
                        onClick = viewModel::claim,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = MinTouch),
                    ) { Text(stringResource(R.string.crew_claim)) }
                } else {
                    val msg = when (t.status) {
                        "claimed" -> R.string.crew_transfer_already_claimed
                        "expired" -> R.string.crew_transfer_expired
                        else -> R.string.crew_transfer_cancelled
                    }
                    StatusPill(stringResource(msg), StatusTone.Neutral, Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

/** CameraX + ML Kit QR scanner (same pattern as feature/rentalsops UnitScanner). */
@Composable
internal fun QrScanner(active: Boolean, onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val isActive by rememberUpdatedState(active)
    val callback by rememberUpdatedState(onCode)
    val label = stringResource(R.string.crew_scan_camera)

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (!granted) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(AvagoSpacing.lg)) {
                Text(stringResource(R.string.crew_scan_permission), color = Color.White, textAlign = TextAlign.Center)
                Button(
                    onClick = { request.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = AvagoSpacing.md).heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.crew_scan_grant)) }
            }
        } else {
            AndroidView(
                factory = { c ->
                    val view = PreviewView(c)
                    val scanner = BarcodeScanning.getClient()
                    view.tag = scanner
                    val future = ProcessCameraProvider.getInstance(c)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(c)) { proxy ->
                            if (isActive) analyze(proxy, scanner) { callback(it) } else proxy.close()
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        } catch (e: Exception) {
                            Timber.w(e, "[Crew] camera bind failed")
                        }
                    }, ContextCompat.getMainExecutor(c))
                    view
                },
                onRelease = { v ->
                    (v.tag as? BarcodeScanner)?.close()
                    runCatching { ProcessCameraProvider.getInstance(v.context).get().unbindAll() }
                },
                modifier = Modifier.fillMaxSize().semantics { contentDescription = label },
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyze(proxy: ImageProxy, scanner: BarcodeScanner, onCode: (String) -> Unit) {
    val img = proxy.image ?: return proxy.close()
    scanner.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees))
        .addOnSuccessListener { codes -> codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.let(onCode) }
        .addOnCompleteListener { proxy.close() }
}
