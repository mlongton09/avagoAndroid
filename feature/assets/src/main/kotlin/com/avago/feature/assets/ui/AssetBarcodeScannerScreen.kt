package com.avago.feature.assets.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.model.RecordQrScanRequest
import com.avago.feature.assets.R
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class AssetBarcodeScannerViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
    private val serviceClient: AvagoServiceClient,
) : ViewModel() {

    /**
     * Looks up an asset by the scanned barcode value (treated as the asset ID).
     * Returns the assetId string if found, null otherwise.
     */
    suspend fun lookupByBarcode(barcode: String): String? {
        val accountId = identityManager.getActiveAccountId() ?: return null
        return try {
            dbFactory.get(accountId).assetDao().getByBarcode(barcode)?.assetId
        } catch (e: Exception) {
            Timber.e(e, "[AssetBarcodeScannerViewModel] Error looking up barcode: $barcode")
            null
        }
    }

    /**
     * Change 132/135: record the QR scan with optional GPS coordinates.
     * Fires-and-forgets; failures are logged but do not block navigation.
     */
    suspend fun recordScan(assetId: String, latitude: Double?, longitude: Double?) {
        val accountId = identityManager.getActiveAccountId() ?: return
        try {
            serviceClient.recordQrScan(
                accountId = accountId,
                request = RecordQrScanRequest(
                    asset_id = assetId,
                    latitude = latitude,
                    longitude = longitude,
                ),
            )
        } catch (e: Exception) {
            Timber.w(e, "[AssetBarcodeScannerViewModel] recordScan failed (non-fatal)")
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetBarcodeScannerScreen(
    onAssetFound: (assetId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: AssetBarcodeScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Track whether scanning is active — paused once an asset is matched
    var scanningActive by remember { mutableStateOf(true) }

    // Camera permission state
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

    // Change 132/135: location permission for GPS tagging of QR scans.
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED,
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasLocationPermission = granted
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.barcode_scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.asset_detail_back),
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (!hasPermission) {
                // Permission denied — show request UI
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.barcode_scanner_permission_rationale),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.barcode_scanner_grant_permission))
                    }
                }
            } else {
                // Camera preview
                CameraPreview(
                    scanningActive = scanningActive,
                    lifecycleOwner = lifecycleOwner,
                    onBarcode = { barcode ->
                        if (!scanningActive) return@CameraPreview
                        scanningActive = false // pause scanning immediately

                        // Request location permission if not yet granted (Change 132/135)
                        if (!hasLocationPermission) {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }

                        scope.launch {
                            val assetId = viewModel.lookupByBarcode(barcode)
                            if (assetId != null) {
                                // Attempt to capture last-known location for the scan record
                                val (lat, lng) = getLastKnownLocation(context, hasLocationPermission)
                                viewModel.recordScan(assetId, lat, lng)
                                onAssetFound(assetId)
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.barcode_scanner_not_found),
                                )
                                // Resume scanning after showing the snackbar
                                scanningActive = true
                            }
                        }
                    },
                )

                // Scanning overlay
                ScannerOverlay(modifier = Modifier.fillMaxSize())

                // Hint text at the bottom
                Text(
                    text = stringResource(R.string.barcode_scanner_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// GPS helper — Change 132/135
// ---------------------------------------------------------------------------

/**
 * Returns the last known fine-location as a Pair(latitude, longitude), or (null, null)
 * if permission is missing or location is unavailable.
 */
@SuppressLint("MissingPermission")
private suspend fun getLastKnownLocation(
    context: android.content.Context,
    hasPermission: Boolean,
): Pair<Double?, Double?> {
    if (!hasPermission) return Pair(null, null)
    return suspendCancellableCoroutine { cont ->
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    cont.resume(Pair(location.latitude, location.longitude))
                } else {
                    // Request a single fresh fix if last known is null
                    val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 0)
                        .setMaxUpdates(1)
                        .build()
                    val cb = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            fusedClient.removeLocationUpdates(this)
                            val loc = result.lastLocation
                            cont.resume(if (loc != null) Pair(loc.latitude, loc.longitude) else Pair(null, null))
                        }
                    }
                    fusedClient.requestLocationUpdates(req, cb, Looper.getMainLooper())
                    cont.invokeOnCancellation { fusedClient.removeLocationUpdates(cb) }
                }
            }
            .addOnFailureListener {
                Timber.w(it, "[AssetBarcodeScanner] getLastKnownLocation failed")
                cont.resume(Pair(null, null))
            }
    }
}

// ---------------------------------------------------------------------------
// CameraX preview + ML Kit analysis
// ---------------------------------------------------------------------------

@Composable
private fun CameraPreview(
    scanningActive: Boolean,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onBarcode: (String) -> Unit,
) {
    val context = LocalContext.current

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
                            if (scanningActive) {
                                processImage(imageProxy, barcodeScanner, onBarcode)
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
                    Timber.e(e, "[AssetBarcodeScannerScreen] CameraX bind failed")
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
private fun processImage(
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
            Timber.w(e, "[AssetBarcodeScannerScreen] Barcode processing failed")
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

// ---------------------------------------------------------------------------
// Scanning overlay
// ---------------------------------------------------------------------------

@Composable
private fun ScannerOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val boxSize = minOf(size.width, size.height) * 0.65f
        val left = (size.width - boxSize) / 2f
        val top = (size.height - boxSize) / 2f

        // Semi-transparent scrim covering everything outside the scan box
        drawRect(color = Color.Black.copy(alpha = 0.55f))

        // Clear the scan box area (cut-out)
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(16.dp.toPx()),
            blendMode = BlendMode.Clear,
        )

        // White border around the scan box
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
        )

        // Corner accent lines (top-left)
        val cornerLen = 32.dp.toPx()
        val strokeWidth = 4.dp.toPx()
        val accentColor = Color(0xFF4CAF50)

        // Top-left corner
        drawLine(accentColor, Offset(left, top + cornerLen), Offset(left, top), strokeWidth)
        drawLine(accentColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth)

        // Top-right corner
        drawLine(accentColor, Offset(left + boxSize - cornerLen, top), Offset(left + boxSize, top), strokeWidth)
        drawLine(accentColor, Offset(left + boxSize, top), Offset(left + boxSize, top + cornerLen), strokeWidth)

        // Bottom-left corner
        drawLine(accentColor, Offset(left, top + boxSize - cornerLen), Offset(left, top + boxSize), strokeWidth)
        drawLine(accentColor, Offset(left, top + boxSize), Offset(left + cornerLen, top + boxSize), strokeWidth)

        // Bottom-right corner
        drawLine(accentColor, Offset(left + boxSize - cornerLen, top + boxSize), Offset(left + boxSize, top + boxSize), strokeWidth)
        drawLine(accentColor, Offset(left + boxSize, top + boxSize - cornerLen), Offset(left + boxSize, top + boxSize), strokeWidth)
    }
}
