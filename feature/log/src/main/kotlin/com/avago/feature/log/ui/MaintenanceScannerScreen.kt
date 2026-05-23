package com.avago.feature.log.ui

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.avago.feature.log.R
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import timber.log.Timber

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScannerScreen(
    onAssetScanned: (assetId: String) -> Unit,
    onPartScanned: (partId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: MaintenanceScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val scanMode by viewModel.scanMode.collectAsState()
    val scanResult by viewModel.scanResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // Scanning is active only when there is no pending result and not loading
    val scanningActive = scanResult == null && !isLoading

    // Flash overlay on successful scan
    var showFlash by remember { mutableStateOf(false) }
    LaunchedEffect(scanResult) {
        if (scanResult != null) {
            showFlash = true
            delay(300)
            showFlash = false
        }
    }

    // Camera permission
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.maintenance_scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.maintenance_scanner_title),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
        ) {
            if (!hasPermission) {
                // ---- Permission denied UI ----
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.maintenance_scanner_permission_rationale),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.maintenance_scanner_grant_permission))
                    }
                }
            } else {
                // ---- Camera preview ----
                MaintenanceCameraPreview(
                    scanningActive = scanningActive,
                    lifecycleOwner = lifecycleOwner,
                    onBarcode = { barcode ->
                        viewModel.onBarcodeScanned(barcode)
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // ---- Scanning overlay (viewfinder box) ----
                MaintenanceScannerOverlay(modifier = Modifier.fillMaxSize())

                // ---- Flash overlay on successful scan ----
                AnimatedVisibility(
                    visible = showFlash,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                }

                // ---- Mode chips at the top ----
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = scanMode == MaintenanceScannerViewModel.ScanMode.ASSET_TAG,
                        onClick = { viewModel.setScanMode(MaintenanceScannerViewModel.ScanMode.ASSET_TAG) },
                        label = { Text(stringResource(R.string.maintenance_scanner_mode_asset)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            labelColor = Color.White,
                        ),
                    )
                    FilterChip(
                        selected = scanMode == MaintenanceScannerViewModel.ScanMode.PART_BARCODE,
                        onClick = { viewModel.setScanMode(MaintenanceScannerViewModel.ScanMode.PART_BARCODE) },
                        label = { Text(stringResource(R.string.maintenance_scanner_mode_part)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            labelColor = Color.White,
                        ),
                    )
                }

                // ---- Loading indicator ----
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp),
                        color = Color.White,
                    )
                }

                // ---- Bottom panel ----
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        scanResult != null -> {
                            val currentResult = scanResult!!
                            // Result card
                            ScanResultCard(result = currentResult)

                            // Action buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.clearResult() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = stringResource(R.string.maintenance_scanner_scan_again),
                                        color = Color.White,
                                    )
                                }
                                Button(
                                    onClick = {
                                        when (val r = scanResult) {
                                            is ScanResult.AssetResult -> onAssetScanned(r.assetId)
                                            is ScanResult.PartResult -> onPartScanned(r.partId)
                                            null -> Unit
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.maintenance_scanner_confirm))
                                }
                            }
                        }

                        error != null -> {
                            Text(
                                text = error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            HintText(mode = scanMode)
                        }

                        else -> {
                            HintText(mode = scanMode)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Result card
// ---------------------------------------------------------------------------

@Composable
private fun ScanResultCard(result: ScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (result) {
                is ScanResult.AssetResult -> {
                    Text(
                        text = stringResource(R.string.maintenance_scanner_asset_found),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result.assetName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (result.assetType.isNotBlank()) {
                        Text(
                            text = result.assetType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is ScanResult.PartResult -> {
                    Text(
                        text = stringResource(R.string.maintenance_scanner_part_found),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result.partName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!result.sku.isNullOrBlank()) {
                        Text(
                            text = "SKU: ${result.sku}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Hint text
// ---------------------------------------------------------------------------

@Composable
private fun HintText(mode: MaintenanceScannerViewModel.ScanMode) {
    val hint = when (mode) {
        MaintenanceScannerViewModel.ScanMode.ASSET_TAG ->
            stringResource(R.string.maintenance_scanner_hint_asset)
        MaintenanceScannerViewModel.ScanMode.PART_BARCODE ->
            stringResource(R.string.maintenance_scanner_hint_part)
    }
    Text(
        text = hint,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

// ---------------------------------------------------------------------------
// CameraX preview + ML Kit analysis
// ---------------------------------------------------------------------------

@Composable
private fun MaintenanceCameraPreview(
    scanningActive: Boolean,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 1.5s debounce: track last scan timestamp (plain Long ref — not state, not needed for recompose)
    val lastScanMsRef = remember { longArrayOf(0L) }

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
                            val now = System.currentTimeMillis()
                            if (scanningActive && (now - lastScanMsRef[0]) >= 1_500L) {
                                processMaintenanceImage(imageProxy, barcodeScanner) { barcode ->
                                    lastScanMsRef[0] = now
                                    onBarcode(barcode)
                                }
                            } else {
                                imageProxy.close()
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
                    Timber.e(e, "[MaintenanceScannerScreen] CameraX bind failed")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        onRelease = { previewView ->
            (previewView.tag as? com.google.mlkit.vision.barcode.BarcodeScanner)?.close()
        },
        modifier = modifier,
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processMaintenanceImage(
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
            Timber.w(e, "[MaintenanceScannerScreen] Barcode processing failed")
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

// ---------------------------------------------------------------------------
// Scanning overlay
// ---------------------------------------------------------------------------

@Composable
private fun MaintenanceScannerOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val boxSize = minOf(size.width, size.height) * 0.65f
        val left = (size.width - boxSize) / 2f
        val top = (size.height - boxSize) / 2f

        // Semi-transparent scrim
        drawRect(color = Color.Black.copy(alpha = 0.55f))

        // Cut-out
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(16.dp.toPx()),
            blendMode = BlendMode.Clear,
        )

        // White border
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
        )

        // Corner accent lines
        val cornerLen = 32.dp.toPx()
        val strokeWidth = 4.dp.toPx()
        val accentColor = Color(0xFF4CAF50)

        // Top-left
        drawLine(accentColor, Offset(left, top + cornerLen), Offset(left, top), strokeWidth)
        drawLine(accentColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth)

        // Top-right
        drawLine(accentColor, Offset(left + boxSize - cornerLen, top), Offset(left + boxSize, top), strokeWidth)
        drawLine(accentColor, Offset(left + boxSize, top), Offset(left + boxSize, top + cornerLen), strokeWidth)

        // Bottom-left
        drawLine(accentColor, Offset(left, top + boxSize - cornerLen), Offset(left, top + boxSize), strokeWidth)
        drawLine(accentColor, Offset(left, top + boxSize), Offset(left + cornerLen, top + boxSize), strokeWidth)

        // Bottom-right
        drawLine(accentColor, Offset(left + boxSize - cornerLen, top + boxSize), Offset(left + boxSize, top + boxSize), strokeWidth)
        drawLine(accentColor, Offset(left + boxSize, top + boxSize - cornerLen), Offset(left + boxSize, top + boxSize), strokeWidth)
    }
}
