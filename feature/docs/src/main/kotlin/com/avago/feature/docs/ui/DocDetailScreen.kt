package com.avago.feature.docs.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.avago.core.data.db.entity.DocEntity
import com.avago.feature.docs.R
import com.avago.feature.docs.model.DocTypes
import com.avago.feature.docs.viewmodel.DocDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocDetailScreen(
    docId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: DocDetailViewModel = hiltViewModel(),
) {
    val doc by viewModel.doc.collectAsStateWithLifecycle()
    val isReScanningOcr by viewModel.isReScanningOcr.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showOcrText by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.doc_detail_confirm_delete)) },
            text = { Text(stringResource(R.string.doc_detail_confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onDeleted)
                }) {
                    Text(
                        stringResource(R.string.doc_detail_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.doc_detail_cancel))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(doc?.name ?: "") },
                colors = TopAppBarDefaults.topAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.doc_detail_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: share in Phase 18 */ }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.doc_detail_share),
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.doc_detail_overflow_menu),
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.doc_detail_rescan_ocr)) },
                                leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    // Re-scan requires page URIs; show info snackbar if not available
                                    viewModel.reScanOcr(emptyList())
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.doc_detail_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        val entity = doc
        if (entity == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            // Image / PDF viewer
            DocMediaSection(doc = entity)

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()

            // Extracted fields
            ExtractedFieldsSection(doc = entity)

            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // OCR text section
            OcrTextSection(
                doc = entity,
                isRescanning = isReScanningOcr,
                expanded = showOcrText,
                onToggle = { showOcrText = !showOcrText },
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DocMediaSection(doc: DocEntity) {
    val downloadUrl = doc.downloadUrl
    when {
        downloadUrl != null && downloadUrl.endsWith(".pdf", ignoreCase = true) -> {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = false
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        loadUrl(downloadUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
            )
        }
        downloadUrl != null -> {
            AsyncImage(
                model = downloadUrl,
                contentDescription = doc.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            )
        }
        else -> {
            // No media available yet — placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = DocTypes.iconFor(doc.docType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(0.3f),
                )
            }
        }
    }
}

@Composable
private fun ExtractedFieldsSection(doc: DocEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.doc_detail_fields),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Common fields
        FieldRow(label = stringResource(R.string.doc_detail_field_vendor), value = doc.vendor)
        FieldRow(
            label = stringResource(R.string.doc_detail_field_total),
            value = doc.total?.let { "%.2f".format(it) + if (doc.currency != null) " ${doc.currency}" else "" },
        )
        FieldRow(
            label = stringResource(R.string.doc_detail_field_date),
            value = doc.purchaseDate?.let { formatDate(it) },
        )

        // Type-specific fields parsed from extractedJson (simplified — full parsing in Phase 18)
        if (doc.docType == "warranty") {
            FieldRow(
                label = stringResource(R.string.doc_detail_field_end_date),
                value = null, // Populated via extractedJson in Phase 18
            )
            FieldRow(
                label = stringResource(R.string.doc_detail_field_terms),
                value = null,
            )
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String?) {
    if (value == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun OcrTextSection(
    doc: DocEntity,
    isRescanning: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TextButton(onClick = onToggle) {
            Text(
                text = stringResource(R.string.doc_detail_ocr_text) +
                    if (expanded) " ▲" else " ▼",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (isRescanning) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        } else if (expanded) {
            val rawText = doc.ocrRawText
            if (rawText.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.doc_detail_ocr_text_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                Text(
                    text = rawText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))
