package com.avago.feature.rentalsops.ui

import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.Location
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.cardListItemColors
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.rememberHaptics
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.data.TransferView
import com.avago.feature.rentalsops.domain.TransferStatus
import com.avago.feature.rentalsops.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TransfersViewModel @Inject constructor(private val repo: RentalOpsRepository) : ViewModel() {
    val transfers: StateFlow<List<TransferView>?> = repo.observeTransfers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val locations: StateFlow<List<Location>> = repo.observeLocations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun lookup(code: String) = repo.lookupAsset(code)

    fun create(asset: RentalOpsRepository.ScannedAsset, to: String, arriveBy: Instant, reason: String?, shipNow: Boolean, done: () -> Unit) =
        viewModelScope.launch {
            repo.createTransfer(asset, to, arriveBy, reason?.takeIf { it.isNotBlank() }, shipNow)
            done()
        }

    fun step(v: TransferView, op: String) = viewModelScope.launch {
        repo.transferStep(v.transfer.id, v.transfer.assetId ?: return@launch, op)
    }

    fun retry(queueId: String) = viewModelScope.launch { repo.retry(queueId) }
    fun discard(queueId: String) = viewModelScope.launch { repo.discard(queueId) }
}

@Composable
fun TransfersScreen(onBack: () -> Unit, vm: TransfersViewModel = hiltViewModel()) {
    val list by vm.transfers.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    if (creating) {
        BackHandler { creating = false }
        CreateTransfer(vm, locations, onClose = { creating = false })
        return
    }
    val names = locations.associate { it.id to it.name }
    val listState = rememberLazyListState()
    val expanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    Scaffold(
        topBar = { OpsTopBar(stringResource(R.string.rentalops_transfers_title), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                expanded = expanded,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.rentalops_transfer_new)) },
            )
        },
    ) { pad ->
        val l = list
        when {
            l == null -> SkeletonList(Modifier.padding(pad), rows = 5)
            l.isEmpty() -> EmptyState(
                stringResource(R.string.rentalops_transfers_empty),
                Modifier.padding(pad),
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                actionLabel = stringResource(R.string.rentalops_transfer_new),
                onAction = { creating = true },
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                state = listState,
                contentPadding = PaddingValues(top = AvagoSpacing.sm, bottom = 88.dp),
            ) {
                items(l, key = { it.transfer.id }, contentType = { "transfer" }) { v -> TransferRow(v, names, vm, Modifier.animateItem()) }
            }
        }
    }
}

internal fun transferTone(s: TransferStatus): StatusTone = when (s) {
    TransferStatus.REQUESTED -> StatusTone.Warning
    TransferStatus.IN_TRANSIT -> StatusTone.Info
    TransferStatus.RECEIVED -> StatusTone.Success
    TransferStatus.CANCELLED -> StatusTone.Neutral
}

internal fun transferIcon(s: TransferStatus): ImageVector = when (s) {
    TransferStatus.REQUESTED -> Icons.Filled.Schedule
    TransferStatus.IN_TRANSIT -> Icons.Filled.LocalShipping
    TransferStatus.RECEIVED -> Icons.Filled.CheckCircle
    TransferStatus.CANCELLED -> Icons.Filled.Block
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransferRow(v: TransferView, names: Map<String, String>, vm: TransfersViewModel, modifier: Modifier = Modifier) {
    val t = v.transfer
    val from = t.fromLocationId?.let { names[it] } ?: "\u2014"
    val to = t.toLocationId?.let { names[it] } ?: "\u2014"
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
    ) {
        Column {
            Row(
                Modifier.padding(AvagoSpacing.md).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md),
            ) {
                LeadingBadge(transferIcon(v.localStatus), transferTone(v.localStatus))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                    Text(v.assetName ?: t.assetId?.take(8) ?: "\u2014", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.rentalops_transfer_route, from, to), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.rentalops_arrive_by, Fmt.dateTime(t.arriveBy)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                        StatusPill(transferStatusLabel(v.localStatus), transferTone(v.localStatus), icon = transferIcon(v.localStatus))
                        PendingChip(v.pending)
                    }
                }
            }
            v.pending.firstOrNull { it.rejected || it.parked }?.let { rej ->
                RejectedCard(rej, onRetry = { vm.retry(rej.queueId) }, onDiscard = { vm.discard(rej.queueId) }, Modifier.padding(bottom = AvagoSpacing.sm))
            }
            if (v.localStatus == TransferStatus.REQUESTED || v.localStatus == TransferStatus.IN_TRANSIT) {
                CardDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.md, vertical = AvagoSpacing.sm),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (v.localStatus == TransferStatus.REQUESTED) {
                        FilledTonalButton(onClick = { vm.step(v, "ship") }, modifier = Modifier.heightIn(min = MinTouch)) {
                            Icon(Icons.Filled.LocalShipping, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(AvagoSpacing.sm))
                            Text(stringResource(R.string.rentalops_transfer_ship))
                        }
                    }
                    if (v.localStatus == TransferStatus.IN_TRANSIT) {
                        Button(onClick = { vm.step(v, "receive") }, modifier = Modifier.heightIn(min = MinTouch)) {
                            Icon(Icons.Filled.MoveToInbox, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(AvagoSpacing.sm))
                            Text(stringResource(R.string.rentalops_transfer_receive))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun transferStatusLabel(s: TransferStatus): String = stringResource(
    when (s) {
        TransferStatus.REQUESTED -> R.string.rentalops_transfer_requested
        TransferStatus.IN_TRANSIT -> R.string.rentalops_transfer_in_transit
        TransferStatus.RECEIVED -> R.string.rentalops_transfer_received
        TransferStatus.CANCELLED -> R.string.rentalops_status_cancelled
    },
)

/** Transit presets (hours); the server requires `arrive_by`. */
private val ARRIVE_PRESETS = listOf(4L, 24L, 48L)

@Composable
private fun CreateTransfer(vm: TransfersViewModel, locations: List<Location>, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    var asset by remember { mutableStateOf<RentalOpsRepository.ScannedAsset?>(null) }
    var manual by rememberSaveable { mutableStateOf("") }
    var lookingUp by remember { mutableStateOf(false) }
    var dest by rememberSaveable { mutableStateOf<String?>(null) }
    var hours by rememberSaveable { mutableStateOf(24L) }
    var reason by rememberSaveable { mutableStateOf("") }
    var shipNow by rememberSaveable { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val notFound = stringResource(R.string.rentalops_scan_not_found)
    var lastMissed by remember { mutableStateOf<String?>(null) }

    fun resolve(code: String) {
        if (lookingUp || code.isBlank()) return
        lookingUp = true
        scope.launch {
            val hit = vm.lookup(code.trim())
            lookingUp = false
            if (hit != null) {
                haptics.success()
                asset = hit
                dest = null
            } else if (lastMissed != code) {
                haptics.error()
                lastMissed = code
                snackbar.showSnackbar(notFound)
            }
        }
    }

    val a = asset
    Scaffold(
        topBar = { OpsTopBar(stringResource(R.string.rentalops_transfer_new), onClose) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (a != null) {
                BottomActionBar {
                    Button(
                        onClick = {
                            dest?.let { d ->
                                saving = true
                                vm.create(a, d, Instant.now().plus(Duration.ofHours(hours)), reason, shipNow) { haptics.success(); onClose() }
                            }
                        },
                        enabled = dest != null && !saving,
                        modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(AvagoSpacing.sm))
                        }
                        Text(stringResource(R.string.rentalops_transfer_create))
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
                .padding(vertical = AvagoSpacing.sm),
        ) {
            if (a == null) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = Color.Black,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm).fillMaxWidth().aspectRatio(4f / 3f),
                ) {
                    UnitScanner(active = !lookingUp, onCode = { resolve(it) }, modifier = Modifier.fillMaxSize())
                }
                Row(
                    Modifier.padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = manual,
                        onValueChange = { manual = it.take(64) },
                        label = { Text(stringResource(R.string.rentalops_scan_manual)) },
                        leadingIcon = { Icon(Icons.Filled.Keyboard, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, capitalization = KeyboardCapitalization.Characters),
                        keyboardActions = KeyboardActions(onSearch = { resolve(manual) }),
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = { resolve(manual) },
                        enabled = manual.isNotBlank() && !lookingUp,
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) {
                        if (lookingUp) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.rentalops_scan_find))
                    }
                }
                return@Column
            }

            val from = locations.firstOrNull { it.id == a.locationId }?.name
            SectionCard {
                ListItem(
                    colors = cardListItemColors(),
                    leadingContent = { LeadingBadge(Icons.Filled.Inventory2, StatusTone.Accent) },
                    headlineContent = { Text(a.name, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(stringResource(R.string.rentalops_transfer_from, from ?: "\u2014")) },
                    trailingContent = {
                        TextButton(onClick = { asset = null }, modifier = Modifier.heightIn(min = MinTouch)) {
                            Text(stringResource(R.string.rentalops_scan_again))
                        }
                    },
                )
            }
            val options = locations.filter { it.id != a.locationId }
            SectionCard(title = stringResource(R.string.rentalops_transfer_to), modifier = Modifier.selectableGroup()) {
                if (options.isEmpty()) {
                    Text(
                        stringResource(R.string.rentalops_transfer_no_dest),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(AvagoSpacing.lg),
                    )
                }
                options.forEachIndexed { i, loc ->
                    if (i > 0) CardDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = dest == loc.id, role = Role.RadioButton, onClick = { dest = loc.id })
                            .padding(horizontal = AvagoSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = dest == loc.id, onClick = null)
                        Text(loc.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AvagoSpacing.lg))
                    }
                }
            }
            SectionCard(title = stringResource(R.string.rentalops_arrive_within), contentPadding = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    ARRIVE_PRESETS.forEach { h ->
                        FilterChip(
                            selected = hours == h,
                            onClick = { hours = h },
                            label = { Text(stringResource(R.string.rentalops_hours_n, h.toInt())) },
                            leadingIcon = if (hours == h) { { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) } } else null,
                            modifier = Modifier.heightIn(min = MinTouch),
                        )
                    }
                }
            }
            SectionCard {
                Column(Modifier.padding(AvagoSpacing.lg)) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(500) },
                        label = { Text(stringResource(R.string.rentalops_transfer_reason)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CardDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = shipNow, role = Role.Switch, onValueChange = { shipNow = it })
                        .padding(horizontal = AvagoSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.rentalops_transfer_ship_now), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = shipNow, onCheckedChange = null)
                }
            }
        }
    }
    LaunchedEffect(asset) { lastMissed = null }
}
