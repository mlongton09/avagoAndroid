package com.avago.core.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * ML Kit text recognition wrapper.
 *
 * Processes one or more image [Uri]s and returns the extracted text.  The recognizer
 * is created lazily and kept open for the lifetime of the singleton; it is **not**
 * closed between calls because GmsCore-backed recognizers are cheap to keep open.
 */
@Singleton
class AvagoTextRecognizer @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Runs OCR on a single image [uri] and returns the full extracted text.
     *
     * @return [Result.success] containing the extracted text (may be empty), or
     *         [Result.failure] if the image could not be loaded or recognition failed.
     */
    suspend fun recognize(uri: Uri, context: Context): Result<String> =
        suspendCancellableCoroutine { cont ->
            val image = try {
                InputImage.fromFilePath(context, uri)
            } catch (e: Exception) {
                Timber.e(e, "[OCR] Failed to load image from $uri")
                cont.resume(Result.failure(e))
                return@suspendCancellableCoroutine
            }

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.textBlocks.joinToString("\n") { it.text }
                    Timber.d("[OCR] Recognized ${text.length} chars from $uri")
                    cont.resume(Result.success(text))
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "[OCR] Recognition failed for $uri")
                    cont.resume(Result.failure(e))
                }

            cont.invokeOnCancellation {
                // Nothing to cancel on the GMS task; just log.
                Timber.d("[OCR] Coroutine cancelled for $uri")
            }
        }

    /**
     * Runs OCR on every URI in [uris] sequentially and concatenates the results,
     * separated by a page-break marker.
     *
     * Pages that fail OCR are silently skipped (the error is logged).
     *
     * @return [Result.success] with the joined text. Always succeeds unless [uris] is
     *         empty, in which case it returns an empty string.
     */
    suspend fun recognizeAll(uris: List<Uri>, context: Context): Result<String> {
        if (uris.isEmpty()) return Result.success("")

        val pageTexts = mutableListOf<String>()
        for ((index, uri) in uris.withIndex()) {
            val result = recognize(uri, context)
            result.fold(
                onSuccess = { text ->
                    if (text.isNotBlank()) pageTexts.add(text)
                },
                onFailure = { e ->
                    Timber.w(e, "[OCR] Skipping page ${index + 1} due to error")
                },
            )
        }

        return Result.success(pageTexts.joinToString("\n\n--- Page Break ---\n\n"))
    }
}
