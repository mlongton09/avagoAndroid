package com.avago.feature.rentals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.rental.LocalFleet
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.RentalRepository
import com.avago.feature.rentals.domain.LineSwap
import com.avago.feature.rentals.domain.LocalSchedule
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.instant
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.str
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** A checked-out line that can be swapped. */
data class SwapLine(val id: String, val assetId: String, val assetName: String, val classId: String?, val endAt: Instant?)

@HiltViewModel
class SwapViewModel @Inject constructor(
    private val repo: RentalRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val orderId: String = requireNotNull(savedState["orderId"])

    private val fleetRows = repo.fleetInputs()

    val lines: StateFlow<List<SwapLine>> = combine(
        repo.record(RentalEntity.ORDER, orderId),
        repo.byOrder(RentalEntity.ORDER_LINE, orderId),
        repo.records(RentalEntity.FLEET),
    ) { order, ls, fleet ->
        val names = fleet.mapNotNull(LocalFleet::unitOf).associate { it.assetId to it.name }
        val status = order?.obj?.str("status")
        ls.filter { LineSwap.swappable(it.obj, status) }.map { l ->
            val asset = l.obj.str("asset_id") ?: l.assetId!!
            SwapLine(l.id, asset, names[asset] ?: asset, l.obj.str("class_id") ?: l.classId, l.obj.instant("end_at"))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedLine = MutableStateFlow<String?>(null)
    val selectedAsset = MutableStateFlow<String?>(null)
    val reason = MutableStateFlow("")
    val inMeter = MutableStateFlow("")
    val inFuel = MutableStateFlow("")
    val outMeter = MutableStateFlow("")
    val outFuel = MutableStateFlow("")

    private val line: StateFlow<SwapLine?> = combine(lines, selectedLine) { ls, s -> ls.firstOrNull { it.id == s } ?: ls.singleOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val currentLine: StateFlow<SwapLine?> get() = line

    /** Free units of the same class from local availability (offline answer). */
    val candidates: StateFlow<List<LocalFleet.FleetUnit>> = combine(fleetRows, line) { rows, l ->
        if (l == null) return@combine emptyList()
        val now = Instant.now()
        LineSwap.candidates(
            LocalFleet.compute(rows, now).values, LocalSchedule.busy(rows),
            l.classId, l.assetId, now, l.endAt, now,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val errors: SharedFlow<String> = _errors
    private val _done = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val done: SharedFlow<String> = _done

    fun swap() = viewModelScope.launch {
        val l = line.value ?: return@launch
        val asset = selectedAsset.value?.takeIf { a -> candidates.value.any { it.assetId == a } } ?: return@launch
        try {
            repo.swapLine(
                RentalRepository.SwapInput(
                    orderId = orderId,
                    lineId = l.id,
                    newAssetId = asset,
                    reason = reason.value.trim().takeIf { it.isNotEmpty() },
                    inMeter = parseMeter(inMeter.value),
                    inFuelPct = parseFuel(inFuel.value),
                    outMeter = parseMeter(outMeter.value),
                    outFuelPct = parseFuel(outFuel.value),
                    at = Instant.now(),
                ),
            )
            _done.emit(l.assetId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "line swap failed")
            _errors.emit(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        fun parseMeter(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
        fun parseFuel(s: String): Int? = s.trim().toIntOrNull()?.takeIf { it in 0..100 }
        fun validMeter(s: String): Boolean = s.isBlank() || parseMeter(s) != null
        fun validFuel(s: String): Boolean = s.isBlank() || parseFuel(s) != null
    }
}

/**
 * Line swap (stage B §2): the checked-out unit comes back and a free unit of the same class goes
 * out. Applied locally at once (old line swapped, allocation released, tentative line and
 * allocation for the replacement) and synced in order; a refusal (`line_not_out`,
 * `allocation_conflict`) shows as a conflict on the order. [onSwapped] gets the returned unit
 * (its readiness checklist is opened next).
 */
@Composable
fun SwapScreen(
    onBack: () -> Unit,
    onSwapped: (returnedAssetId: String) -> Unit,
    viewModel: SwapViewModel = hiltViewModel(),
) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val line by viewModel.currentLine.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val selected by viewModel.selectedAsset.collectAsStateWithLifecycle()
    val reason by viewModel.reason.collectAsStateWithLifecycle()
    val inMeter by viewModel.inMeter.collectAsStateWithLifecycle()
    val inFuel by viewModel.inFuel.collectAsStateWithLifecycle()
    val outMeter by viewModel.outMeter.collectAsStateWithLifecycle()
    val outFuel by viewModel.outFuel.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.errors.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.done.collect { onSwapped(it) } }
    val zone = ZoneId.systemDefault()
    val readingsValid = SwapViewModel.validMeter(inMeter) && SwapViewModel.validFuel(inFuel) &&
        SwapViewModel.validMeter(outMeter) && SwapViewModel.validFuel(outFuel)

    Scaffold(
        topBar = { RentalsTopBar(stringResource(R.string.rentals_swap_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.rentals_swap_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (lines.isEmpty()) {
                EmptyState(stringResource(R.string.rentals_swap_no_lines))
                return@Column
            }
            SectionHeader(stringResource(R.string.rentals_swap_line))
            lines.forEach { l ->
                ChoiceRow(
                    selected = line?.id == l.id,
                    title = l.assetName,
                    subtitle = l.endAt?.let { stringResource(R.string.rentals_due_back, formatDateTime(it, zone)) },
                    onClick = { viewModel.selectedLine.value = l.id; viewModel.selectedAsset.value = null },
                )
            }
            SectionHeader(stringResource(R.string.rentals_swap_replacement))
            if (candidates.isEmpty()) EmptyState(stringResource(R.string.rentals_swap_no_candidates))
            candidates.forEach { u ->
                ChoiceRow(
                    selected = selected == u.assetId,
                    title = u.name,
                    subtitle = u.className,
                    onClick = { viewModel.selectedAsset.value = u.assetId },
                )
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    reason, { viewModel.reason.value = it },
                    label = { Text(stringResource(R.string.rentals_reason)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.rentals_swap_returning), style = MaterialTheme.typography.titleSmall)
                Readings(inMeter, inFuel, { viewModel.inMeter.value = it }, { viewModel.inFuel.value = it })
                Text(stringResource(R.string.rentals_swap_outgoing), style = MaterialTheme.typography.titleSmall)
                Readings(outMeter, outFuel, { viewModel.outMeter.value = it }, { viewModel.outFuel.value = it })
                Button(
                    onClick = viewModel::swap,
                    enabled = line != null && candidates.any { it.assetId == selected } && readingsValid,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentals_swap_confirm)) }
            }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Readings(meter: String, fuel: String, onMeter: (String) -> Unit, onFuel: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            meter, onMeter,
            label = { Text(stringResource(R.string.rentals_meter)) },
            isError = !SwapViewModel.validMeter(meter),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            fuel, onFuel,
            label = { Text(stringResource(R.string.rentals_meter_fuel)) },
            isError = !SwapViewModel.validFuel(fuel),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}
