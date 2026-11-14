package com.avago.feature.log.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avago.feature.log.R
import com.avago.feature.log.model.LogPacks
import com.avago.feature.log.model.PositionReadings
import com.avago.feature.log.model.PositionReadings.ReadingState
import com.avago.feature.log.model.PositionReadings.WheelState
import com.avago.feature.log.model.VehicleSketchModel
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Per-position readings around the vehicle sketch (web `PositionReadings.tsx`)
// ---------------------------------------------------------------------------

/** Side margin beside the sketch for the per-wheel "FL ✓" labels. */
private val LABEL_MARGIN = 40.dp
private const val GLYPH_OK = "✓"
private const val GLYPH_OUT = "!"

/** Localized full position name ("Front left"); falls back to the short code. */
@Composable
internal fun positionFullName(pos: String): String = when (pos) {
    "fl" -> stringResource(R.string.log_readings_pos_fl)
    "fr" -> stringResource(R.string.log_readings_pos_fr)
    "rl" -> stringResource(R.string.log_readings_pos_rl)
    "rr" -> stringResource(R.string.log_readings_pos_rr)
    "rlo" -> stringResource(R.string.log_readings_pos_rlo)
    "rli" -> stringResource(R.string.log_readings_pos_rli)
    "rro" -> stringResource(R.string.log_readings_pos_rro)
    "rri" -> stringResource(R.string.log_readings_pos_rri)
    "spare" -> stringResource(R.string.log_readings_pos_spare)
    else -> pos.uppercase()
}

/** Localized strings a readings panel needs, resolved once per composition. */
internal class ReadingsText(
    val names: Map<String, String>,
    val short: Map<String, String>,
    val metricLabels: Map<String, String>,
    val ok: String,
    val out: String,
    val noReading: String,
    val selected: String,
) {
    fun stateLabel(s: ReadingState?): String? = when (s) {
        ReadingState.OK -> ok
        ReadingState.OUT -> out
        null -> null
    }

    /** "Front left, Tread depth 6 /32", Pressure 32 psi, OK" (TalkBack, web aria parity). */
    fun describe(panel: PositionReadings.Panel, pos: String): String {
        val parts = mutableListOf(names[pos] ?: pos)
        val readings = panel.specs.mapNotNull { spec ->
            val v = panel.metrics.firstOrNull { it.key == spec.key }?.values?.get(pos) ?: return@mapNotNull null
            "${metricLabels[spec.key] ?: spec.key} ${LogPacks.formatNumber(v)} ${spec.unit}".trim()
        }
        if (panel.selected?.contains(pos) == true) parts += selected
        if (readings.isEmpty()) parts += noReading else parts += readings
        stateLabel(panel.state(pos))?.let { parts += it }
        return parts.joinToString(", ")
    }

    /** Description of one sketch wheel target (duals / shared wheels list every position). */
    fun describeCorners(panel: PositionReadings.Panel, corners: List<String>): String {
        val positions = corners.flatMap { PositionReadings.positionsAt(it, panel.positions) }
        if (positions.isEmpty()) return corners.joinToString(" / ") { names[it] ?: it } + ", " + noReading
        return positions.joinToString("; ") { describe(panel, it) }
    }
}

@Composable
internal fun rememberReadingsText(pack: PositionReadings.PackKind): ReadingsText {
    val positions = LogPacks.TIRE_POSITIONS
    val names = positions.associateWith { positionFullName(it) }
    val short = positions.associateWith { tirePositionLabel(it) }
    val metricLabels = when (pack) {
        PositionReadings.PackKind.TIRE -> mapOf(
            "tread" to stringResource(R.string.log_pack_f_tire_tread),
            "psi" to stringResource(R.string.log_pack_f_tire_psi),
        )
        PositionReadings.PackKind.BRAKE -> mapOf(
            "pad" to stringResource(R.string.log_pack_f_brake_pad_mm),
            "rotor" to stringResource(R.string.log_pack_f_brake_rotor_mm),
        )
    }
    val ok = stringResource(R.string.log_readings_state_ok)
    val out = stringResource(R.string.log_readings_state_out)
    val none = stringResource(R.string.log_readings_no_reading)
    val sel = stringResource(R.string.log_readings_selected)
    return remember(names, short, metricLabels, ok, out, none, sel) {
        ReadingsText(names, short, metricLabels, ok, out, none, sel)
    }
}

@Composable
private fun okColor() = MaterialTheme.colorScheme.primary

@Composable
private fun outColor() = MaterialTheme.colorScheme.error

/**
 * Top-down sketch in "readings" mode: each tinted wheel is coloured by [corners] (web
 * `VehicleSketch states`) and labelled beside the outline with its short position code plus a
 * ✓ / ! glyph, so colour is never the only cue. When [onWheelTap] is set every wheel is a ≥48dp
 * button (also for TalkBack) that jumps to the matching inputs.
 */
@Composable
internal fun ReadingsSketch(
    assetType: String?,
    panel: PositionReadings.Panel,
    text: ReadingsText,
    height: Dp,
    modifier: Modifier = Modifier,
    onWheelTap: ((String) -> Unit)? = null,
) {
    val kind = remember(assetType) { VehicleSketchModel.kindFor(assetType) }
    val paths = remember(kind) { buildPaths(kind) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val bodyFill = MaterialTheme.colorScheme.surface
    val wheelFill = MaterialTheme.colorScheme.surfaceVariant
    val ok = okColor()
    val out = outColor()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val sketchWidth = height * (VehicleSketchModel.VIEW_W / VehicleSketchModel.VIEW_H)
    val totalWidth = sketchWidth + LABEL_MARGIN * 2
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val action = stringResource(R.string.log_readings_wheel_action)

    fun cornerLabel(corners: List<String>): String {
        val st = PositionReadings.wheelState(corners, panel.corners)
        val glyph = when (st) {
            WheelState.OK -> " $GLYPH_OK"
            WheelState.OUT -> " $GLYPH_OUT"
            else -> ""
        }
        return corners.joinToString("/") { text.short[it] ?: it.uppercase() } + glyph
    }

    Box(
        modifier = modifier
            .size(totalWidth, height)
            .onSizeChanged { boxSize = it },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = SketchTransform(size.width, size.height, 1f, Offset.Zero)
            if (t.scale <= 0f) return@Canvas
            withTransform({
                translate(t.originX, t.originY)
                scale(t.scale, t.scale, pivot = Offset.Zero)
            }) {
                paths.wheels.forEach { w ->
                    val st = PositionReadings.wheelState(w.corners, panel.corners)
                    val (fill, stroke, sw) = when (st) {
                        null -> Triple(wheelFill.copy(alpha = 0.6f), lineColor.copy(alpha = lineColor.alpha * 0.6f), 1.6f)
                        WheelState.OK -> Triple(lerp(bodyFill, ok, 0.35f), ok, 2f)
                        WheelState.OUT -> Triple(lerp(bodyFill, out, 0.4f), out, 2.6f)
                        WheelState.EMPTY -> Triple(wheelFill, lineColor, 1.6f)
                    }
                    drawRoundRect(fill, Offset(w.x, w.y), Size(w.w, w.h), CornerRadius(w.rx))
                    drawRoundRect(stroke, Offset(w.x, w.y), Size(w.w, w.h), CornerRadius(w.rx), style = Stroke(sw))
                    w.treads().forEach { (x1, y, x2) ->
                        drawLine(lineColor.copy(alpha = 0.6f), Offset(x1, y), Offset(x2, y), strokeWidth = 0.8f, cap = StrokeCap.Round)
                    }
                }
                val bodyStroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                paths.body.forEach { (p, filled) ->
                    if (filled) drawPath(p, bodyFill)
                    drawPath(p, lineColor, style = bodyStroke)
                }
                paths.detail.forEach { (p, _) ->
                    drawPath(p, lineColor.copy(alpha = 0.55f), style = Stroke(width = 1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            // Labels beside each tinted wheel (screen space, outside the outline).
            val seen = HashSet<String>()
            paths.wheels.filter { it.corners.isNotEmpty() }.forEach { w ->
                val key = w.corners.joinToString()
                if (!seen.add(key)) return@forEach
                val st = PositionReadings.wheelState(w.corners, panel.corners)
                val color = when (st) {
                    WheelState.OUT -> out
                    else -> labelColor
                }
                val measured = textMeasurer.measure(cornerLabel(w.corners), labelStyle.copy(color = color))
                val wheelsAtCorner = paths.wheels.filter { it.corners.joinToString() == key }
                val cy = t.toScreen(0f, wheelsAtCorner.minOf { it.y } + (wheelsAtCorner.maxOf { it.y + it.h } - wheelsAtCorner.minOf { it.y }) / 2f).y
                val left = w.corners.first().getOrNull(1) == 'l' && w.corners.size == 1
                val gap = 4.dp.toPx()
                val x = if (left) {
                    t.toScreen(wheelsAtCorner.minOf { it.x }, 0f).x - gap - measured.size.width
                } else {
                    t.toScreen(wheelsAtCorner.maxOf { it.x + it.w }, 0f).x + gap
                }
                drawText(measured, topLeft = Offset(x.coerceIn(0f, size.width - measured.size.width), cy - measured.size.height / 2f))
            }
        }

        // Wheel targets: ≥48dp, extended into the label margin; semantics for TalkBack.
        if (boxSize.width > 0) {
            val t = SketchTransform(boxSize.width.toFloat(), boxSize.height.toFloat(), 1f, Offset.Zero)
            val minView = with(density) { SKETCH_MIN_TOUCH.toPx() } / t.scale
            val targets = remember(kind, minView) { PositionReadings.wheelTargets(kind, minView) }
            val marginPx = with(density) { LABEL_MARGIN.toPx() }
            targets.forEach { tg ->
                val desc = text.describeCorners(panel, tg.corners)
                val tl = t.toScreen(tg.x, tg.y)
                val left = tg.corners.size == 1 && tg.corner.getOrNull(1) == 'l'
                val right = tg.corners.size == 1 && tg.corner.getOrNull(1) == 'r'
                val x0 = if (left) (tl.x - marginPx).coerceAtLeast(0f) else tl.x
                val x1 = (tl.x + tg.w * t.scale).let { if (right) (it + marginPx).coerceAtMost(boxSize.width.toFloat()) else it }
                val target = Modifier
                    .offset(with(density) { x0.toDp() }, with(density) { tl.y.toDp() })
                    .size(with(density) { (x1 - x0).toDp() }, with(density) { (tg.h * t.scale).toDp() })
                val tap = onWheelTap
                Box(
                    if (tap != null) {
                        target.clickable(onClickLabel = action, role = Role.Button) { tap(tg.corner) }
                            .semantics { contentDescription = desc }
                    } else {
                        target.semantics { contentDescription = desc }
                    },
                )
            }
        }
    }
}

/**
 * Editable readings panel (web `PositionReadings`): wide screens lay positions out left /
 * sketch / right; phones show the sketch on top (tap a wheel to jump to its inputs) and a
 * two-column grid keeping left / right sides. Values are written through [onValue] into the
 * existing form state (`packMaps`), so the offline draft / sync flow is unchanged.
 */
@Composable
internal fun PositionReadingsPanel(
    assetType: String?,
    panel: PositionReadings.Panel,
    values: Map<String, Map<String, String>>,
    onValue: (attrKey: String, pos: String, value: String) -> Unit,
    onToggle: ((String) -> Unit)? = null,
) {
    val text = rememberReadingsText(panel.pack)
    val layout = panel.layout
    val focus = remember(panel.positions) { panel.positions.associateWith { FocusRequester() } }
    val bring = remember(panel.positions) { panel.positions.associateWith { BringIntoViewRequester() } }
    val scope = rememberCoroutineScope()
    val jump: (String) -> Unit = { corner ->
        PositionReadings.positionsAt(corner, panel.positions).firstOrNull()?.let { pos ->
            scope.launch { bring[pos]?.bringIntoView() }
            runCatching { focus[pos]?.requestFocus() }
        }
    }

    @Composable
    fun Cell(pos: String, modifier: Modifier) {
        ReadingCell(
            pos = pos,
            panel = panel,
            text = text,
            values = values,
            onValue = onValue,
            onToggle = onToggle,
            focusRequester = focus.getValue(pos),
            modifier = modifier.bringIntoViewRequester(bring.getValue(pos)),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            panel.specs.joinToString(" · ") { "${text.metricLabels[it.key]} (${it.unit})" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 560.dp
            if (wide) {
                val sketchH = (layout.rows * 150).coerceIn(220, 320).dp
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            layout.left.forEach { Cell(it, Modifier.fillMaxWidth()) }
                        }
                        SketchWithFront(assetType, panel, text, sketchH, jump)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            layout.right.forEach { Cell(it, Modifier.fillMaxWidth()) }
                        }
                    }
                    if (layout.spare) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Cell(PositionReadings.SPARE, Modifier.weight(1f))
                            Box(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SketchWithFront(assetType, panel, text, 200.dp, jump)
                    }
                    for (i in 0 until layout.rows) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            layout.left.getOrNull(i)?.let { Cell(it, Modifier.weight(1f)) } ?: Box(Modifier.weight(1f))
                            layout.right.getOrNull(i)?.let { Cell(it, Modifier.weight(1f)) } ?: Box(Modifier.weight(1f))
                        }
                    }
                    if (layout.spare) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Cell(PositionReadings.SPARE, Modifier.weight(1f))
                            Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SketchWithFront(
    assetType: String?,
    panel: PositionReadings.Panel,
    text: ReadingsText,
    height: Dp,
    onWheelTap: ((String) -> Unit)?,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.log_readings_front).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReadingsSketch(assetType, panel, text, height, onWheelTap = onWheelTap)
    }
}

/** Status line under a position: icon + text (never colour alone). */
@Composable
private fun StatusLine(state: ReadingState?, text: ReadingsText) {
    val label = text.stateLabel(state) ?: return
    val color = if (state == ReadingState.OUT) outColor() else okColor()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            if (state == ReadingState.OUT) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (state == ReadingState.OUT) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadingCell(
    pos: String,
    panel: PositionReadings.Panel,
    text: ReadingsText,
    values: Map<String, Map<String, String>>,
    onValue: (String, String, String) -> Unit,
    onToggle: ((String) -> Unit)?,
    focusRequester: FocusRequester,
    modifier: Modifier,
) {
    val state = panel.state(pos)
    val name = text.names[pos] ?: pos
    val border = if (state == ReadingState.OUT) BorderStroke(2.dp, outColor()) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = border,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onToggle != null) {
                val checked = panel.selected?.contains(pos) == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(pos) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Text(name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.heightIn(min = 24.dp).semantics { heading() },
                )
            }
            panel.specs.forEachIndexed { idx, spec ->
                val label = text.metricLabels[spec.key] ?: spec.key
                val ms = panel.metricState(spec.key, pos)
                val cd = "$label (${spec.unit}) — $name"
                OutlinedTextField(
                    value = values[spec.attrKey]?.get(pos).orEmpty(),
                    onValueChange = { onValue(spec.attrKey, pos, it) },
                    label = { Text(label, maxLines = 1) },
                    suffix = { Text(spec.unit) },
                    isError = ms == ReadingState.OUT,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (idx == 0) Modifier.focusRequester(focusRequester) else Modifier)
                        .semantics {
                            contentDescription = cd
                            text.stateLabel(ms)?.let { stateDescription = it }
                        },
                )
            }
            StatusLine(state, text)
        }
    }
}

/**
 * Read-only readings map for log detail: coloured, labelled sketch plus one line per position
 * with data (values + status icon / text), which is also the text alternative for the sketch.
 */
@Composable
internal fun PositionReadingsMap(assetType: String?, panel: PositionReadings.Panel, modifier: Modifier = Modifier) {
    val text = rememberReadingsText(panel.pack)
    val rows = panel.withData()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SketchWithFront(assetType, panel, text, 180.dp, onWheelTap = null)
        }
        rows.forEach { pos ->
            val state = panel.state(pos)
            val values = panel.specs.mapNotNull { spec ->
                panel.metrics.firstOrNull { it.key == spec.key }?.values?.get(pos)?.let {
                    "${text.metricLabels[spec.key]} ${LogPacks.formatNumber(it)} ${spec.unit}".trim()
                }
            }
            val desc = text.describe(panel, pos)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .semantics(mergeDescendants = true) { contentDescription = desc },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text.names[pos] ?: pos,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(120.dp),
                )
                Text(
                    if (values.isEmpty()) text.noReading else values.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (values.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    modifier = Modifier.weight(1f),
                )
                StatusLine(state, text)
            }
        }
    }
}
