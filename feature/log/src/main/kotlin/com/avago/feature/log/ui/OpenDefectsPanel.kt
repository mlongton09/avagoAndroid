package com.avago.feature.log.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.log.R
import com.avago.feature.log.model.DefectPrefill
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.OpenDefects
import com.avago.feature.log.nav.DefectWorkOrderRequest
import com.avago.feature.log.viewmodel.OpenDefectsUiState
import com.avago.feature.log.viewmodel.OpenDefectsViewModel

/**
 * "N open defects" card for the top of an asset's Log list (web `OpenDefectsPanel`). Renders
 * nothing while the asset has no open defects. Row tap opens the inspection that last saw the
 * defect; the overflow sheet offers Create work order (`work_orders.create`) and Defer / Reopen /
 * Mark repaired (`service_records.edit_own`), applied locally and queued for sync — rows with an
 * unsynced change show a "Waiting to sync" marker.
 */
@Composable
fun AssetOpenDefectsPanel(
    assetId: String,
    onOpenLog: (entryId: String) -> Unit,
    modifier: Modifier = Modifier,
    onCreateWorkOrder: ((DefectWorkOrderRequest) -> Unit)? = null,
) {
    val viewModel: OpenDefectsViewModel = hiltViewModel(key = "open_defects:$assetId")
    LaunchedEffect(assetId) { viewModel.setAssetId(assetId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val haptic = LocalHapticFeedback.current
    val updatedMsg = stringResource(R.string.log_insp_defect_updated)
    val failedMsg = stringResource(R.string.log_insp_defect_update_failed)
    LaunchedEffect(viewModel) {
        viewModel.updateResults.collect { ok ->
            haptic.performHapticFeedback(if (ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
            if (ok) viewModel.toast.success(updatedMsg) else viewModel.toast.error(failedMsg)
        }
    }

    // Web CreateWoFromDefectButton: localized title / description / priority prefill + source link.
    val severityNames = InspectionV2.SEVERITIES.associateWith { inspectionSeverityName(it) }
    val locationNames = InspectionV2.LOCATIONS.associateWith { inspectionLocationLabel(it) ?: it }
    val locationWord = stringResource(R.string.log_insp_wo_location)
    val noteWord = stringResource(R.string.log_insp_wo_note)
    val createWo: ((InspectionV2.OpenDefect) -> Unit)? = onCreateWorkOrder?.takeIf { state.canCreateWo }?.let { cb ->
        { d ->
            val defect = DefectPrefill.of(d).copy(
                label = state.label(d.label) ?: d.label,
                cornerLabel = state.label(d.cornerLabel),
                sectionTitle = state.label(d.sectionTitle),
            )
            DefectPrefill.build(listOf(defect), { severityNames[it] ?: it }, { locationNames[it] ?: it }, locationWord, noteWord)
                ?.let { r ->
                    cb(
                        DefectWorkOrderRequest(
                            assetId = d.assetId ?: assetId,
                            title = r.title,
                            description = r.description,
                            defectIds = r.defectIds,
                            inspectionLogId = r.inspectionLogId,
                            priority = r.priority,
                        ),
                    )
                }
        }
    }

    OpenDefectsPanelContent(
        state = state,
        nowMs = System.currentTimeMillis(),
        onOpenLog = onOpenLog,
        onCreateWorkOrder = createWo,
        onSetStatus = if (state.lifecycleAvailable && state.canEdit) {
            { d, status, note -> viewModel.updateStatus(d, status, note) }
        } else {
            null
        },
        modifier = modifier,
    )
}

/** Light / dark text colors per tone; the strip and icon use the web base colors. */
private data class ToneColors(val base: Color, val textLight: Color, val textDark: Color)

private fun toneColors(tone: OpenDefects.Tone): ToneColors = when (tone) {
    OpenDefects.Tone.RED -> ToneColors(Color(0xFFD93025), Color(0xFFB3261E), Color(0xFFF28B82))
    OpenDefects.Tone.ORANGE -> ToneColors(Color(0xFFE8710A), Color(0xFFA34E00), Color(0xFFFDBA74))
    OpenDefects.Tone.AMBER -> ToneColors(Color(0xFFF9AB00), Color(0xFF7A5600), Color(0xFFFDD663))
}

@Composable
private fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun OpenDefectsPanelContent(
    state: OpenDefectsUiState,
    nowMs: Long,
    onOpenLog: (String) -> Unit,
    onCreateWorkOrder: ((InspectionV2.OpenDefect) -> Unit)?,
    onSetStatus: ((InspectionV2.OpenDefect, status: String, note: String?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val defects = state.defects
    if (defects.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<InspectionV2.OpenDefect?>(null) }
    val accent = toneColors(OpenDefects.tone(defects.first())).base
    val dark = isDarkSurface()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .drawBehind { drawRect(accent, size = Size(4.dp.toPx(), size.height)) }
                .padding(start = 4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Text(
                    text = pluralStringResource(R.plurals.log_open_defects_title, defects.size, defects.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (!state.serverBacked) {
                Text(
                    text = stringResource(R.string.log_open_defects_hint_derived),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp, end = 12.dp, top = 2.dp),
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            OpenDefects.visible(defects, expanded).forEach { d ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DefectRow(
                    d = d,
                    state = state,
                    nowMs = nowMs,
                    dark = dark,
                    target = state.openTargets[d.id],
                    onOpenLog = onOpenLog,
                    hasActions = (onCreateWorkOrder != null && d.status != InspectionV2.RESOLUTION_REPAIRED) || onSetStatus != null,
                    onMore = { menuFor = d },
                )
            }
            if (defects.size > OpenDefects.COLLAPSED_COUNT) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (expanded) {
                            stringResource(R.string.log_open_defects_show_less)
                        } else {
                            stringResource(R.string.log_open_defects_show_all, defects.size)
                        },
                    )
                }
            }
        }
    }

    // Keep the sheet's copy current (status may change while it's open); gone rows close it.
    val live = menuFor?.let { m -> defects.firstOrNull { it.id == m.id } }
    if (live != null) {
        DefectActionsSheet(
            d = live,
            title = listOfNotNull(state.label(live.label) ?: live.label, state.label(live.cornerLabel)).joinToString(" · "),
            target = state.openTargets[live.id],
            onDismiss = { menuFor = null },
            onOpenLog = onOpenLog,
            onCreateWorkOrder = onCreateWorkOrder?.takeIf { live.status != InspectionV2.RESOLUTION_REPAIRED },
            onSetStatus = onSetStatus,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefectRow(
    d: InspectionV2.OpenDefect,
    state: OpenDefectsUiState,
    nowMs: Long,
    dark: Boolean,
    target: String?,
    onOpenLog: (String) -> Unit,
    hasActions: Boolean,
    onMore: () -> Unit,
) {
    val tone = toneColors(OpenDefects.tone(d))
    val toneText = if (dark) tone.textDark else tone.textLight
    val label = listOfNotNull(state.label(d.label) ?: d.label, state.label(d.cornerLabel)).joinToString(" · ")
    val viewSource = stringResource(R.string.log_open_defects_view_source)
    val days = OpenDefects.ageDays(d.reportedAt, nowMs)
    val age = if (days == 0) {
        stringResource(R.string.log_open_defects_reported_today)
    } else {
        pluralStringResource(R.plurals.log_open_defects_reported_ago, days, days)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (target != null) {
                    Modifier.clickable(onClickLabel = viewSource, role = Role.Button) { onOpenLog(target) }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 56.dp)
            .padding(start = 12.dp, end = if (hasActions) 0.dp else 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = inspectionSeverityName(d.severity),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = toneText,
                    modifier = Modifier
                        .background(tone.base.copy(alpha = if (dark) 0.22f else 0.14f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                inspectionLocationLabel(d.location)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (d.status != "open") {
                    Text(
                        text = defectStatusLabel(d.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(age, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (d.id in state.pendingIds) {
                    // Local change / inspection not synced yet (offline-first outbox).
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            stringResource(R.string.log_open_defects_pending_sync),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (!d.note.isNullOrBlank()) {
                Text(d.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (hasActions) {
            IconButton(onClick = onMore) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.log_open_defects_more_actions, label),
                )
            }
        }
    }
}

@Composable
private fun defectStatusLabel(status: String): String = when (status) {
    "monitoring" -> stringResource(R.string.log_inspection_status_monitoring)
    "deferred" -> stringResource(R.string.log_inspection_status_deferred)
    "repaired" -> stringResource(R.string.log_inspection_status_repaired)
    else -> stringResource(R.string.log_inspection_status_open)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefectActionsSheet(
    d: InspectionV2.OpenDefect,
    title: String,
    target: String?,
    onDismiss: () -> Unit,
    onOpenLog: (String) -> Unit,
    onCreateWorkOrder: ((InspectionV2.OpenDefect) -> Unit)?,
    onSetStatus: ((InspectionV2.OpenDefect, status: String, note: String?) -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var resolving by rememberSaveable(d.id) { mutableStateOf(false) }
    var note by rememberSaveable(d.id) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            if (resolving && onSetStatus != null) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.log_insp_defect_resolution_prompt)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    OutlinedButton(onClick = { resolving = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(com.avago.core.ui.R.string.common_cancel))
                    }
                    Button(
                        onClick = {
                            onSetStatus(d, InspectionV2.RESOLUTION_REPAIRED, note)
                            onDismiss()
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.log_insp_defect_action_repaired))
                    }
                }
                return@Column
            }
            if (target != null) {
                ActionItem(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.log_open_defects_view_source)) {
                    onDismiss()
                    onOpenLog(target)
                }
            }
            if (onCreateWorkOrder != null) {
                ActionItem(Icons.Default.Build, stringResource(R.string.log_inspection_defect_create_wo)) {
                    onDismiss()
                    onCreateWorkOrder(d)
                }
            }
            if (onSetStatus != null) {
                if (d.status == "deferred") {
                    ActionItem(Icons.Default.Refresh, stringResource(R.string.log_insp_defect_action_reopen)) {
                        onSetStatus(d, "open", null)
                        onDismiss()
                    }
                } else {
                    ActionItem(Icons.Default.Pause, stringResource(R.string.log_insp_defect_action_defer)) {
                        onSetStatus(d, "deferred", null)
                        onDismiss()
                    }
                }
                ActionItem(Icons.Default.Check, stringResource(R.string.log_insp_defect_action_repaired)) {
                    note = ""
                    resolving = true
                }
            }
        }
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}
