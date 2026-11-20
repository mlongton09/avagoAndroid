package com.avago.feature.docs.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.Document
import com.avago.core.data.domain.DocumentEdit
import com.avago.core.data.domain.DocumentRevision
import com.avago.core.data.domain.StoredFile
import com.avago.core.docscan.DocScanResult
import com.avago.core.docscan.launchDocScan
import com.avago.core.docscan.rememberDocScanLauncher
import com.avago.feature.docs.R
import com.avago.feature.docs.model.DocTypes
import com.avago.feature.docs.model.formatAmount
import com.avago.feature.docs.model.formatSize
import com.avago.feature.docs.viewmodel.DocDetailViewModel
import com.avago.feature.docs.viewmodel.DocDetailViewModel.Companion.extractionType
import timber.log.Timber
import java.io.File
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocDetailScreen(onBack: () -> Unit, viewModel: DocDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val revisions by viewModel.revisions.collectAsStateWithLifecycle()
    val canEdit by viewModel.canEdit.collectAsStateWithLifecycle()
    val canDelete by viewModel.canDelete.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val noViewer = stringResource(R.string.doc_detail_no_viewer)

    var menu by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var extracted by remember { mutableStateOf<DocumentEdit?>(null) }
    val reviewExtracted = stringResource(R.string.doc_detail_reextract_review)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DocDetailViewModel.Event.Open -> if (!context.view(event.file, event.mimeType)) snackbar.showSnackbar(noViewer)
                is DocDetailViewModel.Event.Failed -> snackbar.showSnackbar(resources.getString(event.messageRes))
                DocDetailViewModel.Event.Deleted -> onBack()
                is DocDetailViewModel.Event.Extracted -> {
                    extracted = event.edit
                    editing = true
                    snackbar.showSnackbar(reviewExtracted)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.replaceFile(uri)
    }
    val scanLauncher = rememberDocScanLauncher { result: DocScanResult? ->
        (result?.pdfUri ?: result?.pageUris?.singleOrNull())?.let(viewModel::replaceFile)
    }

    val document = (state as? DocDetailViewModel.State.Loaded)?.document

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(document?.displayTitle ?: stringResource(R.string.doc_untitled), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.doc_add_back))
                    }
                },
                actions = {
                    if (document != null && canEdit) {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.doc_detail_edit))
                        }
                    }
                    if (document != null && (canEdit || canDelete)) {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.doc_detail_more))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (canEdit) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.doc_detail_replace_scan)) },
                                        onClick = {
                                            menu = false
                                            (context as? Activity)?.let { activity ->
                                                launchDocScan(activity, scanLauncher) { e -> Timber.e(e, "[DocDetailScreen] scanner failed to start") }
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.doc_detail_replace_import)) },
                                        onClick = {
                                            menu = false
                                            importLauncher.launch(arrayOf("image/*", "application/pdf"))
                                        },
                                    )
                                    if (document.kind.extractionType != null && document.file?.isDownloadable == true) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.doc_detail_reextract)) },
                                            enabled = !busy,
                                            onClick = {
                                                menu = false
                                                viewModel.reextract()
                                            },
                                        )
                                    }
                                }
                                if (canDelete) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.doc_detail_delete), color = MaterialTheme.colorScheme.error) },
                                        onClick = { menu = false; confirmDelete = true },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                DocDetailViewModel.State.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                DocDetailViewModel.State.Missing -> Text(
                    stringResource(R.string.doc_detail_missing),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is DocDetailViewModel.State.Loaded -> DocumentBody(s.document, revisions, onOpen = viewModel::open)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    if (editing && document != null) {
        ModalBottomSheet(
            onDismissRequest = { editing = false; extracted = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            DocEditForm(document, extracted, busy) { edit ->
                viewModel.save(edit) {
                    editing = false
                    extracted = null
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.doc_detail_delete_title)) },
            text = { Text(stringResource(R.string.doc_detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.doc_detail_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.doc_add_cancel)) } },
        )
    }
}

@Composable
private fun DocumentBody(doc: Document, revisions: List<DocumentRevision>, onOpen: (StoredFile) -> Unit) {
    val type = DocTypes.forKind(doc.kind)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "file") { FileCard(doc.file, onOpen) }
        item(key = "details") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(stringResource(R.string.doc_add_field_type), stringResource(type.labelResId))
                    if (doc.status != "issued") Field(stringResource(R.string.doc_detail_status), doc.status.replaceFirstChar { it.uppercase() })
                    doc.issuedOn?.let { Field(stringResource(R.string.doc_add_field_date), it.format(dateFormatter)) }
                    doc.issuerName?.let { Field(stringResource(R.string.doc_add_field_vendor), it) }
                    doc.total?.let { Field(stringResource(R.string.doc_add_field_amount), formatAmount(it, doc.currency)) }
                    doc.warrantyEndsOn?.let { ends ->
                        val expired = ends.isBefore(LocalDate.now())
                        Field(
                            stringResource(R.string.doc_add_field_warranty_end),
                            ends.format(dateFormatter) + if (expired) " \u00b7 " + stringResource(R.string.doc_detail_warranty_expired) else "",
                            isError = expired,
                        )
                    }
                    doc.notes?.let { Field(stringResource(R.string.doc_add_field_notes), it) }
                }
            }
        }
        if (revisions.size > 1) {
            item(key = "revisions") {
                Text(stringResource(R.string.doc_detail_revisions), style = MaterialTheme.typography.titleSmall)
            }
            items(revisions, key = { it.id }) { rev ->
                val file = rev.file
                ListItem(
                    modifier = if (file != null) Modifier.clickable { onOpen(file) } else Modifier,
                    headlineContent = { Text(stringResource(R.string.doc_detail_revision, rev.revisionNo)) },
                    supportingContent = {
                        Text(listOfNotNull(file?.name, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(rev.createdAt))).joinToString(" \u00b7 "))
                    },
                )
                HorizontalDivider()
            }
        }
        item(key = "added") {
            Text(
                stringResource(R.string.doc_detail_added, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(doc.createdAt))),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileCard(file: StoredFile?, onOpen: (StoredFile) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val pending = file != null && !file.isDownloadable && file.localFile == null
            Icon(if (pending) Icons.Default.CloudUpload else Icons.Default.InsertDriveFile, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file?.name ?: stringResource(R.string.doc_detail_file_none), style = MaterialTheme.typography.bodyLarge)
                val meta = listOfNotNull(file?.mimeType?.substringAfter('/')?.uppercase(), file?.sizeBytes?.let(::formatSize))
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString(" \u00b7 "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (file != null && !file.isDownloadable) {
                    Text(
                        stringResource(R.string.doc_detail_file_uploading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (file != null) {
                OutlinedButton(onClick = { onOpen(file) }, enabled = !pending) { Text(stringResource(R.string.doc_detail_open)) }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, isError: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The metadata form of an existing document, started from [initial] (re-extracted fields) or the
 * stored values; emptied fields are cleared.
 */
@Composable
private fun DocEditForm(doc: Document, initial: DocumentEdit?, busy: Boolean, onSave: (DocumentEdit) -> Unit) {
    val start = initial ?: DocumentEdit(doc.kind, doc.title, doc.issuedOn, doc.issuerName, doc.total, doc.currency, doc.warrantyEndsOn, doc.notes)
    var kind by rememberSaveable { mutableStateOf(start.kind) }
    var title by rememberSaveable { mutableStateOf(start.title.orEmpty()) }
    var issuer by rememberSaveable { mutableStateOf(start.issuerName.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(start.total?.toPlainString().orEmpty()) }
    var currency by rememberSaveable { mutableStateOf(start.currency.orEmpty()) }
    var issuedOnDay by rememberSaveable { mutableStateOf(start.issuedOn?.toEpochDay()) }
    var warrantyDay by rememberSaveable { mutableStateOf(start.warrantyEndsOn?.toEpochDay()) }
    var notes by rememberSaveable { mutableStateOf(start.notes.orEmpty()) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val issuedOn = issuedOnDay?.let(LocalDate::ofEpochDay)
    val warrantyEndsOn = warrantyDay?.let(LocalDate::ofEpochDay)
    val parsedAmount = amount.trim().takeIf { it.isNotEmpty() }?.replace(',', '.')?.toBigDecimalOrNull()
    val amountError = amount.isNotBlank() && parsedAmount == null
    val currencyError = parsedAmount != null && currency.trim().length != 3

    when (picking) {
        "issued" -> DocDatePicker(issuedOn, { issuedOnDay = it?.toEpochDay(); picking = null }, { picking = null })
        "warranty" -> DocDatePicker(warrantyEndsOn, { warrantyDay = it?.toEpochDay(); picking = null }, { picking = null })
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.doc_edit_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.doc_add_field_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        KindField(kind) { kind = it }
        DateField(stringResource(R.string.doc_add_field_date), issuedOn, onClear = { issuedOnDay = null }) { picking = "issued" }
        OutlinedTextField(
            value = issuer,
            onValueChange = { issuer = it },
            label = { Text(stringResource(R.string.doc_add_field_vendor)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.doc_add_field_amount)) },
                isError = amountError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.take(3).uppercase() },
                label = { Text(stringResource(R.string.doc_add_field_currency)) },
                isError = currencyError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f),
            )
        }
        DateField(stringResource(R.string.doc_add_field_warranty_end), warrantyEndsOn, onClear = { warrantyDay = null }) { picking = "warranty" }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.doc_add_field_notes)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onSave(
                    DocumentEdit(
                        kind = kind, title = title, issuedOn = issuedOn, issuerName = issuer, total = parsedAmount,
                        currency = currency.takeIf { parsedAmount != null }, warrantyEndsOn = warrantyEndsOn, notes = notes,
                    ),
                )
            },
            enabled = !busy && !amountError && !currencyError,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.doc_add_save)) }
        Spacer(Modifier.height(24.dp))
    }
}

/** Opens [file] in a viewer app through the app's FileProvider; false when no app can show it. */
private fun Context.view(file: File, mimeType: String): Boolean {
    val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
