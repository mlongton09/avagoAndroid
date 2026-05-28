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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocAddScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    /** Non-null when editing an existing doc. */
    existingDocId: String? = null,
    /** Pre-attach to an asset on creation. */
    assetId: String? = null,
    viewModel: DocAddViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(existingDocId) {
        if (existingDocId != null) viewModel.loadForEdit(existingDocId)
    }

    LaunchedEffect(state) {
        if (state is DocAddViewModel.UiState.Done) onSaved()
    }

    val scanLauncher = rememberDocScanLauncher { result: DocScanResult? ->
        if (result != null && result.pageUris.isNotEmpty()) {
            viewModel.processScannedPages(result.pageUris)
        } else {
            viewModel.resetToIdle()
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.processScannedPages(listOf(uri))
        else viewModel.resetToIdle()
    }

    val topBarTitle = when {
        existingDocId != null -> stringResource(R.string.doc_edit_title)
        else -> stringResource(R.string.doc_add_title)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(topBarTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.doc_add_cancel))
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val s = state) {
                is DocAddViewModel.UiState.Idle -> {
                    if (existingDocId != null) {
                        CenteredLoader(message = null)
                    } else {
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
                                    viewModel.resetToIdle()
                                }
                            },
                            onImportClick = {
                                viewModel.onImportRequested()
                                fileLauncher.launch(arrayOf("image/*", "application/pdf"))
                            },
                        )
                    }
                }

                is DocAddViewModel.UiState.Scanning -> CenteredLoader(message = null)

                is DocAddViewModel.UiState.OcrProcessing -> CenteredLoader(
                    message = stringResource(R.string.doc_add_ocr_processing),
                )

                is DocAddViewModel.UiState.Form -> {
                    DocFormContent(
                        formState = s,
                        onSave = { name, docType, vendor, amount, currency, purchaseDateMs, warrantyDateMs, notes ->
                            viewModel.save(
                                name = name,
                                docType = docType,
                                vendor = vendor,
                                amount = amount,
                                currency = currency,
                                purchaseDateMs = purchaseDateMs,
                                warrantyEndDateMs = warrantyDateMs,
                                notes = notes,
                                rawText = s.rawText,
                                ocrResult = s.ocrResult,
                                assetId = assetId,
                                existingDocId = s.existingDocId,
                            )
                        },
                    )
                }

                is DocAddViewModel.UiState.Saving -> CenteredLoader(message = stringResource(R.string.doc_add_saving))

                DocAddViewModel.UiState.Done -> CenteredLoader(message = null)

                is DocAddViewModel.UiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(text = s.message, color = MaterialTheme.colorScheme.error)
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
// Form — all fields matching iOS AddEditDocViewController
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocFormContent(
    formState: DocAddViewModel.UiState.Form,
    onSave: (
        name: String,
        docType: String,
        vendor: String,
        amount: String,
        currency: String,
        purchaseDateMs: Long?,
        warrantyDateMs: Long?,
        notes: String,
    ) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(formState.name) }
    var nameError by remember { mutableStateOf(false) }
    var docTypeExpanded by remember { mutableStateOf(false) }
    var selectedDocType by rememberSaveable {
        mutableStateOf(DocTypes.all.firstOrNull { it.key == formState.docType } ?: DocTypes.all.first())
    }
    var vendor by rememberSaveable { mutableStateOf(formState.vendor) }
    var amount by rememberSaveable { mutableStateOf(formState.amount) }
    var currency by rememberSaveable { mutableStateOf(formState.currency) }
    var purchaseDateMs by rememberSaveable { mutableStateOf(formState.purchaseDateMs) }
    var warrantyDateMs by rememberSaveable { mutableStateOf(formState.warrantyEndDateMs) }
    var notes by rememberSaveable { mutableStateOf(formState.notes) }

    var showPurchaseDatePicker by remember { mutableStateOf(false) }
    var showWarrantyDatePicker by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    if (showPurchaseDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = purchaseDateMs)
        DatePickerDialog(
            onDismissRequest = { showPurchaseDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    purchaseDateMs = dpState.selectedDateMillis
                    showPurchaseDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPurchaseDatePicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = dpState) }
    }

    if (showWarrantyDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = warrantyDateMs)
        DatePickerDialog(
            onDismissRequest = { showWarrantyDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    warrantyDateMs = dpState.selectedDateMillis
                    showWarrantyDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showWarrantyDatePicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = dpState) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = false },
            label = { Text(stringResource(R.string.doc_add_field_name)) },
            isError = nameError,
            supportingText = if (nameError) {
                { Text(stringResource(R.string.doc_add_field_name_required)) }
            } else null,
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
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = docTypeExpanded, onDismissRequest = { docTypeExpanded = false }) {
                DocTypes.all.forEach { typeItem ->
                    DropdownMenuItem(
                        text = { Text(stringResource(typeItem.labelResId)) },
                        leadingIcon = { Icon(typeItem.icon, contentDescription = null) },
                        onClick = { selectedDocType = typeItem; docTypeExpanded = false },
                    )
                }
            }
        }

        HorizontalDivider()

        // Date (purchase date) — tappable row with inline picker
        OutlinedTextField(
            value = purchaseDateMs?.let { dateFormatter.format(Date(it)) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Date") },
            placeholder = { Text("Select date") },
            trailingIcon = {
                IconButton(onClick = { showPurchaseDatePicker = true }) {
                    Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                }
            },
            modifier = Modifier.fillMaxWidth().clickable { showPurchaseDatePicker = true },
        )

        // Vendor
        OutlinedTextField(
            value = vendor,
            onValueChange = { vendor = it },
            label = { Text("Vendor") },
            placeholder = { Text("Vendor name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        // Amount + currency
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it },
                label = { Text("Currency") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        // Warranty expiry date (only relevant for warranty/receipt doc types)
        OutlinedTextField(
            value = warrantyDateMs?.let { dateFormatter.format(Date(it)) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Warranty Expiry") },
            placeholder = { Text("Select date (optional)") },
            trailingIcon = {
                IconButton(onClick = { showWarrantyDatePicker = true }) {
                    Icon(Icons.Default.CalendarToday, contentDescription = "Pick warranty date")
                }
            },
            modifier = Modifier.fillMaxWidth().clickable { showWarrantyDatePicker = true },
        )

        HorizontalDivider()

        // Notes
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        // OCR preview (read-only)
        if (formState.rawText.isNotBlank()) {
            Text(
                text = stringResource(R.string.doc_add_ocr_raw_text),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formState.rawText.take(500) + if (formState.rawText.length > 500) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (name.isBlank()) {
                    nameError = true
                } else {
                    onSave(name.trim(), selectedDocType.key, vendor, amount, currency, purchaseDateMs, warrantyDateMs, notes)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.doc_add_save))
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

// ---------------------------------------------------------------------------
// Source picker (new doc only)
// ---------------------------------------------------------------------------

@Composable
private fun SourcePicker(
    onScanClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
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
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
