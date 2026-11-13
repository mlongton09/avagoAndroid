package com.avago.feature.log.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.avago.feature.log.R
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.VehicleSketchModel
import com.avago.feature.log.model.VehicleSketchModel.PathCmd
import com.avago.feature.log.model.VehicleSketchModel.PlacedMark
import com.avago.feature.log.model.VehicleSketchModel.Shape
import com.avago.feature.log.model.VehicleSketchModel.SketchMark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Vehicle sketch / damage map (web `VehicleSketch.tsx` + `DefectExtras` LocationPicker)
// ---------------------------------------------------------------------------

private const val MAX_ZOOM = 4f
private val SKETCH_MIN_TOUCH = 48.dp

/** Pin colours: severity tint, plus the rental baseline (grey) / new (red) variants. */
private val PIN_MINOR = Color(0xFFFF9800)
private val PIN_MAJOR = Color(0xFFF44336)
private val PIN_OOS = Color(0xFF8E0000)
private val PIN_NEW = Color(0xFFD32F2F)

private enum class PinShape { CIRCLE, SQUARE, OCTAGON }

private fun pinShape(severity: String) = when (severity) {
    InspectionV2.SEV_MINOR -> PinShape.CIRCLE
    InspectionV2.SEV_OUT_OF_SERVICE -> PinShape.OCTAGON
    else -> PinShape.SQUARE
}

private fun pinFill(severity: String) = when (severity) {
    InspectionV2.SEV_MINOR -> PIN_MINOR
    InspectionV2.SEV_OUT_OF_SERVICE -> PIN_OOS
    else -> PIN_MAJOR
}

/** viewBox ↔ canvas mapping for a fitted, zoomed and panned sketch. */
private class SketchTransform(val width: Float, val height: Float, val zoom: Float, val pan: Offset) {
    val scale = VehicleSketchModel.fitScale(width, height) * zoom
    val originX = width / 2f + pan.x - VehicleSketchModel.VIEW_W / 2f * scale
    val originY = height / 2f + pan.y - VehicleSketchModel.VIEW_H / 2f * scale
    fun toScreen(x: Float, y: Float) = Offset(originX + x * scale, originY + y * scale)
    fun toView(p: Offset): Offset {
        val (x, y) = VehicleSketchModel.toView(p.x, p.y, scale, originX, originY)
        return Offset(x, y)
    }
}

private fun clampPan(pan: Offset, zoom: Float, w: Float, h: Float): Offset {
    val s = VehicleSketchModel.fitScale(w, h) * zoom
    val mx = max(0f, (VehicleSketchModel.VIEW_W * s - w) / 2f)
    val my = max(0f, (VehicleSketchModel.VIEW_H * s - h) / 2f)
    return Offset(pan.x.coerceIn(-mx, mx), pan.y.coerceIn(-my, my))
}

private fun pathOf(cmds: List<PathCmd>): Path = Path().apply {
    cmds.forEach { c ->
        when (c) {
            is PathCmd.MoveTo -> moveTo(c.x, c.y)
            is PathCmd.LineTo -> lineTo(c.x, c.y)
            is PathCmd.QuadTo -> quadraticTo(c.cx, c.cy, c.x, c.y)
            PathCmd.Close -> close()
        }
    }
}

private fun shapePath(s: Shape): Path = when (s) {
    is Shape.Path -> pathOf(VehicleSketchModel.parsePath(s.d))
    is Shape.Rect -> Path().apply { addRoundRect(RoundRect(Rect(s.x, s.y, s.x + s.w, s.y + s.h), CornerRadius(s.rx, s.rx))) }
    is Shape.Circle -> Path().apply { addOval(Rect(s.cx - s.r, s.cy - s.r, s.cx + s.r, s.cy + s.r)) }
    is Shape.Ellipse -> Path().apply { addOval(Rect(s.cx - s.rx, s.cy - s.ry, s.cx + s.rx, s.cy + s.ry)) }
}

private class SketchPaths(
    val body: List<Pair<Path, Boolean>>, // path, filled
    val detail: List<Pair<Path, Boolean>>, // path, dashed
    val wheels: List<VehicleSketchModel.Wheel>,
)

private fun buildPaths(kind: VehicleSketchModel.Kind): SketchPaths {
    val o = VehicleSketchModel.outline(kind)
    return SketchPaths(
        body = o.body.map { shapePath(it) to !(it is Shape.Path && it.open) },
        detail = o.detail.map { shapePath(it) to (it is Shape.Rect && it.dashed) },
        wheels = o.wheels,
    )
}

/** Localized "Mark 3: Front left wheel, Major, Brakes" (+ new / pre-existing). */
@Composable
private fun pinDescription(mark: SketchMark, baseline: Boolean, hasBaseline: Boolean): String {
    val zone = inspectionLocationLabel(mark.zone) ?: mark.zone
    val base = stringResource(R.string.log_insp_sketch_pin_cd, mark.number, zone, inspectionSeverityName(mark.severity), mark.label)
    return when {
        baseline -> "$base. ${stringResource(R.string.log_insp_sketch_baseline)}"
        hasBaseline && mark.isNew -> "$base. ${stringResource(R.string.log_insp_sketch_new)}"
        else -> base
    }
}

private fun DrawScope.drawPinShape(center: Offset, r: Float, shape: PinShape, fill: Color, stroke: Color, strokeW: Float, dashed: Boolean) {
    val path = Path()
    when (shape) {
        PinShape.CIRCLE -> path.addOval(Rect(center.x - r, center.y - r, center.x + r, center.y + r))
        PinShape.SQUARE -> {
            val h = r * 0.9f
            path.addRoundRect(RoundRect(Rect(center.x - h, center.y - h, center.x + h, center.y + h), CornerRadius(r * 0.3f)))
        }
        PinShape.OCTAGON -> {
            for (k in 0 until 8) {
                val a = (PI / 8 + k * PI / 4).toFloat()
                val px = center.x + r * 1.05f * cos(a)
                val py = center.y + r * 1.05f * sin(a)
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
        }
    }
    drawPath(path, fill, style = Fill)
    drawPath(
        path,
        stroke,
        style = Stroke(width = strokeW, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(strokeW * 2f, strokeW * 1.5f)) else null),
    )
}

/**
 * Top-down vehicle sketch with damage pins. Read-only when [onZoneTap] is null.
 *
 * - [marks]: current pins (numbered); [baseline]: optional read-only reference set (rental
 *   check-out) drawn grey/hollow/dashed — current marks flagged [SketchMark.isNew] get a red "NEW" tag.
 * - [activeMarkId]: the only pin that can be dragged / edited (null = every current pin).
 * - Gestures: tap a zone → [onZoneTap]; drag a pin → [onMarkMove] with the drop zone; tap or
 *   long-press a pin → [onMarkEdit]; pinch to zoom, one-finger pan while zoomed.
 * - TalkBack: every zone is a radio button, every pin has a description plus Edit / Move to … actions.
 */
@Composable
fun VehicleSketchView(
    assetType: String?,
    marks: List<SketchMark>,
    modifier: Modifier = Modifier,
    baseline: List<SketchMark> = emptyList(),
    selectedZone: String? = null,
    activeMarkId: String? = null,
    onZoneTap: ((String) -> Unit)? = null,
    onMarkMove: ((SketchMark, String) -> Unit)? = null,
    onMarkEdit: ((SketchMark) -> Unit)? = null,
    zoomable: Boolean = true,
) {
    val kind = remember(assetType) { VehicleSketchModel.kindFor(assetType) }
    val paths = remember(kind) { buildPaths(kind) }
    val zones = remember(kind) { VehicleSketchModel.zones(kind) }
    val layout = remember(kind, marks, baseline) { VehicleSketchModel.layout(kind, marks, baseline) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(kind) { mutableFloatStateOf(1f) }
    var pan by remember(kind) { mutableStateOf(Offset.Zero) }
    /** Pin being dragged: (mark id, finger position in canvas px). */
    var drag by remember { mutableStateOf<Pair<String, Offset>?>(null) }

    val canDrag: (PlacedMark) -> Boolean = { p -> !p.baseline && onMarkMove != null && (activeMarkId == null || p.mark.id == activeMarkId) }
    val canEdit: (PlacedMark) -> Boolean = { p -> !p.baseline && onMarkEdit != null && (activeMarkId == null || p.mark.id == activeMarkId) }
    val latestPlaced by rememberUpdatedState(layout.placed)
    val latestZones by rememberUpdatedState(zones)
    val latestCanDrag by rememberUpdatedState(canDrag)
    val latestCanEdit by rememberUpdatedState(canEdit)
    val latestZoneTap by rememberUpdatedState(onZoneTap)
    val latestMove by rememberUpdatedState(onMarkMove)
    val latestEdit by rememberUpdatedState(onMarkEdit)
    val latestZoomable by rememberUpdatedState(zoomable)

    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val bodyFill = MaterialTheme.colorScheme.surface
    val wheelFill = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val numberStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val tagStyle = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
    val newTag = stringResource(R.string.log_insp_sketch_new_tag)
    val hasBaseline = baseline.isNotEmpty()

    fun transform() = SketchTransform(boxSize.width.toFloat(), boxSize.height.toFloat(), zoom, pan)

    fun applyZoom(centroid: Offset, zoomChange: Float, panChange: Offset) {
        val w = boxSize.width.toFloat(); val h = boxSize.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        val k = newZoom / zoom
        val c = centroid - Offset(w / 2f, h / 2f)
        pan = clampPan(c - (c - pan) * k + panChange, newZoom, w, h)
        zoom = newZoom
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { boxSize = it }
            .pointerInput(kind) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val t = transform()
                    if (t.scale <= 0f) return@awaitEachGesture
                    val v = t.toView(down.position)
                    val hitR = max(VehicleSketchModel.PIN_R, (SKETCH_MIN_TOUCH.toPx() / 2f) / t.scale)
                    val pin = VehicleSketchModel.hitMark(latestPlaced, v.x, v.y, hitR)
                    val draggable = pin?.takeIf { latestCanDrag(it) }
                    val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var result = "up"
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.count { it.pressed } > 1) {
                                result = "multi"; break
                            }
                            val c = ev.changes.firstOrNull { it.id == down.id }
                            if (c == null || !c.pressed) {
                                result = "up"; break
                            }
                            if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                if (draggable != null || zoom > 1f) c.consume()
                                result = "move"; break
                            }
                        }
                        result
                    }
                    when (outcome) {
                        null -> {
                            // Long press: edit the pin under the finger.
                            if (pin != null && latestCanEdit(pin)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                latestEdit?.invoke(pin.mark)
                            }
                            do {
                                val ev = awaitPointerEvent()
                                ev.changes.forEach { it.consume() }
                            } while (ev.changes.any { it.pressed })
                        }
                        "up" -> {
                            if (pin != null && latestCanEdit(pin)) {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                latestEdit?.invoke(pin.mark)
                            } else {
                                val tap = latestZoneTap
                                val zone = VehicleSketchModel.hitZone(latestZones, v.x, v.y)
                                if (tap != null && zone != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    tap(zone)
                                }
                            }
                        }
                        "move" -> {
                            if (draggable != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                var pos = down.position
                                var hover: String? = draggable.mark.zone
                                drag = draggable.mark.id to pos
                                try {
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!c.pressed) break
                                        pos = c.position
                                        c.consume()
                                        drag = draggable.mark.id to pos
                                        val pv = transform().toView(pos)
                                        val z = VehicleSketchModel.hitZone(latestZones, pv.x, pv.y)
                                        if (z != hover) {
                                            hover = z
                                            if (z != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                        }
                                    }
                                } finally {
                                    drag = null
                                }
                                val pv = transform().toView(pos)
                                val target = VehicleSketchModel.hitZone(latestZones, pv.x, pv.y)
                                if (target != null && target != draggable.mark.zone) {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    latestMove?.invoke(draggable.mark, target)
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                }
                            } else if (zoom > 1f && latestZoomable) {
                                do {
                                    val ev = awaitPointerEvent()
                                    val centroid = ev.calculateCentroid()
                                    if (centroid != Offset.Unspecified) applyZoom(centroid, ev.calculateZoom(), ev.calculatePan())
                                    ev.changes.forEach { it.consume() }
                                } while (ev.changes.any { it.pressed })
                            }
                        }
                        "multi" -> if (latestZoomable) {
                            do {
                                val ev = awaitPointerEvent()
                                val centroid = ev.calculateCentroid()
                                if (centroid != Offset.Unspecified) applyZoom(centroid, ev.calculateZoom(), ev.calculatePan())
                                ev.changes.forEach { it.consume() }
                            } while (ev.changes.any { it.pressed })
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = SketchTransform(size.width, size.height, zoom, pan)
            if (t.scale <= 0f) return@Canvas
            val dragging = drag
            val hoverZone = dragging?.let { (_, p) -> t.toView(p).let { VehicleSketchModel.hitZone(zones, it.x, it.y) } }
            withTransform({
                translate(t.originX, t.originY)
                scale(t.scale, t.scale, pivot = Offset.Zero)
            }) {
                // Wheels (behind the body, like the SVG).
                paths.wheels.forEach { w ->
                    val alpha = if (w.corners.isEmpty()) 0.6f else 1f
                    drawRoundRect(wheelFill.copy(alpha = alpha), Offset(w.x, w.y), Size(w.w, w.h), CornerRadius(w.rx))
                    drawRoundRect(lineColor.copy(alpha = lineColor.alpha * alpha), Offset(w.x, w.y), Size(w.w, w.h), CornerRadius(w.rx), style = Stroke(1.6f))
                    w.treads().forEach { (x1, y, x2) ->
                        drawLine(lineColor.copy(alpha = 0.6f), Offset(x1, y), Offset(x2, y), strokeWidth = 0.8f, cap = StrokeCap.Round)
                    }
                }
                val bodyStroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                paths.body.forEach { (p, filled) ->
                    if (filled) drawPath(p, bodyFill)
                    drawPath(p, lineColor, style = bodyStroke)
                }
                val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
                paths.detail.forEach { (p, dashed) ->
                    drawPath(
                        p,
                        lineColor.copy(alpha = 0.55f),
                        style = Stroke(width = 1f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (dashed) dash else null),
                    )
                }
                // Selected / drop-target zones (web `.zone[aria-checked]`).
                zones.forEach { z ->
                    val isSel = z.zone == selectedZone && dragging == null
                    val isHover = z.zone == hoverZone
                    if (isSel || isHover) {
                        drawRoundRect(accent.copy(alpha = if (isHover) 0.22f else 0.3f), Offset(z.x, z.y), Size(z.w, z.h), CornerRadius(z.rx))
                        drawRoundRect(accent, Offset(z.x, z.y), Size(z.w, z.h), CornerRadius(z.rx), style = Stroke(1.5f))
                    }
                }
            }

            // Pins in screen space so they stay finger-sized at any zoom.
            val r = (VehicleSketchModel.PIN_R * t.scale).coerceIn(9.dp.toPx(), 15.dp.toPx())
            val strokeW = 1.5.dp.toPx()
            val ordered = layout.placed.sortedBy { if (it.baseline) 0 else 1 }
            ordered.forEach { p ->
                val isDragged = dragging?.first == p.mark.id && !p.baseline
                val center = if (isDragged) dragging.second else t.toScreen(p.x, p.y)
                val rr = if (isDragged) r * 1.25f else r
                val dimmed = activeMarkId != null && !p.baseline && p.mark.id != activeMarkId
                val fill: Color
                val textColor: Color
                if (p.baseline) {
                    fill = surface; textColor = baselineColor
                } else {
                    fill = if (hasBaseline && p.mark.isNew) PIN_NEW else pinFill(p.mark.severity)
                    textColor = if (fill == PIN_MINOR) Color.Black else Color.White
                }
                val a = if (dimmed) 0.55f else 1f
                if (!p.baseline && p.mark.id == activeMarkId) {
                    drawCircle(accent, rr + 4.dp.toPx(), center, style = Stroke(2.5.dp.toPx()))
                }
                drawPinShape(
                    center, rr, pinShape(p.mark.severity),
                    fill.copy(alpha = a),
                    if (p.baseline) baselineColor else surface.copy(alpha = a),
                    strokeW, dashed = p.baseline,
                )
                val label = textMeasurer.measure(p.mark.number.toString(), numberStyle.copy(color = textColor.copy(alpha = a)))
                drawText(label, topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f))
                if (hasBaseline && !p.baseline && p.mark.isNew) {
                    val tag = textMeasurer.measure(newTag, tagStyle)
                    val pad = 2.dp.toPx()
                    val tl = Offset(center.x + rr * 0.4f, center.y - rr - tag.size.height - pad)
                    drawRoundRect(PIN_NEW, tl - Offset(pad, pad / 2f), Size(tag.size.width + pad * 2, tag.size.height + pad), CornerRadius(pad * 2))
                    drawText(tag, topLeft = tl)
                }
            }
        }

        // Accessibility overlay (semantics only — pointer input stays on the parent).
        if (boxSize.width > 0) {
            val t = transform()
            fun Modifier.at(x: Float, y: Float, w: Float, h: Float): Modifier {
                val tl = t.toScreen(x, y)
                return with(density) {
                    this@at.offset(tl.x.toDp(), tl.y.toDp()).size((w * t.scale).toDp(), (h * t.scale).toDp())
                }
            }
            val order = VehicleSketchModel.accessibilityOrder(zones)
            val zoneTap = onZoneTap
            if (zoneTap != null) {
                val actionTemplate = stringResource(R.string.log_insp_sketch_zone_cd)
                order.forEachIndexed { i, z ->
                    val label = inspectionLocationLabel(z.zone) ?: z.zone
                    Box(
                        Modifier.at(z.x, z.y, z.w, z.h).semantics {
                            contentDescription = label
                            role = Role.RadioButton
                            selected = z.zone == selectedZone
                            traversalIndex = i.toFloat()
                            onClick(label = String.format(actionTemplate, label)) { zoneTap(z.zone); true }
                        },
                    )
                }
            }
            val editLabel = stringResource(R.string.log_insp_sketch_edit)
            val moveTemplate = stringResource(R.string.log_insp_sketch_move_to)
            val zoneLabels = order.map { it.zone to (inspectionLocationLabel(it.zone) ?: it.zone) }
            layout.placed.forEachIndexed { i, p ->
                val desc = pinDescription(p.mark, p.baseline, hasBaseline)
                val box = (SKETCH_MIN_TOUCH.value * density.density) / t.scale
                val actions = buildList {
                    if (canEdit(p)) add(CustomAccessibilityAction(editLabel) { onMarkEdit?.invoke(p.mark); true })
                    if (canDrag(p)) zoneLabels.filter { it.first != p.mark.zone }.forEach { (zone, label) ->
                        add(CustomAccessibilityAction(String.format(moveTemplate, label)) { onMarkMove?.invoke(p.mark, zone); true })
                    }
                }
                Box(
                    Modifier.at(p.x - box / 2f, p.y - box / 2f, box, box).semantics {
                        contentDescription = desc
                        traversalIndex = (order.size + i).toFloat()
                        if (actions.isNotEmpty()) customActions = actions
                        if (canEdit(p)) onClick(label = editLabel) { onMarkEdit?.invoke(p.mark); true }
                    },
                )
            }
        }

        if (zoom > 1.01f) {
            TextButton(
                onClick = { zoom = 1f; pan = Offset.Zero },
                modifier = Modifier.align(Alignment.TopEnd).heightIn(min = SKETCH_MIN_TOUCH),
            ) { Text(stringResource(R.string.log_insp_sketch_zoom_reset)) }
        }
    }
}

/** Severity shapes (and baseline / new when comparing) — a non-colour key for the pins. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleSketchLegend(showBaseline: Boolean, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    @Composable
    fun Item(label: String, shape: PinShape, fill: Color, stroke: Color, dashed: Boolean = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(16.dp)) {
                drawPinShape(center, size.minDimension / 2.3f, shape, fill, stroke, 1.dp.toPx(), dashed)
            }
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Item(inspectionSeverityName(InspectionV2.SEV_MINOR), PinShape.CIRCLE, PIN_MINOR, surface)
        Item(inspectionSeverityName(InspectionV2.SEV_MAJOR), PinShape.SQUARE, PIN_MAJOR, surface)
        Item(inspectionSeverityName(InspectionV2.SEV_OUT_OF_SERVICE), PinShape.OCTAGON, PIN_OOS, surface)
        if (showBaseline) {
            Item(stringResource(R.string.log_insp_sketch_baseline), PinShape.CIRCLE, surface, outline, dashed = true)
            Item(stringResource(R.string.log_insp_sketch_new), PinShape.CIRCLE, PIN_NEW, surface)
        }
    }
}

/**
 * Read-only damage map for log detail (and, later, rental check-in comparison): the sketch with
 * numbered pins, the legend and a numbered key. Hidden when there is nothing to show.
 */
@Composable
fun VehicleDamageMap(
    assetType: String?,
    marks: List<SketchMark>,
    modifier: Modifier = Modifier,
    baseline: List<SketchMark> = emptyList(),
) {
    if (marks.isEmpty() && baseline.isEmpty()) return
    val title = stringResource(R.string.log_insp_sketch_title)
    val mapCd = stringResource(R.string.log_insp_sketch_map_cd, marks.size)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            VehicleSketchView(
                assetType = assetType,
                marks = marks,
                baseline = baseline,
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .fillMaxWidth(0.6f)
                    .aspectRatio(VehicleSketchModel.VIEW_W / VehicleSketchModel.VIEW_H)
                    .semantics { contentDescription = mapCd },
            )
        }
        VehicleSketchLegend(showBaseline = baseline.isNotEmpty())
        MarkKey(marks, baseline = false)
        if (baseline.isNotEmpty()) {
            Text(stringResource(R.string.log_insp_sketch_baseline), style = MaterialTheme.typography.labelMedium)
            MarkKey(baseline, baseline = true)
        }
    }
}

@Composable
private fun MarkKey(marks: List<SketchMark>, baseline: Boolean) {
    val newLabel = stringResource(R.string.log_insp_sketch_new)
    val hasNew = !baseline && marks.any { it.isNew } && marks.any { !it.isNew }
    marks.forEach { m ->
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                m.number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (baseline) MaterialTheme.colorScheme.outline else Color.White,
                modifier = Modifier
                    .background(
                        if (baseline) MaterialTheme.colorScheme.surfaceVariant else pinFill(m.severity),
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(m.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(
                        inspectionLocationLabel(m.zone) ?: m.zone,
                        inspectionSeverityName(m.severity),
                        newLabel.takeIf { hasNew && m.isNew },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Full-screen location picker for one defect item (web `LocationPicker`, touch-first):
 * tap an area to place the item's pin, drag the pin to move it, tap / long-press it to change
 * severity or remove it, pinch to zoom. Other located defects are shown dimmed for context.
 * Writes go through [onLocation] (`<itemId>__loc`, "" = clear) and [onSeverity] (`<itemId>__sev`)
 * so they land in the existing draft / offline save path.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleSketchPickerDialog(
    assetType: String?,
    itemId: String,
    itemLabel: String,
    location: String?,
    severity: String,
    otherMarks: List<SketchMark>,
    activeNumber: Int,
    onLocation: (String) -> Unit,
    onSeverity: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var editing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val activeMark = location?.let { SketchMark(itemId, it, severity, itemLabel, activeNumber) }
    val marks = otherMarks.filter { it.id != itemId } + listOfNotNull(activeMark)
    val placedTemplate = stringResource(R.string.log_insp_sketch_placed)
    val movedTemplate = stringResource(R.string.log_insp_sketch_moved)
    val removedText = stringResource(R.string.log_insp_sketch_removed)
    val noneLabel = stringResource(R.string.log_inspection_defect_location_none)
    val zoneLabels = VehicleSketchModel.WHEEL_CORNERS + listOf("front", "rear", "left", "right", "cab", "bed") +
        VehicleSketchModel.OFF_SKETCH_LOCATIONS
    val labels = zoneLabels.associateWith { inspectionLocationLabel(it) ?: it }

    fun place(zone: String) {
        if (zone == location) {
            onLocation("") // web: picking the current value clears it
            status = removedText
        } else {
            onLocation(zone)
            status = String.format(placedTemplate, labels[zone] ?: zone)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.log_insp_sketch_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(itemLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(onClick = onDismiss, modifier = Modifier.heightIn(min = SKETCH_MIN_TOUCH)) {
                        Text(stringResource(R.string.log_insp_sketch_done))
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val wide = maxWidth > maxHeight
                    val controlsMax = maxHeight * 0.45f
                    val sketch: @Composable (Modifier) -> Unit = { m ->
                        Box(m, contentAlignment = Alignment.Center) {
                            VehicleSketchView(
                                assetType = assetType,
                                marks = marks,
                                selectedZone = location,
                                activeMarkId = itemId,
                                onZoneTap = { place(it) },
                                onMarkMove = { _, zone ->
                                    onLocation(zone)
                                    status = String.format(movedTemplate, labels[zone] ?: zone)
                                },
                                onMarkEdit = { editing = true },
                                // The sketch is fitted + centred inside, so it may fill the box.
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    val controls: @Composable (Modifier) -> Unit = { m ->
                        Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.log_insp_sketch_hint), style = MaterialTheme.typography.bodySmall)
                            Text(
                                status ?: (location?.let { labels[it] } ?: noneLabel).let {
                                    "${stringResource(R.string.log_inspection_defect_location)}: $it"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            Text(stringResource(R.string.log_insp_sketch_other_areas), style = MaterialTheme.typography.labelMedium)
                            FlowRow(
                                modifier = Modifier.selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                VehicleSketchModel.OFF_SKETCH_LOCATIONS.forEach { loc ->
                                    FilterChip(
                                        selected = location == loc,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            place(loc)
                                        },
                                        label = { Text(labels[loc] ?: loc) },
                                        modifier = Modifier.heightIn(min = SKETCH_MIN_TOUCH).semantics { role = Role.RadioButton },
                                    )
                                }
                            }
                            VehicleSketchLegend(showBaseline = false)
                            if (location != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { editing = true }, modifier = Modifier.heightIn(min = SKETCH_MIN_TOUCH)) {
                                        Text(stringResource(R.string.log_insp_sketch_edit))
                                    }
                                    TextButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                            onLocation("")
                                            status = removedText
                                        },
                                        modifier = Modifier.heightIn(min = SKETCH_MIN_TOUCH),
                                    ) { Text(stringResource(R.string.log_insp_sketch_remove)) }
                                }
                            }
                        }
                    }
                    if (wide) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            sketch(Modifier.weight(1f).fillMaxSize())
                            controls(Modifier.weight(1f).fillMaxSize())
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sketch(Modifier.fillMaxWidth().weight(1f))
                            controls(Modifier.fillMaxWidth().heightIn(max = controlsMax))
                        }
                    }
                }
            }
        }
    }

    if (editing && location != null) {
        VehicleMarkEditDialog(
            title = "${activeNumber}. $itemLabel · ${labels[location] ?: location}",
            severity = severity,
            onSeverity = {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSeverity(it)
            },
            onRemove = {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                onLocation("")
                status = removedText
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
    LaunchedEffect(location) { if (location == null) editing = false }
}

/** Severity radio list + "Remove mark" for one pin (long-press / tap on the pin). */
@Composable
fun VehicleMarkEditDialog(
    title: String,
    severity: String,
    onSeverity: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_insp_sketch_edit_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                InspectionV2.SEVERITIES.forEach { sev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SKETCH_MIN_TOUCH)
                            .selectable(selected = severity == sev, role = Role.RadioButton, onClick = { onSeverity(sev) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = severity == sev, onClick = null)
                        Canvas(Modifier.padding(horizontal = 8.dp).size(16.dp)) {
                            drawPinShape(center, size.minDimension / 2.3f, pinShape(sev), pinFill(sev), Color.White, 1.dp.toPx(), false)
                        }
                        Text(inspectionSeverityName(sev))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = SKETCH_MIN_TOUCH)) {
                Text(stringResource(R.string.log_insp_sketch_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = SKETCH_MIN_TOUCH)) {
                Text(stringResource(R.string.log_insp_sketch_remove), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
