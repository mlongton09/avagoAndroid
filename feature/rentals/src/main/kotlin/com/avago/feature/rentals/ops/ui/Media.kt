package com.avago.feature.rentals.ops.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.media.ExifInterface
import androidx.core.content.ContextCompat
import com.avago.core.data.domain.StopFix
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.math.BigDecimal
import kotlin.coroutines.resume

internal object Media {
    private const val MAX_EDGE = 1600
    private const val JPEG_QUALITY = 82

    /** Downscales a camera JPEG in place (≤ [MAX_EDGE] px, EXIF rotation applied). False if unreadable. */
    suspend fun normalizePhoto(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext false
        val rotation = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
        val m = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val out = if (scale < 1f || rotation != 0f) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true) else decoded
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (out !== decoded) decoded.recycle()
        out.recycle()
        true
    }

    /**
     * Renders signature strokes (pad coordinates) as a PNG on a white background: the complete
     * command carries the signature as PNG bytes, and a white ground keeps the ink visible in
     * every viewer.
     */
    suspend fun writeSignature(strokes: List<List<Pair<Float, Float>>>, width: Int, height: Int, strokePx: Float, file: File): Boolean =
        withContext(Dispatchers.IO) {
            if (width <= 0 || height <= 0 || strokes.none { it.isNotEmpty() }) return@withContext false
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(android.graphics.Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            for (s in strokes) {
                if (s.isEmpty()) continue
                if (s.size == 1) {
                    c.drawPoint(s[0].first, s[0].second, paint)
                    continue
                }
                val p = Path().apply {
                    moveTo(s[0].first, s[0].second)
                    s.drop(1).forEach { lineTo(it.first, it.second) }
                }
                c.drawPath(p, paint)
            }
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            true
        }
}

internal object LastLocation {
    /** Last known fix if location permission was granted; never prompts, null after 2 s. */
    @SuppressLint("MissingPermission")
    suspend fun get(ctx: Context): Location? {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return null
        return withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
    }
    /** The last fix as a stop position with its accuracy radius, or null without one. */
    suspend fun fix(ctx: Context): StopFix? = get(ctx)?.let { l ->
        StopFix(
            lat = BigDecimal.valueOf(l.latitude),
            lng = BigDecimal.valueOf(l.longitude),
            accuracyM = if (l.hasAccuracy()) BigDecimal.valueOf(l.accuracy.toDouble()) else null,
        )
    }
}
