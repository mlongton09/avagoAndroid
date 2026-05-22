package com.avago.feature.assets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.avago.feature.assets.R
import com.avago.feature.assets.model.AssetColorPalette
import com.avago.feature.assets.model.AssetTypes
import com.avago.feature.assets.viewmodel.AddEditAssetViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAssetScreen(
    assetId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenTypePicker: () -> Unit,
    navController: NavController? = null,
    viewModel: AddEditAssetViewModel = hiltViewModel(),
) {
    // Load for edit mode
    LaunchedEffect(assetId) {
        if (assetId != null) {
            viewModel.loadForEdit(assetId)
        }
    }

    // Listen for type picker result from back stack
    LaunchedEffect(navController) {
        navController?.currentBackStackEntry
            ?.savedStateHandle
            ?.getStateFlow<String?>("selected_asset_type", null)
            ?.collect { type ->
                if (type != null) {
                    viewModel.onAssetTypeChanged(type)
                }
            }
    }

    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    // Show error in snackbar
    LaunchedEffect(form.saveError) {
        if (form.saveError != null) {
            snackbarHostState.showSnackbar(form.saveError!!)
        }
    }

    val title = if (assetId != null) stringResource(R.string.asset_edit_title)
    else stringResource(R.string.asset_add_title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.asset_cancel),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Name (required)
            item {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { viewModel.onNameChanged(it) },
                    label = { Text(stringResource(R.string.asset_field_name)) },
                    isError = form.name.isBlank() && form.saveError != null,
                    supportingText = if (form.name.isBlank() && form.saveError != null) {
                        { Text(stringResource(R.string.asset_field_name_required)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Asset Type picker row
            item {
                FieldRow(
                    label = stringResource(R.string.asset_field_type),
                    onClick = onOpenTypePicker,
                ) {
                    val selectedLabel = form.assetType?.let { key ->
                        val resId = AssetTypes.labelResIdFor(key)
                        if (resId != null) stringResource(resId)
                        else key.replace("_", " ").replaceFirstChar { it.uppercase() }
                    }
                    Text(
                        text = selectedLabel ?: stringResource(R.string.asset_field_type_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedLabel != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Make
            item {
                OutlinedTextField(
                    value = form.make,
                    onValueChange = { viewModel.onMakeChanged(it) },
                    label = { Text(stringResource(R.string.asset_field_make)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Model
            item {
                OutlinedTextField(
                    value = form.model,
                    onValueChange = { viewModel.onModelChanged(it) },
                    label = { Text(stringResource(R.string.asset_field_model)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Year
            item {
                OutlinedTextField(
                    value = form.year,
                    onValueChange = { viewModel.onYearChanged(it.filter { c -> c.isDigit() }.take(4)) },
                    label = { Text(stringResource(R.string.asset_field_year)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Color picker
            item {
                Column {
                    Text(
                        text = stringResource(R.string.asset_field_color),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    ColorSwatchGrid(
                        selected = form.avatarColor,
                        onSelect = { viewModel.onAvatarColorChanged(it) },
                    )
                }
            }

            // License Plate
            item {
                OutlinedTextField(
                    value = form.licensePlate,
                    onValueChange = { viewModel.onLicensePlateChanged(it) },
                    label = { Text(stringResource(R.string.asset_field_license_plate)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // VIN / Serial
            item {
                OutlinedTextField(
                    value = form.vinSerial,
                    onValueChange = { viewModel.onVinSerialChanged(it) },
                    label = { Text(stringResource(R.string.asset_field_vin)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Purchase Date
            item {
                FieldRow(
                    label = stringResource(R.string.asset_field_purchase_date),
                    onClick = { showDatePicker = true },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (form.purchaseDate != null) {
                                SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                    .format(Date(form.purchaseDate!!))
                            } else {
                                stringResource(R.string.asset_field_purchase_date)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (form.purchaseDate != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(start = 4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Purchase Price
            item {
                OutlinedTextField(
                    value = form.purchasePrice,
                    onValueChange = { viewModel.onPurchasePriceChanged(it) },
                    label = { Text(stringResource(R.string.asset_field_purchase_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Location
            item {
                OutlinedTextField(
                    value = form.location,
                    onValueChange = { viewModel.onLocationChanged(it) },
                    label = { Text(stringResource(R.string.asset_field_location)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Notes
            item {
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { viewModel.onNotesChanged(it) },
                    label = { Text(stringResource(R.string.asset_field_notes)) },
                    minLines = 3,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Save button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.save(onSuccess = { onSaved() }) },
                    enabled = !form.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (form.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.asset_save))
                    }
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = form.purchaseDate,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onPurchaseDateChanged(datePickerState.selectedDateMillis)
                        showDatePicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.asset_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(4.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            content()
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ColorSwatchGrid(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(10),
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        userScrollEnabled = false,
    ) {
        items(AssetColorPalette) { colorLong ->
            val hex = "#%06X".format(colorLong and 0xFFFFFF)
            val isSelected = selected.equals(hex, ignoreCase = true)
            val color = Color(colorLong)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape,
                        ) else Modifier
                    )
                    .clickable { onSelect(hex) },
            )
        }
    }
}
