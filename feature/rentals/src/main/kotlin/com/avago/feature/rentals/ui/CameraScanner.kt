package com.avago.feature.rentals.ui

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.avago.feature.rentals.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A frame analyser behind an interface so screens stay testable and the ML Kit engine can be
 * swapped (spec §14 scan / document capture). ML Kit + CameraX were already app dependencies.
 */
interface CodeAnalyzer {
    /** Analyse [image]; call [onResult] for each decoded value, then [onDone] exactly once. */
    fun analyze(image: InputImage, onResult: (String) -> Unit, onDone: () -> Unit)
    fun close()
}

/** ML Kit barcodes (QR, Code128, DataMatrix … or only PDF417 for licences). */
class MlKitBarcodeAnalyzer(pdf417Only: Boolean = false) : CodeAnalyzer {
    private val client = BarcodeScanning.getClient(
        if (pdf417Only) BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_PDF417).build()
        else BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build(),
    )

    override fun analyze(image: InputImage, onResult: (String) -> Unit, onDone: () -> Unit) {
        client.process(image)
            .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }?.let(onResult) }
            .addOnFailureListener { Timber.w(it, "barcode analysis failed") }
            .addOnCompleteListener { onDone() }
    }

    override fun close() = client.close()
}

/** ML Kit Latin text (plates / fleet numbers painted on units). Emits each text line. */
class MlKitTextAnalyzer : CodeAnalyzer {
    private val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun analyze(image: InputImage, onResult: (String) -> Unit, onDone: () -> Unit) {
        client.process(image)
            .addOnSuccessListener { text ->
                text.textBlocks.flatMap { it.lines }.map { it.text.trim() }.filter { it.length in 3..20 }.forEach(onResult)
            }
            .addOnFailureListener { Timber.w(it, "text analysis failed") }
            .addOnCompleteListener { onDone() }
    }

    override fun close() = client.close()
}

/** CameraX preview feeding [analyzer]; asks for the camera permission first. */
@Composable
internal fun CameraScanner(
    analyzer: CodeAnalyzer,
    active: Boolean,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.rentals_camera_permission))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentals_grant))
                }
            }
        }
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val isActive by rememberUpdatedState(active)
    val emit by rememberUpdatedState(onCode)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = PreviewView(ctx)
            val busy = AtomicBoolean(false)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                    if (!isActive || !busy.compareAndSet(false, true)) {
                        proxy.close()
                    } else {
                        analyse(proxy, analyzer, { emit(it) }) { busy.set(false) }
                    }
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    Timber.e(e, "rentals camera bind failed")
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
        onRelease = { analyzer.close() },
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyse(proxy: ImageProxy, analyzer: CodeAnalyzer, onResult: (String) -> Unit, done: () -> Unit) {
    val media = proxy.image
    if (media == null) {
        proxy.close(); done(); return
    }
    analyzer.analyze(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees), onResult) {
        proxy.close()
        done()
    }
}
