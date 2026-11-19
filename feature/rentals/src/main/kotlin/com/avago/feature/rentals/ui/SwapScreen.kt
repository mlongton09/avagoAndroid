package com.avago.feature.rentals.ui

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.domain.SwapDraft
import com.avago.core.data.rental.LocalFleet
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.rememberHaptics
import com.avago.feature.rentals.R
import com.avago.feature.rentals.domain.LineSwap
import com.avago.feature.rentals.domain.LocalSchedule
import com.avago.feature.rentals.domain.MeterInfo
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
import java.math.BigDecimal
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

    private val fleet = repo.observeFleet()

    val lines: StateFlow<List<SwapLine>> = combine(
        repo.observeOrder(orderId),
        repo.observeLines(orderId),
        fleet,
    ) { order, ls, inputs ->
        if (order == null || order.source == "legacy") return@combine emptyList()
        val names = inputs.units.associate { it.assetId to it.name }
        ls.filter(LineSwap::swappable).mapNotNull { l ->
            val asset = l.assetId ?: return@mapNotNull null
            SwapLine(l.id, asset, names[asset] ?: asset, l.classId, l.endAt)
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
    val candidates: StateFlow<List<LocalFleet.FleetUnit>> = combine(fleet, line) { inputs, l ->
        if (l == null) return@combine emptyList()
        val now = Instant.now()
        LineSwap.candidates(
            LocalFleet.compute(inputs, now).values, LocalSchedule.busy(inputs),
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
            repo.swap(
                SwapDraft(
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
        fun parseMeter(s: String): BigDecimal? = MeterInfo.parseReading(s)
        fun parseFuel(s: String): Int? = s.trim().toIntOrNull()?.takeIf { it in 0..100 }
        fun validMeter(s: String): Boolean = s.isBlank() || parseMeter(s) != null
        fun validFuel(s: String): Boolean = s.isBlank() || parseFuel(s) != null
    }
}

/**
 * Line swap (stage B §2): the checked-out unit comes back and a free unit of the same class goes
 * out. Sent as one `rental.swap_line` command with an optimistic local apply; a refusal
 * (`line_not_out`, `allocation_conflict`) is rolled back and shows as a failure on the order. [onSwapped] gets the returned unit
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
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) { viewModel.errors.collect { haptics.error(); snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.done.collect { haptics.success(); onSwapped(it) } }
    val zone = ZoneId.systemDefault()
    val readingsValid = SwapViewModel.validMeter(inMeter) && SwapViewModel.validFuel(inFuel) &&
        SwapViewModel.validMeter(outMeter) && SwapViewModel.validFuel(outFuel)
    val canSwap = line != null && candidates.any { it.assetId == selected } && readingsValid

    Scaffold(
        topBar = { RentalsTopBar(stringResource(R.string.rentals_swap_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (lines.isNotEmpty()) {
                BottomActionBar {
                    Button(onClick = viewModel::swap, enabled = canSwap, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                        Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.rentals_swap_confirm))
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
                stringResource(R.string.rentals_swap_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (lines.isEmpty()) {
                EmptyState(stringResource(R.string.rentals_swap_no_lines), icon = Icons.Default.SwapHoriz)
                return@Column
            }
            SectionCard(title = stringResource(R.string.rentals_swap_line), modifier = Modifier.selectableGroup()) {
                lines.forEachIndexed { i, l ->
                    if (i > 0) CardDivider()
                    ChoiceRow(
                        selected = line?.id == l.id,
                        title = l.assetName,
                        subtitle = l.endAt?.let { stringResource(R.string.rentals_due_back, formatDateTime(it, zone)) },
                        onClick = { viewModel.selectedLine.value = l.id; viewModel.selectedAsset.value = null },
                    )
                }
            }
            SectionCard(title = stringResource(R.string.rentals_swap_replacement), modifier = Modifier.selectableGroup()) {
                if (candidates.isEmpty()) EmptyState(stringResource(R.string.rentals_swap_no_candidates), icon = Icons.Default.Inventory2)
                candidates.forEachIndexed { i, u ->
                    if (i > 0) CardDivider()
                    ChoiceRow(
                        selected = selected == u.assetId,
                        title = u.name,
                        subtitle = u.className,
                        onClick = { viewModel.selectedAsset.value = u.assetId },
                    )
                }
            }
            SectionCard(title = stringResource(R.string.rentals_reason), contentPadding = true) {
                OutlinedTextField(
                    reason, { viewModel.reason.value = it },
                    placeholder = { Text(stringResource(R.string.rentals_reason)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SectionCard(title = stringResource(R.string.rentals_swap_returning), contentPadding = true) {
                Readings(inMeter, inFuel, { viewModel.inMeter.value = it }, { viewModel.inFuel.value = it })
            }
            SectionCard(title = stringResource(R.string.rentals_swap_outgoing), contentPadding = true) {
                Readings(outMeter, outFuel, { viewModel.outMeter.value = it }, { viewModel.outFuel.value = it }, last = true)
            }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Readings(meter: String, fuel: String, onMeter: (String) -> Unit, onFuel: (String) -> Unit, last: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val meterOk = SwapViewModel.validMeter(meter)
        val fuelOk = SwapViewModel.validFuel(fuel)
        OutlinedTextField(
            meter, onMeter,
            label = { Text(stringResource(R.string.rentals_meter)) },
            isError = !meterOk,
            supportingText = if (!meterOk) { { Text(stringResource(R.string.rentals_value_invalid)) } } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            fuel, onFuel,
            label = { Text(stringResource(R.string.rentals_meter_fuel)) },
            isError = !fuelOk,
            supportingText = if (!fuelOk) { { Text(stringResource(R.string.rentals_value_invalid)) } } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (last) ImeAction.Done else ImeAction.Next),
            modifier = Modifier.weight(1f),
        )
    }
}
