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
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.ui.categoryBadgeColor
import com.avago.core.ui.categoryIconName
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.LogPacks
import com.avago.feature.log.model.PackDisplay
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.avago.feature.log.viewmodel.LogDetailViewModel
import androidx.compose.ui.res.stringResource
import com.avago.feature.log.R
import kotlinx.serialization.json.JsonPrimitive
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
    val inspectionStrings by viewModel.inspectionStrings.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onDeleted()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.log_detail_delete_title)) },
            text = { Text(stringResource(R.string.log_detail_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteLog(onDeleted)
                    }
                ) { Text(stringResource(R.string.log_action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.log_action_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.log?.title ?: stringResource(R.string.log_detail_title_fallback)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.log_action_back))
                    }
                },
                actions = {
                    val log = state.log
                    val shareContext = LocalContext.current
                    val isInspection = log != null && (
                        log.category?.lowercase()?.contains("inspect") == true ||
                            ((InspectionV2.parseDataObject(log.data)?.get("log_type") as? JsonPrimitive)?.content == "inspection")
                        )
                    if (log != null && isInspection) {
                        IconButton(onClick = { InspectionReportShare.share(shareContext, log.assetId, log.entryId, log.title) }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.log_share_report))
                        }
                    }
                    IconButton(onClick = { onEdit(entryId) }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.log_action_edit))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.log_action_delete),
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
            ) { Text(stringResource(R.string.log_detail_not_found)) }

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

                val inspectionData = remember(log.data) { InspectionV2.parseDataObject(log.data) }
                val logType = remember(log.data, log.category) {
                    (inspectionData?.get("log_type") as? JsonPrimitive)?.content
                        ?: if (log.category?.lowercase()?.contains("inspect") == true) "inspection" else "service"
                }
                val hasInspectionContent = remember(log.data) {
                    val env = InspectionV2.parseEnvelope(inspectionData)
                    env.values.isNotEmpty() || env.defects.isNotEmpty() || env.signOff != null ||
                        env.resolvedDefects.isNotEmpty() || env.certification != null || env.result != null
                }

                val isHoursMeter = distanceUnit.lowercase().let { it == "hrs" || it == "hours" || it == "hr" }
                val meterLabel = stringResource(if (isHoursMeter) R.string.log_entry_section_hours else R.string.log_entry_section_odometer)

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
                                    text = stringResource(R.string.log_entry_section_cost),
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
                                    text = stringResource(R.string.log_entry_section_notes),
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

                    // ── 4. Item attributes: localized pack sections (web PackFieldsView parity)
                    //       + any remaining non-pack keys as generic "Item details" rows.
                    log.attributes?.takeIf { it.isNotBlank() }?.let { attrsJson ->
                        val attrsObj = remember(attrsJson) { LogPacks.parse(attrsJson) }
                        val translator = rememberPackTranslator()
                        val packSections = remember(attrsObj, translator, currencyFormat, currencyRate) {
                            PackDisplay.build(
                                attrs = attrsObj,
                                t = translator,
                                formatCurrency = { currencyFormat.format(convertUSD(it)) },
                                formatDate = { iso -> formatIsoDate(iso) },
                            )
                        }
                        packSections.forEach { section ->
                            Spacer(Modifier.height(16.dp))
                            DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                                SectionHeader(section.title.uppercase(Locale.getDefault()))
                                KeyValueRows(section.rows.map { it.label to it.value })
                            }
                        }
                        val attrs = remember(attrsObj) {
                            PackDisplay.extraRows(attrsObj).map { (k, v) -> PackDisplay.humanize(k) to v }
                        }
                        if (attrs.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                                SectionHeader(stringResource(R.string.log_detail_section_item_details))
                                KeyValueRows(attrs)
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
                                    text = stringResource(R.string.log_entry_label_performed_by),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(120.dp),
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = when {
                                        !performedByName.isNullOrBlank() -> performedByName
                                        performedByUserId != null -> stringResource(R.string.log_detail_user_fallback, performedByUserId)
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
                                    text = stringResource(R.string.log_detail_section_photos),
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
                                            contentDescription = stringResource(R.string.log_detail_photo_cd),
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
                                    text = stringResource(R.string.log_detail_section_cost_breakdown),
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
                                        Text(stringResource(R.string.log_detail_total), fontWeight = FontWeight.SemiBold)
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
                                    text = stringResource(R.string.log_entry_section_parts),
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
                                                text = line.description ?: stringResource(R.string.log_cost_line_part),
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
                                    text = stringResource(R.string.log_detail_section_labor),
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
                    if (logType == "inspection" && hasInspectionContent) {
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
                                    text = stringResource(R.string.log_detail_section_inspection),
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
                                InspectionEnvelopeDetail(data = inspectionData, strings = inspectionStrings)
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
                                    contentDescription = stringResource(R.string.log_detail_prev_entry),
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
                                    contentDescription = stringResource(R.string.log_detail_next_entry),
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

/** Uppercase, letter-spaced section header row + hairline divider (iOS section style). */
@Composable
private fun SectionHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(horizontal = 16.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp),
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
}

/** Label / value rows; each row is merged for TalkBack ("Tread depth, FL 6 mm"). */
@Composable
private fun KeyValueRows(rows: List<Pair<String, String>>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        rows.forEachIndexed { idx, (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.4f, fill = false),
                )
            }
            if (idx < rows.size - 1) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Localized label lookup for [PackDisplay] keys (`pack_*`, `f_*`, `pos_*`, …). */
@Composable
private fun rememberPackTranslator(): PackDisplay.Translator {
    val res = LocalContext.current.resources
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    return remember(res, config) {
        PackDisplay.Translator { key, count ->
            val id = PACK_LABELS[key]
            when {
                key == "pm_plans_count" -> res.getQuantityString(R.plurals.log_pack_pm_plans_count, count ?: 0, count ?: 0)
                id != null -> res.getString(id)
                else -> PackDisplay.humanize(key.substringAfter('_'))
            }
        }
    }
}

internal val PACK_LABELS: Map<String, Int> = mapOf(
    "pack_fuel" to R.string.log_pack_section_fuel,
    "pack_oil" to R.string.log_pack_section_oil,
    "pack_tire" to R.string.log_pack_section_tire,
    "pack_battery" to R.string.log_pack_section_battery,
    "pack_brake" to R.string.log_pack_section_brake,
    "pack_common" to R.string.log_pack_section_common,
    "partial_fill" to R.string.log_pack_partial_fill,
    "f_fuel_volume" to R.string.log_pack_f_fuel_volume,
    "f_fuel_price_per_unit" to R.string.log_pack_f_fuel_price_per_unit,
    "f_fuel_grade" to R.string.log_pack_f_fuel_grade,
    "f_fuel_station" to R.string.log_pack_f_fuel_station,
    "f_fuel_full_fill" to R.string.log_pack_f_fuel_full_fill,
    "f_fuel_card_last4" to R.string.log_pack_f_fuel_card_last4,
    "f_fuel_economy" to R.string.log_pack_f_fuel_economy,
    "f_oil_type" to R.string.log_pack_f_oil_type,
    "f_oil_viscosity" to R.string.log_pack_f_oil_viscosity,
    "f_oil_quantity" to R.string.log_pack_f_oil_quantity,
    "f_oil_filter_part" to R.string.log_pack_f_oil_filter_part,
    "f_oil_next_due_meter" to R.string.log_pack_f_oil_next_due_meter,
    "f_oil_next_due_date" to R.string.log_pack_f_oil_next_due_date,
    "f_tire_action" to R.string.log_pack_f_tire_action,
    "f_tire_positions" to R.string.log_pack_f_tire_positions,
    "f_tire_brand" to R.string.log_pack_f_tire_brand,
    "f_tire_size" to R.string.log_pack_f_tire_size,
    "f_tire_dot_date" to R.string.log_pack_f_tire_dot_date,
    "f_tire_tread" to R.string.log_pack_f_tire_tread,
    "f_tire_psi" to R.string.log_pack_f_tire_psi,
    "f_battery_action" to R.string.log_pack_f_battery_action,
    "f_battery_rated_cca" to R.string.log_pack_f_battery_rated_cca,
    "f_battery_measured_cca" to R.string.log_pack_f_battery_measured_cca,
    "f_battery_voltage" to R.string.log_pack_f_battery_voltage,
    "f_battery_test_result" to R.string.log_pack_f_battery_test_result,
    "f_battery_date_code" to R.string.log_pack_f_battery_date_code,
    "f_brake_axle" to R.string.log_pack_f_brake_axle,
    "f_brake_pad_mm" to R.string.log_pack_f_brake_pad_mm,
    "f_brake_rotor_mm" to R.string.log_pack_f_brake_rotor_mm,
    "f_brake_fluid" to R.string.log_pack_f_brake_fluid,
    "f_vendor_name" to R.string.log_pack_f_vendor_name,
    "f_warranty_until" to R.string.log_pack_f_warranty_until,
    "f_warranty_provider" to R.string.log_pack_f_warranty_provider,
    "f_pm_plan_ids" to R.string.log_pack_f_pm_plan_ids,
    "pos_fl" to R.string.log_pack_pos_fl,
    "pos_fr" to R.string.log_pack_pos_fr,
    "pos_rl" to R.string.log_pack_pos_rl,
    "pos_rr" to R.string.log_pack_pos_rr,
    "pos_rlo" to R.string.log_pack_pos_rlo,
    "pos_rli" to R.string.log_pack_pos_rli,
    "pos_rro" to R.string.log_pack_pos_rro,
    "pos_rri" to R.string.log_pack_pos_rri,
    "pos_spare" to R.string.log_pack_pos_spare,
)

/** `yyyy-MM-dd` (or a full ISO timestamp) → localized medium date; falls back to the raw value. */
private fun formatIsoDate(iso: String): String = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(iso.take(10))
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault()).format(parsed!!)
}.getOrDefault(iso)

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
                text = line.description ?: stringResource(if (line.kind == "part") R.string.log_cost_line_part else R.string.log_cost_line_labor),
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
            val kindLabel = stringResource(if (line.kind == "part") R.string.log_cost_line_part else R.string.log_cost_line_labor).uppercase(Locale.getDefault())
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
                text = line.description ?: stringResource(if (line.kind == "part") R.string.log_cost_line_part else R.string.log_cost_line_labor),
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
                    text = stringResource(R.string.log_cost_line_tax, currencyFormat.format(convert(tax))),
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

/** Extracts the raw JSON substring of a nested object value, e.g. extractJsonObject(json, "configSnapshot"). */
private fun extractJsonObject(json: String, key: String): String? {
    val keyIdx = json.indexOf("\"$key\"")
    if (keyIdx < 0) return null
    val colonIdx = json.indexOf(':', keyIdx)
    if (colonIdx < 0) return null
    val braceStart = json.indexOf('{', colonIdx)
    if (braceStart < 0) return null
    var depth = 0
    for (i in braceStart until json.length) {
        when (json[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return json.substring(braceStart, i + 1)
            }
        }
    }
    return null
}

/**
 * Parses the embedded `configSnapshot.fields` array into an itemId -> localized question text map,
 * so the inspection answers list can show real question text instead of raw ids/insp.* keys.
 * Mirrors iOS's AVRenderableConfigSnapshot read-mode rendering.
 */
private fun parseInspectionItemLabels(json: String?): Map<String, String> {
    if (json.isNullOrBlank()) return emptyMap()
    val snapshot = extractJsonObject(json, "configSnapshot") ?: return emptyMap()
    val fieldsIdx = snapshot.indexOf("\"fields\"")
    if (fieldsIdx < 0) return emptyMap()
    val result = mutableMapOf<String, String>()
    Regex("\\{[^{}]*\\}").findAll(snapshot, fieldsIdx).forEach { m ->
        val obj = m.value
        val key = Regex("\"key\"\\s*:\\s*\"([^\"]*)\"").find(obj)?.groupValues?.get(1)
        val label = Regex("\"label\"\\s*:\\s*\"([^\"]*)\"").find(obj)?.groupValues?.get(1)
        if (key != null && label != null) result[key] = label
    }
    return result
}

/** Localized short tire / brake position label (FL -> VL in German). */
@Composable
internal fun tirePositionLabel(pos: String): String =
    PACK_LABELS["pos_$pos"]?.let { stringResource(it) } ?: pos.uppercase()
