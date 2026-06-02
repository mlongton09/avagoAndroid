package com.avago.feature.workorders.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.CategoryItem
import com.avago.core.ui.GlobalCategoryPickerScreen
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.WoPriority
import com.avago.feature.workorders.ui.sheets.RepeatsSheet
import com.avago.feature.workorders.ui.sheets.TechPickerSheet
import com.avago.feature.workorders.viewmodel.WorkOrderCreateViewModel
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
    /** Job ID returned from JobPickerScreen via nav back-stack SavedStateHandle. */
    selectedJobId: String? = null,
    selectedLocationId: String? = null,
    selectedLocationName: String? = null,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderCreateViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val vin by viewModel.vin.collectAsStateWithLifecycle()
    val vinDecodeResult by viewModel.vinDecodeResult.collectAsStateWithLifecycle()
    val isDecodingVin by viewModel.isDecodingVin.collectAsStateWithLifecycle()
    val assetName by viewModel.assetName.collectAsStateWithLifecycle()
    val locationName by viewModel.locationName.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val dueDateMs by viewModel.dueDateMs.collectAsStateWithLifecycle()
    val priority by viewModel.priority.collectAsStateWithLifecycle()
    val estimatedHours by viewModel.estimatedHours.collectAsStateWithLifecycle()
    val assignedTechIds by viewModel.assignedTechIds.collectAsStateWithLifecycle()
    val checklistDrafts by viewModel.checklistDrafts.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val selectedTemplateId by viewModel.selectedTemplateId.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val titleError by viewModel.titleError.collectAsStateWithLifecycle()
    val savedSuccessfully by viewModel.savedSuccessfully.collectAsStateWithLifecycle()
    val jobId by viewModel.jobId.collectAsStateWithLifecycle()
    val jobTitle by viewModel.jobTitle.collectAsStateWithLifecycle()
    val timezone by viewModel.timezone.collectAsStateWithLifecycle()
    val effortHint by viewModel.effortHint.collectAsStateWithLifecycle()

    // Apply job selection returned from JobPickerScreen via nav back-stack
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

    var showPriorityMenu by remember { mutableStateOf(false) }
    var showTemplateMenu by remember { mutableStateOf(false) }
    var showTechPicker by remember { mutableStateOf(false) }
    var showRepeatsSheet by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var repeatsRrule by remember { mutableStateOf<String?>(null) }
    var showVinSection by remember { mutableStateOf(false) }

    if (showRepeatsSheet) {
        RepeatsSheet(
            currentRrule = repeatsRrule,
            currentEndType = null,
            currentEndCount = null,
            currentEndDateMs = null,
            onDismiss = { showRepeatsSheet = false },
            onSave = { rrule ->
                repeatsRrule = rrule
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
            categories = availableCategories.map { CategoryItem(key = it, displayName = it) },
            onSelect = { item ->
                viewModel.category.value = item.key
                viewModel.fetchEffortHint(item.key)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wo_cancel),
                        )
                    }
                },
                title = {
                    Text(
                        if (woId == null)
                            stringResource(R.string.wo_create_title)
                        else
                            stringResource(R.string.wo_edit_title)
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
            // Template picker
            if (templates.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.wo_field_template),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(onClick = { showTemplateMenu = true }) {
                        val selectedTemplate = templates.firstOrNull { it.templateId == selectedTemplateId }
                        Text(selectedTemplate?.title ?: stringResource(R.string.wo_field_template_placeholder))
                    }
                    DropdownMenu(
                        expanded = showTemplateMenu,
                        onDismissRequest = { showTemplateMenu = false },
                    ) {
                        templates.forEach { template ->
                            DropdownMenuItem(
                                text = { Text(template.title) },
                                onClick = {
                                    viewModel.applyTemplate(template)
                                    showTemplateMenu = false
                                },
                            )
                        }
                    }
                }
            }

            // Title (required)
            OutlinedTextField(
                value = title,
                onValueChange = { viewModel.title.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.wo_field_title)) },
                isError = titleError != null,
                supportingText = titleError?.let { { Text(it) } },
                singleLine = true,
            )

            LaunchedEffect(vin, vinDecodeResult) {
                if (vin.isNotBlank() || vinDecodeResult != null) {
                    showVinSection = true
                }
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.wo_vin_label)) },
                supportingContent = { Text(stringResource(R.string.wo_vin_placeholder)) },
                trailingContent = {
                    Icon(
                        imageVector = if (showVinSection) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable { showVinSection = !showVinSection },
            )

            if (showVinSection) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = vin,
                        onValueChange = { viewModel.vin.value = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.wo_vin_label)) },
                        placeholder = { Text(stringResource(R.string.wo_vin_placeholder)) },
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = viewModel::decodeVin,
                        enabled = vin.isNotBlank() && !isDecodingVin,
                    ) {
                        Text(
                            if (isDecodingVin) {
                                stringResource(R.string.wo_vin_decoding)
                            } else {
                                stringResource(R.string.wo_vin_decode_btn)
                            }
                        )
                    }
                }

                vinDecodeResult?.let { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            result.year?.let {
                                Text("${stringResource(R.string.wo_vin_year)}: $it", style = MaterialTheme.typography.bodySmall)
                            }
                            result.make?.takeIf { it.isNotBlank() }?.let {
                                Text("${stringResource(R.string.wo_vin_make)}: $it", style = MaterialTheme.typography.bodySmall)
                            }
                            result.model?.takeIf { it.isNotBlank() }?.let {
                                Text("${stringResource(R.string.wo_vin_model)}: $it", style = MaterialTheme.typography.bodySmall)
                            }
                            result.engine?.takeIf { it.isNotBlank() }?.let {
                                Text("Engine: $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { viewModel.description.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.wo_field_description)) },
                minLines = 2,
                maxLines = 4,
            )

            ListItem(
                headlineContent = { Text(category ?: stringResource(R.string.wo_category_placeholder)) },
                supportingContent = { Text(stringResource(R.string.wo_create_section_category)) },
                modifier = Modifier.clickable { showCategoryPicker = true },
            )

            // Asset picker (stub — navigates out)
            OutlinedButton(
                onClick = onPickAsset,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(assetName ?: stringResource(R.string.wo_field_asset_placeholder))
            }

            ListItem(
                headlineContent = {
                    Text(locationName ?: stringResource(R.string.wo_location_same_as_asset))
                },
                supportingContent = { Text(stringResource(R.string.wo_create_section_location)) },
                modifier = Modifier.clickable { onPickLocation() },
            )

            // Asset Group picker
            OutlinedButton(
                onClick = onPickAssetGroup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.wo_create_select_asset_group))
            }

            // Job / Project picker
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

            // Priority
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.wo_field_priority), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { showPriorityMenu = true }) {
                    Text(priority.displayName)
                }
                DropdownMenu(expanded = showPriorityMenu, onDismissRequest = { showPriorityMenu = false }) {
                    WoPriority.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.displayName) },
                            onClick = {
                                viewModel.priority.value = p
                                showPriorityMenu = false
                            },
                        )
                    }
                }
            }

            // Estimated hours
            OutlinedTextField(
                value = estimatedHours,
                onValueChange = { viewModel.estimatedHours.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.wo_field_estimated_hours)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )

            // Effort hint (populated from server when category is known)
            effortHint?.let { hint ->
                Text(
                    text = "Typical: ${hint.typicalMinutes} min  ·  fast: ${hint.fastMinutes}  ·  slow: ${hint.slowMinutes}  ·  ${hint.sampleCount} jobs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
            }

            // Assigned techs
            Text(
                stringResource(R.string.wo_field_assignees),
                style = MaterialTheme.typography.labelLarge,
            )
            if (assignedTechIds.isNotEmpty()) {
                assignedTechIds.forEach { techId ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(techId, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = {
                            viewModel.assignedTechIds.value =
                                viewModel.assignedTechIds.value - techId
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

            // Repeats
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.wo_field_repeats), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { showRepeatsSheet = true }) {
                    Text(if (repeatsRrule != null) stringResource(R.string.wo_create_repeats_configured) else stringResource(R.string.wo_create_repeats_none))
                }
            }

            // Timezone selector — only shown when a due date is set
            if (dueDateMs != null) {
                val availableZones = listOf(
                    "America/New_York", "America/Chicago", "America/Denver",
                    "America/Los_Angeles", "America/Anchorage", "Pacific/Honolulu",
                    "UTC", "Europe/London", "Europe/Paris", "Asia/Tokyo",
                )
                var tzMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = tzMenuExpanded,
                    onExpandedChange = { tzMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = timezone.ifEmpty { TimeZone.getDefault().id },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Timezone") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tzMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = tzMenuExpanded,
                        onDismissRequest = { tzMenuExpanded = false },
                    ) {
                        availableZones.forEach { tz ->
                            DropdownMenuItem(
                                text = { Text(tz) },
                                onClick = {
                                    viewModel.onTimezoneChanged(tz)
                                    tzMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // Checklist
            Text(
                stringResource(R.string.wo_field_checklist),
                style = MaterialTheme.typography.labelLarge,
            )
            checklistDrafts.forEachIndexed { _, draft ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { viewModel.updateChecklistItem(draft.id, it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.wo_field_checklist_item_placeholder)) },
                        singleLine = true,
                    )
                    IconButton(onClick = { viewModel.removeChecklistItem(draft.id) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.wo_create_remove_checklist_step))
                    }
                }
            }
            TextButton(onClick = viewModel::addChecklistItem) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.wo_field_add_checklist_item))
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
