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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.docscan.DocScanResult
import com.avago.core.docscan.launchDocScan
import com.avago.core.docscan.rememberDocScanLauncher
import com.avago.feature.docs.R
import com.avago.core.data.domain.DocumentKind
import com.avago.feature.docs.model.DocTypes
import com.avago.feature.docs.viewmodel.DocAddViewModel
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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

    LaunchedEffect(state) {
        if (state is DocAddViewModel.UiState.Done) onSaved()
    }

    val scanLauncher = rememberDocScanLauncher { result: DocScanResult? ->
        if (result != null && (result.pdfUri != null || result.pageUris.isNotEmpty())) {
            viewModel.onScanned(result.pdfUri, result.pageUris)
        } else {
            viewModel.resetToIdle()
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.onImported(uri) else viewModel.resetToIdle()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.doc_add_title)) },
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
                DocAddViewModel.UiState.Idle -> SourcePicker(
                    onScanClick = {
                        viewModel.onPicking()
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
                        viewModel.onPicking()
                        fileLauncher.launch(arrayOf("image/*", "application/pdf"))
                    },
                )

                DocAddViewModel.UiState.Picking, DocAddViewModel.UiState.Done -> CenteredLoader(message = null)

                is DocAddViewModel.UiState.Recognizing -> CenteredLoader(
                    message = stringResource(R.string.doc_add_ocr_processing),
                )

                is DocAddViewModel.UiState.Editing -> DocFormContent(
                    form = s.form,
                    onSave = { form, title, notes -> viewModel.save(form, title, notes) },
                )

                is DocAddViewModel.UiState.Saving -> CenteredLoader(message = stringResource(R.string.doc_add_saving))

                is DocAddViewModel.UiState.Error -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = s.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.dismissError() }) {
                        Text(stringResource(R.string.doc_add_back))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocFormContent(
    form: DocAddViewModel.Form,
    onSave: (form: DocAddViewModel.Form, title: String, notes: String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var titleError by remember { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(form.kind) }
    var issuer by rememberSaveable { mutableStateOf(form.issuerName) }
    var amount by rememberSaveable { mutableStateOf(form.amount) }
    var currency by rememberSaveable { mutableStateOf(form.currency) }
    var issuedOnDay by rememberSaveable { mutableStateOf(form.issuedOn?.toEpochDay()) }
    var warrantyDay by rememberSaveable { mutableStateOf(form.warrantyEndsOn?.toEpochDay()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    val issuedOn = issuedOnDay?.let(LocalDate::ofEpochDay)
    val warrantyEndsOn = warrantyDay?.let(LocalDate::ofEpochDay)

    when (picking) {
        PICK_ISSUED -> DocDatePicker(issuedOn, { issuedOnDay = it?.toEpochDay(); picking = null }, { picking = null })
        PICK_WARRANTY -> DocDatePicker(warrantyEndsOn, { warrantyDay = it?.toEpochDay(); picking = null }, { picking = null })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; titleError = false },
            label = { Text(stringResource(R.string.doc_add_field_name)) },
            isError = titleError,
            supportingText = if (titleError) {
                { Text(stringResource(R.string.doc_add_field_name_required)) }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        KindField(kind) { kind = it }

        HorizontalDivider()

        DateField(stringResource(R.string.doc_add_field_date), issuedOn, onClear = { issuedOnDay = null }) { picking = PICK_ISSUED }

        OutlinedTextField(
            value = issuer,
            onValueChange = { issuer = it },
            label = { Text(stringResource(R.string.doc_add_field_vendor)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.doc_add_field_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.take(3).uppercase() },
                label = { Text(stringResource(R.string.doc_add_field_currency)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f),
            )
        }

        DateField(stringResource(R.string.doc_add_field_warranty_end), warrantyEndsOn, onClear = { warrantyDay = null }) { picking = PICK_WARRANTY }

        HorizontalDivider()

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.doc_add_field_notes)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        if (form.rawText.isNotBlank()) {
            Text(
                text = stringResource(R.string.doc_add_ocr_raw_text),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = form.rawText.take(500) + if (form.rawText.length > 500) "\u2026" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (title.isBlank()) {
                    titleError = true
                } else {
                    onSave(
                        form.copy(
                            kind = kind,
                            issuerName = issuer,
                            amount = amount,
                            currency = currency,
                            issuedOn = issuedOn,
                            warrantyEndsOn = warrantyEndsOn,
                        ),
                        title.trim(),
                        notes,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.doc_add_save))
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

private const val PICK_ISSUED = "issued"
private const val PICK_WARRANTY = "warranty"

// ---------------------------------------------------------------------------
// Source picker
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
