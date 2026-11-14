package com.avago.feature.rentalsops.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.db.entity.LocationEntity
import com.avago.core.design.theme.AvagoSpacing
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.data.TransferView
import com.avago.feature.rentalsops.domain.TransferStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class TransfersViewModel @Inject constructor(private val repo: RentalOpsRepository) : ViewModel() {
    val transfers: StateFlow<List<TransferView>?> = repo.observeTransfers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val locations: StateFlow<List<LocationEntity>> = repo.observeLocations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        CreateTransfer(vm, locations, onClose = { creating = false })
        return
    }
    val names = locations.associate { it.locationId to it.name }
    Scaffold(
        topBar = { OpsTopBar(stringResource(R.string.rentalops_transfers_title), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.rentalops_transfer_new)) },
            )
        },
    ) { pad ->
        val l = list
        when {
            l == null -> Unit
            l.isEmpty() -> EmptyState(stringResource(R.string.rentalops_transfers_empty), Modifier.padding(pad))
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(l, key = { it.transfer.id }) { v -> TransferRow(v, names, vm) }
            }
        }
    }
}

@Composable
private fun TransferRow(v: TransferView, names: Map<String, String>, vm: TransfersViewModel) {
    val t = v.transfer
    val from = t.fromLocationId?.let { names[it] } ?: "\u2014"
    val to = t.toLocationId?.let { names[it] } ?: "\u2014"
    Column(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(v.assetName ?: t.assetId?.take(8) ?: "\u2014") },
            supportingContent = {
                Column {
                    Text(stringResource(R.string.rentalops_transfer_route, from, to))
                    Text(stringResource(R.string.rentalops_arrive_by, Fmt.dateTime(t.arriveBy)), style = MaterialTheme.typography.bodySmall)
                }
            },
            trailingContent = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(transferStatusLabel(v.localStatus), style = MaterialTheme.typography.labelLarge)
                    PendingChip(v.pending)
                }
            },
            modifier = Modifier.semantics(mergeDescendants = true) {},
        )
        v.pending.firstOrNull { it.rejected || it.parked }?.let { rej ->
            RejectedCard(rej, onRetry = { vm.retry(rej.queueId) }, onDiscard = { vm.discard(rej.queueId) })
        }
        Row(Modifier.padding(horizontal = AvagoSpacing.lg), horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
            if (v.localStatus == TransferStatus.REQUESTED) {
                OutlinedButton(onClick = { vm.step(v, "ship") }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentalops_transfer_ship))
                }
            }
            if (v.localStatus == TransferStatus.IN_TRANSIT) {
                Button(onClick = { vm.step(v, "receive") }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentalops_transfer_receive))
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
private fun CreateTransfer(vm: TransfersViewModel, locations: List<LocationEntity>, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var asset by remember { mutableStateOf<RentalOpsRepository.ScannedAsset?>(null) }
    var manual by rememberSaveable { mutableStateOf("") }
    var lookingUp by remember { mutableStateOf(false) }
    var dest by rememberSaveable { mutableStateOf<String?>(null) }
    var hours by rememberSaveable { mutableStateOf(24L) }
    var reason by rememberSaveable { mutableStateOf("") }
    var shipNow by rememberSaveable { mutableStateOf(true) }
    val notFound = stringResource(R.string.rentalops_scan_not_found)
    var lastMissed by remember { mutableStateOf<String?>(null) }

    fun resolve(code: String) {
        if (lookingUp) return
        lookingUp = true
        scope.launch {
            val hit = vm.lookup(code)
            lookingUp = false
            if (hit != null) {
                asset = hit
                dest = null
            } else if (lastMissed != code) {
                lastMissed = code
                snackbar.showSnackbar(notFound)
            }
        }
    }

    Scaffold(
        topBar = { OpsTopBar(stringResource(R.string.rentalops_transfer_new), onClose) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            val a = asset
            if (a == null) {
                UnitScanner(
                    active = !lookingUp,
                    onCode = { resolve(it) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                )
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it.take(64) },
                    label = { Text(stringResource(R.string.rentalops_scan_manual)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { resolve(manual) }),
                    modifier = Modifier.fillMaxWidth().padding(AvagoSpacing.lg),
                )
                Button(
                    onClick = { resolve(manual) },
                    enabled = manual.isNotBlank() && !lookingUp,
                    modifier = Modifier.padding(horizontal = AvagoSpacing.lg).heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentalops_scan_find)) }
                return@Column
            }

            val from = locations.firstOrNull { it.locationId == a.locationId }?.name
            ListItem(
                headlineContent = { Text(a.name, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(stringResource(R.string.rentalops_transfer_from, from ?: "\u2014")) },
                trailingContent = {
                    OutlinedButton(onClick = { asset = null }, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.rentalops_scan_again))
                    }
                },
            )
            SectionHeader(stringResource(R.string.rentalops_transfer_to))
            val options = locations.filter { it.locationId != a.locationId }
            if (options.isEmpty()) EmptyState(stringResource(R.string.rentalops_transfer_no_dest))
            options.forEach { loc ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouch)
                        .selectable(selected = dest == loc.locationId, role = Role.RadioButton, onClick = { dest = loc.locationId })
                        .padding(horizontal = AvagoSpacing.lg),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    RadioButton(selected = dest == loc.locationId, onClick = null)
                    Text(loc.name, Modifier.padding(start = AvagoSpacing.sm))
                }
            }
            SectionHeader(stringResource(R.string.rentalops_arrive_within))
            Row(Modifier.padding(horizontal = AvagoSpacing.lg), horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                ARRIVE_PRESETS.forEach { h ->
                    FilterChip(
                        selected = hours == h,
                        onClick = { hours = h },
                        label = { Text(stringResource(R.string.rentalops_hours_n, h.toInt())) },
                        modifier = Modifier.heightIn(min = MinTouch),
                    )
                }
            }
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it.take(500) },
                label = { Text(stringResource(R.string.rentalops_transfer_reason)) },
                modifier = Modifier.fillMaxWidth().padding(AvagoSpacing.lg),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouch)
                    .toggleable(value = shipNow, role = Role.Checkbox, onValueChange = { shipNow = it })
                    .padding(horizontal = AvagoSpacing.lg),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Checkbox(checked = shipNow, onCheckedChange = null)
                Text(stringResource(R.string.rentalops_transfer_ship_now), Modifier.padding(start = AvagoSpacing.sm))
            }
            Button(
                onClick = { dest?.let { d -> vm.create(a, d, Instant.now().plus(Duration.ofHours(hours)), reason, shipNow, onClose) } },
                enabled = dest != null,
                modifier = Modifier.fillMaxWidth().padding(AvagoSpacing.lg).heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.rentalops_transfer_create)) }
        }
    }
    LaunchedEffect(asset) { lastMissed = null }
}
