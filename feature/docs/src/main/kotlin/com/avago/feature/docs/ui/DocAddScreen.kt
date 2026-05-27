package com.avago.feature.docs.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.docscan.DocScanResult
import com.avago.core.docscan.launchDocScan
import com.avago.core.docscan.rememberDocScanLauncher
import com.avago.feature.docs.R
import com.avago.feature.docs.model.DocTypes
import com.avago.feature.docs.viewmodel.DocAddViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocAddScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: DocAddViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    // Navigate away when save completes
    LaunchedEffect(state) {
        if (state is DocAddViewModel.UiState.Done) {
            onSaved()
        }
    }

    // ML Kit document scanner launcher
    val scanLauncher = rememberDocScanLauncher { result: DocScanResult? ->
        if (result != null && result.pageUris.isNotEmpty()) {
            viewModel.processScannedPages(result.pageUris)
        } else {
            viewModel.resetToIdle()
        }
    }

    // System file picker launcher (image or PDF)
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processScannedPages(listOf(uri))
        } else {
            viewModel.resetToIdle()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doc_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.doc_add_cancel),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (val s = state) {
                is DocAddViewModel.UiState.Idle -> {
                    SourcePicker(
                        onScanClick = {
                            viewModel.onScanRequested()
                            if (activity != null) {
                                launchDocScan(
                                    activity = activity,
                                    launcher = scanLauncher,
                                    onError = { e ->
                                        Timber.e(e, "[DocAddScreen] Scanner failed to start")
                                        viewModel.resetToIdle()
                                    },
                                )
                            } else {
                                Timber.w("[DocAddScreen] Cannot launch scanner — context is not an Activity")
                                viewModel.resetToIdle()
                            }
                        },
                        onImportClick = {
                            viewModel.onImportRequested()
                            fileLauncher.launch(arrayOf("image/*", "application/pdf"))
                        },
                    )
                }

                is DocAddViewModel.UiState.Scanning -> {
                    CenteredLoader(message = null)
                }

                is DocAddViewModel.UiState.OcrProcessing -> {
                    CenteredLoader(
                        message = stringResource(R.string.doc_add_ocr_processing),
                    )
                }

                is DocAddViewModel.UiState.Form -> {
                    DocFormContent(
                        rawText = s.rawText,
                        onSave = { name, docType ->
                            viewModel.save(
                                name = name,
                                docType = docType,
                                rawText = s.rawText,
                                ocrResult = s.ocrResult,
                                assetId = null,
                            )
                        },
                    )
                }

                is DocAddViewModel.UiState.Saving -> {
                    CenteredLoader(message = stringResource(R.string.doc_add_saving))
                }

                DocAddViewModel.UiState.Done -> {
                    // LaunchedEffect above handles navigation
                    CenteredLoader(message = null)
                }

                is DocAddViewModel.UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.resetToIdle() }) {
                            Text(stringResource(R.string.doc_add_cancel))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun SourcePicker(
    onScanClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.doc_add_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(32.dp))

        SourceCard(
            icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(40.dp)) },
            title = stringResource(R.string.doc_add_scan),
            description = stringResource(R.string.doc_add_scan_description),
            onClick = onScanClick,
        )

        Spacer(modifier = Modifier.height(16.dp))

        SourceCard(
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(40.dp)) },
            title = stringResource(R.string.doc_add_import),
            description = stringResource(R.string.doc_add_import_description),
            onClick = onImportClick,
        )
    }
}

@Composable
private fun SourceCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(modifier = Modifier.size(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocFormContent(
    rawText: String,
    onSave: (name: String, docType: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var docTypeExpanded by remember { mutableStateOf(false) }
    var selectedDocType by rememberSaveable { mutableStateOf(DocTypes.all.first()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Name field
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                nameError = false
            },
            label = { Text(stringResource(R.string.doc_add_field_name)) },
            isError = nameError,
            supportingText = {
                if (nameError) Text(stringResource(R.string.doc_add_field_name_required))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        // Doc type dropdown
        ExposedDropdownMenuBox(
            expanded = docTypeExpanded,
            onExpandedChange = { docTypeExpanded = !docTypeExpanded },
        ) {
            OutlinedTextField(
                value = stringResource(selectedDocType.labelResId),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.doc_add_field_type)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = docTypeExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = docTypeExpanded,
                onDismissRequest = { docTypeExpanded = false },
            ) {
                DocTypes.all.forEach { typeItem ->
                    DropdownMenuItem(
                        text = { Text(stringResource(typeItem.labelResId)) },
                        leadingIcon = { Icon(typeItem.icon, contentDescription = null) },
                        onClick = {
                            selectedDocType = typeItem
                            docTypeExpanded = false
                        },
                    )
                }
            }
        }

        // OCR text preview (read-only, collapsible handled by state)
        if (rawText.isNotBlank()) {
            Text(
                text = stringResource(R.string.doc_add_ocr_raw_text),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = rawText.take(500) + if (rawText.length > 500) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (name.isBlank()) {
                    nameError = true
                } else {
                    onSave(name.trim(), selectedDocType.key)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.doc_add_save))
        }
    }
}

@Composable
private fun CenteredLoader(message: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
