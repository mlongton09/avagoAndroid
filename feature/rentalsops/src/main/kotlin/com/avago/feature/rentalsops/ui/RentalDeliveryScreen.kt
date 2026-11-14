package com.avago.feature.rentalsops.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.data.MediaEntity
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.data.TaskView
import com.avago.feature.rentalsops.domain.DeliveryStateMachine
import com.avago.feature.rentalsops.domain.TaskAction
import com.avago.feature.rentalsops.domain.TaskKind
import com.avago.feature.rentalsops.domain.TransitionError
import com.avago.feature.rentalsops.domain.TransitionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DeliveryState(
    val view: TaskView? = null,
    val media: List<MediaEntity> = emptyList(),
    val unitNames: List<String> = emptyList(),
    val loaded: Boolean = false,
) {
    val drafts: List<MediaEntity> get() = media.filter { it.eventQueueId == null }
}

sealed class DeliveryMessage {
    data class Error(val error: TransitionError) : DeliveryMessage()
    data object Queued : DeliveryMessage()
    data object PhotoFailed : DeliveryMessage()
}

@HiltViewModel
class RentalDeliveryViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: RentalOpsRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    val taskId: String = checkNotNull(saved["taskId"])

    val state: StateFlow<DeliveryState> = combine(repo.observeTask(taskId), repo.observeMedia(taskId), repo.observeFleet()) { v, m, fleet ->
        val names = fleet.associate { it.assetId to it.name }
        DeliveryState(v, m, v?.task?.assetIds?.map { names[it] ?: it.take(8) }.orEmpty(), loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeliveryState())

    private val _messages = MutableSharedFlow<DeliveryMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<DeliveryMessage> = _messages

    /** Pending camera target (id, file) between launching the camera and its result. */
    private var pendingPhoto: Pair<String, File>? = null

    suspend fun preparePhoto(): Uri? {
        val (id, file) = repo.newMediaFile("photo") ?: return null
        pendingPhoto = id to file
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
    }

    fun onPhotoResult(ok: Boolean) {
        val (id, file) = pendingPhoto ?: return
        pendingPhoto = null
        viewModelScope.launch {
            if (ok && Media.normalizePhoto(file)) {
                repo.addMedia(taskId, id, "photo", file)
            } else {
                withContext(Dispatchers.IO) { file.delete() }
                if (ok) _messages.tryEmit(DeliveryMessage.PhotoFailed)
            }
        }
    }

    fun saveSignature(c: SignatureCapture) = viewModelScope.launch {
        // One signature per task: replace an earlier draft.
        state.value.drafts.filter { it.kind == "signature" }.forEach { repo.removeDraftMedia(it.id) }
        val (id, file) = repo.newMediaFile("signature") ?: return@launch
        if (Media.writeSignature(c.strokes, c.width, c.height, c.strokePx, file)) repo.addMedia(taskId, id, "signature", file)
    }

    fun removeDraft(id: String) = viewModelScope.launch { repo.removeDraftMedia(id) }

    fun submit(action: TaskAction, reason: String?, signedName: String?, notes: String?, onDone: () -> Unit = {}) = viewModelScope.launch {
        val v = state.value.view ?: return@launch
        val loc = LastLocation.get(ctx)
        when (val r = repo.submit(v, action, reason, signedName, notes, loc?.latitude, loc?.longitude)) {
            is TransitionResult.Ok -> {
                _messages.tryEmit(DeliveryMessage.Queued)
                onDone()
            }
            is TransitionResult.Rejected -> _messages.tryEmit(DeliveryMessage.Error(r.error))
        }
    }

    fun retry(queueId: String) = viewModelScope.launch { repo.retry(queueId) }
    fun discard(queueId: String) = viewModelScope.launch { repo.discard(queueId) }
}

@Composable
fun RentalDeliveryScreen(onBack: () -> Unit, vm: RentalDeliveryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showSignature by rememberSaveable { mutableStateOf(false) }
    var showFail by rememberSaveable { mutableStateOf(false) }
    var showComplete by rememberSaveable { mutableStateOf(false) }
    var signedName by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.onPhotoResult(ok) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraUri?.let { camera.launch(it) }
    }

    val msgQueued = stringResource(R.string.rentalops_queued)
    val msgPhoto = stringResource(R.string.rentalops_photo_failed)
    val errInvalid = stringResource(R.string.rentalops_err_invalid)
    val errDriver = stringResource(R.string.rentalops_err_driver)
    val errProof = stringResource(R.string.rentalops_err_proof)
    val errReason = stringResource(R.string.rentalops_err_reason)
    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            snackbar.showSnackbar(
                when (m) {
                    DeliveryMessage.Queued -> msgQueued
                    DeliveryMessage.PhotoFailed -> msgPhoto
                    is DeliveryMessage.Error -> when (m.error) {
                        is TransitionError.Invalid -> errInvalid
                        TransitionError.DriverRequired -> errDriver
                        TransitionError.ProofRequired -> errProof
                        TransitionError.ReasonRequired -> errReason
                    }
                },
            )
        }
    }

    val view = s.view
    Scaffold(
        topBar = { OpsTopBar(view?.let { kindLabel(it.task.kind) } ?: stringResource(R.string.rentalops_tasks_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (view == null) {
            if (s.loaded) EmptyState(stringResource(R.string.rentalops_task_missing), Modifier.padding(pad))
            return@Scaffold
        }
        val t = view.task
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Column(Modifier.padding(AvagoSpacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(statusLabel(view.localStatus), style = MaterialTheme.typography.titleMedium, color = statusColor(view.localStatus))
                    Box(Modifier.padding(start = AvagoSpacing.sm)) { PendingChip(view.pending) }
                }
                t.customerName?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
                t.orderNumber?.let { Text(stringResource(R.string.rentalops_order_n, it), style = MaterialTheme.typography.bodyMedium) }
                Text(
                    stringResource(R.string.rentalops_scheduled, Fmt.dateTime(t.scheduledAt)) +
                        (t.windowEnd?.let { " \u2013 " + Fmt.dateTime(it) } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                t.failedReason?.let { Text(stringResource(R.string.rentalops_failed_reason, it), color = MaterialTheme.colorScheme.error) }
            }

            view.rejected?.let { rej -> RejectedCard(rej, onRetry = { vm.retry(rej.queueId) }, onDiscard = { vm.discard(rej.queueId) }) }

            // Where / who
            Card(Modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs)) {
                Row(Modifier.padding(AvagoSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.address ?: stringResource(R.string.rentalops_no_address), style = MaterialTheme.typography.bodyLarge)
                        t.contactName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (t.address != null || (t.lat != null && t.lng != null)) {
                        FilledTonalIconButton(onClick = { openNavigation(ctx, t.lat, t.lng, t.address) }, modifier = Modifier.size(MinTouch)) {
                            Icon(Icons.Filled.Directions, contentDescription = stringResource(R.string.rentalops_navigate))
                        }
                    }
                    t.contactPhone?.let { phone ->
                        FilledTonalIconButton(
                            onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                            modifier = Modifier.padding(start = AvagoSpacing.sm).size(MinTouch),
                        ) { Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.rentalops_call, t.contactName ?: phone)) }
                    }
                }
            }

            if (s.unitNames.isNotEmpty()) {
                SectionHeader(stringResource(R.string.rentalops_units))
                Text(s.unitNames.joinToString(", "), modifier = Modifier.padding(horizontal = AvagoSpacing.lg))
            }
            t.notes?.let {
                SectionHeader(stringResource(R.string.rentalops_notes))
                Text(it, modifier = Modifier.padding(horizontal = AvagoSpacing.lg))
            }

            // Proof (captured offline, uploaded with the Complete event)
            val actions = DeliveryStateMachine.availableActions(view.localStatus, t.driverUserId != null)
            if (TaskAction.COMPLETE in actions) {
                SectionHeader(stringResource(if (t.kind == TaskKind.DELIVERY) R.string.rentalops_proof_required else R.string.rentalops_proof_optional))
                ProofStrip(s.drafts, onRemove = vm::removeDraft)
                Row(Modifier.padding(horizontal = AvagoSpacing.lg), horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                cameraUri = vm.preparePhoto()
                                cameraUri?.let {
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA) ==
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        camera.launch(it)
                                    } else {
                                        cameraPermission.launch(android.Manifest.permission.CAMERA)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.heightIn(min = MinTouch),
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Text(stringResource(R.string.rentalops_take_photo), Modifier.padding(start = AvagoSpacing.xs))
                    }
                    OutlinedButton(onClick = { showSignature = true }, modifier = Modifier.heightIn(min = MinTouch)) {
                        Icon(Icons.Filled.Draw, contentDescription = null)
                        Text(stringResource(R.string.rentalops_signature), Modifier.padding(start = AvagoSpacing.xs))
                    }
                }
                OutlinedTextField(
                    value = signedName,
                    onValueChange = { signedName = it.take(120) },
                    label = { Text(stringResource(R.string.rentalops_signed_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(1000) },
                    label = { Text(stringResource(R.string.rentalops_notes)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
                )
            }

            // Actions
            Column(Modifier.padding(AvagoSpacing.lg), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                actions.forEach { a ->
                    when (a) {
                        TaskAction.FAIL -> OutlinedButton(
                            onClick = { showFail = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.rentalops_action_fail)) }
                        TaskAction.COMPLETE -> Button(onClick = { showComplete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                            Text(stringResource(R.string.rentalops_action_complete))
                        }
                        TaskAction.START -> Button(onClick = { vm.submit(a, null, null, null) }, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                            Text(stringResource(R.string.rentalops_action_start))
                        }
                        TaskAction.ARRIVE -> Button(onClick = { vm.submit(a, null, null, null) }, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                            Text(stringResource(R.string.rentalops_action_arrive))
                        }
                    }
                }
            }
        }
    }

    if (showSignature) {
        SignatureDialog(onDismiss = { showSignature = false }, onSave = {
            vm.saveSignature(it)
            showSignature = false
        })
    }
    if (showFail) {
        var reason by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFail = false },
            title = { Text(stringResource(R.string.rentalops_action_fail)) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text(stringResource(R.string.rentalops_fail_reason)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.submit(TaskAction.FAIL, reason, null, null) { showFail = false } },
                    enabled = reason.isNotBlank(),
                    modifier = Modifier.heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentalops_action_fail)) }
            },
            dismissButton = { TextButton(onClick = { showFail = false }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentalops_cancel)) } },
        )
    }
    if (showComplete) {
        AlertDialog(
            onDismissRequest = { showComplete = false },
            title = { Text(stringResource(R.string.rentalops_action_complete)) },
            text = { Text(stringResource(R.string.rentalops_complete_confirm, s.drafts.count { it.kind == "photo" }, if (s.drafts.any { it.kind == "signature" }) 1 else 0)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showComplete = false
                        vm.submit(TaskAction.COMPLETE, null, signedName, notes)
                    },
                    modifier = Modifier.heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentalops_action_complete)) }
            },
            dismissButton = { TextButton(onClick = { showComplete = false }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentalops_cancel)) } },
        )
    }
}

@Composable
private fun ProofStrip(drafts: List<MediaEntity>, onRemove: (String) -> Unit) {
    if (drafts.isEmpty()) {
        Text(
            stringResource(R.string.rentalops_proof_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
        )
        return
    }
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = AvagoSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = AvagoSpacing.lg),
    ) {
        items(drafts, key = { it.id }) { m ->
            val index = drafts.filter { it.kind == "photo" }.indexOf(m) + 1
            val label = if (m.kind == "signature") stringResource(R.string.rentalops_signature) else stringResource(R.string.rentalops_photo_n, index)
            Box {
                Thumb(m.localPath, label)
                FilledTonalIconButton(onClick = { onRemove(m.id) }, modifier = Modifier.align(Alignment.TopEnd).size(MinTouch)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.rentalops_remove_item, label))
                }
            }
        }
    }
}

@Composable
private fun Thumb(path: String, label: String) {
    val bmp by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 }) }.getOrNull()
        }
    }
    Box(Modifier.size(96.dp)) {
        bmp?.let { Image(it.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: Text(label, Modifier.align(Alignment.Center))
    }
}

/** Google Maps turn-by-turn when installed, else any maps app via `geo:`. */
internal fun openNavigation(ctx: Context, lat: Double?, lng: Double?, address: String?) {
    val q = if (lat != null && lng != null) "$lat,$lng" else Uri.encode(address ?: return)
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")).setPackage("com.google.android.apps.maps")
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse(if (lat != null && lng != null) "geo:$lat,$lng?q=$q" else "geo:0,0?q=$q"))
    for (i in listOf(nav, geo)) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
    android.widget.Toast.makeText(ctx, R.string.rentalops_no_maps, android.widget.Toast.LENGTH_SHORT).show()
}
