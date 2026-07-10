package com.avago.feature.log.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avago.feature.log.model.InspectionChecklist
import com.avago.feature.log.model.InspectionFieldDef
import com.avago.feature.log.model.InspectionGroup
import com.avago.feature.log.model.InspectionItem
import com.avago.feature.log.model.InspectionOption
import com.avago.feature.log.model.InspectionSection
import com.avago.feature.log.model.inspectionOptionColumn
import com.avago.feature.log.model.isScoreEligible

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
) {
    if (checklist != null) {
        InspectionChecklistRenderer(checklist, answers, onAnswerChanged, modifier)
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
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        InspectionScoreBar(checklist, answers)

        checklist.groups.forEach { group ->
            InspectionGroupBlock(group, answers, onAnswerChanged)
        }
    }
}

@Composable
private fun InspectionScoreBar(checklist: InspectionChecklist, answers: Map<String, String>) {
    val eligible = checklist.allItems().filter { it.isScoreEligible() }
    if (eligible.isEmpty()) return
    var normal = 0
    var monitor = 0
    var repair = 0
    eligible.forEach { item ->
        val itemAnswerKeys = if (item.corners.isNotEmpty()) {
            item.corners.map { "${item.id}.${it.id}" }
        } else {
            listOf(item.id)
        }
        itemAnswerKeys.forEach { key ->
            when (inspectionOptionColumn(answers[key] ?: "")) {
                0 -> normal++
                1 -> monitor++
                2 -> repair++
            }
        }
    }
    if (normal == 0 && monitor == 0 && repair == 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (normal > 0) InspectionSummaryPill("Normal $normal", INSPECTION_OPTION_COLORS.getValue("normal"))
        if (monitor > 0) InspectionSummaryPill("Monitor $monitor", INSPECTION_OPTION_COLORS.getValue("monitor"))
        if (repair > 0) InspectionSummaryPill("Needs Repair $repair", INSPECTION_OPTION_COLORS.getValue("needs_repair"))
    }
}

@Composable
private fun InspectionGroupBlock(
    group: InspectionGroup,
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        group.sections.forEach { section ->
            InspectionSectionBlock(section, answers, onAnswerChanged)
        }
    }
}

@Composable
private fun InspectionSectionBlock(
    section: InspectionSection,
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        section.items.forEach { item ->
            InspectionItemBlock(item, answers, onAnswerChanged)
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
        "select" -> InspectionSelectItem(item, answers[item.id] ?: "", onAnswerChanged)
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
    selected: String,
    onAnswerChanged: (key: String, value: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InspectionQuestionHeader(text = item.text, note = item.note)
        FixedColumnOptionRow(
            options = item.options,
            selected = selected,
            onSelect = { onAnswerChanged(item.id, it) },
        )
    }
}

/**
 * "corner-select" layout: one column-aligned option row per corner (e.g. LF/RF/LR/RR tire
 * positions), each corner's label shown above its own answer row. Answers are stored
 * under "{itemId}.{cornerId}" keys.
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
            val key = "${item.id}.${corner.id}"
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = corner.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FixedColumnOptionRow(
                    options = item.options,
                    selected = answers[key] ?: "",
                    onSelect = { onAnswerChanged(key, it) },
                )
            }
        }
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
@Composable
private fun FixedColumnOptionRow(
    options: List<InspectionOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var nextExtraCol = OPTION_STD_COLS
    val colToOption = mutableMapOf<Int, InspectionOption>()
    options.forEach { opt ->
        var col = inspectionOptionColumn(opt.value)
        if (col < 0) {
            col = nextExtraCol
            nextExtraCol++
        }
        colToOption[col] = opt
    }
    val maxCol = colToOption.keys.maxOrNull() ?: -1
    if (maxCol < 0) return

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (col in 0..maxCol) {
            val opt = colToOption[col]
            if (opt == null) {
                Spacer(Modifier.width(OPTION_BUTTON_WIDTH))
            } else {
                InspectionOptionButton(
                    option = opt,
                    isSelected = opt.value == selected,
                    onClick = { onSelect(opt.value) },
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
    val color = INSPECTION_OPTION_COLORS[colorKeyFor(option.value)] ?: MaterialTheme.colorScheme.primary
    var showGuide by remember(option.value) { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = {
                if (!option.guide.isNullOrBlank() && isSelected) showGuide = true
                onClick()
            },
            modifier = Modifier.width(OPTION_BUTTON_WIDTH).height(OPTION_BUTTON_HEIGHT),
            colors = if (isSelected) {
                ButtonDefaults.outlinedButtonColors(
                    containerColor = color,
                    contentColor = Color.White,
                )
            } else {
                ButtonDefaults.outlinedButtonColors(
                    contentColor = color,
                )
            },
        ) {
            Text(
                text = option.value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
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
