package com.avago.feature.assets.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Generates a QR code bitmap for [content] using ZXing's MultiFormatWriter.
 * Returns null if ZXing is not on the classpath or encoding fails.
 */
fun generateQrBitmap(content: String, size: Int = 512): Bitmap? = try {
    val writerClass = Class.forName("com.google.zxing.MultiFormatWriter")
    val formatClass = Class.forName("com.google.zxing.BarcodeFormat")
    val qrCodeFormat = formatClass.getField("QR_CODE").get(null)
    val writer = writerClass.getDeclaredConstructor().newInstance()
    val encodeMethod = writerClass.getMethod(
        "encode",
        String::class.java,
        formatClass,
        Int::class.java,
        Int::class.java,
    )
    val matrix = encodeMethod.invoke(writer, content, qrCodeFormat, size, size)
    val getMethod = matrix.javaClass.getMethod("get", Int::class.java, Int::class.java)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            val isBlack = getMethod.invoke(matrix, x, y) as Boolean
            bmp.setPixel(x, y, if (isBlack) Color.BLACK else Color.WHITE)
        }
    }
    bmp
} catch (_: Exception) {
    null
}

/**
 * A card that displays a QR code for the given [assetId].
 *
 * If ZXing is available on the classpath the actual QR bitmap is rendered.
 * Otherwise a placeholder icon is shown. In both cases the asset ID is shown
 * below with a "Copy ID" button.
 */
@Composable
fun QrCodeCard(
    assetId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val qrBitmap = remember(assetId) { generateQrBitmap(assetId) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "QR Code",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR code for asset $assetId",
                    modifier = Modifier.size(200.dp),
                )
            } else {
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = assetId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Asset ID", assetId))
                },
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 4.dp),
                )
                Text("Copy ID")
            }
        }
    }
}
