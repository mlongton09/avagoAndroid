package com.avago.core.docscan

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import timber.log.Timber

/**
 * The result of a successful document scan.
 *
 * @param pdfUri  URI to the generated PDF (null if the scanner did not produce one).
 * @param pageUris Ordered list of JPEG URIs — one per scanned page.
 */
data class DocScanResult(
    val pdfUri: Uri?,
    val pageUris: List<Uri>,
)

/**
 * Returns an [ActivityResultLauncher] pre-wired to receive ML Kit Document Scanner results.
 *
 * Usage in a Composable:
 * ```
 * val launcher = rememberDocScanLauncher { result -> /* handle result */ }
 * // later:
 * launchDocScan(activity, launcher)
 * ```
 */
@Composable
fun rememberDocScanLauncher(
    onResult: (DocScanResult?) -> Unit,
): ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val raw = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            if (raw == null) {
                Timber.w("[DocScan] RESULT_OK but GmsDocumentScanningResult was null")
                onResult(null)
            } else {
                onResult(
                    DocScanResult(
                        pdfUri = raw.pdf?.uri,
                        pageUris = raw.pages?.map { it.imageUri } ?: emptyList(),
                    ),
                )
            }
        } else {
            Timber.d("[DocScan] Scan cancelled or failed (resultCode=${activityResult.resultCode})")
            onResult(null)
        }
    }
}

/**
 * Builds the ML Kit document scanner [GmsDocumentScannerOptions] and asynchronously obtains
 * the scanner's [IntentSenderRequest], then launches it via [launcher].
 *
 * Must be called from the UI thread (e.g., inside a click handler).
 *
 * @param activity   The foreground [Activity] required by GMS Document Scanner.
 * @param launcher   The launcher returned by [rememberDocScanLauncher].
 * @param maxPages   Maximum number of pages the user is allowed to scan (default 10).
 * @param onError    Invoked on the calling thread if the scanner fails to start.
 */
fun launchDocScan(
    activity: Activity,
    launcher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
    maxPages: Int = 10,
    onError: (Exception) -> Unit = { Timber.e(it, "[DocScan] Failed to start scanner") },
) {
    val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(maxPages)
        .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
        .setScannerMode(SCANNER_MODE_FULL)
        .build()

    GmsDocumentScanning.getClient(options)
        .getStartScanIntent(activity)
        .addOnSuccessListener { intentSender ->
            launcher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
        .addOnFailureListener { e ->
            Timber.e(e, "[DocScan] getStartScanIntent failed")
            onError(e)
        }
}
