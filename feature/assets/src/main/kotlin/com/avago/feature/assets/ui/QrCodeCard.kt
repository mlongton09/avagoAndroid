package com.avago.feature.assets.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.assets.R
import com.avago.feature.assets.model.QrScan
import com.avago.feature.assets.viewmodel.AssetQrViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Encodes [content] as a QR code bitmap (ZXing); null when it cannot be encoded. */
fun generateQrBitmap(content: String, size: Int = 512): Bitmap? = try {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
} catch (_: WriterException) {
    null
}

/**
 * An asset's public QR label: the server-minted link printed on the asset (scanning it opens the
 * public report page, and the in-app scanner resolves it to the asset). Users who can edit assets
 * generate, replace or revoke the label; the scan history lists who scanned it and where.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetQrCard(
    assetId: String,
    modifier: Modifier = Modifier,
    viewModel: AssetQrViewModel = hiltViewModel(),
) {
    LaunchedEffect(assetId) { viewModel.load(assetId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canManage by viewModel.canManage.collectAsStateWithLifecycle()
    val canViewScans by viewModel.canViewScans.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    var failure by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<QrConfirm?>(null) }
    var showScans by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.failures.collect { failure = scopeErrorText(resources, it) } }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.asset_qr_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            val token = state.token
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                token != null -> {
                    val bitmap = remember(token.url) { generateQrBitmap(token.url) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.asset_qr_image_description),
                            modifier = Modifier.size(200.dp),
                        )
                    } else {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(120.dp))
                    }
                    Text(
                        text = token.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                state.error != null -> Text(
                    text = scopeErrorText(resources, state.error!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                else -> Text(
                    text = stringResource(R.string.asset_qr_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            failure?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                if (token != null) {
                    OutlinedButton(onClick = { copyToClipboard(context, token.url) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.asset_qr_copy_link), modifier = Modifier.padding(start = 4.dp))
                    }
                    OutlinedButton(onClick = { shareLink(context, token.url) }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.asset_qr_share), modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (state.error != null && !state.loading) {
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.asset_qr_retry)) }
                }
                if (canManage && !state.loading && state.error == null) {
                    if (token == null) {
                        TextButton(onClick = { failure = null; viewModel.mint() }, enabled = !state.busy) {
                            Text(stringResource(R.string.asset_qr_generate))
                        }
                    } else {
                        TextButton(onClick = { confirm = QrConfirm.REPLACE }, enabled = !state.busy) {
                            Text(stringResource(R.string.asset_qr_replace))
                        }
                        TextButton(onClick = { confirm = QrConfirm.REVOKE }, enabled = !state.busy) {
                            Text(stringResource(R.string.asset_qr_revoke), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (canViewScans) {
                    TextButton(onClick = { showScans = true; viewModel.loadScans(reset = true) }) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.asset_qr_scan_history), modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(stringResource(if (action == QrConfirm.REPLACE) R.string.asset_qr_replace else R.string.asset_qr_revoke))
            },
            text = { Text(stringResource(R.string.asset_qr_invalidates_label)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    failure = null
                    if (action == QrConfirm.REPLACE) viewModel.mint() else viewModel.revoke()
                }) {
                    Text(stringResource(if (action == QrConfirm.REPLACE) R.string.asset_qr_replace else R.string.asset_qr_revoke))
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    if (showScans) {
        ModalBottomSheet(onDismissRequest = { showScans = false }) {
            QrScanHistory(viewModel)
        }
    }
}

private enum class QrConfirm { REPLACE, REVOKE }

@Composable
private fun QrScanHistory(viewModel: AssetQrViewModel) {
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val names by viewModel.namesByUserId.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.asset_qr_scan_history),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn {
            items(scans.scans, key = QrScan::id) { scan ->
                ListItem(
                    headlineContent = { Text(formatScanTime(scan.scannedAt)) },
                    supportingContent = {
                        val who = scan.scannedByUserId?.let { names[it] } ?: stringResource(R.string.asset_qr_scan_anonymous)
                        val where = if (scan.latitude != null && scan.longitude != null) {
                            "%.5f, %.5f".format(scan.latitude, scan.longitude)
                        } else {
                            null
                        }
                        Text(listOfNotNull(who, scan.deviceInfo, where).joinToString(" · "))
                    },
                )
                HorizontalDivider()
            }
            item(key = "footer") {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    when {
                        scans.loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        scans.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(scopeErrorText(resources, scans.error!!), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.loadScans() }) { Text(stringResource(R.string.asset_qr_retry)) }
                        }
                        scans.hasMore -> TextButton(onClick = { viewModel.loadScans() }) {
                            Text(stringResource(R.string.asset_qr_load_more))
                        }
                        scans.scans.isEmpty() -> Text(
                            stringResource(R.string.asset_qr_no_scans),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatScanTime(iso: String?): String =
    iso?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?.let { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(it) }
        ?: iso.orEmpty()

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("QR", text))
}

private fun shareLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
