package com.avago.feature.rentalsops.ui

import javax.inject.Inject
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.rememberHaptics
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.toneColors
import com.avago.feature.rentalsops.data.LineView
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.domain.UnitAssignment
import com.avago.feature.rentalsops.push.RentalOpsRoutes
import com.avago.feature.rentalsops.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
            l == null -> SkeletonList(Modifier.padding(pad), rows = 5)
            l.isEmpty() -> EmptyState(stringResource(R.string.rentalops_assign_empty), Modifier.padding(pad), icon = Icons.Filled.TaskAlt)
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(vertical = AvagoSpacing.sm)) {
                items(l, key = { it.line.lineId }, contentType = { "line" }) { v ->
                    LineRow(v, Modifier.animateItem()) { onNavigate(RentalOpsRoutes.assignLine(v.line.lineId)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineRow(v: LineView, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
    ) {
        Row(Modifier.padding(AvagoSpacing.md), horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md)) {
            LeadingBadge(Icons.Filled.Inventory2, if (v.pendingAssetName != null) StatusTone.Info else StatusTone.Warning)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                v.line.customerName?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(v.line.title ?: stringResource(R.string.rentalops_class_line), style = MaterialTheme.typography.titleMedium)
                LineMeta(v)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                    v.pendingAssetName?.let {
                        StatusPill(stringResource(R.string.rentalops_assign_pending, it), StatusTone.Info, icon = Icons.Filled.Sync)
                    }
                    PendingChip(listOfNotNull(v.pending))
                }
            }
        }
    }
}

/** Order number · quantity, the window and the class's free-unit headroom. */
@Composable
private fun LineMeta(v: LineView) {
    val l = v.line
    val head = listOfNotNull(
        l.orderNumber?.let { stringResource(R.string.rentalops_order_n, it) },
        l.qty?.takeIf { it > 1 }?.let { stringResource(R.string.rentalops_qty_n, it) },
    )
    if (head.isNotEmpty()) Text(head.joinToString(" \u00b7 "), style = MaterialTheme.typography.bodyMedium)
    Text(
        "${Fmt.dateTime(l.start)} \u2013 ${Fmt.dateTime(l.end)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    l.classFreeUnits?.let { free ->
        StatusPill(
            if (free <= 0) stringResource(R.string.rentalops_class_full) else stringResource(R.string.rentalops_class_free, free),
            if (free <= 0) StatusTone.Danger else StatusTone.Success,
            icon = if (free <= 0) Icons.Filled.Block else Icons.Filled.CheckCircle,
        )
    }
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
        val r = repo.candidates(v.line)
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
    val haptics = rememberHaptics()
    Scaffold(topBar = { OpsTopBar(stringResource(R.string.rentalops_assign_unit), onBack) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(vertical = AvagoSpacing.sm)) {
            s.view?.let { v ->
                item(key = "line", contentType = "header") {
                    SectionCard {
                        Column(Modifier.padding(AvagoSpacing.lg), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                            v.line.customerName?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(v.line.title ?: stringResource(R.string.rentalops_class_line), style = MaterialTheme.typography.titleLarge)
                            LineMeta(v)
                        }
                    }
                }
            }
            if (!s.loading && s.view != null && !s.confirmed) {
                item(key = "offline", contentType = "banner") { OfflineBanner(stringResource(R.string.rentalops_assign_offline)) }
            }
            when {
                s.loading -> item(key = "loading", contentType = "skeleton") { SkeletonList(rows = 4) }
                s.view == null -> item(key = "done", contentType = "empty") {
                    EmptyState(stringResource(R.string.rentalops_assign_done), icon = Icons.Filled.TaskAlt)
                }
                s.candidates.isEmpty() -> item(key = "none", contentType = "empty") {
                    EmptyState(stringResource(R.string.rentalops_assign_no_units), icon = Icons.Filled.Inventory2)
                }
                else -> {
                    item(key = "cands-h", contentType = "header") { SectionHeader(stringResource(R.string.rentalops_assign_candidates, s.candidates.size)) }
                    items(s.candidates, key = { it.unit.assetId }, contentType = { "candidate" }) { c ->
                        CandidateRow(c, suggested = c == s.candidates.first()) { confirm = c }
                    }
                }
            }
        }
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Filled.Inventory2, contentDescription = null) },
            title = { Text(stringResource(R.string.rentalops_assign_unit)) },
            text = { Text(stringResource(R.string.rentalops_assign_confirm, c.unit.name)) },
            confirmButton = {
                TextButton(onClick = { vm.assign(c) { haptics.success(); confirm = null; onBack() } }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentalops_assign_action))
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentalops_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateRow(c: UnitAssignment.Candidate, suggested: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (suggested) toneColors(StatusTone.Accent).container.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (suggested) 2.dp else 1.dp, if (suggested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(AvagoSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingBadge(Icons.Filled.Inventory2, if (c.unit.ready) StatusTone.Success else StatusTone.Warning)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                Text(c.unit.name, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                    if (suggested) StatusPill(stringResource(R.string.rentalops_suggested), StatusTone.Accent, icon = Icons.Filled.AutoAwesome)
                    if (c.sameLocation) StatusPill(stringResource(R.string.rentalops_same_branch), StatusTone.Info, icon = Icons.Filled.Place)
                    if (!c.unit.ready) StatusPill(stringResource(R.string.rentalops_not_ready), StatusTone.Warning, icon = Icons.Filled.Build)
                    if (!c.confirmed) StatusPill(stringResource(R.string.rentalops_unconfirmed), StatusTone.Neutral, icon = Icons.Filled.CloudOff)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
