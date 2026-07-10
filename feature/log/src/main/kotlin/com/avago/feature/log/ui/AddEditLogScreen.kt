package com.avago.feature.log.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.avago.core.ui.CategoryItem
import com.avago.core.ui.GlobalCategoryPickerScreen
import com.avago.core.ui.categoryBadgeColor
import com.avago.core.ui.categoryGroup
import com.avago.core.ui.categoryIconName
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
    /** Work order ID — when set, shows the WO timer card (mirrors iOS WOTimerView). */
    workOrderId: String? = null,
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

    LaunchedEffect(preselectedAssetId) {
        if (preselectedAssetId != null && entryId == null) {
            viewModel.onAssetSelected(preselectedAssetId, null)
        }
    }

    LaunchedEffect(initialCategory) {
        if (initialCategory != null && entryId == null) {
            val displayName = initialCategory.replace("_", " ").split(" ")
                .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
            viewModel.onCategoryChanged(initialCategory, displayName)
        }
    }

    LaunchedEffect(performedByUserId, performedByName) {
        if (performedByUserId != null || performedByName != null) {
            viewModel.onPerformedBySelected(performedByUserId, performedByName)
        }
    }

    LaunchedEffect(scannedPartId) {
        if (scannedPartId != null) viewModel.onScannedPartSelected(scannedPartId)
    }

    LaunchedEffect(workOrderId) {
        viewModel.setWorkOrderId(workOrderId)
    }

    val form by viewModel.form.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val timerStartedAt = form.timerStartedAt
    var timerElapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(timerStartedAt) {
        if (timerStartedAt != null) {
            while (true) {
                timerElapsedSeconds = (System.currentTimeMillis() - timerStartedAt) / 1000L
                kotlinx.coroutines.delay(1000L)
            }
        } else {
            timerElapsedSeconds = 0L
        }
    }

    val saveFailedMessage = stringResource(R.string.log_entry_alert_save_failed_message)
    LaunchedEffect(form.saveError) {
        form.saveError?.let { code ->
            val message = if (code == "save_failed") saveFailedMessage else code
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> -> uris.forEach { viewModel.addPhotoUri(it) } }

    var captureUri by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success) captureUri?.let { viewModel.addPhotoUri(it) } }

    // Permission launcher: on grant, launch the camera immediately
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val tmpFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tmpFile)
                captureUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Unable to launch camera") }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Camera permission is required to take photos") }
        }
    }

    fun launchCamera() {
        val camPerm = android.Manifest.permission.CAMERA
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, camPerm)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                val tmpFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tmpFile)
                captureUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Unable to launch camera") }
            }
        } else {
            cameraPermissionLauncher.launch(camPerm)
        }
    }

    val costSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCostSheet by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showInspectionSubtypePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    // Mirrors iOS itemAttrExpanded = true: card starts visible, row tap collapses/expands it.
    var detailsExpanded by remember(form.category) { mutableStateOf(true) }

    LaunchedEffect(form.logType) {
        if (form.logType == "inspection") {
            viewModel.loadInspectionSubtypes()
            if (form.inspectionSubtype == null) showInspectionSubtypePicker = true
        }
    }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = form.entryDate)
    val dateFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    // ---- Meter section labels derived from asset meterType ----
    val meterSectionHeader = when (form.meterType?.lowercase()) {
        "hours", "hour", "hr" -> stringResource(R.string.log_entry_section_hours)
        else -> stringResource(R.string.log_entry_section_odometer)
    }
    val meterUnitLabel = when (form.meterType?.lowercase()) {
        "hours", "hour", "hr" -> "hrs"
        else -> if (form.distanceUnit == "km") "km" else "mi"
    }
    val isDateMeter = form.meterType?.lowercase() == "date"

    // ---- Validation alert ----
    val validationError = form.validationError
    if (validationError != null) {
        val (dlgTitle, dlgMsg) = when (validationError) {
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
            title = { Text(dlgTitle) },
            text = { Text(dlgMsg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearValidationError() }) {
                    Text(stringResource(com.avago.core.ui.R.string.common_done))
                }
            },
        )
    }

    // ---- Date picker dialog ----
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
        ) { DatePicker(state = datePickerState) }
    }

    // ---- Category picker ----
    if (showCategoryPicker) {
        GlobalCategoryPickerScreen(
            categories = com.avago.core.ui.buildCategoryItems(
                keys = form.availableCategories,
                noneLabel = stringResource(com.avago.core.ui.R.string.common_none),
            ),
            onSelect = { item ->
                viewModel.onCategoryChanged(
                    if (item.key == "__none__") null else item.key,
                    if (item.key == "__none__") null else item.displayName,
                )
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    // ---- Inspection subtype picker ----
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
                            Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
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

    // ---- Attachment action sheet (iOS-style bottom sheet) ----
    if (showAttachmentMenu) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showAttachmentMenu = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                TextButton(
                    onClick = { launchCamera(); showAttachmentMenu = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text(
                        text = stringResource(R.string.log_entry_attachment_take_photo),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                HorizontalDivider()
                TextButton(
                    onClick = { galleryLauncher.launch("image/*"); showAttachmentMenu = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text(
                        text = stringResource(R.string.log_entry_attachment_photo_library),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    // ---- Cost lines editor bottom sheet ----
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
            // iOS: nav bar with stacked title + asset name subtitle, green Save button on right
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (entryId != null)
                                stringResource(com.avago.core.ui.R.string.nav_edit_entry)
                            else
                                stringResource(com.avago.core.ui.R.string.nav_new_entry),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (form.assetName != null) {
                            Text(
                                text = form.assetName!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(com.avago.core.ui.R.string.common_cancel),
                        )
                    }
                },
                actions = {
                    // iOS: "Save" in green (accentGreen = secondary token) on nav bar right
                    TextButton(
                        onClick = { focusManager.clearFocus(); viewModel.save(onSuccess = onSaved) },
                        enabled = !form.isSaving,
                    ) {
                        if (form.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        } else {
                            Text(
                                stringResource(com.avago.core.ui.R.string.common_save),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { innerPadding ->
        if (form.isLoadingExisting) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // iOS: contentStack leading/trailing inset = 16, spacing = 16
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))

            // ==================================================================
            // HEADER CARD — Title (22sp) + Category badge + Date button
            // iOS: buildHeaderCard() in AddLogItemViewController
            // ==================================================================
            FormCard {
                // Title: largeTitleFont (22sp Bold), no border, top=14/left=16/right=16/bottom=6
                BasicTextField(
                    value = form.title,
                    onValueChange = { viewModel.onTitleChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) viewModel.onTitleFocused()
                        },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = if (form.isTitleHint)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(Modifier.fillMaxWidth()) {
                            if (form.title.isEmpty() && !form.isTitleHint) {
                                Text(
                                    text = stringResource(R.string.log_entry_placeholder_title),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                CardSeparator()

                // Category badge (LEFT) + Date button (RIGHT) — 44dp row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadgePill(
                        categoryId = form.category,
                        onClick = { showCategoryPicker = true },
                    )
                    Spacer(Modifier.weight(1f))
                    // iOS: date shown as tappable label, opens inline picker
                    TextButton(
                        onClick = { showDatePicker = true },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = dateFormatter.format(Date(form.entryDate)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // ==================================================================
            // FUEL CARD — only shown for fuel_log category
            // ==================================================================
            if (form.category?.lowercase()?.contains("fuel") == true) {
                Spacer(Modifier.height(16.dp))
                FormCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
            }

            Spacer(Modifier.height(16.dp))

            // ==================================================================
            // DETAILS CARD — Cost mode segmented control + Cost | Odometer row
            // iOS: buildCostOdoRow() in AddLogItemViewController+Cost.swift
            // ==================================================================
            FormCard {
                // Row 1: "Cost" label LEFT + Total/Itemized segmented control RIGHT (44dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.log_cost_mode_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.widthIn(min = 180.dp).height(28.dp)) {
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
                }

                CardSeparator()

                // Row 2: COST field | vertical hairline | ODOMETER/HOURS field (44dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left column: COST
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.log_entry_section_cost),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "$",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val costEnabled = form.costMode != CostMode.ITEMIZED
                        BasicTextField(
                            value = form.totalCost,
                            onValueChange = { if (costEnabled) viewModel.onTotalCostChanged(it) },
                            modifier = Modifier.weight(1f),
                            enabled = costEnabled,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (costEnabled) 1f else 0.4f,
                                ),
                                textAlign = TextAlign.End,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth()) {
                                    if (form.totalCost.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.log_entry_placeholder_cost),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                textAlign = TextAlign.End,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }

                    if (!isDateMeter) {
                        // Vertical hairline separator
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        // Right column: ODOMETER or HOURS — unit suffix appears after the value
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = meterSectionHeader,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            BasicTextField(
                                value = form.meterReading,
                                onValueChange = { viewModel.onMeterReadingChanged(it) },
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    Box(Modifier.fillMaxWidth()) {
                                        if (form.meterReading.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.log_entry_placeholder_cost),
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    textAlign = TextAlign.End,
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                            Text(
                                text = meterUnitLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ==================================================================
            // NOTES CARD — header row (36dp) + inline BasicTextField (min 80dp)
            // iOS: buildNotesCard() — UITextView inline, no border
            // ==================================================================
            FormCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.log_entry_section_notes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CardSeparator()
                BasicTextField(
                    value = form.notes,
                    onValueChange = { viewModel.onNotesChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .heightIn(min = 80.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            if (form.notes.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.log_entry_placeholder_notes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            // ==================================================================
            // ITEMIZE CARD — only shown in ITEMIZED cost mode
            // iOS: itemize CTA row in buildItemizeCTACard()
            // ==================================================================
            if (form.costMode == CostMode.ITEMIZED) {
                Spacer(Modifier.height(16.dp))
                FormCard {
                    val itemizeLabel = if (form.costLineCount == 0) {
                        stringResource(R.string.log_entry_itemize_label)
                    } else {
                        stringResource(R.string.log_cost_itemize_cta_count, form.costLineCount)
                    }
                    TextButton(
                        onClick = { showCostSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 10.dp),
                    ) {
                        Text(
                            text = itemizeLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ==================================================================
            // PERFORMED BY CARD — inline text field + person-add icon
            // iOS: buildPerformedByRow() — text entry clears linked userId
            // ==================================================================
            FormCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.log_entry_label_performed_by),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(120.dp),
                    )
                    BasicTextField(
                        value = form.performedByName ?: "",
                        onValueChange = { viewModel.onPerformedByTextChanged(it) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth()) {
                                if (form.performedByName.isNullOrEmpty()) {
                                    Text(
                                        text = stringResource(R.string.log_entry_placeholder_performed_by),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            textAlign = TextAlign.End,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    IconButton(
                        onClick = onOpenPerformedByPicker,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            // ==================================================================
            // WO TIMER CARD — only shown when form is in WO context
            // iOS: WOTimerView shown inside AddLogItemViewController WO context
            // ==================================================================
            if (form.workOrderId != null) {
                Spacer(Modifier.height(16.dp))
                WoTimerCard(
                    elapsedSeconds = timerElapsedSeconds,
                    isRunning = form.timerStartedAt != null,
                    onStart = { viewModel.startTimer() },
                    onStop = {
                        val elapsed = viewModel.stopTimer()
                        if (elapsed > 0 && form.totalCost.isBlank()) {
                            // Pre-fill elapsed time as decimal hours in the cost field (mirrors iOS)
                            val hours = elapsed / 3600.0
                            viewModel.onTotalCostChanged("%.2f".format(hours))
                        }
                    },
                    onLap = { viewModel.lapTimer() },
                )
            }

            // ==================================================================
            // ITEM ATTRIBUTES — "add details" contacts row + attrs card
            // iOS: contacts-style add row + expandable details section
            // iOS default: itemAttrExpanded = true (starts visible, row tap collapses/expands)
            // ==================================================================
            if (form.itemAttributeDefs.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                ContactsAddRow(
                    text = stringResource(R.string.log_entry_action_add_details),
                    onClick = { detailsExpanded = !detailsExpanded },
                )
                if (detailsExpanded) {
                    Spacer(Modifier.height(8.dp))
                    FormCard {
                        ItemAttributesRenderer(
                            defs = form.itemAttributeDefs,
                            values = form.itemAttributes,
                            onValueChanged = { key, value -> viewModel.onItemAttributeChanged(key, value) },
                        )
                    }
                }
            }

            // ==================================================================
            // ATTACHMENTS — "add attachment" contacts row + photos strip card
            // iOS: ContactsAddRow("add attachment") + photo strip (140×105dp thumbs)
            // ==================================================================
            Spacer(Modifier.height(16.dp))
            ContactsAddRow(
                text = stringResource(R.string.log_entry_action_add_attachment),
                onClick = { showAttachmentMenu = true },
            )
            if (form.photoUris.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FormCard {
                    // iOS photo strip: 140×105pt, 8pt corner radius, gap=10
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(form.photoUris) { uri ->
                            Box(
                                modifier = Modifier
                                    .size(width = 140.dp, height = 105.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                // Delete button: 20×20dp, black@55%, top-end with 6dp offset
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-6).dp, y = 6.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .clickable { viewModel.removePhotoUri(uri) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==================================================================
            // INSPECTION CARD — only shown for inspection logType
            // ==================================================================
            if (form.logType == "inspection") {
                Spacer(Modifier.height(16.dp))
                FormCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                        if (form.inspectionChecklist != null || form.inspectionFields.isNotEmpty()) {
                            InspectionFormRenderer(
                                checklist = form.inspectionChecklist,
                                fields = form.inspectionFields,
                                answers = form.inspectionAnswers,
                                onAnswerChanged = { key, value -> viewModel.onInspectionAnswerChanged(key, value) },
                                snackbarHostState = snackbarHostState,
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
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// iOS-style card helpers
// ---------------------------------------------------------------------------

/** White surface with hairline border and 10dp corners — mirrors iOS makeCard(). */
@Composable
private fun FormCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(content = content)
    }
}

/** Hairline horizontal separator inside a card — mirrors iOS makeSeparator(). */
@Composable
private fun CardSeparator() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline,
    )
}

/**
 * iOS-style category badge pill: colored background, dot, label, chevron.
 * Background = categoryBadgeColor @ 13% opacity. Dot = full opacity. Shape = 12dp radius.
 */
@Composable
private fun CategoryBadgePill(
    categoryId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconName = categoryIconName(categoryId)
    val dotColor = categoryBadgeColor(iconName)
    val bgColor = dotColor.copy(alpha = 0.13f)
    val label = categoryId
        ?.replace("_", " ")
        ?.split(" ")
        ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        ?: stringResource(R.string.log_entry_placeholder_select_category)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * iOS Contacts-style "add" row: green filled circle with white plus + body-font label.
 * Used for "add attachment" and "add details" — 44dp height, green = secondary token.
 */
@Composable
private fun ContactsAddRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------
// Item attributes renderer
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemAttributesRenderer(
    defs: List<ItemAttributeDef>,
    values: Map<String, String>,
    onValueChanged: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        defs.forEachIndexed { index, def ->
            if (index > 0) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
            }
            val current = values[def.key] ?: ""
            when (def.fieldType) {
                "enum" -> {
                    // Picker-style row: label left, selected value right, chevron
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .height(44.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = def.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = current.ifBlank { def.placeholder ?: "" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (current.isBlank())
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
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
                "multiline" -> {
                    // Multi-line text row (min 3 lines, matches iOS multiline field)
                    val labelText = if (def.unit != null) "${def.label} (${def.unit})" else def.label
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        BasicTextField(
                            value = current,
                            onValueChange = { onValueChanged(def.key, it) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            minLines = 3,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth()) {
                                    if (current.isEmpty() && def.placeholder != null) {
                                        Text(
                                            text = def.placeholder,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
                else -> {
                    // text / number / checkbox — inline label-left, input-right (44dp row, matches iOS)
                    val labelText = if (def.unit != null) "${def.label} (${def.unit})" else def.label
                    val keyboardType = if (def.fieldType == "number") KeyboardType.Decimal else KeyboardType.Text
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.widthIn(min = 80.dp),
                        )
                        BasicTextField(
                            value = current,
                            onValueChange = { onValueChanged(def.key, it) },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth()) {
                                    if (current.isEmpty()) {
                                        Text(
                                            text = def.placeholder ?: "",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                textAlign = TextAlign.End,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Form row (used for asset card)
// ---------------------------------------------------------------------------

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
            .padding(horizontal = 16.dp, vertical = 12.dp),
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

/** WO-context timer card — matches iOS WOTimerView (start/stop/lap). */
@Composable
private fun WoTimerCard(
    elapsedSeconds: Long,
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val timeText = "%02d:%02d:%02d".format(hours, minutes, seconds)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TIMER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isRunning) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (!isRunning) {
                    TextButton(onClick = onStart) {
                        Text("Start", color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    TextButton(onClick = onLap) {
                        Text("Lap", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onStop) {
                        Text("Stop", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

