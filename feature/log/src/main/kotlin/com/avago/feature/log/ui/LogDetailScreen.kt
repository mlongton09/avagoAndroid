package com.avago.feature.log.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.feature.log.model.InspectionFieldDef
import com.avago.feature.log.model.parseInspectionFields
import com.avago.feature.log.viewmodel.LogDetailViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onEdit: (entryId: String) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(entryId) {
        viewModel.setEntryId(entryId)
    }

    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onDeleted()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Log Entry") },
            text = { Text("Are you sure you want to delete this log entry? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteLog(onDeleted)
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.log?.title ?: "Log Entry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(entryId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.log == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text("Log entry not found") }

            else -> {
                val log = state.log!!
                val dateFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
                val currencyFormat = remember { NumberFormat.getCurrencyInstance() }

                // Parse inspection fields from log data JSON
                val logType = remember(log.data) { parseJsonField(log.data, "log_type") ?: "service" }
                val inspectionFields: List<InspectionFieldDef> = remember { emptyList() } // Config loaded on demand
                val inspectionAnswers = remember(log.data) { parseJsonMap(log.data) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // --------------- Header section ---------------
                    Column(modifier = Modifier.padding(16.dp)) {
                        log.category?.let { cat ->
                            Text(
                                text = cat.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            text = log.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = dateFormatter.format(Date(log.entryDate)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        log.performedBy?.let { performer ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Performed by: $performer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        log.odometerValue?.let { meter ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Meter: ${"%.1f".format(meter)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    SectionDivider()

                    // --------------- Cost section ---------------
                    DetailSection(title = "Cost") {
                        val costLines = state.costLines
                        if (costLines.isEmpty()) {
                            val cost = log.cost
                            if (cost != null && cost > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Total")
                                    Text(
                                        text = currencyFormat.format(cost),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            } else {
                                Text(
                                    text = "No cost recorded",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                text = "(${costLines.size} cost line${if (costLines.size > 1) "s" else ""})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Total", fontWeight = FontWeight.Medium)
                                Text(
                                    text = currencyFormat.format(state.totalCost),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    // --------------- Notes section ---------------
                    if (!log.notes.isNullOrBlank()) {
                        SectionDivider()
                        DetailSection(title = "Notes") {
                            // Basic markdown: bold, no external dep
                            Text(
                                text = log.notes,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    // --------------- Photos section ---------------
                    if (state.photos.isNotEmpty()) {
                        SectionDivider()
                        DetailSection(title = "Photos") {
                            val pagerState = rememberPagerState { state.photos.size }
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                            ) { page ->
                                val photo = state.photos[page]
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = photo.downloadUrl ?: photo.storageKey,
                                        contentDescription = "Log photo ${page + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = { viewModel.setPrimaryPhoto(photo.photoId) },
                                            ),
                                    )
                                    if (photo.isPrimary) {
                                        Badge(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp),
                                        ) { Text("Primary") }
                                    }
                                }
                            }
                            // Page indicator dots
                            if (state.photos.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    state.photos.indices.forEach { idx ->
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 3.dp)
                                                .size(if (idx == pagerState.currentPage) 8.dp else 6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (idx == pagerState.currentPage)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --------------- Parts section ---------------
                    if (state.partLines.isNotEmpty()) {
                        SectionDivider()
                        DetailSection(title = "Parts") {
                            state.partLines.forEach { line ->
                                CostLineRow(line = line, currencyFormat = currencyFormat)
                            }
                        }
                    }

                    // --------------- Labor section ---------------
                    if (state.laborLines.isNotEmpty()) {
                        SectionDivider()
                        DetailSection(title = "Labor") {
                            state.laborLines.forEach { line ->
                                CostLineRow(line = line, currencyFormat = currencyFormat)
                            }
                        }
                    }

                    // --------------- Inspection section ---------------
                    if (logType == "inspection" && inspectionAnswers.isNotEmpty()) {
                        SectionDivider()
                        DetailSection(title = "Inspection Results") {
                            inspectionAnswers.entries
                                .filter { (k, _) -> k != "log_type" }
                                .forEach { (key, value) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = key.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun DetailSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp),
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun CostLineRow(
    line: LogCostLineEntity,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.description ?: if (line.kind == "part") "Part" else "Labor",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${line.quantity} × ${currencyFormat.format(line.unitCost)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = currencyFormat.format(line.quantity * line.unitCost),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
// JSON helpers (local to this file)
// ---------------------------------------------------------------------------

private fun parseJsonField(json: String?, key: String): String? {
    if (json.isNullOrBlank()) return null
    val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
    return pattern.find(json)?.groupValues?.get(1)
}

private fun parseJsonMap(json: String?): Map<String, String> {
    if (json.isNullOrBlank()) return emptyMap()
    val result = mutableMapOf<String, String>()
    val pattern = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
    pattern.findAll(json).forEach { match ->
        result[match.groupValues[1]] = match.groupValues[2]
    }
    return result
}
