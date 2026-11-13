package com.avago.feature.log.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.avago.feature.log.R
import com.avago.feature.log.model.InspectionChecklist
import com.avago.feature.log.model.InspectionDraft
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.InspectionValidation
import com.avago.feature.log.viewmodel.InspectionSaveIssues
import com.avago.feature.log.viewmodel.LogSyncStatus
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.time.Instant
import java.util.Date

// ---------------------------------------------------------------------------
// Jump-to (summary bar, navigator, validation dialog)
// ---------------------------------------------------------------------------

/**
 * Lets the summary bar / navigator / validation dialog scroll the form to an item or section.
 * Items register under their item id, answer keys (corner ids, measurement input ids) and sections
 * under `section:<id>`.
 */
@Stable
class InspectionJumpController {
    private val requesters = HashMap<String, BringIntoViewRequester>()
    /** Viewport height of the scroll container (px): the requested rect top-aligns the target. */
    var viewportHeightPx: Float = 1_000f
    var highlightKey by mutableStateOf<String?>(null)
        private set

    fun register(key: String, requester: BringIntoViewRequester) { requesters[key] = requester }
    fun unregister(key: String, requester: BringIntoViewRequester) { if (requesters[key] === requester) requesters.remove(key) }

    suspend fun jumpTo(vararg keys: String) {
        val key = keys.firstOrNull { it in requesters } ?: return
        highlightKey = key
        requesters[key]?.bringIntoView(Rect(0f, 0f, 1f, (viewportHeightPx - 1f).coerceAtLeast(1f)))
    }

    suspend fun jumpTo(target: InspectionValidation.FocusTarget) = jumpTo(target.key, target.itemId, sectionKey(target.sectionId))

    fun clearHighlight() { highlightKey = null }

    companion object {
        fun sectionKey(id: String) = "section:$id"
    }
}

@Composable
fun rememberInspectionJumpController(): InspectionJumpController = remember { InspectionJumpController() }

/** Registers this node under [keys] so [InspectionJumpController.jumpTo] can scroll to it. */
@Composable
fun Modifier.inspectionJumpTarget(controller: InspectionJumpController?, vararg keys: String): Modifier {
    if (controller == null) return this
    val requester = remember { BringIntoViewRequester() }
    val list = keys.toList()
    DisposableEffect(controller, list) {
        list.forEach { controller.register(it, requester) }
        onDispose { list.forEach { controller.unregister(it, requester) } }
    }
    return this.bringIntoViewRequester(requester)
}

// ---------------------------------------------------------------------------
// Persistent summary bar
// ---------------------------------------------------------------------------

@Composable
fun inspectionResultLabel(result: String): String = when (result) {
    InspectionV2.RESULT_FAIL -> stringResource(R.string.log_detail_inspection_result_fail)
    InspectionV2.RESULT_PASS_WITH_DEFECTS -> stringResource(R.string.log_detail_inspection_result_pass_with_defects)
    else -> stringResource(R.string.log_detail_inspection_result_pass)
}

fun inspectionResultColor(result: String, outOfService: Boolean): Color = when {
    outOfService || result == InspectionV2.RESULT_FAIL -> INSPECTION_OPTION_COLORS.getValue("needs_repair")
    result == InspectionV2.RESULT_PASS_WITH_DEFECTS -> INSPECTION_OPTION_COLORS.getValue("monitor")
    else -> INSPECTION_OPTION_COLORS.getValue("normal")
}

/**
 * Sticky compact summary: answered / total, required remaining (tap → next one), missing notes,
 * defect count and the live result / out-of-service state (announced on change).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InspectionSummaryBar(
    checklist: InspectionChecklist,
    answers: Map<String, String>,
    onJump: (InspectionValidation.FocusTarget) -> Unit,
    onOpenSections: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val progress = remember(checklist, answers) { InspectionV2.computeProgress(checklist, answers) }
    val required = remember(checklist, answers) { InspectionValidation.missingRequired(checklist, answers) }
    val notes = remember(checklist, answers) { InspectionValidation.missingDefectNotes(checklist, answers) }
    val defects = remember(checklist, answers) { InspectionV2.collectDefects(checklist, answers) }
    val summary = remember(checklist, answers) { InspectionV2.computeResult(checklist, answers) }
    val resultText = if (summary.outOfService) {
        stringResource(R.string.log_detail_inspection_out_of_service)
    } else {
        inspectionResultLabel(summary.result)
    }
    val resultColor = inspectionResultColor(summary.result, summary.outOfService)
    val announce = stringResource(R.string.log_insp_announce_result, resultText)
    val progressCd = stringResource(R.string.log_insp_announce_progress, progress.answered, progress.total)

    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (progress.total > 0) {
                LinearProgressIndicator(
                    progress = { progress.answered.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressCd },
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.log_insp_bar_answered, progress.answered, progress.total),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 4.dp),
                )
                if (required.isNotEmpty()) {
                    AssistChip(
                        onClick = { onJump(required.first()) },
                        label = { Text(stringResource(R.string.log_insp_bar_required, required.size)) },
                        leadingIcon = { Icon(Icons.Filled.Error, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.error,
                            leadingIconContentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                if (notes.isNotEmpty()) {
                    AssistChip(
                        onClick = { onJump(notes.first()) },
                        label = { Text(stringResource(R.string.log_insp_bar_notes, notes.size)) },
                        leadingIcon = { Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.error,
                            leadingIconContentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                Text(
                    text = if (defects.isEmpty()) {
                        stringResource(R.string.log_insp_bar_no_defects)
                    } else {
                        stringResource(R.string.log_insp_bar_defects, defects.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    
                )
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = announce
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = resultText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(resultColor, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (onOpenSections != null) {
                    val cd = stringResource(R.string.log_insp_sections_cd)
                    TextButton(
                        onClick = onOpenSections,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = cd },
                    ) {
                        Icon(Icons.Filled.ListAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.log_insp_sections), modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

/** Section list with per-section progress; tap jumps to the section. */
@Composable
fun InspectionSectionNavigator(
    sections: List<InspectionValidation.SectionProgress>,
    onSelect: (sectionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.log_insp_sections),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { heading() },
        )
        sections.forEach { s ->
            val cd = stringResource(R.string.log_insp_section_progress_cd, s.title, s.answered, s.total)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { onSelect(s.sectionId) }
                    .semantics(mergeDescendants = true) { contentDescription = cd }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = s.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (s.total > 0 && s.answered == s.total && s.attention == 0) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = INSPECTION_OPTION_COLORS.getValue("normal"),
                            modifier = Modifier.size(18.dp),
                        )
                    } else if (s.total > 0) {
                        Text("${s.answered}/${s.total}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (s.total > 0) {
                    LinearProgressIndicator(
                        progress = { s.answered.toFloat() / s.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (s.attention > 0) {
                    Text(
                        text = stringResource(R.string.log_insp_section_attention, s.attention),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Validation dialogs
// ---------------------------------------------------------------------------

@Composable
fun InspectionIssuesDialog(
    issues: InspectionSaveIssues,
    labelFor: (String) -> String,
    onJump: (InspectionValidation.FocusTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_insp_issues_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (issues.required.isNotEmpty()) {
                    Text(stringResource(R.string.log_insp_required_body, issues.required.size))
                    issues.required.take(5).forEach { t -> IssueRow(labelFor(t.key)) { onJump(t) } }
                }
                if (issues.notes.isNotEmpty()) {
                    Text(stringResource(R.string.log_insp_notes_body, issues.notes.size))
                    issues.notes.take(5).forEach { t -> IssueRow(labelFor(t.key)) { onJump(t) } }
                }
                if (issues.certificationNameMissing) {
                    Text(stringResource(R.string.log_insp_cert_name_missing))
                }
            }
        },
        confirmButton = {
            val first = issues.required.firstOrNull() ?: issues.notes.firstOrNull()
            if (first != null) {
                TextButton(onClick = { onJump(first) }) {
                    Text(
                        stringResource(
                            if (issues.required.isNotEmpty()) R.string.log_insp_go_to_required else R.string.log_insp_go_to_note,
                        ),
                    )
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_insp_ok)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(com.avago.core.ui.R.string.common_cancel)) }
        },
    )
}

@Composable
private fun IssueRow(label: String, onClick: () -> Unit) {
    Text(
        text = "• $label",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

private fun formatMeter(v: Double): String =
    if (v % 1.0 == 0.0) String.format("%,d", v.toLong()) else String.format("%,.1f", v)

private fun formatDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

@Composable
fun MeterIssueDialog(
    issue: InspectionValidation.MeterIssue,
    onSaveAnyway: () -> Unit,
    onEdit: () -> Unit,
) {
    val message = if (issue.type == InspectionValidation.MeterIssue.LOWER) {
        stringResource(
            R.string.log_insp_meter_lower,
            formatMeter(issue.entered),
            formatMeter(issue.last.value),
            formatDate(issue.last.atMs),
        )
    } else {
        stringResource(
            R.string.log_insp_meter_rate,
            formatMeter(issue.entered),
            (issue.perDay ?: 0L).toInt(),
            formatMeter(issue.last.value),
            formatDate(issue.last.atMs),
            formatMeter(issue.limit ?: 0.0),
        )
    }
    AlertDialog(
        onDismissRequest = onEdit,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
        title = { Text(stringResource(R.string.log_insp_meter_title)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onSaveAnyway) { Text(stringResource(R.string.log_insp_meter_save_anyway)) } },
        dismissButton = { TextButton(onClick = onEdit) { Text(stringResource(R.string.log_insp_meter_edit)) } },
    )
}

@Composable
fun LastMeterHint(last: InspectionValidation.MeterPoint?) {
    if (last == null) return
    Text(
        text = stringResource(R.string.log_insp_meter_last, formatMeter(last.value), formatDate(last.atMs)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------
// Drafts / sync status
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DraftBanner(
    restoredAt: Long?,
    serverOffer: InspectionDraft?,
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
    onRestoreServer: () -> Unit,
    onDismissServer: () -> Unit,
) {
    if (restoredAt == null && serverOffer == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            if (restoredAt != null) {
                Text(
                    stringResource(R.string.log_insp_draft_restored, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(restoredAt))),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.log_insp_draft_discard))
                    }
                    TextButton(onClick = onKeep, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.log_insp_draft_keep))
                    }
                }
            }
            if (serverOffer != null) {
                Text(
                    stringResource(R.string.log_insp_server_draft, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(serverOffer.savedAt))),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRestoreServer, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.log_insp_server_draft_restore))
                    }
                    TextButton(onClick = onDismissServer, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.log_insp_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
fun SyncStatusChip(status: LogSyncStatus?, modifier: Modifier = Modifier) {
    if (status == null) return
    val (icon, label, color) = when (status) {
        LogSyncStatus.SAVED -> Triple(Icons.Filled.CloudDone, stringResource(R.string.log_sync_saved), INSPECTION_OPTION_COLORS.getValue("normal"))
        LogSyncStatus.PENDING -> Triple(Icons.Filled.CloudOff, stringResource(R.string.log_sync_pending), INSPECTION_OPTION_COLORS.getValue("monitor"))
        LogSyncStatus.CONFLICT -> Triple(Icons.Filled.Error, stringResource(R.string.log_sync_conflict), INSPECTION_OPTION_COLORS.getValue("needs_repair"))
    }
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** High-contrast light scheme for outdoor use ("sunlight mode"). */
fun sunlightColorScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF00305F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF1B1B1B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0E0),
    onSecondaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF111111),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF2F2F2),
    surfaceContainerHighest = Color(0xFFE6E6E6),
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White,
    outline = Color.Black,
    outlineVariant = Color(0xFF444444),
    error = Color(0xFFB00000),
    onError = Color.White,
)

// ---------------------------------------------------------------------------
// Signature pad / sign-off / certification
// ---------------------------------------------------------------------------

/** Decodes a `data:image/png;base64,…` signature for display; null when it isn't one. */
fun decodeSignature(dataUrl: String?): ImageBitmap? {
    if (dataUrl == null || !dataUrl.startsWith("data:image")) return null
    return runCatching {
        val bytes = Base64.decode(dataUrl.substringAfter("base64,"), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/** Renders strokes to a transparent PNG data URL — the format web stores in `signOff.signature`. */
private fun strokesToDataUrl(strokes: List<List<Offset>>, size: IntSize, strokePx: Float): String? {
    if (strokes.isEmpty() || size.width <= 0 || size.height <= 0) return null
    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    strokes.forEach { pts ->
        if (pts.size == 1) {
            canvas.drawPoint(pts[0].x, pts[0].y, paint)
        } else {
            val p = android.graphics.Path()
            p.moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { p.lineTo(it.x, it.y) }
            canvas.drawPath(p, paint)
        }
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    bmp.recycle()
    return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

/**
 * Finger / stylus signature pad (Compose Canvas). Emits a PNG data URL after each stroke, or null
 * when cleared. An existing [signature] is shown until cleared.
 */
@Composable
fun SignaturePad(
    signature: String?,
    onSignatureChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    val existing = remember(signature, strokes.isEmpty()) { if (strokes.isEmpty()) decodeSignature(signature) else null }
    val strokePx = with(LocalDensity.current) { 2.5.dp.toPx() }
    val inkColor = MaterialTheme.colorScheme.onSurface
    val padCd = stringResource(if (signature != null) R.string.log_insp_signoff_pad_signed else R.string.log_insp_signoff_pad_label)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(3f)
                .heightIn(min = 120.dp)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(8.dp))
                .background(Color.White, RoundedCornerShape(8.dp))
                .onSizeChanged { padSize = it }
                .semantics { contentDescription = padCd }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDrag = { change, _ ->
                            change.consume()
                            current = current + change.position
                        },
                        onDragEnd = {
                            if (current.isNotEmpty()) strokes.add(current)
                            current = emptyList()
                            onSignatureChange(strokesToDataUrl(strokes.toList(), padSize, strokePx))
                        },
                        onDragCancel = { current = emptyList() },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        strokes.add(listOf(pos))
                        onSignatureChange(strokesToDataUrl(strokes.toList(), padSize, strokePx))
                    }
                },
        ) {
            if (existing != null) {
                Image(existing, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(3f))
            }
            Canvas(Modifier.fillMaxWidth().aspectRatio(3f)) {
                (strokes + listOf(current)).forEach { pts ->
                    if (pts.isEmpty()) return@forEach
                    if (pts.size == 1) {
                        drawCircle(Color.Black, radius = strokePx / 2, center = pts[0])
                    } else {
                        val path = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, Color.Black, style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            if (existing == null && strokes.isEmpty() && current.isEmpty()) {
                Text(
                    stringResource(R.string.log_insp_signoff_draw_hint),
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        if (existing != null || strokes.isNotEmpty()) {
            TextButton(
                onClick = {
                    strokes.clear()
                    current = emptyList()
                    onSignatureChange(null)
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.log_insp_signoff_clear), color = inkColor) }
        }
    }
}

private fun nowIso(): String = Instant.now().toString()

/**
 * Inspector sign-off (web `InspectionSignOffField`): name + drawn or typed signature. Stored as
 * `{name, signature, typed, signedAt}`; null when there is neither a drawing nor typed text.
 */
@Composable
fun InspectionSignOffSection(
    signOff: InspectionV2.SignOff?,
    defaultName: String?,
    onChange: (InspectionV2.SignOff?) -> Unit,
) {
    var name by remember { mutableStateOf(signOff?.name ?: defaultName.orEmpty()) }
    var typed by remember { mutableStateOf(signOff?.typed.orEmpty()) }
    var drawn by remember { mutableStateOf(signOff?.signature) }
    var tab by remember { mutableStateOf(if (!signOff?.typed.isNullOrBlank() && signOff?.signature == null) 1 else 0) }

    fun emit() {
        val sig = drawn
        val t = typed.trim().ifBlank { null }
        if (sig == null && t == null) {
            onChange(null)
        } else {
            onChange(InspectionV2.SignOff(name = name.trim().ifBlank { t.orEmpty() }, signature = sig, typed = t, signedAt = nowIso()))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.log_insp_signoff_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.log_insp_signoff_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; emit() },
            label = { Text(stringResource(R.string.log_insp_signoff_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = tab == 0, onClick = { tab = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text(stringResource(R.string.log_insp_signoff_draw))
            }
            SegmentedButton(selected = tab == 1, onClick = { tab = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text(stringResource(R.string.log_insp_signoff_type))
            }
        }
        if (tab == 0) {
            SignaturePad(signature = drawn, onSignatureChange = { drawn = it; emit() })
        } else {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it; emit() },
                placeholder = { Text(stringResource(R.string.log_insp_signoff_type_placeholder)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        signOff?.signedAt?.let { at ->
            val ms = runCatching { Instant.parse(at).toEpochMilli() }.getOrNull()
            if (ms != null) {
                Text(
                    stringResource(R.string.log_insp_signed_at, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * DVIR repair certification (web `InspectionWorkspaceCertification`): defects corrected / need not
 * be corrected, certifier name, note and (Android extra) the mechanic's drawn signature.
 */
@Composable
fun InspectionCertificationSection(
    certification: InspectionV2.Certification?,
    defaultName: String?,
    onChange: (InspectionV2.Certification?) -> Unit,
) {
    val nameMissing = certification != null && certification.name.isBlank()

    fun update(
        status: String = certification?.status ?: "corrected",
        name: String = certification?.name ?: defaultName.orEmpty(),
        note: String? = certification?.note,
        signature: String? = certification?.signature,
    ) = onChange(InspectionV2.Certification(status = status, name = name, signedAt = nowIso(), note = note?.ifBlank { null }, signature = signature))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.log_insp_cert_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.log_insp_cert_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(
            "corrected" to stringResource(R.string.log_insp_cert_corrected),
            "not_needed" to stringResource(R.string.log_insp_cert_not_needed),
        ).forEach { (status, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = certification?.status == status, role = Role.RadioButton) { update(status = status) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = certification?.status == status, onClick = null)
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (certification != null) {
            OutlinedTextField(
                value = certification.name,
                onValueChange = { update(name = it) },
                label = { Text(stringResource(R.string.log_insp_cert_name)) },
                isError = nameMissing,
                supportingText = if (nameMissing) ({ Text(stringResource(R.string.log_insp_cert_name_missing)) }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = certification.note.orEmpty(),
                onValueChange = { update(note = it) },
                label = { Text(stringResource(R.string.log_insp_cert_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.log_insp_cert_signature), style = MaterialTheme.typography.labelLarge)
            SignaturePad(signature = certification.signature, onSignatureChange = { update(signature = it) })
            OutlinedButton(onClick = { onChange(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.log_insp_cert_clear))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Vehicle sketch (web `VehicleSketch.tsx`)
// ---------------------------------------------------------------------------

/**
 * Top-down vehicle outline with tappable zones (front at top). Tapping a zone writes its id to
 * `<itemId>__loc`; tapping the selected zone clears it. Zones are exposed to TalkBack as custom
 * actions.
 */
@Composable
fun VehicleSketch(
    assetType: String?,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zones = remember(assetType) { InspectionValidation.sketchZones(InspectionValidation.hasBed(assetType)) }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val body = MaterialTheme.colorScheme.surfaceVariant
    val labels = zones.associate { it.id to (inspectionLocationLabel(it.id) ?: it.id) }
    val selectedLabel = selected?.let { inspectionLocationLabel(it) ?: it } ?: stringResource(R.string.log_inspection_defect_location_none)
    val sketchCd = stringResource(R.string.log_insp_sketch_cd, selectedLabel)
    val zoneCdTemplate = stringResource(R.string.log_insp_sketch_zone_cd)
    Canvas(
        modifier = modifier
            .width(200.dp)
            .aspectRatio(InspectionValidation.SKETCH_W / InspectionValidation.SKETCH_H)
            .semantics {
                contentDescription = sketchCd
                stateDescription = selectedLabel
                customActions = zones.map { z ->
                    CustomAccessibilityAction(String.format(zoneCdTemplate, labels[z.id])) {
                        onSelect(if (selected == z.id) null else z.id); true
                    }
                }
            }
            .pointerInput(zones, selected) {
                detectTapGestures { pos ->
                    val sx = pos.x / size.width * InspectionValidation.SKETCH_W
                    val sy = pos.y / size.height * InspectionValidation.SKETCH_H
                    val hit = InspectionValidation.hitZone(zones, sx, sy) ?: return@detectTapGestures
                    onSelect(if (hit == selected) null else hit)
                }
            },
    ) {
        val k = size.width / InspectionValidation.SKETCH_W
        fun rectOf(z: InspectionValidation.Zone) = Pair(Offset(z.x * k, z.y * k), Size(z.w * k, z.h * k))
        // Body outline.
        drawRoundRect(
            color = body,
            topLeft = Offset(26f * k, 4f * k),
            size = Size(68f * k, 212f * k),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f * k),
        )
        zones.asReversed().forEach { z ->
            val (o, s) = rectOf(z)
            val isWheel = z.id in InspectionValidation.WHEEL_CORNERS
            if (z.id == selected) {
                drawRect(primary.copy(alpha = 0.45f), o, s)
            } else if (isWheel) {
                drawRoundRect(Color(0xFF424242), o, s, androidx.compose.ui.geometry.CornerRadius(4f * k))
            }
            drawRect(outline.copy(alpha = 0.6f), o, s, style = Stroke(width = 1.dp.toPx()))
        }
    }
}
