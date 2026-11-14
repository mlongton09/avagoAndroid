package com.avago.feature.rentalsops.ui

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.avago.core.design.theme.AvagoSpacing
import com.avago.feature.rentalsops.R
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber

/**
 * Compact CameraX + ML Kit unit scanner. [active] and [onCode] are read through
 * [rememberUpdatedState] so the analyzer (created once) never sees stale values.
 */
@Composable
internal fun UnitScanner(active: Boolean, onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val isActive by rememberUpdatedState(active)
    val callback by rememberUpdatedState(onCode)
    val label = stringResource(R.string.rentalops_scan_camera)

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (!granted) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(AvagoSpacing.lg)) {
                Text(
                    stringResource(R.string.rentalops_scan_permission),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { request.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = AvagoSpacing.md).heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentalops_scan_grant)) }
            }
        } else {
            AndroidView(
                factory = { c ->
                    val view = PreviewView(c)
                    val scanner = BarcodeScanning.getClient()
                    view.tag = scanner
                    val future = ProcessCameraProvider.getInstance(c)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(c)) { proxy ->
                            if (isActive) analyze(proxy, scanner) { callback(it) } else proxy.close()
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        } catch (e: Exception) {
                            Timber.w(e, "[RentalOps] camera bind failed")
                        }
                    }, ContextCompat.getMainExecutor(c))
                    view
                },
                onRelease = { v ->
                    (v.tag as? BarcodeScanner)?.close()
                    runCatching { ProcessCameraProvider.getInstance(v.context).get().unbindAll() }
                },
                modifier = Modifier.fillMaxSize().semantics { contentDescription = label },
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyze(proxy: ImageProxy, scanner: BarcodeScanner, onCode: (String) -> Unit) {
    val img = proxy.image ?: return proxy.close()
    scanner.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees))
        .addOnSuccessListener { codes -> codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.let(onCode) }
        .addOnCompleteListener { proxy.close() }
}
