package com.avago.feature.log.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.avago.feature.log.model.parseInspectionFields
import com.avago.feature.log.viewmodel.AddEditLogViewModel
import com.avago.feature.log.viewmodel.CostMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LOG_TYPES = listOf(
    "service" to "Service",
    "inspection" to "Inspection",
    "note" to "Note",
    "fuel" to "Fuel",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLogScreen(
    entryId: String? = null,
    preselectedAssetId: String? = null,
    /** Result from PerformedByPickerScreen, delivered via SavedStateHandle → nav parameter. */
    performedByUserId: String? = null,
    performedByName: String? = null,
    onBack: () -> Unit,
    onSaved: (entryId: String) -> Unit,
    onOpenAssetPicker: () -> Unit,
    onOpenPerformedByPicker: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEditLogViewModel = hiltViewModel(),
) {
    // Load existing entry for edit
    LaunchedEffect(entryId) {
        if (entryId != null) viewModel.loadForEdit(entryId)
        else viewModel.loadCategories()
    }

    // Pre-fill asset if navigated from asset screen
    LaunchedEffect(preselectedAssetId) {
        if (preselectedAssetId != null && entryId == null) {
            viewModel.onAssetSelected(preselectedAssetId, null)
        }
    }

    // Apply performer result from picker (delivered via nav SavedStateHandle)
    LaunchedEffect(performedByUserId, performedByName) {
        if (performedByUserId != null || performedByName != null) {
            viewModel.onPerformedBySelected(performedByUserId, performedByName)
        }
    }

    val form by viewModel.form.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show save errors via snackbar
    LaunchedEffect(form.saveError) {
        form.saveError?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
    }

    // Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { viewModel.addPhotoUri(it) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        // Camera URI is handled below via captureUri
    }
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    // Sheet state for cost lines editor
    val costSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCostSheet by remember { mutableStateOf(false) }

    // Date picker state
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = form.entryDate)

    val dateFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onEntryDateChanged(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showCostSheet) {
        CostLinesEditorSheet(
            costLines = form.pendingCostLines,
            onAdd = { viewModel.addCostLine(it) },
            onUpdate = { viewModel.updateCostLine(it) },
            onRemove = { viewModel.removeCostLine(it) },
            onDismiss = { showCostSheet = false },
            sheetState = costSheetState,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entryId != null) "Edit Log Entry" else "New Log Entry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        if (form.isLoadingExisting) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // --------------- Asset row ---------------
            FormSection {
                FormRow(
                    label = "Asset",
                    value = form.assetName ?: form.assetId ?: "Select asset",
                    onClick = onOpenAssetPicker,
                    isPlaceholder = form.assetId == null,
                )
            }

            HorizontalDivider()

            // --------------- Log type picker ---------------
            FormSection {
                Text(
                    text = "Log Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = LOG_TYPES.firstOrNull { it.first == form.logType }?.second ?: form.logType,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        LOG_TYPES.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.onLogTypeChanged(key)
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // --------------- Category picker ---------------
            FormSection {
                var catExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = !catExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = form.category ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = { viewModel.onCategoryChanged(null); catExpanded = false },
                        )
                        form.availableCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = { viewModel.onCategoryChanged(cat); catExpanded = false },
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // --------------- Title ---------------
            FormSection {
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { viewModel.onTitleChanged(it) },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            HorizontalDivider()

            // --------------- Date row ---------------
            FormSection {
                FormRow(
                    label = "Date",
                    value = dateFormatter.format(Date(form.entryDate)),
                    onClick = { showDatePicker = true },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            HorizontalDivider()

            // --------------- Performed By ---------------
            FormSection {
                FormRow(
                    label = "Performed By",
                    value = form.performedByName ?: "Select",
                    onClick = onOpenPerformedByPicker,
                    isPlaceholder = form.performedByName == null,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            HorizontalDivider()

            // --------------- Cost ---------------
            FormSection {
                Text(
                    text = "Cost",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                // Segmented control: Total / Itemized
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = form.costMode == CostMode.TOTAL,
                        onClick = { viewModel.onCostModeChanged(CostMode.TOTAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Total") }
                    SegmentedButton(
                        selected = form.costMode == CostMode.ITEMIZED,
                        onClick = { viewModel.onCostModeChanged(CostMode.ITEMIZED) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Itemized") }
                }

                Spacer(Modifier.height(10.dp))

                when (form.costMode) {
                    CostMode.TOTAL -> {
                        OutlinedTextField(
                            value = form.totalCost,
                            onValueChange = { viewModel.onTotalCostChanged(it) },
                            label = { Text("Total cost") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            prefix = { Text("$") },
                        )
                    }
                    CostMode.ITEMIZED -> {
                        // Disclosure button to open cost lines editor
                        Card(
                            onClick = { showCostSheet = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Itemize parts & labor",
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (form.costLineCount > 0) {
                                        Text(
                                            text = "${form.costLineCount} line${if (form.costLineCount > 1) "s" else ""} · ${"$%.2f".format(form.itemizedTotal)} total",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Text(
                                            text = "Tap to add parts and labor",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // --------------- Meter reading ---------------
            FormSection {
                OutlinedTextField(
                    value = form.meterReading,
                    onValueChange = { viewModel.onMeterReadingChanged(it) },
                    label = { Text("Meter reading (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

            HorizontalDivider()

            // --------------- Notes ---------------
            FormSection {
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { viewModel.onNotesChanged(it) },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }

            HorizontalDivider()

            // --------------- Photos ---------------
            FormSection {
                Text(
                    text = "Photos",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                if (form.photoUris.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(form.photoUris) { uri ->
                            Box {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(MaterialTheme.shapes.small),
                                )
                                IconButton(
                                    onClick = { viewModel.removePhotoUri(uri) },
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.TopEnd),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove photo",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Gallery")
                    }
                    Button(
                        onClick = {
                            // System camera intent — full CameraX implementation is future work
                            galleryLauncher.launch("image/*")
                        },
                        colors = ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Camera")
                    }
                }
            }

            // --------------- Inspection form (shown only when logType == "inspection") ---------------
            if (form.logType == "inspection") {
                HorizontalDivider()
                FormSection {
                    Text(
                        text = "Inspection Checklist",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Fields come from ConfigEntity in a real account; show a placeholder row
                    // if none are loaded yet. The InspectionFormRenderer handles the actual fields.
                    InspectionFormRenderer(
                        fields = emptyList(), // TODO: wire up config-based inspection fields
                        answers = form.inspectionAnswers,
                        onAnswerChanged = { key, value -> viewModel.onInspectionAnswerChanged(key, value) },
                    )
                    if (form.inspectionAnswers.isEmpty()) {
                        Text(
                            text = "No inspection fields configured for this asset type.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider()

            // --------------- Save button ---------------
            FormSection {
                Button(
                    onClick = { viewModel.save(onSuccess = onSaved) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !form.isSaving,
                ) {
                    if (form.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (form.isSaving) "Saving…" else if (entryId != null) "Save Changes" else "Save Log Entry")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Form layout helpers
// ---------------------------------------------------------------------------

@Composable
private fun FormSection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun FormRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    isPlaceholder: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaceholder)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface,
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
