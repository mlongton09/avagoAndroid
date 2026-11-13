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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import com.avago.feature.log.model.InspectionValidation

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
private val MIN_TOUCH = 48.dp
private const val OPTION_STD_COLS = 4

/**
 * Renders an inspection form. Prefers the full nested [checklist] (groups -> sections -> items,
 * matching iOS's schema); falls back to the legacy flat [fields] list for older configs that
 * don't have the nested groups/sections/notes/localization shape.
 *
 * [onAnswersChanged] applies several writes atomically (measurement + its severity, mark all);
 * [jumpController] lets the summary bar / navigator / validation dialog scroll to an item;
 * [onCreateWorkOrder] adds a "Create work order" action to each current defect.
 */
@Composable
fun InspectionFormRenderer(
    checklist: InspectionChecklist? = null,
    fields: List<InspectionFieldDef> = emptyList(),
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    onAnswersChanged: ((List<Pair<String, String>>) -> Unit)? = null,
    jumpController: InspectionJumpController? = null,
    assetType: String? = null,
    onCreateWorkOrder: ((List<InspectionV2.EnvelopeDefect>) -> Unit)? = null,
) {
    if (checklist != null) {
        val ctx = InspectionRenderContext(
            checklist = checklist,
            answers = answers,
            onAnswerChanged = onAnswerChanged,
            onAnswersChanged = onAnswersChanged ?: { writes -> writes.forEach { (k, v) -> onAnswerChanged(k, v) } },
            snackbarHostState = snackbarHostState,
            jump = jumpController,
            assetType = assetType,
            onCreateWorkOrder = onCreateWorkOrder,
            missingNotes = InspectionValidation.missingDefectNotes(checklist, answers).map { it.itemId }.toSet(),
            missingRequired = InspectionValidation.missingRequired(checklist, answers).map { it.key }.toSet(),
            defects = InspectionV2.collectDefects(checklist, answers).groupBy { it.itemId },
        )
        InspectionChecklistRenderer(ctx, modifier)
    } else {
        LegacyInspectionFieldsRenderer(fields, answers, onAnswerChanged, modifier)
    }
}

/** Everything an item needs to render and write; rebuilt on every answers change. */
private class InspectionRenderContext(
    val checklist: InspectionChecklist,
    val answers: Map<String, String>,
    val onAnswerChanged: (String, String) -> Unit,
    val onAnswersChanged: (List<Pair<String, String>>) -> Unit,
    val snackbarHostState: SnackbarHostState?,
    val jump: InspectionJumpController?,
    val assetType: String?,
    val onCreateWorkOrder: ((List<InspectionV2.EnvelopeDefect>) -> Unit)?,
    /** Item ids whose defect / out-of-service note is missing. */
    val missingNotes: Set<String>,
    /** Answer keys (item, corner or input ids) of required items still unanswered. */
    val missingRequired: Set<String>,
    val defects: Map<String, List<InspectionV2.EnvelopeDefect>>,
)

// ---------------------------------------------------------------------------
// Nested checklist renderer (groups -> sections -> items) — the new, iOS-parity layout.
// ---------------------------------------------------------------------------

@Composable
private fun InspectionChecklistRenderer(ctx: InspectionRenderContext, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        InspectionScoreBar(ctx.checklist, ctx.answers)

        ctx.checklist.groups.forEach { group ->
            InspectionGroupBlock(group, ctx)
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (progress.n > 0) InspectionSummaryPill("$normalLabel ${progress.n}", INSPECTION_OPTION_COLORS.getValue("normal"))
        if (progress.m > 0) InspectionSummaryPill("$monitorLabel ${progress.m}", INSPECTION_OPTION_COLORS.getValue("monitor"))
        if (progress.nr > 0) InspectionSummaryPill("$repairLabel ${progress.nr}", INSPECTION_OPTION_COLORS.getValue("needs_repair"))
    }
}

@Composable
private fun InspectionGroupBlock(group: InspectionGroup, ctx: InspectionRenderContext) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        group.sections.forEach { section ->
            InspectionSectionBlock(section, ctx)
        }
    }
}

/** One answer slot a section "mark all" can fill: a select item, or one corner of a corner-select item. */
private data class QuickFillTarget(val item: InspectionItem, val corner: InspectionCorner?) {
    val key: String get() = if (corner == null) item.id else InspectionV2.cornerKey(corner)
}

/** Pending "mark all" confirmation: column (0=N, 1=M, 2=NR, 3=N/A), its label and the answered-count. */
private data class QuickFillRequest(val col: Int, val label: String, val answered: Int, val unanswered: Int)

/** Score-eligible select / corner-select answer slots in this section (numeric and wheel items are skipped). */
private fun sectionQuickFillTargets(section: InspectionSection): List<QuickFillTarget> =
    section.items.filter { it.isScoreEligible() && !InspectionValidation.isNumericType(it.type) }.flatMap { item ->
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
 * Writes for a section "mark all" over [targets] (or a clear when [col] is null), plus the previous
 * value of every touched key (for undo). A pre-v2 "item.corner" key is cleared so the old answer
 * can't shadow the new one.
 */
private fun sectionQuickFillWrites(
    targets: List<QuickFillTarget>,
    col: Int?,
    answers: Map<String, String>,
): Pair<List<Pair<String, String>>, List<Pair<String, String>>> {
    val writes = mutableListOf<Pair<String, String>>()
    val previous = mutableListOf<Pair<String, String>>()
    targets.forEach { target ->
        val value = if (col == null) "" else target.optionAt(col)?.rawValue ?: return@forEach
        previous += target.key to (answers[target.key] ?: "")
        writes += target.key to value
        if (target.corner != null) {
            val legacy = InspectionV2.legacyCornerKey(target.item, target.corner)
            if (!answers[legacy].isNullOrEmpty()) {
                previous += legacy to (answers[legacy] ?: "")
                writes += legacy to ""
            }
        }
    }
    return writes to previous
}

@Composable
private fun InspectionSectionBlock(section: InspectionSection, ctx: InspectionRenderContext) {
    val answers = ctx.answers
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pendingQuickFill by remember { mutableStateOf<QuickFillRequest?>(null) }
    val targets = remember(section) { sectionQuickFillTargets(section) }
    val doneTemplate = stringResource(R.string.log_inspection_mark_all_done)
    val clearedTemplate = stringResource(R.string.log_inspection_mark_all_cleared)
    val undoLabel = stringResource(R.string.log_inspection_undo)

    // A column is "active" when every slot that offers it is already answered with it.
    val activeCol = (0..3).firstOrNull { col ->
        val slots = targets.filter { it.optionAt(col) != null }
        slots.isNotEmpty() && slots.all { t ->
            inspectionOptionColumn(InspectionV2.canonicalAnswer(t.item, t.answer(answers)) ?: "") == col
        }
    }

    fun runQuickFill(col: Int?, label: String, slots: List<QuickFillTarget>) {
        val (writes, previous) = sectionQuickFillWrites(slots, col, answers)
        if (writes.isEmpty()) return
        ctx.onAnswersChanged(writes)
        val count = slots.size
        if (ctx.snackbarHostState != null) {
            scope.launch {
                val result = ctx.snackbarHostState.showSnackbar(
                    message = if (col == null) String.format(clearedTemplate, count) else String.format(doneTemplate, count, label),
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) ctx.onAnswersChanged(previous)
            }
        }
    }

    Column(
        modifier = Modifier.inspectionJumpTarget(ctx.jump, InspectionJumpController.sectionKey(section.id)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
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
                        if (activeCol == col) {
                            // Web parity: tapping the active column clears the section.
                            runQuickFill(null, label, slots)
                            return@SectionQuickFillRow
                        }
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
            InspectionItemBlock(item, ctx)
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }

    // Answered items are only overwritten after an explicit choice — web MarkAllBar parity:
    // [Overwrite all] / [Only empty (n)] / [Cancel].
    pendingQuickFill?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingQuickFill = null },
            title = { Text(stringResource(R.string.log_inspection_mark_all_title, request.label)) },
            text = { Text(stringResource(R.string.log_inspection_mark_all_message, request.answered, request.label)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            pendingQuickFill = null
                            runQuickFill(request.col, request.label, targets.filter { it.optionAt(request.col) != null })
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.log_inspection_mark_all_overwrite)) }
                    TextButton(
                        enabled = request.unanswered > 0,
                        onClick = {
                            pendingQuickFill = null
                            runQuickFill(
                                request.col,
                                request.label,
                                targets.filter { it.optionAt(request.col) != null && it.answer(answers) == null },
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.log_inspection_mark_all_unanswered, request.unanswered)) }
                    TextButton(onClick = { pendingQuickFill = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(com.avago.core.ui.R.string.common_cancel))
                    }
                }
            },
        )
    }
}

/** N / M / NR / N/A quick-fill buttons — mirrors iOS's AVInspectionSectionView quick-fill row. */
@OptIn(ExperimentalLayoutApi::class)
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        defs.forEach { (col, raw, full) ->
            val color = INSPECTION_OPTION_COLORS.getValue(colorKeyFor(raw))
            val isActive = activeCol == col
            OutlinedButton(
                onClick = { onRequested(col, full) },
                modifier = Modifier
                    .widthIn(min = OPTION_BUTTON_WIDTH)
                    .heightIn(min = MIN_TOUCH)
                    .semantics {
                        contentDescription = String.format(cdTemplate, full)
                        selected = isActive
                    },
                contentPadding = PaddingValues(horizontal = 4.dp),
                colors = if (isActive) {
                    ButtonDefaults.outlinedButtonColors(containerColor = color, contentColor = Color.White)
                } else {
                    ButtonDefaults.outlinedButtonColors(contentColor = color)
                },
            ) {
                Text(inspectionOptionShortLabel(raw), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InspectionItemBlock(item: InspectionItem, ctx: InspectionRenderContext) {
    val highlight = ctx.jump?.highlightKey
    val keys = buildList {
        add(item.id)
        item.corners.forEach { add(it.id) }
        item.inputs.forEach { add(it.id) }
    }
    val highlighted = highlight != null && highlight in keys
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .inspectionJumpTarget(ctx.jump, *keys.toTypedArray())
            .then(
                if (highlighted) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).padding(6.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        when (item.type) {
            "select" -> InspectionSelectItem(item, ctx)
            "corner-select" -> InspectionCornerSelectItem(item, ctx)
            "wheel-data", "measurement", "number-input" -> InspectionMeasureItem(item, ctx)
            "number" -> InspectionNumberItem(item, ctx)
            else -> InspectionTextItem(item, ctx)
        }
    }
}

/**
 * The primary "select" layout: full-width question text (with an optional info/tooltip icon)
 * above a row of larger, column-aligned answer buttons — mirrors iOS's redesigned
 * AVInspectionFormView.buildSelectLayout().
 */
@Composable
private fun InspectionSelectItem(item: InspectionItem, ctx: InspectionRenderContext) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InspectionQuestionHeader(text = item.text, note = item.note, requiredMissing = item.id in ctx.missingRequired)
        FixedColumnOptionRow(
            options = item.options,
            selected = ctx.answers[item.id] ?: "",
            question = item.text,
            onSelect = { ctx.onAnswerChanged(item.id, it) },
        )
        InspectionDefectDetails(item, ctx)
    }
}

/**
 * "corner-select" layout: one column-aligned option row per corner (e.g. LF/RF/LR/RR tire
 * positions). Answers are stored under the corner id (contract v2); answers written by older
 * Android builds under "{itemId}.{cornerId}" are still read and cleared on change.
 */
@Composable
private fun InspectionCornerSelectItem(item: InspectionItem, ctx: InspectionRenderContext) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InspectionQuestionHeader(
            text = item.text,
            note = item.note,
            requiredMissing = item.corners.any { it.id in ctx.missingRequired },
        )
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
                    selected = InspectionV2.answerOf(ctx.answers, item, corner) ?: "",
                    question = "${item.text} · ${corner.label}",
                    onSelect = {
                        val writes = mutableListOf(key to it)
                        if (!ctx.answers[legacyKey].isNullOrEmpty()) writes += legacyKey to ""
                        ctx.onAnswersChanged(writes)
                    },
                )
            }
        }
        InspectionDefectDetails(item, ctx)
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

@Composable
private fun quickPhraseLabel(phrase: String): String = when (phrase) {
    "low" -> stringResource(R.string.log_insp_phrase_low)
    "worn" -> stringResource(R.string.log_insp_phrase_worn)
    "cracked" -> stringResource(R.string.log_insp_phrase_cracked)
    "leaking" -> stringResource(R.string.log_insp_phrase_leaking)
    "missing" -> stringResource(R.string.log_insp_phrase_missing)
    "loose" -> stringResource(R.string.log_insp_phrase_loose)
    "damaged" -> stringResource(R.string.log_insp_phrase_damaged)
    "dirty" -> stringResource(R.string.log_insp_phrase_dirty)
    "inoperative" -> stringResource(R.string.log_insp_phrase_inoperative)
    else -> phrase
}

/** Locations offered next to the vehicle sketch (not drawn as sketch zones). */
private val NON_SKETCH_LOCATIONS = listOf("roof", "under", "interior", "engine")

/**
 * Severity / location / note inputs shown under an item answered Monitor or Needs Repair, or a
 * measurement outside its acceptable range. Values are stored under `<itemId>__sev`,
 * `<itemId>__loc`, `<itemId>__note` (contract v2). The note is required for Needs Repair and
 * anything out of service (web `notesMissing`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectionDefectDetails(item: InspectionItem, ctx: InspectionRenderContext) {
    val answers = ctx.answers
    val haptic = LocalHapticFeedback.current
    val rangeSeverity = if (InspectionValidation.isNumericType(item.type)) InspectionValidation.itemRangeSeverity(item, answers) else null
    val buckets = when (item.type) {
        "corner-select" -> item.corners.mapNotNull { InspectionV2.bucketOf(item, InspectionV2.answerOf(answers, item, it)) }
        "select" -> listOfNotNull(InspectionV2.bucketOf(item, answers[item.id]))
        else -> emptyList()
    }
    val bucket = when {
        InspectionV2.Bucket.NR in buckets -> InspectionV2.Bucket.NR
        InspectionV2.Bucket.M in buckets -> InspectionV2.Bucket.M
        rangeSeverity != null -> null
        else -> return
    }
    val severity = if (bucket != null) {
        InspectionV2.severityOf(answers, item.id, bucket)
    } else {
        answers[InspectionV2.sevKey(item.id)]?.takeIf { it.isNotEmpty() } ?: rangeSeverity!!
    }
    val location = InspectionV2.locationOf(answers, item.id)
    val noteKey = InspectionV2.noteKey(item.id)
    val note = answers[noteKey] ?: ""
    val noteMissing = item.id in ctx.missingNotes
    val isOos = severity == InspectionV2.SEV_OUT_OF_SERVICE
    var locationDialogOpen by remember(item.id) { mutableStateOf(false) }
    val phraseHint = stringResource(R.string.log_insp_phrase_add_hint)

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
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InspectionV2.SEVERITIES.forEach { sev ->
                val chipColor = when (sev) {
                    InspectionV2.SEV_MINOR -> INSPECTION_OPTION_COLORS.getValue("monitor")
                    else -> INSPECTION_OPTION_COLORS.getValue("needs_repair")
                }
                val label = inspectionSeverityLabel(sev)
                FilterChip(
                    selected = severity == sev,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        ctx.onAnswerChanged(InspectionV2.sevKey(item.id), sev)
                    },
                    label = { Text(label) },
                    modifier = Modifier
                        .heightIn(min = MIN_TOUCH)
                        .semantics { role = Role.RadioButton },
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
        OutlinedButton(onClick = { locationDialogOpen = true }, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
            Text(inspectionLocationLabel(location) ?: stringResource(R.string.log_inspection_defect_location_none))
        }
        OutlinedTextField(
            value = note,
            onValueChange = { ctx.onAnswerChanged(noteKey, it) },
            label = {
                Text(
                    stringResource(
                        when {
                            noteMissing && isOos -> R.string.log_insp_oos_reason
                            noteMissing -> R.string.log_insp_note_required
                            else -> R.string.log_inspection_defect_note
                        },
                    ),
                )
            },
            placeholder = if (noteMissing) ({ Text(stringResource(R.string.log_insp_note_required_placeholder)) }) else null,
            isError = noteMissing,
            modifier = Modifier.fillMaxWidth().inspectionJumpTarget(ctx.jump, noteKey),
            minLines = 1,
        )
        Text(
            text = stringResource(R.string.log_insp_phrases_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InspectionValidation.QUICK_PHRASES.forEach { key ->
                val label = quickPhraseLabel(key)
                SuggestionChip(
                    onClick = { ctx.onAnswerChanged(noteKey, InspectionValidation.appendPhrase(note, label)) },
                    label = { Text(label) },
                    modifier = Modifier
                        .heightIn(min = MIN_TOUCH)
                        .semantics { contentDescription = String.format(phraseHint, label) },
                )
            }
        }
        val itemDefects = ctx.defects[item.id].orEmpty()
        if (ctx.onCreateWorkOrder != null && itemDefects.isNotEmpty()) {
            TextButton(
                onClick = { ctx.onCreateWorkOrder.invoke(itemDefects) },
                modifier = Modifier.heightIn(min = MIN_TOUCH),
            ) {
                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.log_inspection_defect_create_wo), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    if (locationDialogOpen) {
        AlertDialog(
            onDismissRequest = { locationDialogOpen = false },
            title = { Text(stringResource(R.string.log_inspection_defect_location)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.log_insp_sketch_label), style = MaterialTheme.typography.bodySmall)
                    VehicleSketch(
                        assetType = ctx.assetType,
                        selected = location,
                        onSelect = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            ctx.onAnswerChanged(InspectionV2.locKey(item.id), it ?: "")
                            locationDialogOpen = false
                        },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NON_SKETCH_LOCATIONS.forEach { loc ->
                            FilterChip(
                                selected = location == loc,
                                onClick = {
                                    ctx.onAnswerChanged(InspectionV2.locKey(item.id), if (location == loc) "" else loc)
                                    locationDialogOpen = false
                                },
                                label = { Text(inspectionLocationLabel(loc) ?: loc) },
                                modifier = Modifier.heightIn(min = MIN_TOUCH),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { locationDialogOpen = false }, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                    Text(stringResource(R.string.log_insp_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        ctx.onAnswerChanged(InspectionV2.locKey(item.id), "")
                        locationDialogOpen = false
                    },
                    modifier = Modifier.heightIn(min = MIN_TOUCH),
                ) { Text(stringResource(R.string.log_inspection_defect_location_none)) }
            },
        )
    }
}

private fun formatBound(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

private fun rangeText(min: Double?, max: Double?, unit: String?): String? {
    val u = unit?.let { " $it" }.orEmpty()
    return when {
        min != null && max != null -> "${formatBound(min)}–${formatBound(max)}$u"
        min != null -> "≥ ${formatBound(min)}$u"
        max != null -> "≤ ${formatBound(max)}$u"
        else -> null
    }
}

/**
 * Numeric items (web `NumericField`): one field per measurement slot — a single value, each
 * `number-input` input, or the four wheel-data corners (merged into the item's JSON dict). An
 * out-of-range value auto-sets `<id>__sev` (minor / major, never overriding out of service) and
 * opens the defect details so a note can be added.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectionMeasureItem(item: InspectionItem, ctx: InspectionRenderContext) {
    val slots = remember(item) { InspectionValidation.measureSlots(item) }
    val scope = rememberCoroutineScope()
    val sevTemplate = stringResource(R.string.log_insp_range_sev_set)
    val minorLabel = stringResource(R.string.log_inspection_severity_minor)
    val majorLabel = stringResource(R.string.log_inspection_severity_major)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InspectionQuestionHeader(
            text = item.text,
            note = item.note,
            requiredMissing = item.id in ctx.missingRequired || slots.any { it.key in ctx.missingRequired },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slots.forEach { slot ->
                val raw = InspectionValidation.measureValue(ctx.answers, slot)
                val status = InspectionV2.rangeStatus(raw, slot.min, slot.max)
                val range = rangeText(slot.min, slot.max, item.unit)
                val label = slot.corner?.let { inspectionLocationLabel(it) } ?: slot.label
                OutlinedTextField(
                    value = raw,
                    onValueChange = { v ->
                        val writes = InspectionValidation.measureWrites(
                            item,
                            ctx.answers,
                            listOf(InspectionValidation.slotWrite(ctx.answers, slot, v)),
                        )
                        ctx.onAnswersChanged(writes)
                        val sev = writes.firstOrNull { it.first == InspectionV2.sevKey(item.id) }?.second
                        if (!sev.isNullOrEmpty() && ctx.snackbarHostState != null) {
                            scope.launch {
                                ctx.snackbarHostState.showSnackbar(
                                    String.format(sevTemplate, if (sev == InspectionV2.SEV_MAJOR) majorLabel else minorLabel),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    },
                    label = label?.let { { Text(it) } },
                    modifier = Modifier
                        .then(if (slots.size > 1) Modifier.widthIn(min = 140.dp, max = 220.dp) else Modifier.fillMaxWidth())
                        .inspectionJumpTarget(ctx.jump, if (slot.corner != null) "${slot.key}:${slot.corner}" else slot.key),
                    placeholder = item.placeholder?.let { ph -> { Text(ph) } },
                    suffix = item.unit?.let { unit -> { Text(unit) } },
                    isError = status != null,
                    supportingText = when {
                        status == "low" -> ({ Text(stringResource(R.string.log_insp_range_low, formatBound(slot.min!!))) })
                        status == "high" -> ({ Text(stringResource(R.string.log_insp_range_high, formatBound(slot.max!!))) })
                        range != null -> ({ Text(stringResource(R.string.log_insp_range_hint, range)) })
                        else -> null
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                    singleLine = true,
                )
            }
        }
        InspectionDefectDetails(item, ctx)
    }
}

/** Legacy "number" items (no range metadata). */
@Composable
private fun InspectionNumberItem(item: InspectionItem, ctx: InspectionRenderContext) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InspectionQuestionHeader(text = item.text, note = item.note, requiredMissing = item.id in ctx.missingRequired)
        OutlinedTextField(
            value = ctx.answers[item.id] ?: "",
            onValueChange = { ctx.onAnswerChanged(item.id, it) },
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
private fun InspectionTextItem(item: InspectionItem, ctx: InspectionRenderContext) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InspectionQuestionHeader(text = item.text, note = item.note, requiredMissing = item.id in ctx.missingRequired)
        OutlinedTextField(
            value = ctx.answers[item.id] ?: "",
            onValueChange = { ctx.onAnswerChanged(item.id, it) },
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
private fun InspectionQuestionHeader(text: String, note: String?, requiredMissing: Boolean = false) {
    var showNote by remember(text) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.semantics { heading() },
            )
            if (requiredMissing) {
                Text(
                    text = stringResource(R.string.log_insp_required_mark),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (!note.isNullOrBlank()) {
            val infoCd = stringResource(R.string.log_insp_more_info)
            IconButton(onClick = { showNote = true }, modifier = Modifier.size(MIN_TOUCH)) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = infoCd,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (showNote && !note.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showNote = false },
            confirmButton = {
                TextButton(onClick = { showNote = false }) { Text(stringResource(R.string.log_insp_ok)) }
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
    question: String,
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
    val selectedOption = colToOption.values.firstOrNull { selected.isNotEmpty() && (it.rawValue == selected || it.value == selected) }
    val stateText = selectedOption?.value ?: stringResource(R.string.log_insp_not_answered)
    val stateTemplate = stringResource(R.string.log_insp_answer_state)

    // FlowRow instead of a plain Row so extra custom options beyond the
    // standard 4 columns wrap onto additional lines rather than running off
    // narrow screens (and at large font scales).
    FlowRow(
        modifier = Modifier
            .selectableGroup()
            .semantics { stateDescription = String.format(stateTemplate, question, stateText) },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (col in 0..maxCol) {
            val opt = colToOption[col]
            if (opt == null) {
                Spacer(Modifier.width(OPTION_BUTTON_WIDTH))
            } else {
                InspectionOptionButton(
                    option = opt,
                    question = question,
                    // Older Android builds stored the localized display text; v2 stores rawValue.
                    isSelected = opt === selectedOption,
                    onClick = { onSelect(opt.rawValue) },
                )
            }
        }
    }
}

@Composable
private fun InspectionOptionButton(
    option: InspectionOption,
    question: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val color = INSPECTION_OPTION_COLORS[colorKeyFor(option.rawValue)] ?: MaterialTheme.colorScheme.primary
    val icon = inspectionOptionIcon(option.rawValue)
    var showGuide by remember(option.value) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val cd = String.format(stringResource(R.string.log_insp_answer_state), question, option.value)

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
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                if (!option.guide.isNullOrBlank() && isSelected) showGuide = true
                onClick()
            },
            modifier = Modifier
                .widthIn(min = OPTION_BUTTON_WIDTH)
                .heightIn(min = OPTION_BUTTON_HEIGHT)
                .semantics {
                    contentDescription = cd
                    role = Role.RadioButton
                    selected = isSelected
                },
            contentPadding = PaddingValues(horizontal = 6.dp),
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
                confirmButton = { TextButton(onClick = { showGuide = false }) { Text(stringResource(R.string.log_insp_ok)) } },
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
                        label = "${stringResource(R.string.log_inspection_opt_normal)} $normalCount",
                        containerColor = INSPECTION_OPTION_COLORS["normal"] ?: Color(0xFF4CAF50),
                    )
                }
                if (monitorCount > 0) {
                    InspectionSummaryPill(
                        label = "${stringResource(R.string.log_inspection_opt_monitor)} $monitorCount",
                        containerColor = INSPECTION_OPTION_COLORS["monitor"] ?: Color(0xFFFF9800),
                    )
                }
                if (repairCount > 0) {
                    InspectionSummaryPill(
                        label = "${stringResource(R.string.log_inspection_opt_needs_repair)} $repairCount",
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
                label = { Text(stringResource(R.string.log_insp_pass)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = INSPECTION_OPTION_COLORS["pass"] ?: Color(0xFF4CAF50),
                    selectedLabelColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            FilterChip(
                selected = value == "fail",
                onClick = { onValueChanged("fail") },
                label = { Text(stringResource(R.string.log_insp_fail)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = INSPECTION_OPTION_COLORS["fail"] ?: Color(0xFFF44336),
                    selectedLabelColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}
