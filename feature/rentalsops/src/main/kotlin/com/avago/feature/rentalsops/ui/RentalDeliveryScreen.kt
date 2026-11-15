package com.avago.feature.rentalsops.ui

import java.io.File
import javax.inject.Inject
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.cardListItemColors
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.rememberHaptics
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentalsops.data.MediaEntity
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.data.TaskView
import com.avago.feature.rentalsops.domain.DeliveryStateMachine
import com.avago.feature.rentalsops.domain.TaskAction
import com.avago.feature.rentalsops.domain.TaskKind
import com.avago.feature.rentalsops.domain.TransitionError
import com.avago.feature.rentalsops.domain.TransitionResult
import com.avago.feature.rentalsops.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val state: StateFlow<DeliveryState> = combine(repo.observeTask(taskId), repo.observeMedia(taskId), repo.observeAssetNames()) { v, m, names ->
        // Task detail carries `assets[{asset_id, name, serial_number}]`; the feed only ids.
        val t = v?.task
        val units = t?.assets?.takeIf { it.isNotEmpty() }?.map { a -> listOfNotNull(a.name ?: names[a.assetId] ?: a.assetId.take(8), a.serialNumber).joinToString(" \u00b7 ") }
            ?: t?.assetIds?.map { names[it] ?: it.take(8) }.orEmpty()
        DeliveryState(v, m, units, loaded = true)
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
        when (val r = repo.submit(v, action, reason, signedName, notes, loc?.latitude, loc?.longitude, loc?.takeIf { it.hasAccuracy() }?.accuracy)) {
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
    val haptics = rememberHaptics()
    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            if (m is DeliveryMessage.Queued) haptics.success() else haptics.error()
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
    val actions = view?.let { DeliveryStateMachine.availableActions(it.localStatus, it.task.driverUserId != null) }.orEmpty()
    Scaffold(
        topBar = { OpsTopBar(view?.let { kindLabel(it.task.kind) } ?: stringResource(R.string.rentalops_tasks_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (view != null && actions.isNotEmpty()) {
                BottomActionBar {
                    actions.forEach { a ->
                        when (a) {
                            TaskAction.FAIL -> OutlinedButton(
                                onClick = { showFail = true },
                                modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text(stringResource(R.string.rentalops_action_fail), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            TaskAction.COMPLETE -> Button(onClick = { showComplete = true }, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                                Icon(Icons.Filled.TaskAlt, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(AvagoSpacing.sm))
                                Text(stringResource(R.string.rentalops_action_complete), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TaskAction.START -> Button(onClick = { vm.submit(a, null, null, null) }, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                                Icon(Icons.Filled.LocalShipping, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(AvagoSpacing.sm))
                                Text(stringResource(R.string.rentalops_action_start), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TaskAction.ARRIVE -> Button(onClick = { vm.submit(a, null, null, null) }, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                                Icon(Icons.Filled.Place, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(AvagoSpacing.sm))
                                Text(stringResource(R.string.rentalops_action_arrive), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        if (view == null) {
            if (s.loaded) {
                EmptyState(stringResource(R.string.rentalops_task_missing), Modifier.padding(pad), icon = Icons.Filled.SearchOff)
            } else {
                SkeletonList(Modifier.padding(pad), rows = 4)
            }
            return@Scaffold
        }
        val t = view.task
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = AvagoSpacing.sm),
        ) {
            // Header summary
            SectionCard {
                Column(Modifier.padding(AvagoSpacing.lg), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                        TaskStatusPill(view.localStatus)
                        PendingChip(view.pending)
                    }
                    Text(
                        t.customerName ?: t.orderNumber?.let { stringResource(R.string.rentalops_order_n, it) } ?: kindLabel(t.kind),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (t.customerName != null) {
                        t.orderNumber?.let {
                            Text(stringResource(R.string.rentalops_order_n, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconLine(
                        Icons.Outlined.Schedule,
                        stringResource(R.string.rentalops_scheduled, Fmt.dateTime(t.scheduledAt)) + (t.windowEnd?.let { " \u2013 " + Fmt.dateTime(it) } ?: ""),
                    )
                    t.jobSiteName?.let { IconLine(Icons.Outlined.Business, stringResource(R.string.rentalops_job_site, it)) }
                }
                t.failedReason?.let {
                    ErrorBanner(stringResource(R.string.rentalops_failed_reason, it), Modifier.padding(bottom = AvagoSpacing.md))
                }
            }

            view.rejected?.let { rej -> RejectedCard(rej, onRetry = { vm.retry(rej.queueId) }, onDiscard = { vm.discard(rej.queueId) }) }

            // Where / who
            SectionCard(title = stringResource(R.string.rentalops_location)) {
                Column(Modifier.padding(AvagoSpacing.lg), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    IconLine(Icons.Outlined.Place, t.address ?: stringResource(R.string.rentalops_no_address), MaterialTheme.typography.bodyLarge)
                    t.contactName?.let { IconLine(Icons.Outlined.Person, it, MaterialTheme.typography.bodyLarge) }
                    val canNavigate = t.address != null || (t.lat != null && t.lng != null)
                    if (canNavigate || t.phone != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                            if (canNavigate) {
                                FilledTonalButton(onClick = { openNavigation(ctx, t.lat, t.lng, t.address) }, modifier = Modifier.heightIn(min = MinTouch)) {
                                    Icon(Icons.Filled.Directions, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(AvagoSpacing.sm))
                                    Text(stringResource(R.string.rentalops_navigate))
                                }
                            }
                            t.phone?.let { phone ->
                                val callLabel = stringResource(R.string.rentalops_call, t.contactName ?: phone)
                                FilledTonalButton(
                                    onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                                    modifier = Modifier.heightIn(min = MinTouch).semantics { contentDescription = callLabel },
                                ) {
                                    Icon(Icons.Filled.Call, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(AvagoSpacing.sm))
                                    Text(stringResource(R.string.rentalops_call_short))
                                }
                            }
                        }
                    }
                }
            }

            if (s.unitNames.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.rentalops_units)) {
                    s.unitNames.forEachIndexed { i, name ->
                        if (i > 0) CardDivider()
                        ListItem(
                            colors = cardListItemColors(),
                            leadingContent = { LeadingBadge(Icons.Filled.Inventory2, StatusTone.Neutral) },
                            headlineContent = { Text(name) },
                        )
                    }
                }
            }
            t.notes?.let {
                SectionCard(title = stringResource(R.string.rentalops_notes)) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(AvagoSpacing.lg))
                }
            }

            // Proof (captured offline, uploaded with the Complete event)
            if (TaskAction.COMPLETE in actions) {
                SectionCard(title = stringResource(if (t.kind == TaskKind.DELIVERY) R.string.rentalops_proof_required else R.string.rentalops_proof_optional)) {
                    ProofGrid(s.drafts, onRemove = vm::removeDraft)
                    Row(
                        Modifier.padding(horizontal = AvagoSpacing.lg).padding(bottom = AvagoSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
                    ) {
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
                            modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(AvagoSpacing.sm))
                            Text(stringResource(R.string.rentalops_take_photo), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick = { showSignature = true }, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                            Icon(Icons.Filled.Draw, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(AvagoSpacing.sm))
                            Text(stringResource(R.string.rentalops_signature), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    CardDivider()
                    Column(Modifier.padding(AvagoSpacing.lg), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                        OutlinedTextField(
                            value = signedName,
                            onValueChange = { signedName = it.take(120) },
                            label = { Text(stringResource(R.string.rentalops_signed_name)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it.take(1000) },
                            label = { Text(stringResource(R.string.rentalops_notes)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
            icon = { Icon(Icons.Filled.ReportProblem, contentDescription = null) },
            title = { Text(stringResource(R.string.rentalops_action_fail)) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text(stringResource(R.string.rentalops_fail_reason)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.submit(TaskAction.FAIL, reason, null, null) { showFail = false } },
                    enabled = reason.isNotBlank(),
                    modifier = Modifier.heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentalops_action_fail), color = if (reason.isNotBlank()) MaterialTheme.colorScheme.error else Color.Unspecified) }
            },
            dismissButton = { TextButton(onClick = { showFail = false }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentalops_cancel)) } },
        )
    }
    if (showComplete) {
        AlertDialog(
            onDismissRequest = { showComplete = false },
            icon = { Icon(Icons.Filled.TaskAlt, contentDescription = null) },
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
private fun IconLine(icon: ImageVector, text: String, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    Row(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = style)
    }
}

/** Photo + signature grid (wraps; 96dp tiles with a 48dp remove target in the corner). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProofGrid(drafts: List<MediaEntity>, onRemove: (String) -> Unit) {
    if (drafts.isEmpty()) {
        Row(
            Modifier.padding(AvagoSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.AddAPhoto, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.rentalops_proof_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(AvagoSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
    ) {
        val photos = drafts.filter { it.kind == "photo" }
        drafts.forEach { m ->
            key(m.id) {
                val label = if (m.kind == "signature") stringResource(R.string.rentalops_signature) else stringResource(R.string.rentalops_photo_n, photos.indexOf(m) + 1)
                Box {
                    Thumb(m.localPath, label, signature = m.kind == "signature")
                    IconButton(onClick = { onRemove(m.id) }, modifier = Modifier.align(Alignment.TopEnd).size(MinTouch)) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.rentalops_remove_item, label), Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(path: String, label: String, signature: Boolean) {
    val bmp by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 }) }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(96.dp)
            .clip(MaterialTheme.shapes.medium)
            // Signatures are dark strokes on transparent: keep a light backdrop in both themes.
            .background(if (signature) Color.White else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let {
            Image(it.asImageBitmap(), contentDescription = label, contentScale = if (signature) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } ?: Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
