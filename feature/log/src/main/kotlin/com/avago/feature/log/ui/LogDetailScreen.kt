package com.avago.feature.log.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.ui.categoryBadgeColor
import com.avago.core.ui.categoryIconName
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
    peerEntryIds: List<String> = emptyList(),
    onNavigateToEntry: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: LogDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(entryId) {
        viewModel.setEntryId(entryId)
    }

    val state by viewModel.uiState.collectAsState()
    val currencyCode by viewModel.currencyCode.collectAsState()
    val currencyRate by viewModel.currencyRate.collectAsState()
    val distanceUnit by viewModel.distanceUnit.collectAsState()
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
            CenterAlignedTopAppBar(
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
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
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
                // Use NumberFormat for symbol lookup, but apply live exchange rate
                // to amounts (mirrors iOS CurrencyManager.format() conversion).
                val currencyFormat = remember(currencyCode) {
                    val fmt = NumberFormat.getCurrencyInstance(Locale.getDefault())
                    runCatching { fmt.currency = java.util.Currency.getInstance(currencyCode.uppercase()) }
                    fmt
                }
                // Conversion helper: multiply a USD base amount by the live rate.
                fun convertUSD(usdAmount: Double) = usdAmount * currencyRate

                val logType = remember(log.data) { parseJsonField(log.data, "log_type") ?: "service" }
                val inspectionAnswers = remember(log.data) { parseJsonMap(log.data) }

                val isHoursMeter = distanceUnit.lowercase().let { it == "hrs" || it == "hours" || it == "hr" }
                val meterLabel = if (isHoursMeter) "HOURS" else "ODOMETER"

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(16.dp))

                    // ── 1. Header card: title | separator | category pill + date ──
                    DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = log.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp),
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            log.category?.let { cat ->
                                val dotColor = categoryBadgeColor(categoryIconName(cat))
                                val label = cat.replace("_", " ").split(" ")
                                    .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(dotColor.copy(alpha = 0.13f)),
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
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = dateFormatter.format(Date(log.entryDate)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── 2. Cost + Meter two-column card ───────────────────────────
                    DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Left: COST
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 14.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = "COST",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                                        1.5f,
                                        androidx.compose.ui.unit.TextUnitType.Sp,
                                    ),
                                )
                                val displayCost = if (state.costLines.isNotEmpty()) state.totalCost else log.cost
                                Text(
                                    text = if (displayCost != null && displayCost > 0)
                                        currencyFormat.format(convertUSD(displayCost)) else "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            // Right: ODOMETER / HOURS (only if recorded)
                            log.odometerValue?.let { meter ->
                                VerticalDivider(
                                    modifier = Modifier.fillMaxHeight(),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = 14.dp),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = meterLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = androidx.compose.ui.unit.TextUnit(
                                            1.5f,
                                            androidx.compose.ui.unit.TextUnitType.Sp,
                                        ),
                                    )
                                    Text(
                                        text = "${"%.1f".format(meter)} $distanceUnit",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }

                    // ── 3. Notes card ─────────────────────────────────────────────
                    log.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Spacer(Modifier.height(16.dp))
                        DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "NOTES",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                                        1.5f,
                                        androidx.compose.ui.unit.TextUnitType.Sp,
                                    ),
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }

                    // ── 4. Item attributes (Details) card — matches iOS "ITEM DETAILS" section ──
                    log.attributes?.takeIf { it.isNotBlank() }?.let { attrsJson ->
                        val fuelKeys = setOf("fuel_volume", "fuel_volume_unit")
                        val attrs = parseJsonMap(attrsJson)
                            .filterKeys { it !in fuelKeys }
                            .filter { (_, v) -> v.isNotBlank() && v.lowercase() != "false" }
                        if (attrs.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "ITEM DETAILS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = androidx.compose.ui.unit.TextUnit(
                                            1.5f,
                                            androidx.compose.ui.unit.TextUnitType.Sp,
                                        ),
                                    )
                                }
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp,
                                    ),
                                ) {
                                    attrs.entries.toList().forEachIndexed { idx, (key, value) ->
                                        // Convert key → display label: "oil_brand" → "Oil Brand"
                                        val label = key.replace('_', ' ').split(' ')
                                            .joinToString(" ") { w ->
                                                w.replaceFirstChar { it.uppercaseChar() }
                                            }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 5.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = value,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                            )
                                        }
                                        if (idx < attrs.size - 1) {
                                            HorizontalDivider(
                                                thickness = 0.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 5. Performed By card ──────────────────────────────────────
                    val performedByName = log.performedBy
                    val performedByUserId = log.performedByUserId
                    if (!performedByName.isNullOrBlank() || performedByUserId != null) {
                        Spacer(Modifier.height(16.dp))
                        DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Performed By",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(120.dp),
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = when {
                                        !performedByName.isNullOrBlank() -> performedByName
                                        performedByUserId != null -> "User $performedByUserId"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }

                    // ── 5. Photos card (horizontal thumbnail strip) ───────────────
                    if (state.photos.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "PHOTOS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                                        1.5f,
                                        androidx.compose.ui.unit.TextUnitType.Sp,
                                    ),
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(state.photos) { photo ->
                                    Box(
                                        modifier = Modifier
                                            .size(width = 140.dp, height = 105.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = { viewModel.setPrimaryPhoto(photo.photoId) },
                                            ),
                                    ) {
                                        AsyncImage(
                                            model = photo.downloadUrl ?: photo.storageKey,
                                            contentDescription = "Log photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        if (photo.isPrimary) {
                                            Badge(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp),
                                            ) { Text("★") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 6. Cost breakdown (itemized) ──────────────────────────────
                    if (log.costMode == "itemized" && state.costLines.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "COST BREAKDOWN",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                                        1.5f,
                                        androidx.compose.ui.unit.TextUnitType.Sp,
                                    ),
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                state.costLines.forEach { line ->
                                    CostLineDetailRow(
                                        line = line,
                                        currencyFormat = currencyFormat,
                                        currencyRate = currencyRate,
                                    )
                                    if (line != state.costLines.last()) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                                if (state.totalCost > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Total", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = currencyFormat.format(convertUSD(state.totalCost)),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 7. Parts Used card ────────────────────────────────────────
                    if (state.partLines.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "PARTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                                        1.5f,
                                        androidx.compose.ui.unit.TextUnitType.Sp,
                                    ),
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                state.partLines.forEach { line ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = line.description ?: "Part",
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
                            }
                        }
                    }

                    // ── 8. Labor card ─────────────────────────────────────────────
                    if (state.laborLines.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "LABOR",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                                        1.5f,
                                        androidx.compose.ui.unit.TextUnitType.Sp,
                                    ),
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                state.laborLines.forEach { line ->
                                    CostLineRow(line = line, currencyFormat = currencyFormat)
                                }
                            }
                        }
                    }

                    // ── 9. Inspection card ────────────────────────────────────────
                    if (logType == "inspection" && inspectionAnswers.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "INSPECTION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                                        1.5f,
                                        androidx.compose.ui.unit.TextUnitType.Sp,
                                    ),
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                                                text = key.replace('_', ' ')
                                                    .replaceFirstChar { it.uppercase() },
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
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Prev / Next navigation row (mirrors iOS vertical-pan gesture) ─────
                    if (peerEntryIds.size > 1 && onNavigateToEntry != null) {
                        val currentIdx = peerEntryIds.indexOf(entryId)
                        val hasPrev = currentIdx > 0
                        val hasNext = currentIdx < peerEntryIds.lastIndex
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { if (hasPrev) onNavigateToEntry(peerEntryIds[currentIdx - 1]) },
                                enabled = hasPrev,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous entry",
                                    tint = if (hasPrev) MaterialTheme.colorScheme.secondary
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                )
                            }
                            VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                            IconButton(
                                onClick = { if (hasNext) onNavigateToEntry(peerEntryIds[currentIdx + 1]) },
                                enabled = hasNext,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next entry",
                                    tint = if (hasNext) MaterialTheme.colorScheme.secondary
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

/** White surface card with hairline border and 10dp corners — mirrors iOS makeCard(). */
@Composable
private fun DetailCard(
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

@Composable
private fun CostLineDetailRow(
    line: LogCostLineEntity,
    currencyFormat: NumberFormat,
    currencyRate: Double = 1.0,
    modifier: Modifier = Modifier,
) {
    fun convert(usd: Double) = usd * currencyRate
    val lineTotal = convert(line.quantity * line.unitCost + (line.taxAmount ?: 0.0))
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val kindLabel = if (line.kind == "part") "PART" else "LABOR"
            val badgeColor = if (line.kind == "part")
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.tertiaryContainer
            val badgeTextColor = if (line.kind == "part")
                MaterialTheme.colorScheme.onSecondaryContainer
            else
                MaterialTheme.colorScheme.onTertiaryContainer
            Box(
                modifier = Modifier
                    .background(badgeColor, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = kindLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeTextColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = line.description ?: if (line.kind == "part") "Part" else "Labor",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = currencyFormat.format(lineTotal),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${line.quantity} × ${currencyFormat.format(convert(line.unitCost))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val tax = line.taxAmount
            if (tax != null && tax > 0) {
                Text(
                    text = "Tax: ${currencyFormat.format(convert(tax))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
