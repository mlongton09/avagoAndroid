package com.avago.feature.log.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avago.feature.log.R
import com.avago.feature.log.model.InspectionChecklist
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.humanizeIfKey
import com.avago.feature.log.model.parseInspectionChecklist
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Formats an ISO instant / date string for display; returns the input when it can't be parsed. */
internal fun formatInspectionDate(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM)
    return runCatching { fmt.format(Date.from(Instant.parse(iso))) }.getOrNull()
        ?: runCatching {
            fmt.format(Date.from(LocalDate.parse(iso.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant()))
        }.getOrNull()
        ?: iso
}

private fun severityColor(severity: String): Color = when (severity) {
    InspectionV2.SEV_MINOR -> INSPECTION_OPTION_COLORS.getValue("monitor")
    else -> INSPECTION_OPTION_COLORS.getValue("needs_repair")
}

@Composable
private fun inspectionStatusLabel(status: String): String = when (status) {
    "monitoring" -> stringResource(R.string.log_inspection_status_monitoring)
    "deferred" -> stringResource(R.string.log_inspection_status_deferred)
    else -> stringResource(R.string.log_inspection_status_open)
}

@Composable
private fun SeverityDot(severity: String) {
    Box(
        modifier = Modifier
            .padding(top = 6.dp)
            .size(8.dp)
            .background(severityColor(severity), CircleShape),
    )
}

/** "Label · Corner" with the severity / location / note line used by both form and detail views. */
@Composable
private fun DefectSummary(
    label: String,
    cornerLabel: String?,
    sectionTitle: String?,
    severity: String,
    location: String?,
    note: String?,
    extra: String? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SeverityDot(severity)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = listOfNotNull(label, cornerLabel).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val meta = listOfNotNull(
                inspectionSeverityName(severity),
                inspectionLocationLabel(location),
                sectionTitle,
                extra,
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!note.isNullOrBlank()) {
                Text(note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * "Open defects from previous inspections (n)" — shown at the top of a NEW inspection. Each defect
 * can be marked Repaired / Still present; the choice is written to `data.resolvedDefects` on save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InspectionOpenDefectsCard(
    defects: List<InspectionV2.OpenDefect>,
    resolutions: Map<String, String>,
    labelOf: (String?) -> String?,
    onResolve: (key: String, resolution: String) -> Unit,
    modifier: Modifier = Modifier,
    onCreateWorkOrder: ((InspectionV2.OpenDefect) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.log_inspection_open_defects_title, defects.size),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        defects.forEachIndexed { idx, d ->
            if (idx > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            val reported = formatInspectionDate(d.reportedAt)?.let {
                stringResource(R.string.log_inspection_open_defects_reported, it)
            }
            DefectSummary(
                label = labelOf(d.label) ?: d.label,
                cornerLabel = labelOf(d.cornerLabel),
                sectionTitle = labelOf(d.sectionTitle),
                severity = d.severity,
                location = d.location,
                note = d.note,
                extra = listOfNotNull(reported, inspectionStatusLabel(d.status).takeIf { d.status != "open" })
                    .joinToString(" · ").ifBlank { null },
            )
            val current = resolutions[d.key]
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = current == InspectionV2.RESOLUTION_REPAIRED,
                    onClick = { onResolve(d.key, InspectionV2.RESOLUTION_REPAIRED) },
                    label = { Text(stringResource(R.string.log_inspection_defect_repaired)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = INSPECTION_OPTION_COLORS.getValue("normal"),
                        selectedLabelColor = Color.White,
                    ),
                )
                FilterChip(
                    selected = current == InspectionV2.RESOLUTION_STILL_PRESENT,
                    onClick = { onResolve(d.key, InspectionV2.RESOLUTION_STILL_PRESENT) },
                    label = { Text(stringResource(R.string.log_inspection_defect_still_present)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = INSPECTION_OPTION_COLORS.getValue("needs_repair"),
                        selectedLabelColor = Color.White,
                    ),
                )
                if (onCreateWorkOrder != null) {
                    androidx.compose.material3.TextButton(onClick = { onCreateWorkOrder(d) }) {
                        Text(stringResource(R.string.log_inspection_defect_create_wo))
                    }
                }
            }
        }
    }
}

/**
 * Read-only rendering of a v2 inspection envelope (also handles legacy flat Android data):
 * result / out-of-service, defects with severity + location + note, re-checked previous defects,
 * repair certification, inspector sign-off and the answer list.
 */
@Composable
internal fun InspectionEnvelopeDetail(
    data: JsonObject?,
    strings: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val envelope = remember(data) { InspectionV2.parseEnvelope(data) }
    val checklist: InspectionChecklist? = remember(data, strings) {
        (envelope.configSnapshot?.get("inspectionChecklist") as? JsonObject)
            ?.let { parseInspectionChecklist(it.toString(), strings) }
    }
    val legacyLabels: Map<String, String> = remember(data) {
        (envelope.configSnapshot?.get("fields") as? JsonArray)?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val k = (o["key"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val l = (o["label"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            k to l
        }?.toMap() ?: emptyMap()
    }
    fun label(key: String?): String? = key?.let { strings[it] ?: humanizeIfKey(it) }

    // Stored defects win; otherwise recompute (v1 entries / legacy Android data).
    val defects = remember(data, checklist) {
        if (envelope.hasDefectsArray) envelope.defects else InspectionV2.collectDefects(checklist, envelope.values)
    }
    val result = envelope.result ?: if (checklist != null) InspectionV2.computeResult(checklist, envelope.values).result else null
    val outOfService = envelope.outOfService || defects.any { it.severity == InspectionV2.SEV_OUT_OF_SERVICE }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (result != null) {
            val (text, color) = when (result) {
                InspectionV2.RESULT_PASS -> stringResource(R.string.log_detail_inspection_result_pass) to INSPECTION_OPTION_COLORS.getValue("normal")
                InspectionV2.RESULT_PASS_WITH_DEFECTS -> stringResource(R.string.log_detail_inspection_result_pass_with_defects) to INSPECTION_OPTION_COLORS.getValue("monitor")
                else -> stringResource(R.string.log_detail_inspection_result_fail) to INSPECTION_OPTION_COLORS.getValue("needs_repair")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.log_detail_inspection_result),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .background(color, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        if (outOfService) {
            Text(
                text = stringResource(R.string.log_detail_inspection_out_of_service),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (defects.isNotEmpty()) {
            SectionLabel(stringResource(R.string.log_detail_inspection_defects, defects.size))
            defects.forEach { d ->
                DefectSummary(
                    label = label(d.label) ?: d.itemId,
                    cornerLabel = label(d.cornerLabel),
                    sectionTitle = label(d.sectionTitle),
                    severity = d.severity,
                    location = d.location,
                    note = d.note,
                )
            }
        }

        if (envelope.resolvedDefects.isNotEmpty()) {
            SectionLabel(stringResource(R.string.log_detail_inspection_resolved, envelope.resolvedDefects.size))
            envelope.resolvedDefects.forEach { r ->
                val itemText = checklist?.allItems()?.firstOrNull { it.id == r.itemId }?.text
                    ?: legacyLabels[r.itemId] ?: label(r.itemId) ?: r.itemId
                val resolution = if (r.resolution == InspectionV2.RESOLUTION_REPAIRED) {
                    stringResource(R.string.log_inspection_defect_repaired)
                } else {
                    stringResource(R.string.log_inspection_defect_still_present)
                }
                Column {
                    Text(itemText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        text = listOfNotNull(resolution, formatInspectionDate(r.at)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.resolution == InspectionV2.RESOLUTION_REPAIRED) {
                            INSPECTION_OPTION_COLORS.getValue("normal")
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (!r.note.isNullOrBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        envelope.certification?.let { c ->
            SectionLabel(stringResource(R.string.log_detail_inspection_certification))
            Text(
                text = if (c.status == "corrected") {
                    stringResource(R.string.log_detail_inspection_cert_corrected)
                } else {
                    stringResource(R.string.log_detail_inspection_cert_not_needed)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            SignedByLine(c.name, c.signedAt)
            if (!c.note.isNullOrBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall)
        }

        envelope.signOff?.let { s ->
            SectionLabel(stringResource(R.string.log_detail_inspection_signoff))
            SignedByLine(s.name, s.signedAt)
        }

        // Answers: rendered in checklist order when the snapshot is available.
        val rows: List<Pair<String, String>> = remember(data, checklist, strings) {
            answerRows(envelope.values, checklist, legacyLabels, strings)
        }
        if (rows.isNotEmpty()) {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            rows.forEach { (k, v) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = k,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = v,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SignedByLine(name: String, signedAt: String?) {
    val date = formatInspectionDate(signedAt)
    Text(
        text = if (date != null) {
            stringResource(R.string.log_detail_inspection_signed_by_on, name, date)
        } else {
            stringResource(R.string.log_detail_inspection_signed_by, name)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** (question, answer) rows; meta keys (`__sev` / `__note` / `__loc`) are shown via the defect list. */
private fun answerRows(
    values: Map<String, String>,
    checklist: InspectionChecklist?,
    legacyLabels: Map<String, String>,
    strings: Map<String, String>,
): List<Pair<String, String>> {
    fun display(raw: String): String = strings[raw] ?: humanizeIfKey(raw)
    if (checklist == null) {
        return values.filterKeys { !InspectionV2.isMetaKey(it) }.map { (k, v) ->
            (legacyLabels[k] ?: strings[k] ?: k.replace('_', ' ').replaceFirstChar { it.uppercase() }) to display(v)
        }
    }
    val out = mutableListOf<Pair<String, String>>()
    checklist.allItems().forEach { item ->
        fun answerText(v: String): String =
            item.options.firstOrNull { it.rawValue == v || it.value == v }?.value ?: display(v)
        if (item.type == "corner-select" && item.corners.isNotEmpty()) {
            item.corners.forEach { corner ->
                InspectionV2.answerOf(values, item, corner)?.let { out += "${item.text} · ${corner.label}" to answerText(it) }
            }
        } else {
            values[item.id]?.takeIf { it.isNotEmpty() }?.let { out += item.text to answerText(it) }
        }
    }
    return out
}
