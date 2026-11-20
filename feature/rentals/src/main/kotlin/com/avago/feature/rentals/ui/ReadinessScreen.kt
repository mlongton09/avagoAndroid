package com.avago.feature.rentals.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.ReadinessCheck
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.domain.StoredFile
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.permissions.PermissionsManager
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.StoredFileThumbnail
import com.avago.core.ui.components.cardListItemColors
import com.avago.core.ui.components.rememberHaptics
import com.avago.core.ui.components.toneColors
import com.avago.feature.rentals.R
import com.avago.feature.rentals.domain.Readiness
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * What the readiness screen shows for a unit: the check being decided ([editable]) or the last
 * decision (read-only until the user starts a new check).
 */
data class ReadinessUi(
    /** The check a decision applies to; null = a new check is started on save. */
    val check: ReadinessCheck?,
    val editable: Boolean,
)

/**
 * Pure selection rule (unit-tested): an open check is edited in place; a decided check that has
 * not synced yet is shown read-only; otherwise (no check, or the last one is closed and synced)
 * a new check can be started when [startNew] is set or none exists.
 */
internal fun readinessUi(current: ReadinessCheck?, startNew: Boolean): ReadinessUi = when {
    current == null -> ReadinessUi(null, editable = true)
    current.open -> ReadinessUi(current, editable = true)
    startNew && current.sync == RecordSyncState.SAVED -> ReadinessUi(null, editable = true)
    else -> ReadinessUi(current, editable = false)
}

@HiltViewModel
class ReadinessViewModel @Inject constructor(
    private val repo: RentalRepository,
    permissions: PermissionsManager,
    savedState: SavedStateHandle,
) : ViewModel() {
    val assetId: String = requireNotNull(savedState["assetId"])

    /** Waiving needs `rental.override_ready` (server answers 403 otherwise). */
    val canWaive: Boolean = permissions.can(Readiness.PERMISSION_WAIVE)

    val unitName: StateFlow<String?> = repo.observeFleet()
        .map { inputs -> inputs.units.firstOrNull { it.assetId == assetId }?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val startNew = MutableStateFlow(false)

    val ui: StateFlow<ReadinessUi?> = combine(
        repo.observeReadinessChecks(assetId).map(Readiness::current),
        startNew,
    ) { current, fresh -> readinessUi(current, fresh) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val items = MutableStateFlow(Readiness.DEFAULT_ITEMS)
    val notes = MutableStateFlow("")

    /** Condition photos taken on this device for the check being decided (added to its saved ones). */
    val photos = MutableStateFlow<List<File>>(emptyList())

    fun addPhoto(file: File) {
        photos.value = photos.value + file
    }
    private var loadedFor: String? = "\u0000"

    private val _problems = MutableSharedFlow<Readiness.Problem>(extraBufferCapacity = 2)
    val problems: SharedFlow<Readiness.Problem> = _problems
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val errors: SharedFlow<String> = _errors
    private val _done = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val done: SharedFlow<String> = _done

    init {
        viewModelScope.launch {
            ui.collect { u ->
                if (u == null || !u.editable) return@collect
                val key = u.check?.id
                if (key != loadedFor) {
                    loadedFor = key
                    items.value = u.check?.items ?: Readiness.DEFAULT_ITEMS
                    notes.value = u.check?.notes.orEmpty()
                }
            }
        }
    }

    fun startNewCheck() {
        startNew.value = true
    }

    fun toggle(key: String, done: Boolean) {
        items.value = items.value.map { if (it.key == key) it.copy(done = done) else it }
    }

    fun decide(status: String) = viewModelScope.launch {
        val u = ui.value ?: return@launch
        if (!u.editable) return@launch
        val from = u.check?.status ?: Readiness.PENDING
        Readiness.validate(from, status, items.value, notes.value, canWaive)?.let {
            _problems.emit(it)
            return@launch
        }
        try {
            val text = notes.value.takeIf { it.isNotBlank() }
            val taken = photos.value.map { LocalFileRef(Uri.fromFile(it).toString(), "image/jpeg") }
            val existing = u.check?.id
            val id = if (existing == null) {
                // New photos go with the create; the decision then only changes status and items.
                repo.startReadinessCheck(assetId, items.value, text, taken).also { created ->
                    if (status != Readiness.PENDING) repo.decideReadiness(created, status, items.value, text)
                }
            } else {
                existing.also { repo.decideReadiness(it, status, items.value, text, taken) }
            }
            photos.value = emptyList()
            startNew.value = false
            _done.emit(id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "readiness save failed")
            _errors.emit(e.message ?: e.javaClass.simpleName)
        }
    }
}

@StringRes
internal fun readinessItemLabel(key: String): Int? = when (key) {
    "inspected" -> R.string.rentals_readiness_item_inspected
    "cleaned" -> R.string.rentals_readiness_item_cleaned
    "refuelled" -> R.string.rentals_readiness_item_refuelled
    "accessories" -> R.string.rentals_readiness_item_accessories
    else -> null
}

@StringRes
internal fun readinessStatusLabel(status: String): Int = when (status) {
    Readiness.PASSED -> R.string.rentals_readiness_status_passed
    Readiness.FAILED -> R.string.rentals_readiness_status_failed
    Readiness.WAIVED -> R.string.rentals_readiness_status_waived
    else -> R.string.rentals_readiness_status_pending
}

@StringRes
private fun problemLabel(p: Readiness.Problem): Int = when (p) {
    Readiness.Problem.CLOSED -> R.string.rentals_readiness_error_closed
    Readiness.Problem.INCOMPLETE -> R.string.rentals_readiness_error_incomplete
    Readiness.Problem.REASON_REQUIRED -> R.string.rentals_readiness_error_reason
    Readiness.Problem.NOT_ALLOWED -> R.string.rentals_readiness_error_not_allowed
}

/**
 * Readiness checklist after a check-in (§11.1, stage B §3): tick items, then pass / fail /
 * waive (waive needs `rental.override_ready` and a reason). Sent as `rental.rental_handovers`
 * readiness commands with an optimistic local apply — the fleet updates at once with a pending
 * marker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadinessScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ReadinessViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val unitName by viewModel.unitName.collectAsStateWithLifecycle()
    val taken by viewModel.photos.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraDenied = stringResource(R.string.rentals_readiness_camera_denied)
    // The pending capture file survives the camera activity / process death.
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val problemTexts = Readiness.Problem.entries.associateWith { stringResource(problemLabel(it)) }
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) { viewModel.problems.collect { haptics.error(); snackbar.showSnackbar(problemTexts.getValue(it)) } }
    LaunchedEffect(Unit) { viewModel.errors.collect { haptics.error(); snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.done.collect { haptics.success(); onDone() } }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = capturePath?.let(::File)
        capturePath = null
        if (ok && file != null && file.length() > 0) {
            haptics.tick()
            viewModel.addPhoto(file)
        } else {
            file?.delete()
        }
    }
    fun launchCamera() {
        val dir = File(context.filesDir, "rental_photos").apply { mkdirs() }
        val file = File(dir, "readiness_${UUID.randomUUID()}.jpg")
        capturePath = file.absolutePath
        takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.provider", file))
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else scope.launch { snackbar.showSnackbar(cameraDenied) }
    }
    val takePhoto: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    val u = ui
    val doneCount = items.count { it.done }
    Scaffold(
        topBar = {
            RentalsTopBar(
                unitName?.let { stringResource(R.string.rentals_readiness_title_for, it) } ?: stringResource(R.string.rentals_readiness_open),
                onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (u != null && u.editable) {
                BottomActionBar {
                    OutlinedButton(
                        onClick = { viewModel.decide(Readiness.FAILED) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                    ) { Text(stringResource(R.string.rentals_readiness_fail), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (viewModel.canWaive) {
                        OutlinedButton(onClick = { viewModel.decide(Readiness.WAIVED) }, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                            Text(stringResource(R.string.rentals_readiness_waive), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Button(
                        onClick = { viewModel.decide(Readiness.PASSED) },
                        enabled = items.all { it.done },
                        modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.rentals_readiness_pass), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else if (u?.check != null && u.check.sync == RecordSyncState.SAVED) {
                BottomActionBar {
                    Button(onClick = viewModel::startNewCheck, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.rentals_readiness_new_check))
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.rentals_readiness_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (u == null) {
                SkeletonList(rows = 4)
                return@Column
            }
            u.check?.let { c ->
                SectionCard(title = stringResource(R.string.rentals_status)) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusPill(stringResource(readinessStatusLabel(c.status)), readinessTone(c.status), icon = readinessIcon(c.status))
                        SyncStateBadge(c.sync)
                    }
                    if (!c.open) {
                        CardDivider()
                        LabelValue(stringResource(R.string.rentals_readiness_completed), formatDateTime(c.at, ZoneId.systemDefault()), Modifier.padding(vertical = 6.dp))
                    }
                }
            }
            if (!u.editable) {
                val c = u.check
                if (c != null) {
                    SectionCard(title = stringResource(R.string.rentals_readiness_checklist)) {
                        c.items.forEachIndexed { i, item ->
                            if (i > 0) CardDivider()
                            val label = readinessItemLabel(item.key)?.let { stringResource(it) } ?: item.label
                            ListItem(
                                colors = cardListItemColors(),
                                leadingContent = {
                                    Icon(
                                        if (item.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (item.done) toneColors(StatusTone.Success).accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                headlineContent = { Text(label) },
                                supportingContent = { Text(stringResource(if (item.done) R.string.rentals_readiness_done else R.string.rentals_readiness_not_done)) },
                            )
                        }
                    }
                    c.notes?.let { n ->
                        SectionCard(title = stringResource(R.string.rentals_notes)) {
                            Text(n, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                        }
                    }
                    if (c.photos.isNotEmpty()) ReadinessPhotoStrip(saved = c.photos, taken = emptyList(), onAdd = null)
                }
                return@Column
            }

            SectionCard(
                title = stringResource(R.string.rentals_readiness_checklist),
                trailing = {
                    Text(
                        stringResource(R.string.rentals_readiness_progress, doneCount, items.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            ) {
                LinearProgressIndicator(
                    progress = { if (items.isEmpty()) 0f else doneCount / items.size.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clearAndSetSemantics { },
                )
                items.forEachIndexed { i, item ->
                    if (i > 0) CardDivider()
                    val label = readinessItemLabel(item.key)?.let { stringResource(it) } ?: item.label
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(value = item.done, role = Role.Checkbox) { viewModel.toggle(item.key, it) }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = item.done, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
            ReadinessPhotoStrip(saved = u.check?.photos.orEmpty(), taken = taken, onAdd = takePhoto)
            SectionCard(title = stringResource(R.string.rentals_readiness_notes), contentPadding = true) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { viewModel.notes.value = it },
                    placeholder = { Text(stringResource(R.string.rentals_readiness_notes)) },
                    supportingText = { Text(stringResource(R.string.rentals_readiness_notes_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!items.all { it.done }) {
                    HintLine(Icons.Outlined.Info, stringResource(R.string.rentals_readiness_error_incomplete))
                }
                if (!viewModel.canWaive) {
                    HintLine(Icons.Outlined.Lock, stringResource(R.string.rentals_readiness_waive_needs_permission))
                }
            }
        }
    }
}

/**
 * Condition photos of a readiness check: the saved ones (`rental_handover_files`, bytes via
 * `files/{id}/download`) then the ones taken here, which upload with the next save. [onAdd] null
 * = read-only.
 */
@Composable
private fun ReadinessPhotoStrip(saved: List<StoredFile>, taken: List<File>, onAdd: (() -> Unit)?) {
    val count = saved.size + taken.size
    SectionCard(
        title = stringResource(R.string.rentals_condition_photos),
        trailing = if (count > 0) {
            { StatusPill(count.toString(), StatusTone.Neutral) }
        } else {
            null
        },
        contentPadding = true,
    ) {
        val tile = Modifier
            .size(AvagoSpacing.photoTile)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
            items(saved, key = { "s:" + it.id }) { f -> StoredFileThumbnail(f, tile) }
            items(taken, key = { "t:" + it.absolutePath }) { f ->
                Box(tile) {
                    AsyncImage(model = f, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            if (onAdd != null) {
                item(key = "add") {
                    val label = stringResource(R.string.rentals_add_photo)
                    Surface(
                        onClick = onAdd,
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(AvagoSpacing.photoTile).semantics { contentDescription = label },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HintLine(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun readinessTone(status: String): StatusTone = when (status) {
    Readiness.PASSED -> StatusTone.Success
    Readiness.FAILED -> StatusTone.Danger
    Readiness.WAIVED -> StatusTone.Warning
    else -> StatusTone.Neutral
}

internal fun readinessIcon(status: String): ImageVector = when (status) {
    Readiness.PASSED -> Icons.Default.CheckCircle
    Readiness.FAILED -> Icons.Default.Cancel
    Readiness.WAIVED -> Icons.Default.Gavel
    else -> Icons.Default.HourglassEmpty
}
