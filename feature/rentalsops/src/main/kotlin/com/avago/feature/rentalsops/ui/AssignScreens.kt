package com.avago.feature.rentalsops.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.data.LineView
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.domain.UnitAssignment
import com.avago.feature.rentalsops.push.RentalOpsRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssignListViewModel @Inject constructor(repo: RentalOpsRepository) : ViewModel() {
    val lines: StateFlow<List<LineView>?> = repo.observeLinesNeedingUnit()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun AssignListScreen(onNavigate: (String) -> Unit, onBack: () -> Unit, vm: AssignListViewModel = hiltViewModel()) {
    val lines by vm.lines.collectAsStateWithLifecycle()
    Scaffold(topBar = { OpsTopBar(stringResource(R.string.rentalops_assign_title), onBack) }) { pad ->
        val l = lines
        when {
            l == null -> Unit
            l.isEmpty() -> EmptyState(stringResource(R.string.rentalops_assign_empty), Modifier.padding(pad))
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(l, key = { it.line.id }) { v -> LineRow(v) { onNavigate(RentalOpsRoutes.assignLine(v.line.id)) } }
            }
        }
    }
}

@Composable
private fun LineRow(v: LineView, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(v.line.description ?: stringResource(R.string.rentalops_class_line)) },
        supportingContent = {
            Column {
                v.order?.number?.let { Text(stringResource(R.string.rentalops_order_n, it)) }
                Text("${Fmt.dateTime(v.line.start)} \u2013 ${Fmt.dateTime(v.line.end)}", style = MaterialTheme.typography.bodySmall)
                v.pendingAssetName?.let {
                    Text(stringResource(R.string.rentalops_assign_pending, it), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        trailingContent = { PendingChip(listOfNotNull(v.pending)) },
        modifier = Modifier.heightIn(min = MinTouch).clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {},
    )
}

data class AssignLineState(
    val view: LineView? = null,
    val loading: Boolean = true,
    val candidates: List<UnitAssignment.Candidate> = emptyList(),
    val confirmed: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AssignLineViewModel @Inject constructor(
    private val repo: RentalOpsRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    private val lineId: String = checkNotNull(saved["lineId"])
    private val _state = MutableStateFlow(AssignLineState())
    val state: StateFlow<AssignLineState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        val v = repo.observeLine(lineId).first()
        if (v == null) {
            _state.value = AssignLineState(loading = false)
            return@launch
        }
        val r = repo.candidates(v.line, v.order?.locationId)
        _state.value = AssignLineState(v, false, r.candidates, r.confirmed, r.error)
    }

    fun assign(c: UnitAssignment.Candidate, done: () -> Unit) = viewModelScope.launch {
        val v = _state.value.view ?: return@launch
        repo.assign(v.line, c.unit)
        done()
    }
}

@Composable
fun AssignLineScreen(onBack: () -> Unit, vm: AssignLineViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<UnitAssignment.Candidate?>(null) }
    Scaffold(topBar = { OpsTopBar(stringResource(R.string.rentalops_assign_unit), onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            s.view?.let { v ->
                Column(Modifier.padding(AvagoSpacing.lg)) {
                    Text(v.line.description ?: stringResource(R.string.rentalops_class_line), style = MaterialTheme.typography.titleMedium)
                    v.order?.number?.let { Text(stringResource(R.string.rentalops_order_n, it)) }
                    Text("${Fmt.dateTime(v.line.start)} \u2013 ${Fmt.dateTime(v.line.end)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!s.loading && s.view != null && !s.confirmed) {
                OfflineBanner(stringResource(R.string.rentalops_assign_offline))
            }
            when {
                s.loading -> CircularProgressIndicator(Modifier.padding(AvagoSpacing.xxl))
                s.view == null -> EmptyState(stringResource(R.string.rentalops_assign_done))
                s.candidates.isEmpty() -> EmptyState(stringResource(R.string.rentalops_assign_no_units))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(s.candidates, key = { it.unit.assetId }) { c ->
                        CandidateRow(c, suggested = c == s.candidates.first()) { confirm = c }
                    }
                }
            }
        }
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.rentalops_assign_unit)) },
            text = { Text(stringResource(R.string.rentalops_assign_confirm, c.unit.name)) },
            confirmButton = {
                TextButton(onClick = { vm.assign(c) { confirm = null; onBack() } }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentalops_assign_action))
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentalops_cancel)) } },
        )
    }
}

@Composable
private fun CandidateRow(c: UnitAssignment.Candidate, suggested: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(c.unit.name) },
        supportingContent = {
            Row {
                if (suggested) Tag(stringResource(R.string.rentalops_suggested))
                if (c.sameLocation) Tag(stringResource(R.string.rentalops_same_branch))
                if (!c.unit.ready) Tag(stringResource(R.string.rentalops_not_ready))
                if (!c.confirmed) Tag(stringResource(R.string.rentalops_unconfirmed))
            }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {},
    )
}

@Composable
private fun Tag(text: String) {
    // Plain label (not a disabled chip) so TalkBack doesn't announce "disabled".
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(end = AvagoSpacing.xs, top = AvagoSpacing.xs),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = AvagoSpacing.sm, vertical = 2.dp))
    }
}
