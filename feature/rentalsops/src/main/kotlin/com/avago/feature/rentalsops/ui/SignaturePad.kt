package com.avago.feature.rentalsops.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.avago.feature.rentalsops.R

/** Captured strokes in pad pixels, plus the pad size and stroke width used to render them. */
data class SignatureCapture(val strokes: List<List<Pair<Float, Float>>>, val width: Int, val height: Int, val strokePx: Float)

/**
 * Canvas signature pad. The pad is always white with black ink (also in dark theme) so what the
 * customer sees matches the stored image. TalkBack users get a state description and the
 * signer-name field remains the accessible alternative.
 */
@Composable
fun SignatureDialog(onDismiss: () -> Unit, onSave: (SignatureCapture) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val strokePx = with(LocalDensity.current) { 3.dp.toPx() }
    val empty = strokes.isEmpty() && current.isEmpty()
    val padDesc = stringResource(R.string.rentalops_signature_pad)
    val stateDesc = stringResource(if (empty) R.string.rentalops_signature_empty else R.string.rentalops_signature_captured)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rentalops_signature)) },
        text = {
            Column {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .onSizeChanged { size = it }
                        .semantics {
                            contentDescription = padDesc
                            stateDescription = stateDesc
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { strokes += listOf(it) })
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current = listOf(it) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    current = current + change.position
                                },
                                onDragEnd = {
                                    if (current.isNotEmpty()) strokes += current
                                    current = emptyList()
                                },
                                onDragCancel = {
                                    if (current.isNotEmpty()) strokes += current
                                    current = emptyList()
                                },
                            )
                        },
                ) {
                    val style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    (strokes + listOf(current)).forEach { s ->
                        if (s.size == 1) {
                            drawCircle(Color.Black, radius = strokePx / 2, center = s[0])
                        } else if (s.size > 1) {
                            val p = Path().apply {
                                moveTo(s[0].x, s[0].y)
                                s.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(p, Color.Black, style = style)
                        }
                    }
                }
                Text(
                    stringResource(R.string.rentalops_signature_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        strokes.clear()
                        current = emptyList()
                    },
                    enabled = !empty,
                    modifier = Modifier.heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentalops_clear)) }
                TextButton(
                    onClick = {
                        onSave(SignatureCapture(strokes.map { s -> s.map { it.x to it.y } }, size.width, size.height, strokePx))
                    },
                    enabled = !empty,
                    modifier = Modifier.heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentalops_save)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentalops_cancel)) }
        },
    )
}
