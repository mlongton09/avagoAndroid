package com.avago.feature.inventory.cyclecounts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.inventory.R
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleCountFloorScreen(
    cycleCountId: String,
    onBack: () -> Unit,
    viewModel: CycleCountFloorViewModel = hiltViewModel(),
) {
    val recentScans by viewModel.recentScans.collectAsStateWithLifecycle()
    val lastScannedBarcode by viewModel.lastScannedBarcode.collectAsStateWithLifecycle()
    val scanFeedback by viewModel.scanFeedback.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show scan feedback as snackbar
    LaunchedEffect(scanFeedback) {
        scanFeedback?.let { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cycle_count_floor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Camera preview (takes most of the available space)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (!hasPermission) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .width(64.dp)
                                .height(64.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.cycle_count_floor_scan_hint),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Camera Permission")
                        }
                    }
                } else {
                    FloorCameraPreview(
                        lifecycleOwner = lifecycleOwner,
                        onBarcode = { barcode ->
                            viewModel.onBarcodeScanned(barcode)
                        },
                    )

                    FloorScannerOverlay(modifier = Modifier.fillMaxSize())

                    Text(
                        text = stringResource(R.string.cycle_count_floor_scan_hint),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp, start = 32.dp, end = 32.dp),
                    )
                }
            }

            // Bottom panel
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Last scanned chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.cycle_count_floor_last_scanned),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (lastScannedBarcode != null) {
                            AssistChip(
                                onClick = {},
                                label = { Text(lastScannedBarcode!!) },
                            )
                        } else {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Recent scans list (last 5, scrollable)
                    if (recentScans.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "Recent scans",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(recentScans) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item.partName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "× ${item.count}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }

                    // Done button
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cycle_count_floor_done))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// CameraX preview + ML Kit barcode analysis
// ---------------------------------------------------------------------------

@Composable
private fun FloorCameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onBarcode: (String) -> Unit,
) {
    val context = LocalContext.current

    // Debounce: avoid firing the same barcode multiple times in quick succession
    var lastBarcodeTime by remember { mutableStateOf(0L) }
    var lastBarcode by remember { mutableStateOf("") }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val barcodeScanner = BarcodeScanning.getClient()
            previewView.tag = barcodeScanner

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            processFloorImage(imageProxy, barcodeScanner) { barcode ->
                                val now = System.currentTimeMillis()
                                // Debounce: same barcode within 1.5s is ignored
                                if (barcode != lastBarcode || now - lastBarcodeTime > 1_500) {
                                    lastBarcode = barcode
                                    lastBarcodeTime = now
                                    onBarcode(barcode)
                                }
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "[CycleCountFloorScreen] CameraX bind failed")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        onRelease = { previewView ->
            (previewView.tag as? com.google.mlkit.vision.barcode.BarcodeScanner)?.close()
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processFloorImage(
    imageProxy: ImageProxy,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onBarcode: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    barcodeScanner.process(image)
        .addOnSuccessListener { barcodes ->
            val value = barcodes.firstOrNull()?.rawValue
            if (!value.isNullOrBlank()) {
                onBarcode(value)
            }
        }
        .addOnFailureListener { e ->
            Timber.w(e, "[CycleCountFloorScreen] Barcode processing failed")
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

// ---------------------------------------------------------------------------
// Scanning overlay
// ---------------------------------------------------------------------------

@Composable
private fun FloorScannerOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val boxWidth = size.width * 0.75f
        val boxHeight = boxWidth * 0.45f
        val left = (size.width - boxWidth) / 2f
        val top = (size.height - boxHeight) / 2f

        drawRect(color = Color.Black.copy(alpha = 0.55f))

        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(12.dp.toPx()),
            blendMode = BlendMode.Clear,
        )

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(12.dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
        )

        val cornerLen = 28.dp.toPx()
        val strokeWidth = 4.dp.toPx()
        val accentColor = Color(0xFF4CAF50)

        drawLine(accentColor, Offset(left, top + cornerLen), Offset(left, top), strokeWidth)
        drawLine(accentColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth)

        drawLine(accentColor, Offset(left + boxWidth - cornerLen, top), Offset(left + boxWidth, top), strokeWidth)
        drawLine(accentColor, Offset(left + boxWidth, top), Offset(left + boxWidth, top + cornerLen), strokeWidth)

        drawLine(accentColor, Offset(left, top + boxHeight - cornerLen), Offset(left, top + boxHeight), strokeWidth)
        drawLine(accentColor, Offset(left, top + boxHeight), Offset(left + cornerLen, top + boxHeight), strokeWidth)

        drawLine(accentColor, Offset(left + boxWidth - cornerLen, top + boxHeight), Offset(left + boxWidth, top + boxHeight), strokeWidth)
        drawLine(accentColor, Offset(left + boxWidth, top + boxHeight - cornerLen), Offset(left + boxWidth, top + boxHeight), strokeWidth)
    }
}
