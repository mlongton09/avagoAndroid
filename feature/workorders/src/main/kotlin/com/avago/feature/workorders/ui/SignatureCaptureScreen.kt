package com.avago.feature.workorders.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.workorders.R
import com.avago.feature.workorders.viewmodel.SignatureCaptureViewModel
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureCaptureScreen(
    woId: String,
    onBack: () -> Unit,
    onSaved: (photoUrl: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignatureCaptureViewModel = hiltViewModel(),
) {
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val savedPhotoUrl by viewModel.savedPhotoUrl.collectAsStateWithLifecycle()

    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(savedPhotoUrl) {
        savedPhotoUrl?.let {
            viewModel.onSavedHandled()
            onSaved(it)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wo_detail_back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.signature_capture_title)) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.signature_capture_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                currentStroke = currentStroke + change.position
                            },
                            onDragEnd = {
                                if (currentStroke.isNotEmpty()) {
                                    strokes.add(currentStroke)
                                }
                                currentStroke = emptyList()
                            },
                            onDragCancel = {
                                currentStroke = emptyList()
                            },
                        )
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    (strokes + listOf(currentStroke)).forEach { stroke ->
                        drawStroke(stroke)
                    }
                }
                if (strokes.isEmpty() && currentStroke.isEmpty()) {
                    Text(
                        text = stringResource(R.string.signature_capture_hint),
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.wo_cancel))
                }
                OutlinedButton(
                    onClick = {
                        strokes.clear()
                        currentStroke = emptyList()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.signature_capture_clear))
                }
                Button(
                    onClick = {
                        val bytes = createSignaturePngBytes(strokes, canvasSize)
                        if (bytes != null) {
                            viewModel.saveSignature(bytes)
                        }
                    },
                    enabled = strokes.isNotEmpty() && !isSaving && canvasSize != IntSize.Zero,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.signature_capture_save))
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(points: List<Offset>) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(
            color = Color.Black,
            radius = 3f,
            center = points.first(),
        )
        return
    }
    points.zipWithNext().forEach { (start, end) ->
        drawLine(
            color = Color.Black,
            start = start,
            end = end,
            strokeWidth = 6f,
            cap = StrokeCap.Round,
        )
    }
}

private fun createSignaturePngBytes(strokes: List<List<Offset>>, canvasSize: IntSize): ByteArray? {
    if (strokes.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return null

    val bitmap = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    strokes.forEach { stroke ->
        if (stroke.isEmpty()) return@forEach
        if (stroke.size == 1) {
            canvas.drawCircle(stroke.first().x, stroke.first().y, 3f, paint.apply { style = Paint.Style.FILL })
            paint.style = Paint.Style.STROKE
        } else {
            val path = AndroidPath().apply {
                moveTo(stroke.first().x, stroke.first().y)
                stroke.drop(1).forEach { point ->
                    lineTo(point.x, point.y)
                }
            }
            canvas.drawPath(path, paint)
        }
    }

    return ByteArrayOutputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    }
}
