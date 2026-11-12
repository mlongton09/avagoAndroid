package com.avago.feature.log.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avago.feature.log.R
import com.avago.feature.log.model.InspectionChecklist
import com.avago.feature.log.model.InspectionCorner
import com.avago.feature.log.model.InspectionFieldDef
import com.avago.feature.log.model.InspectionGroup
import com.avago.feature.log.model.InspectionItem
import com.avago.feature.log.model.InspectionOption
import com.avago.feature.log.model.InspectionSection
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.inspectionOptionColumn
import com.avago.feature.log.model.inspectionOptionShortLabel
import com.avago.feature.log.model.isScoreEligible
import kotlinx.coroutines.launch

/**
 * Color map for well-known inspection option values.
 * Used to render colored buttons/chips on select-type fields.
 */
val INSPECTION_OPTION_COLORS = mapOf(
    "normal"       to Color(0xFF4CAF50), // green
    "monitor"      to Color(0xFFFF9800), // orange
    "needs_repair" to Color(0xFFF44336), // red
    "na"           to Color(0xFF9E9E9E), // gray
    "pass"         to Color(0xFF4CAF50), // green
    "fail"         to Color(0xFFF44336), // red
)

/** Maps a resolved/raw option value to a lookup key in [INSPECTION_OPTION_COLORS]. */
private fun colorKeyFor(value: String): String = when (value) {
    "Normal", "insp.opt.normal" -> "normal"
    "Monitor", "insp.opt.monitor" -> "monitor"
    "Needs Repair", "insp.opt.needs_repair" -> "needs_repair"
    "N/A", "insp.opt.na" -> "na"
    "Pass", "insp.opt.pass" -> "pass"
    "Fail", "insp.opt.fail" -> "fail"
    else -> value.lowercase()
}

/**
 * Small icon shown alongside a standard option button's text so status is
 * distinguishable without relying on color alone (colorblind accessibility) —
 * mirrors iOS's avOptionIcon(_:). Returns null for custom/non-standard options.
 */
private fun inspectionOptionIcon(rawValue: String) = when (rawValue) {
    "Normal", "insp.opt.normal" -> Icons.Filled.CheckCircle
    "Monitor", "insp.opt.monitor" -> Icons.Filled.Visibility
    "Needs Repair", "insp.opt.needs_repair" -> Icons.Filled.Warning
    "N/A", "insp.opt.na" -> Icons.Filled.RemoveCircle
    else -> null
}

// Answer buttons render at a fixed column width so Normal/Monitor/Needs Repair/N-A stay vertically
// aligned across every question — matches iOS's AVInspectionFormView redesign (avOptionColumn).
private val OPTION_BUTTON_WIDTH = 76.dp
private val OPTION_BUTTON_HEIGHT = 48.dp
private const val OPTION_STD_COLS = 4

/**
 * Renders an inspection form. Prefers the full nested [checklist] (groups -> sections -> items,
 * matching iOS's schema); falls back to the legacy flat [fields] list for older configs that
 * don't have the nested groups/sections/notes/localization shape.
 */
@Composable
fun InspectionFormRenderer(
    checklist: InspectionChecklist? = null,
    fields: List<InspectionFieldDef> = emptyList(),
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    if (checklist != null) {
        InspectionChecklistRenderer(checklist, answers, onAnswerChanged, modifier, snackbarHostState)
    } else {
        LegacyInspectionFieldsRenderer(fields, answers, onAnswerChanged, modifier)
    }
}

// ---------------------------------------------------------------------------
// Nested checklist renderer (groups -> sections -> items) — the new, iOS-parity layout.
// ---------------------------------------------------------------------------

@Composable
private fun InspectionChecklistRenderer(
    checklist: InspectionChecklist,
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState?,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        InspectionScoreBar(checklist, answers)

        checklist.groups.forEach { group ->
            InspectionGroupBlock(group, answers, onAnswerChanged, snackbarHostState)
        }
    }
}

@Composable
private fun InspectionScoreBar(checklist: InspectionChecklist, answers: Map<String, String>) {
    if (checklist.allItems().none { it.isScoreEligible() }) return
    val progress = InspectionV2.computeProgress(checklist, answers)
    if (progress.n == 0 && progress.m == 0 && progress.nr == 0) return
    val normalLabel = stringResource(R.string.log_inspection_opt_normal)
    val monitorLabel = stringResource(R.string.log_inspection_opt_monitor)
    val repairLabel = stringResource(R.string.log_inspection_opt_needs_repair)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progress.n > 0) InspectionSummaryPill("$normalLabel ${progress.n}", INSPECTION_OPTION_COLORS.getValue("normal"))
        if (progress.m > 0) InspectionSummaryPill("$monitorLabel ${progress.m}", INSPECTION_OPTION_COLORS.getValue("monitor"))
        if (progress.nr > 0) InspectionSummaryPill("$repairLabel ${progress.nr}", INSPECTION_OPTION_COLORS.getValue("needs_repair"))
    }
}

@Composable
private fun InspectionGroupBlock(
    group: InspectionGroup,
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
    snackbarHostState: SnackbarHostState?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        group.sections.forEach { section ->
            InspectionSectionBlock(section, answers, onAnswerChanged, snackbarHostState)
        }
    }
}

/** One answer slot a section "mark all" can fill: a select item, or one corner of a corner-select item. */
private data class QuickFillTarget(val item: InspectionItem, val corner: InspectionCorner?) {
    val key: String get() = if (corner == null) item.id else InspectionV2.cornerKey(corner)
}

/** Pending "mark all" confirmation: column (0=N, 1=M, 2=NR, 3=N/A), its label and the answered-count. */
private data class QuickFillRequest(val col: Int, val label: String, val answered: Int, val unanswered: Int)

/** Score-eligible select / corner-select answer slots in this section. */
private fun sectionQuickFillTargets(section: InspectionSection): List<QuickFillTarget> =
    section.items.filter { it.isScoreEligible() }.flatMap { item ->
        if (item.type == "corner-select" && item.corners.isNotEmpty()) {
            item.corners.map { QuickFillTarget(item, it) }
        } else {
            listOf(QuickFillTarget(item, null))
        }
    }

private fun QuickFillTarget.optionAt(col: Int): InspectionOption? =
    item.options.firstOrNull { inspectionOptionColumn(it.rawValue) == col }

private fun QuickFillTarget.answer(answers: Map<String, String>): String? = InspectionV2.answerOf(answers, item, corner)

/**
 * Applies a section "mark all" to [targets] and returns the previous values of every key it touched
 * (for undo). Stores the option's raw value under the contract key; a pre-v2 "item.corner" key is
 * cleared so the old answer can't shadow the new one.
 */
private fun applySectionQuickFill(
    targets: List<QuickFillTarget>,
    col: Int,
    answers: Map<String, String>,
    onAnswerChanged: (String, String) -> Unit,
): Map<String, String> {
    val previous = LinkedHashMap<String, String>()
    targets.forEach { target ->
        val option = target.optionAt(col) ?: return@forEach
        previous[target.key] = answers[target.key] ?: ""
        onAnswerChanged(target.key, option.rawValue)
        if (target.corner != null) {
            val legacy = InspectionV2.legacyCornerKey(target.item, target.corner)
            if (!answers[legacy].isNullOrEmpty()) {
                previous[legacy] = answers[legacy] ?: ""
                onAnswerChanged(legacy, "")
            }
        }
    }
    return previous
}

@Composable
private fun InspectionSectionBlock(
    section: InspectionSection,
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
    snackbarHostState: SnackbarHostState?,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pendingQuickFill by remember { mutableStateOf<QuickFillRequest?>(null) }
    val targets = remember(section) { sectionQuickFillTargets(section) }
    val doneTemplate = stringResource(R.string.log_inspection_mark_all_done)
    val undoLabel = stringResource(R.string.log_inspection_undo)

    // A column is "active" when every slot that offers it is already answered with it.
    val activeCol = (0..3).firstOrNull { col ->
        val slots = targets.filter { it.optionAt(col) != null }
        slots.isNotEmpty() && slots.all { t ->
            inspectionOptionColumn(InspectionV2.canonicalAnswer(t.item, t.answer(answers)) ?: "") == col
        }
    }

    fun runQuickFill(col: Int, label: String, slots: List<QuickFillTarget>) {
        val previous = applySectionQuickFill(slots, col, answers, onAnswerChanged)
        val count = slots.count { it.optionAt(col) != null }
        if (snackbarHostState != null && previous.isNotEmpty()) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = String.format(doneTemplate, count, label),
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    previous.forEach { (key, value) -> onAnswerChanged(key, value) }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (targets.isNotEmpty()) {
                // Quick-fill buttons sit on their own row below the title using the same
                // column width/spacing as FixedColumnOptionRow, so each lands above its
                // matching answer column. Mirrors iOS's title-row / quick-fill-row split.
                SectionQuickFillRow(
                    activeCol = activeCol,
                    onRequested = { col, label ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val slots = targets.filter { it.optionAt(col) != null }
                        val unanswered = slots.filter { it.answer(answers) == null }
                        val overwrite = slots.count { t ->
                            val a = t.answer(answers)
                            a != null && inspectionOptionColumn(InspectionV2.canonicalAnswer(t.item, a) ?: "") != col
                        }
                        if (overwrite == 0) {
                            if (unanswered.isNotEmpty()) runQuickFill(col, label, unanswered)
                        } else {
                            pendingQuickFill = QuickFillRequest(col, label, overwrite, unanswered.size)
                        }
                    },
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        section.items.forEach { item ->
            InspectionItemBlock(item, answers, onAnswerChanged)
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }

    // Answered items are only overwritten after an explicit choice — web/iOS parity.
    pendingQuickFill?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingQuickFill = null },
            title = { Text(stringResource(R.string.log_inspection_mark_all_title, request.label)) },
            text = { Text(stringResource(R.string.log_inspection_mark_all_message, request.answered, section.title)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        pendingQuickFill = null
                        runQuickFill(request.col, request.label, targets.filter { it.optionAt(request.col) != null })
                    }) { Text(stringResource(R.string.log_inspection_mark_all_overwrite)) }
                    if (request.unanswered > 0) {
                        TextButton(onClick = {
                            pendingQuickFill = null
                            runQuickFill(
                                request.col,
                                request.label,
                                targets.filter { it.optionAt(request.col) != null && it.answer(answers) == null },
                            )
                        }) { Text(stringResource(R.string.log_inspection_mark_all_unanswered)) }
                    }
                    TextButton(onClick = { pendingQuickFill = null }) {
                        Text(stringResource(com.avago.core.ui.R.string.common_cancel))
                    }
                }
            },
        )
    }
}

/** N / M / NR / N/A quick-fill buttons — mirrors iOS's AVInspectionSectionView quick-fill row. */
@Composable
private fun SectionQuickFillRow(
    activeCol: Int?,
    onRequested: (col: Int, label: String) -> Unit,
) {
    // Same column order and button width/spacing as FixedColumnOptionRow's answer buttons, so
    // each quick-fill button sits directly above its matching answer column.
    val defs = listOf(
        Triple(0, "insp.opt.normal", stringResource(R.string.log_inspection_opt_normal)),
        Triple(1, "insp.opt.monitor", stringResource(R.string.log_inspection_opt_monitor)),
        Triple(2, "insp.opt.needs_repair", stringResource(R.string.log_inspection_opt_needs_repair)),
        Triple(3, "insp.opt.na", stringResource(R.string.log_inspection_opt_na)),
    )
    val cdTemplate = stringResource(R.string.log_inspection_mark_all_cd)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        defs.forEach { (col, raw, full) ->
            val color = INSPECTION_OPTION_COLORS.getValue(colorKeyFor(raw))
            val isActive = activeCol == col
            OutlinedButton(
                onClick = { onRequested(col, full) },
                modifier = Modifier
                    .width(OPTION_BUTTON_WIDTH)
                    .height(36.dp)
                    .semantics { contentDescription = String.format(cdTemplate, full) },
                contentPadding = PaddingValues(0.dp),
                colors = if (isActive) {
                    ButtonDefaults.outlinedButtonColors(containerColor = color, contentColor = Color.White)
                } else {
                    ButtonDefaults.outlinedButtonColors(contentColor = color)
                },
            ) {
                Text(inspectionOptionShortLabel(raw), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InspectionItemBlock(
    item: InspectionItem,
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
) {
    when (item.type) {
        "select" -> InspectionSelectItem(item, answers, onAnswerChanged)
        "corner-select" -> InspectionCornerSelectItem(item, answers, onAnswerChanged)
        "wheel-data", "measurement", "number", "number-input" ->
            InspectionNumberItem(item, answers[item.id] ?: "", onAnswerChanged)
        else -> InspectionTextItem(item, answers[item.id] ?: "", onAnswerChanged)
    }
}

/**
 * The primary "select" layout: full-width question text (with an optional info/tooltip icon)
 * above a row of larger, column-aligned answer buttons — mirrors iOS's redesigned
 * AVInspectionFormView.buildSelectLayout().
 */
@Composable
private fun InspectionSelectItem(
    item: InspectionItem,
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InspectionQuestionHeader(text = item.text, note = item.note)
        FixedColumnOptionRow(
            options = item.options,
            selected = answers[item.id] ?: "",
            onSelect = { onAnswerChanged(item.id, it) },
        )
        InspectionDefectDetails(item, answers, onAnswerChanged)
    }
}

/**
 * "corner-select" layout: one column-aligned option row per corner (e.g. LF/RF/LR/RR tire
 * positions). Answers are stored under the corner id (contract v2); answers written by older
 * Android builds under "{itemId}.{cornerId}" are still read and cleared on change.
 */
@Composable
private fun InspectionCornerSelectItem(
    item: InspectionItem,
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InspectionQuestionHeader(text = item.text, note = item.note)
        item.corners.forEach { corner ->
            val key = InspectionV2.cornerKey(corner)
            val legacyKey = InspectionV2.legacyCornerKey(item, corner)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = corner.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FixedColumnOptionRow(
                    options = item.options,
                    selected = InspectionV2.answerOf(answers, item, corner) ?: "",
                    onSelect = {
                        onAnswerChanged(key, it)
                        if (!answers[legacyKey].isNullOrEmpty()) onAnswerChanged(legacyKey, "")
                    },
                )
            }
        }
        InspectionDefectDetails(item, answers, onAnswerChanged)
    }
}

@Composable
private fun inspectionSeverityLabel(severity: String): String = when (severity) {
    InspectionV2.SEV_MINOR -> stringResource(R.string.log_inspection_severity_minor)
    InspectionV2.SEV_OUT_OF_SERVICE -> stringResource(R.string.log_inspection_severity_out_of_service)
    else -> stringResource(R.string.log_inspection_severity_major)
}

/** Localized name for a contract location id (fl, fr, rl, rr, front, …); null for unknown ids. */
@Composable
fun inspectionLocationLabel(location: String?): String? = when (location) {
    "fl" -> stringResource(R.string.log_inspection_loc_fl)
    "fr" -> stringResource(R.string.log_inspection_loc_fr)
    "rl" -> stringResource(R.string.log_inspection_loc_rl)
    "rr" -> stringResource(R.string.log_inspection_loc_rr)
    "front" -> stringResource(R.string.log_inspection_loc_front)
    "rear" -> stringResource(R.string.log_inspection_loc_rear)
    "left" -> stringResource(R.string.log_inspection_loc_left)
    "right" -> stringResource(R.string.log_inspection_loc_right)
    "cab" -> stringResource(R.string.log_inspection_loc_cab)
    "bed" -> stringResource(R.string.log_inspection_loc_bed)
    "roof" -> stringResource(R.string.log_inspection_loc_roof)
    "under" -> stringResource(R.string.log_inspection_loc_under)
    "interior" -> stringResource(R.string.log_inspection_loc_interior)
    "engine" -> stringResource(R.string.log_inspection_loc_engine)
    else -> null
}

/** Localized severity name for the read-only detail screen. */
@Composable
fun inspectionSeverityName(severity: String): String = inspectionSeverityLabel(severity)

/**
 * Severity / location / note inputs shown under an item answered Monitor or Needs Repair.
 * Values are stored under `<itemId>__sev`, `<itemId>__loc`, `<itemId>__note` (contract v2).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectionDefectDetails(
    item: InspectionItem,
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
) {
    val buckets = if (item.type == "corner-select") {
        item.corners.mapNotNull { InspectionV2.bucketOf(item, InspectionV2.answerOf(answers, item, it)) }
    } else {
        listOfNotNull(InspectionV2.bucketOf(item, answers[item.id]))
    }
    val bucket = when {
        InspectionV2.Bucket.NR in buckets -> InspectionV2.Bucket.NR
        InspectionV2.Bucket.M in buckets -> InspectionV2.Bucket.M
        else -> return
    }
    val severity = InspectionV2.severityOf(answers, item.id, bucket)
    val location = InspectionV2.locationOf(answers, item.id)
    var locationMenuOpen by remember(item.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.log_inspection_defect_severity),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InspectionV2.SEVERITIES.forEach { sev ->
                val chipColor = when (sev) {
                    InspectionV2.SEV_MINOR -> INSPECTION_OPTION_COLORS.getValue("monitor")
                    else -> INSPECTION_OPTION_COLORS.getValue("needs_repair")
                }
                FilterChip(
                    selected = severity == sev,
                    onClick = { onAnswerChanged(InspectionV2.sevKey(item.id), sev) },
                    label = { Text(inspectionSeverityLabel(sev)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
        Text(
            text = stringResource(R.string.log_inspection_defect_location),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(onClick = { locationMenuOpen = true }) {
                Text(inspectionLocationLabel(location) ?: stringResource(R.string.log_inspection_defect_location_none))
            }
            DropdownMenu(expanded = locationMenuOpen, onDismissRequest = { locationMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.log_inspection_defect_location_none)) },
                    onClick = {
                        locationMenuOpen = false
                        onAnswerChanged(InspectionV2.locKey(item.id), "")
                    },
                )
                InspectionV2.LOCATIONS.forEach { loc ->
                    DropdownMenuItem(
                        text = { Text(inspectionLocationLabel(loc) ?: loc) },
                        onClick = {
                            locationMenuOpen = false
                            onAnswerChanged(InspectionV2.locKey(item.id), loc)
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = answers[InspectionV2.noteKey(item.id)] ?: "",
            onValueChange = { onAnswerChanged(InspectionV2.noteKey(item.id), it) },
            label = { Text(stringResource(R.string.log_inspection_defect_note)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
        )
    }
}

@Composable
private fun InspectionNumberItem(
    item: InspectionItem,
    value: String,
    onAnswerChanged: (key: String, value: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InspectionQuestionHeader(text = item.text, note = item.note)
        OutlinedTextField(
            value = value,
            onValueChange = { onAnswerChanged(item.id, it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = item.placeholder?.let { ph -> { Text(ph) } },
            suffix = item.unit?.let { unit -> { Text(unit) } },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            ),
            singleLine = true,
        )
    }
}

@Composable
private fun InspectionTextItem(
    item: InspectionItem,
    value: String,
    onAnswerChanged: (key: String, value: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InspectionQuestionHeader(text = item.text, note = item.note)
        OutlinedTextField(
            value = value,
            onValueChange = { onAnswerChanged(item.id, it) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
    }
}

/**
 * Full-width question text with a larger font, plus an optional info icon that shows the
 * (already-localized) note text in a dialog. Confirms hover/guide text is localized —
 * [note] is resolved via parseInspectionChecklist(...) before this composable ever sees it.
 */
@Composable
private fun InspectionQuestionHeader(text: String, note: String?) {
    var showNote by remember(text) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (!note.isNullOrBlank()) {
            IconButton(onClick = { showNote = true }, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = note,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (showNote && !note.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showNote = false },
            confirmButton = {
                TextButton(onClick = { showNote = false }) { Text("OK") }
            },
            text = { Text(note) },
        )
    }
}

/**
 * Renders answer options at fixed column positions (Normal=0, Monitor=1, Needs Repair=2, N/A=3,
 * any extra custom options after) so the same semantic answer always lines up in the same column
 * across every question — matches iOS's avOptionColumn(_:) redesign.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FixedColumnOptionRow(
    options: List<InspectionOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var nextExtraCol = OPTION_STD_COLS
    val colToOption = mutableMapOf<Int, InspectionOption>()
    options.forEach { opt ->
        // Match by rawValue (stable "insp.opt.*" key), not the locale-resolved
        // value — otherwise column alignment breaks whenever the active
        // locale's translated text doesn't equal the hardcoded English
        // literals below (mirrors iOS's avOptionRawValue fix).
        var col = inspectionOptionColumn(opt.rawValue)
        if (col < 0) {
            col = nextExtraCol
            nextExtraCol++
        }
        colToOption[col] = opt
    }
    val maxCol = colToOption.keys.maxOrNull() ?: -1
    if (maxCol < 0) return

    // FlowRow instead of a plain Row so extra custom options beyond the
    // standard 4 columns wrap onto additional lines rather than running off
    // narrow screens.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (col in 0..maxCol) {
            val opt = colToOption[col]
            if (opt == null) {
                Spacer(Modifier.width(OPTION_BUTTON_WIDTH))
            } else {
                InspectionOptionButton(
                    option = opt,
                    // Older Android builds stored the localized display text; v2 stores rawValue.
                    isSelected = selected.isNotEmpty() && (opt.rawValue == selected || opt.value == selected),
                    onClick = { onSelect(opt.rawValue) },
                )
            }
        }
    }
}

@Composable
private fun InspectionOptionButton(
    option: InspectionOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val color = INSPECTION_OPTION_COLORS[colorKeyFor(option.rawValue)] ?: MaterialTheme.colorScheme.primary
    val icon = inspectionOptionIcon(option.rawValue)
    var showGuide by remember(option.value) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // Animate the selected-state color transition so tapping an answer feels
    // responsive instead of an instant, jarring color swap.
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) color else Color.Transparent,
        label = "optionContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else color,
        label = "optionContentColor",
    )

    Box {
        OutlinedButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (!option.guide.isNullOrBlank() && isSelected) showGuide = true
                onClick()
            },
            modifier = Modifier
                .width(OPTION_BUTTON_WIDTH)
                .height(OPTION_BUTTON_HEIGHT)
                .semantics { contentDescription = option.value },
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Small icon alongside the text so status is distinguishable
                // without relying on color alone (colorblind accessibility;
                // mirrors iOS's avOptionIcon redundancy fix).
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    // Compact label ("N"/"M"/"NR"/"N/A") to match iOS's density —
                    // full text is still exposed to accessibility services via
                    // the button's contentDescription above.
                    text = inspectionOptionShortLabel(option.rawValue),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        if (!option.guide.isNullOrBlank() && showGuide) {
            AlertDialog(
                onDismissRequest = { showGuide = false },
                confirmButton = { TextButton(onClick = { showGuide = false }) { Text("OK") } },
                text = { Text(option.guide) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Legacy flat-field renderer — used when a config doesn't have the nested groups/sections shape.
// ---------------------------------------------------------------------------

/**
 * Renders a list of legacy inspection form fields based on their [InspectionFieldDef] type.
 * All state changes are reported via [onAnswerChanged].
 *
 * Supported field types:
 * - "checkbox"    → Checkbox row
 * - "select"      → Color-coded FilterChip row
 * - "text"        → OutlinedTextField (multiline)
 * - "number"      → OutlinedTextField (numeric keyboard); shows unit suffix when available
 * - "measurement" → Same as "number" but unit is required; always shows unit suffix
 * - "pass_fail"   → Color-coded Pass / Fail chip row
 */
@Composable
private fun LegacyInspectionFieldsRenderer(
    fields: List<InspectionFieldDef>,
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalCount   = answers.values.count { it.equals("normal", ignoreCase = true) || it.equals("pass", ignoreCase = true) }
    val monitorCount  = answers.values.count { it.equals("monitor", ignoreCase = true) }
    val repairCount   = answers.values.count { it.equals("needs_repair", ignoreCase = true) || it.equals("fail", ignoreCase = true) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (normalCount > 0 || monitorCount > 0 || repairCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (normalCount > 0) {
                    InspectionSummaryPill(
                        label = "Normal $normalCount",
                        containerColor = INSPECTION_OPTION_COLORS["normal"] ?: Color(0xFF4CAF50),
                    )
                }
                if (monitorCount > 0) {
                    InspectionSummaryPill(
                        label = "Monitor $monitorCount",
                        containerColor = INSPECTION_OPTION_COLORS["monitor"] ?: Color(0xFFFF9800),
                    )
                }
                if (repairCount > 0) {
                    InspectionSummaryPill(
                        label = "Needs Repair $repairCount",
                        containerColor = INSPECTION_OPTION_COLORS["needs_repair"] ?: Color(0xFFF44336),
                    )
                }
            }
        }

        fields.forEach { field ->
            val current = answers[field.key] ?: ""
            when (field.type) {
                "checkbox" -> CheckboxField(
                    label = field.label,
                    checked = current == "true",
                    onCheckedChange = { checked -> onAnswerChanged(field.key, if (checked) "true" else "false") },
                )

                "select" -> ColorChipSelectField(
                    label = field.label,
                    options = field.options,
                    selected = current,
                    onSelected = { onAnswerChanged(field.key, it) },
                )

                "number" -> NumberField(
                    label = field.label,
                    value = current,
                    unit = null,
                    onValueChange = { onAnswerChanged(field.key, it) },
                )

                "measurement" -> NumberField(
                    label = field.label,
                    value = current,
                    unit = field.options.firstOrNull(), // convention: unit stored as first option
                    onValueChange = { onAnswerChanged(field.key, it) },
                )

                "pass_fail" -> PassFailChipField(
                    label = field.label,
                    value = current,
                    onValueChanged = { onAnswerChanged(field.key, it) },
                )

                else -> /* "text" and unknown */ OutlinedTextField(
                    value = current,
                    onValueChange = { onAnswerChanged(field.key, it) },
                    label = { Text(field.label) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Summary pill — one per severity tier (Normal / Monitor / Needs Repair)
// ---------------------------------------------------------------------------

/**
 * A small rounded pill badge showing a status label + count.
 * Matches the circular count badges iOS shows at the top of InspectionSummaryView.
 */
@Composable
private fun InspectionSummaryPill(
    label: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(color = containerColor, shape = RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

// ---------------------------------------------------------------------------
// Legacy field composables
// ---------------------------------------------------------------------------

@Composable
private fun CheckboxField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    unit: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
        ),
        singleLine = true,
        suffix = if (unit != null) ({ Text(unit) }) else null,
    )
}

/**
 * Renders select-type inspection options as color-coded [FilterChip]s.
 * The selected chip shows the option's semantic color as its container;
 * unselected chips use the surface color.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorChipSelectField(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val optionColor = INSPECTION_OPTION_COLORS[option.lowercase()]
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(option) },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = optionColor ?: MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
    }
}

/**
 * Renders pass/fail as color-coded [FilterChip]s — green for pass, red for fail.
 */
@Composable
private fun PassFailChipField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = value == "pass",
                onClick = { onValueChanged("pass") },
                label = { Text("Pass") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = INSPECTION_OPTION_COLORS["pass"] ?: Color(0xFF4CAF50),
                    selectedLabelColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            FilterChip(
                selected = value == "fail",
                onClick = { onValueChanged("fail") },
                label = { Text("Fail") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = INSPECTION_OPTION_COLORS["fail"] ?: Color(0xFFF44336),
                    selectedLabelColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}
