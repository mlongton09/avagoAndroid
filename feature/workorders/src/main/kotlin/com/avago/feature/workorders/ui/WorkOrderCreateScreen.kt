package com.avago.feature.workorders.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.CategoryBadge
import com.avago.core.ui.CategoryItem
import com.avago.core.ui.GlobalCategoryPickerScreen
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.WoPriority
import com.avago.feature.workorders.model.summariseRrule
import com.avago.feature.workorders.ui.components.priorityBarColor
import com.avago.feature.workorders.ui.sheets.RepeatsSheet
import com.avago.feature.workorders.ui.sheets.TechPickerSheet
import com.avago.feature.workorders.viewmodel.WorkOrderCreateViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderCreateScreen(
    woId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onPickAsset: () -> Unit,
    onPickLocation: () -> Unit = {},
    onPickAssetGroup: () -> Unit = {},
    onPickJob: () -> Unit = {},
    selectedJobId: String? = null,
    selectedLocationId: String? = null,
    selectedLocationName: String? = null,
    selectedAssetId: String? = null,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderCreateViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val assetName by viewModel.assetName.collectAsStateWithLifecycle()
    val locationName by viewModel.locationName.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val recentCategoryKeys by viewModel.recentCategoryKeys.collectAsStateWithLifecycle()
    val assetType by viewModel.assetType.collectAsStateWithLifecycle()
    val assetSubtitle by viewModel.assetSubtitle.collectAsStateWithLifecycle()
    val dueDateMs by viewModel.dueDateMs.collectAsStateWithLifecycle()
    val priority by viewModel.priority.collectAsStateWithLifecycle()
    val estimatedHours by viewModel.estimatedHours.collectAsStateWithLifecycle()
    val assignedTechIds by viewModel.assignedTechIds.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val titleError by viewModel.titleError.collectAsStateWithLifecycle()
    val savedSuccessfully by viewModel.savedSuccessfully.collectAsStateWithLifecycle()
    val jobId by viewModel.jobId.collectAsStateWithLifecycle()
    val jobTitle by viewModel.jobTitle.collectAsStateWithLifecycle()
    val timezone by viewModel.timezone.collectAsStateWithLifecycle()
    val effortHint by viewModel.effortHint.collectAsStateWithLifecycle()
    val repeatsRrule by viewModel.repeatsRrule.collectAsStateWithLifecycle()

    // iOS parity: UnifiedWorkOrdersViewController.addWorkOrderTapped() immediately presents
    // the asset picker as Stage 1 of the creation flow. Mirror that here for new WOs.
    LaunchedEffect(Unit) {
        if (woId == null && selectedAssetId == null) {
            onPickAsset()
        }
    }

    LaunchedEffect(selectedJobId) {
        if (selectedJobId != null) {
            viewModel.onJobSelected(selectedJobId, selectedJobId)
        }
    }

    LaunchedEffect(selectedLocationId, selectedLocationName) {
        if (selectedLocationId != null) {
            viewModel.onLocationSelected(selectedLocationId, selectedLocationName ?: selectedLocationId)
        }
    }

    LaunchedEffect(savedSuccessfully) {
        if (savedSuccessfully) onSaved()
    }

    var showTechPicker by remember { mutableStateOf(false) }
    var showRepeatsSheet by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedAssetId) {
        if (selectedAssetId != null) {
            viewModel.onAssetSelected(selectedAssetId)
            showCategoryPicker = true
        }
    }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDateMs)

    LaunchedEffect(dueDateMs) {
        if (datePickerState.selectedDateMillis != dueDateMs) {
            datePickerState.selectedDateMillis = dueDateMs
        }
    }

    LaunchedEffect(datePickerState.selectedDateMillis) {
        if (dueDateMs != datePickerState.selectedDateMillis) {
            viewModel.dueDateMs.value = datePickerState.selectedDateMillis
        }
    }

    if (showRepeatsSheet) {
        RepeatsSheet(
            currentRrule = repeatsRrule,
            currentEndType = null,
            currentEndCount = null,
            currentEndDateMs = null,
            onDismiss = { showRepeatsSheet = false },
            onSave = { rrule ->
                viewModel.repeatsRrule.value = rrule.ifBlank { null }
                showRepeatsSheet = false
            },
        )
    }

    if (showTechPicker) {
        TechPickerSheet(
            selectedTechIds = assignedTechIds,
            onDismiss = { showTechPicker = false },
            onConfirm = { techIds ->
                viewModel.assignedTechIds.value = techIds
                showTechPicker = false
            },
            woId = null,
        )
    }

    if (showCategoryPicker) {
        GlobalCategoryPickerScreen(
            title = stringResource(R.string.wo_category),
            categories = com.avago.core.ui.buildCategoryItems(keys = availableCategories),
            recents = com.avago.core.ui.buildCategoryItems(keys = recentCategoryKeys),
            onSelect = { item ->
                viewModel.category.value = item.key
                viewModel.fetchEffortHint(item.key)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    // Compact date picker dialog — replaces the heavy inline DatePicker calendar
    if (showDatePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.dueDateMs.value = it }
                    showDatePickerDialog = false
                }) { Text(stringResource(com.avago.core.ui.R.string.common_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text(stringResource(com.avago.core.ui.R.string.common_cancel))
                }
            },
        ) { DatePicker(state = datePickerState, showModeToggle = false) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // Use X (close) for new WO to match iOS modal cancel button
                        Icon(
                            if (woId == null) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wo_cancel),
                        )
                    }
                },
                title = {
                    Text(
                        if (woId == null) stringResource(R.string.wo_create_title)
                        else stringResource(R.string.wo_edit_title),
                    )
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                    } else {
                        TextButton(onClick = viewModel::save) {
                            Text(stringResource(R.string.wo_save))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Asset header — mirrors iOS tableHeaderView: avatar + name + subtitle ──
            if (assetName != null) {
                AssetHeaderCard(
                    name = assetName!!,
                    subtitle = assetSubtitle,
                    initial = assetName!!.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                    onTap = onPickAsset,
                )
            }

            FormSection {
                OutlinedTextField(
                    value = title,
                    onValueChange = { viewModel.title.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.wo_field_title)) },
                    isError = titleError != null,
                    supportingText = titleError?.let { { Text(it) } },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text(locationName ?: stringResource(R.string.wo_location_same_as_asset)) },
                    supportingContent = { Text(stringResource(R.string.wo_create_section_location)) },
                    modifier = Modifier.clickable { onPickLocation() },
                )
            }

            FormSection(title = stringResource(R.string.wo_create_section_details)) {
                // Category — show formatted display name ("Oil Change" not "oil_change")
                val categoryDisplayName = category?.replace("_", " ")?.split(" ")
                    ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                ListItem(
                    leadingContent = { CategoryBadge(categoryId = category) },
                    headlineContent = {
                        Text(categoryDisplayName ?: stringResource(R.string.wo_category_placeholder))
                    },
                    supportingContent = { Text(stringResource(R.string.wo_create_section_category)) },
                    modifier = Modifier.clickable { showCategoryPicker = true },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.wo_field_priority),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                PrioritySegmentedControl(
                    selected = priority,
                    onSelected = { viewModel.priority.value = it },
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Compact due-date row (tap → DatePickerDialog) — matches iOS compact date picker
                HorizontalDivider()
                ListItem(
                    headlineContent = {
                        Text(dueDateMs?.let(::formatDate) ?: stringResource(R.string.wo_no_due_date))
                    },
                    supportingContent = { Text(stringResource(R.string.wo_field_due_date)) },
                    modifier = Modifier.clickable { showDatePickerDialog = true },
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = estimatedHours,
                    onValueChange = { viewModel.estimatedHours.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.wo_field_estimated_hours)) },
                    placeholder = { Text(stringResource(R.string.wo_effort_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                effortHint?.let { hint ->
                    Text(
                        text = "Typical: ${hint.typicalMinutes} min  ·  fast: ${hint.fastMinutes}  ·  slow: ${hint.slowMinutes}  ·  ${hint.sampleCount} jobs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
            }

            FormSection(title = stringResource(R.string.wo_create_section_notes)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { viewModel.description.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.wo_field_description)) },
                    minLines = 3,
                    maxLines = 5,
                )
            }

            FormSection(title = stringResource(R.string.wo_field_repeats)) {
                ListItem(
                    headlineContent = { Text(summariseRrule(repeatsRrule)) },
                    supportingContent = { Text(stringResource(R.string.wo_field_repeats)) },
                    modifier = Modifier.clickable { showRepeatsSheet = true },
                )
            }

            FormSection(title = stringResource(R.string.wo_field_assignees)) {
                if (assignedTechIds.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wo_detail_no_technicians),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    assignedTechIds.forEach { techId ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(techId, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = {
                                viewModel.assignedTechIds.value = viewModel.assignedTechIds.value - techId
                            }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.wo_create_remove_assignee))
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { showTechPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.wo_field_add_assignee))
                }
            }

            FormSection(title = stringResource(R.string.wo_create_select_asset_group)) {
                OutlinedButton(
                    onClick = onPickAssetGroup,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.wo_create_select_asset_group))
                }
            }

            FormSection(title = stringResource(R.string.wo_job_label)) {
                ListItem(
                    headlineContent = { Text(jobTitle ?: jobId ?: stringResource(R.string.wo_no_job_assigned)) },
                    supportingContent = { Text(stringResource(R.string.wo_job_label)) },
                    trailingContent = {
                        if (jobId != null) {
                            IconButton(onClick = viewModel::clearJob) {
                                Icon(Icons.Default.Close, contentDescription = "Remove job")
                            }
                        }
                    },
                    modifier = Modifier.clickable { onPickJob() },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
            ) {
                Text(if (isSaving) stringResource(R.string.wo_saving) else stringResource(R.string.wo_save))
            }
        }
    }
}

@Composable
private fun AssetHeaderCard(
    name: String,
    subtitle: String?,
    initial: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        onClick = onTap,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 40dp avatar with initial — matches iOS AvatarView 40pt
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun FormSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
private fun PrioritySegmentedControl(
    selected: WoPriority,
    onSelected: (WoPriority) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WoPriority.entries.forEach { option ->
            val selectedColor = priorityBarColor(option.key)
            val isSelected = option == selected
            OutlinedButton(
                onClick = { onSelected(option) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) selectedColor else Color.Transparent,
                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) selectedColor else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Text(option.displayName.substringBefore(" · "))
            }
        }
    }
}

private fun formatDate(ms: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}
