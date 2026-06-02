package com.avago.feature.log.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.avago.core.ui.CategoryItem
import com.avago.core.ui.GlobalCategoryPickerScreen
import com.avago.core.ui.MarkdownText
import com.avago.feature.log.R
import com.avago.feature.log.viewmodel.AddEditLogViewModel
import com.avago.feature.log.viewmodel.CostMode
import com.avago.feature.log.viewmodel.ItemAttributeDef
import com.avago.feature.log.viewmodel.LogValidationError
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLogScreen(
    entryId: String? = null,
    preselectedAssetId: String? = null,
    initialCategory: String? = null,
    /** Result from PerformedByPickerScreen, delivered via SavedStateHandle → nav parameter. */
    performedByUserId: String? = null,
    performedByName: String? = null,
    /** Result from MaintenanceScannerScreen — part ID to pre-fill as a cost line part. */
    scannedPartId: String? = null,
    onBack: () -> Unit,
    onSaved: (entryId: String) -> Unit,
    onOpenAssetPicker: () -> Unit,
    onOpenPerformedByPicker: () -> Unit,
    /** Opens the MaintenanceScannerScreen to scan an asset tag or part barcode. */
    onOpenMaintenanceScanner: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AddEditLogViewModel = hiltViewModel(),
) {
    LaunchedEffect(entryId) {
        if (entryId != null) viewModel.loadForEdit(entryId)
        else viewModel.loadCategories()
        viewModel.loadInspectionFields()
    }

    // Pre-fill asset if navigated from asset screen
    LaunchedEffect(preselectedAssetId) {
        if (preselectedAssetId != null && entryId == null) {
            viewModel.onAssetSelected(preselectedAssetId, null)
        }
    }

    // iOS parity: when the user picks a category before opening the form,
    // pre-fill it once so they don't have to pick it again inside the form.
    LaunchedEffect(initialCategory) {
        if (initialCategory != null && entryId == null) {
            viewModel.onCategoryChanged(initialCategory)
        }
    }

    // Apply performer result from picker (delivered via nav SavedStateHandle)
    LaunchedEffect(performedByUserId, performedByName) {
        if (performedByUserId != null || performedByName != null) {
            viewModel.onPerformedBySelected(performedByUserId, performedByName)
        }
    }

    // Apply scanned part from MaintenanceScannerScreen (pre-fills itemized cost line)
    LaunchedEffect(scannedPartId) {
        if (scannedPartId != null) {
            viewModel.onScannedPartSelected(scannedPartId)
        }
    }

    val form by viewModel.form.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Map the generic "save_failed" error code to a localised string for the snackbar.
    val saveFailedMessage = stringResource(R.string.log_entry_alert_save_failed_message)
    LaunchedEffect(form.saveError) {
        form.saveError?.let { code ->
            val message = if (code == "save_failed") saveFailedMessage else code
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    // Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { viewModel.addPhotoUri(it) }
    }

    // Camera capture: create a temp file URI via FileProvider so the camera can write to it.
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) captureUri?.let { viewModel.addPhotoUri(it) }
    }

    fun launchCamera() {
        val tmpFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tmpFile)
        captureUri = uri
        cameraLauncher.launch(uri)
    }

    // Sheet state for cost lines editor
    val costSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCostSheet by remember { mutableStateOf(false) }

    // Category picker state
    var showCategoryPicker by remember { mutableStateOf(false) }

    // Inspection subtype picker
    var showInspectionSubtypePicker by remember { mutableStateOf(false) }

    // When log type switches to inspection, load subtypes and show picker if none selected
    LaunchedEffect(form.logType) {
        if (form.logType == "inspection") {
            viewModel.loadInspectionSubtypes()
            if (form.inspectionSubtype == null) {
                showInspectionSubtypePicker = true
            }
        }
    }

    // Date picker state
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = form.entryDate)

    val dateFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    // Notes preview toggle
    var notesPreviewMode by remember { mutableStateOf(false) }

    // ---------------------------------------------------------------------------
    // Validation error alert dialog — mirrors iOS UIAlertController behaviour
    // ---------------------------------------------------------------------------
    val validationError = form.validationError
    if (validationError != null) {
        val (title, message) = when (validationError) {
            LogValidationError.TITLE_REQUIRED -> Pair(
                stringResource(R.string.log_entry_alert_title_required_title),
                stringResource(R.string.log_entry_alert_title_required_message),
            )
            LogValidationError.NO_ASSET -> Pair(
                stringResource(R.string.log_entry_alert_no_asset_title),
                stringResource(R.string.log_entry_alert_no_asset_message),
            )
            LogValidationError.NO_ACCOUNT -> Pair(
                stringResource(R.string.log_entry_alert_save_failed_title),
                stringResource(R.string.log_entry_alert_save_failed_message),
            )
        }
        AlertDialog(
            onDismissRequest = { viewModel.clearValidationError() },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearValidationError() }) {
                    Text(stringResource(com.avago.core.ui.R.string.common_done))
                }
            },
        )
    }

    // ---------------------------------------------------------------------------
    // Date picker dialog
    // ---------------------------------------------------------------------------
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onEntryDateChanged(it) }
                    showDatePicker = false
                }) { Text(stringResource(com.avago.core.ui.R.string.common_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(com.avago.core.ui.R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ---------------------------------------------------------------------------
    // Global category picker bottom sheet
    // ---------------------------------------------------------------------------
    if (showCategoryPicker) {
        GlobalCategoryPickerScreen(
            categories = buildLogCategoryItems(form.availableCategories),
            onSelect = { item ->
                viewModel.onCategoryChanged(if (item.key == "__none__") null else item.key)
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    // ---------------------------------------------------------------------------
    // Inspection subtype picker dialog — mirrors iOS action sheet (Basic / Full / custom)
    // ---------------------------------------------------------------------------
    if (showInspectionSubtypePicker) {
        val subtypes = form.availableInspectionSubtypes
        val options: List<Pair<String, String?>> = if (subtypes.isEmpty()) {
            listOf("Base" to "Basic", "Base" to "Full")
        } else {
            buildList {
                if (subtypes.contains("Base")) {
                    add("Base" to "Basic")
                    add("Base" to "Full")
                }
                subtypes.filter { it != "Base" }.forEach { add(it to null) }
            }
        }
        AlertDialog(
            onDismissRequest = { showInspectionSubtypePicker = false },
            title = { Text(stringResource(R.string.log_entry_inspection_type_title)) },
            text = {
                Column {
                    options.forEachIndexed { idx, (subtype, mode) ->
                        val label = when {
                            mode == "Basic" -> stringResource(R.string.log_entry_inspection_basic)
                            mode == "Full" -> stringResource(R.string.log_entry_inspection_full)
                            else -> subtype
                        }
                        TextButton(
                            onClick = {
                                viewModel.setInspectionSubtype(subtype, mode)
                                showInspectionSubtypePicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        if (idx < options.lastIndex) HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showInspectionSubtypePicker = false }) {
                    Text(stringResource(com.avago.core.ui.R.string.common_cancel))
                }
            },
        )
    }

    // ---------------------------------------------------------------------------
    // Cost lines editor bottom sheet
    // ---------------------------------------------------------------------------
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (entryId != null) stringResource(com.avago.core.ui.R.string.nav_edit_entry)
                        else stringResource(com.avago.core.ui.R.string.nav_new_entry)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(com.avago.core.ui.R.string.common_cancel),
                        )
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        FormRow(
                            label = stringResource(R.string.log_entry_label_asset),
                            value = form.assetName ?: form.assetId
                                ?: stringResource(R.string.log_entry_placeholder_select_asset),
                            onClick = onOpenAssetPicker,
                            isPlaceholder = form.assetId == null,
                        )
                    }
                    IconButton(onClick = onOpenMaintenanceScanner) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.maintenance_scanner_title),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            HorizontalDivider()

            // --------------- Title ---------------
            // Matches iOS position: large title field at top of content area.
            FormSection {
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { viewModel.onTitleChanged(it) },
                    label = { Text(stringResource(R.string.log_entry_placeholder_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            HorizontalDivider()

            // --------------- Category picker ---------------
            FormSection {
                FormRow(
                    label = stringResource(R.string.log_entry_label_category),
                    value = form.category?.replace("_", " ")
                        ?.split(" ")
                        ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                        ?: stringResource(R.string.log_entry_placeholder_select_category),
                    onClick = { showCategoryPicker = true },
                    isPlaceholder = form.category == null,
                )
            }

            HorizontalDivider()

            // --------------- Date row ---------------
            FormSection {
                FormRow(
                    label = stringResource(R.string.log_entry_label_date),
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

            // --------------- Cost ---------------
            // Matches iOS section order: cost before odometer, before Performed By.
            FormSection {
                Text(
                    text = stringResource(R.string.log_entry_section_cost),
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
                    ) { Text(stringResource(R.string.log_entry_cost_mode_total)) }
                    SegmentedButton(
                        selected = form.costMode == CostMode.ITEMIZED,
                        onClick = { viewModel.onCostModeChanged(CostMode.ITEMIZED) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.log_entry_cost_mode_itemized)) }
                }

                Spacer(Modifier.height(10.dp))

                when (form.costMode) {
                    CostMode.TOTAL -> {
                        OutlinedTextField(
                            value = form.totalCost,
                            onValueChange = { viewModel.onTotalCostChanged(it) },
                            label = { Text(stringResource(R.string.log_entry_label_total_cost)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            prefix = { Text("$") },
                        )
                    }
                    CostMode.ITEMIZED -> {
                        // Disclosure button — mirrors iOS "Itemize parts & labor" card
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
                                        text = stringResource(R.string.log_entry_itemize_label),
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (form.costLineCount > 0) {
                                        val linesLabel = if (form.costLineCount == 1)
                                            stringResource(R.string.log_entry_itemize_lines, form.costLineCount, form.itemizedTotal)
                                        else
                                            stringResource(R.string.log_entry_itemize_lines_plural, form.costLineCount, form.itemizedTotal)
                                        Text(
                                            text = linesLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.log_entry_itemize_tap_hint),
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
                    label = { Text(stringResource(R.string.log_entry_meter_optional, form.meterLabel)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

            HorizontalDivider()

            // --------------- Performed By ---------------
            // Matches iOS position: after odometer, before notes.
            FormSection {
                FormRow(
                    label = stringResource(R.string.log_entry_label_performed_by),
                    value = form.performedByName
                        ?: stringResource(R.string.log_entry_placeholder_performed_by),
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

            // --------------- Notes ---------------
            FormSection {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.log_entry_section_notes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { notesPreviewMode = !notesPreviewMode }) {
                        Text(
                            if (notesPreviewMode) stringResource(R.string.log_entry_notes_edit)
                            else stringResource(R.string.log_entry_notes_preview)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (notesPreviewMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        if (form.notes.isBlank()) {
                            Text(
                                text = stringResource(R.string.log_entry_notes_empty),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            MarkdownText(
                                text = form.notes,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = form.notes,
                        onValueChange = { viewModel.onNotesChanged(it) },
                        label = { Text(stringResource(R.string.log_entry_placeholder_notes)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            }

            HorizontalDivider()

            // --------------- Photos (Attachments) ---------------
            // Section mirrors iOS log_entry.section_attachments / log_entry.action_add_attachment.
            FormSection {
                Text(
                    text = stringResource(R.string.log_entry_photos_label),
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
                                    contentDescription = stringResource(R.string.log_entry_photos_label),
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
                                    contentDescription = stringResource(com.avago.core.ui.R.string.common_clear),
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
                        Text(stringResource(R.string.log_entry_photo_gallery))
                    }
                    Button(
                        onClick = { launchCamera() },
                        colors = ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.log_entry_photo_camera))
                    }
                }
            }

            // --------------- Item Details (category-specific attributes) ---------------
            if (form.itemAttributeDefs.isNotEmpty()) {
                HorizontalDivider()
                FormSection {
                    Text(
                        text = stringResource(R.string.log_entry_section_item_details),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    ItemAttributesRenderer(
                        defs = form.itemAttributeDefs,
                        values = form.itemAttributes,
                        onValueChanged = { key, value -> viewModel.onItemAttributeChanged(key, value) },
                    )
                }
            }

            // --------------- Fuel volume (shown for fuel-category entries) ---------------
            // Matches iOS: fuel card appears conditionally after item details.
            if (form.category?.lowercase()?.contains("fuel") == true) {
                HorizontalDivider()
                FormSection {
                    Text(
                        text = stringResource(R.string.log_entry_fuel_volume_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = form.fuelVolume,
                            onValueChange = { viewModel.onFuelVolumeChanged(it) },
                            label = { Text(stringResource(R.string.log_entry_fuel_volume_optional)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        SingleChoiceSegmentedButtonRow {
                            listOf("gallon", "liter").forEachIndexed { idx, unit ->
                                SegmentedButton(
                                    selected = form.fuelVolumeUnit == unit,
                                    onClick = { viewModel.onFuelVolumeUnitChanged(unit) },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2),
                                    label = {
                                        Text(
                                            if (unit == "gallon")
                                                stringResource(R.string.log_entry_fuel_unit_gallon)
                                            else
                                                stringResource(R.string.log_entry_fuel_unit_liter)
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // --------------- Inspection form (shown only when logType == "inspection") ---------------
            if (form.logType == "inspection") {
                HorizontalDivider()
                FormSection {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.log_entry_inspection_checklist_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (form.inspectionSubtype != null) {
                                val subtypeLabel = when {
                                    form.inspectionMode != null ->
                                        "${form.inspectionMode} (${form.inspectionSubtype})"
                                    else -> form.inspectionSubtype.orEmpty()
                                }
                                Text(
                                    text = subtypeLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        TextButton(onClick = { showInspectionSubtypePicker = true }) {
                            Text(
                                if (form.inspectionSubtype == null)
                                    stringResource(R.string.log_entry_inspection_select_type)
                                else
                                    stringResource(R.string.log_entry_inspection_change_type)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (form.inspectionFields.isNotEmpty()) {
                        InspectionFormRenderer(
                            fields = form.inspectionFields,
                            answers = form.inspectionAnswers,
                            onAnswerChanged = { key, value -> viewModel.onInspectionAnswerChanged(key, value) },
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.log_entry_inspection_no_fields),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
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
                        Text(stringResource(R.string.log_entry_saving))
                    } else {
                        Text(
                            if (entryId != null) stringResource(R.string.log_entry_save_changes_button)
                            else stringResource(R.string.log_entry_save_button)
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Item attributes renderer
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ItemAttributesRenderer(
    defs: List<ItemAttributeDef>,
    values: Map<String, String>,
    onValueChanged: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        defs.forEach { def ->
            val current = values[def.key] ?: ""
            when (def.fieldType) {
                "text" -> OutlinedTextField(
                    value = current,
                    onValueChange = { onValueChanged(def.key, it) },
                    label = { Text(if (def.unit != null) "${def.label} (${def.unit})" else def.label) },
                    modifier = Modifier.fillMaxWidth(),
                )
                "number" -> OutlinedTextField(
                    value = current,
                    onValueChange = { onValueChanged(def.key, it) },
                    label = { Text(def.label) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = if (def.unit != null) ({ Text(def.unit) }) else null,
                )
                "enum" -> {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = current,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (def.unit != null) "${def.label} (${def.unit})" else def.label) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            def.options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        onValueChanged(def.key, option)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                else -> OutlinedTextField(
                    value = current,
                    onValueChange = { onValueChanged(def.key, it) },
                    label = { Text(def.label) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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

// ---------------------------------------------------------------------------
// Category helper
// ---------------------------------------------------------------------------

/**
 * Maps the list of category strings (loaded from config) to [CategoryItem]s
 * for use in [GlobalCategoryPickerScreen].  A "None" sentinel is prepended so
 * the user can clear the selection.
 */
private fun buildLogCategoryItems(availableCategories: List<String>): List<CategoryItem> {
    val none = CategoryItem(key = "__none__", displayName = "None", group = "COMMON")
    val rest = availableCategories.map { cat ->
        val iconName = com.avago.core.ui.categoryIconName(cat)
        CategoryItem(
            key = cat,
            displayName = cat.replace("_", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } },
            iconAssetName = iconName,
            color = com.avago.core.ui.categoryBadgeColor(iconName),
            group = com.avago.core.ui.categoryGroup(cat),
        )
    }
    return listOf(none) + rest
}
