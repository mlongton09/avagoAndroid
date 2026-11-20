package com.avago.feature.log.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.Document
import com.avago.core.data.domain.DocumentKind
import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityType
import com.avago.core.data.domain.CostLine
import com.avago.core.data.domain.CostLineType
import com.avago.core.ui.categoryBadgeColor
import com.avago.core.ui.categoryIconName
import com.avago.core.ui.components.StoredFileImage
import com.avago.feature.log.R
import com.avago.feature.assets.R as AssetsR
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.LogPacks
import com.avago.feature.log.model.PackDisplay
import com.avago.feature.log.model.PositionReadings
import com.avago.feature.log.viewmodel.LogAudit
import com.avago.feature.log.viewmodel.LogDetailUiState
import com.avago.feature.log.viewmodel.LogDetailViewModel
import kotlinx.serialization.json.JsonPrimitive
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.math.BigDecimal
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
    onAddDocument: ((activityId: String) -> Unit)? = null,
    onOpenDocument: (documentId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LogDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(entryId) {
        viewModel.setEntryId(entryId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()
    val distanceUnit by viewModel.distanceUnit.collectAsStateWithLifecycle()
    val inspectionStrings by viewModel.inspectionStrings.collectAsStateWithLifecycle()
    val assetType by viewModel.assetType.collectAsStateWithLifecycle()
    val canAddDocument by viewModel.canAddDocument.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbarHostState.showSnackbar(it) }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.log?.displayTitle ?: stringResource(R.string.log_detail_title_fallback)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.log_action_back))
                    }
                },
                actions = {
                    val log = state.log
                    val shareContext = LocalContext.current
                    val shareAssetId = log?.assetId
                    if (log != null && shareAssetId != null && isInspectionLog(log)) {
                        IconButton(onClick = { InspectionReportShare.share(shareContext, shareAssetId, log.id, log.displayTitle) }) {
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
                // Amounts are in the record currency.
                val currencyFormat = remember(currencyCode) {
                    val fmt = NumberFormat.getCurrencyInstance(Locale.getDefault())
                    runCatching { fmt.currency = java.util.Currency.getInstance(currencyCode.uppercase()) }
                    fmt
                }

                val inspectionData = remember(log.payload) { InspectionV2.parseDataObject(log.payload) }
                val isInspection = remember(log) { isInspectionLog(log) }
                val hasInspectionContent = remember(log.payload) {
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
                            text = log.displayTitle,
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
                            log.categoryLabel?.let { cat ->
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
                                text = dateFormatter.format(Date(log.occurredAt)),
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
                                val displayCost = state.totalCost
                                Text(
                                    text = if (displayCost.signum() > 0) currencyFormat.format(displayCost) else "\u2014",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            // Right: ODOMETER / HOURS (only if recorded)
                            log.meterValue?.let { meter ->
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
                                        text = "${"%.1f".format(meter.toDouble())} $distanceUnit",
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
                    log.attributes.takeIf { it.isNotBlank() && it != "{}" }?.let { attrsJson ->
                        val attrsObj = remember(attrsJson) { LogPacks.parse(attrsJson) }
                        val translator = rememberPackTranslator()
                        // Tire / brake per-position readings render as a coloured sketch + per-wheel
                        // list (web PositionReadings); their raw rows are skipped to avoid duplicates.
                        val tireReadings = remember(attrsObj, assetType) { PositionReadings.tireFromAttrs(attrsObj, assetType) }
                        val brakeReadings = remember(attrsObj, assetType) { PositionReadings.brakeFromAttrs(attrsObj, assetType) }
                        val readingKeys = remember(tireReadings, brakeReadings) {
                            buildSet {
                                if (tireReadings != null) addAll(listOf("tire_positions", "tire_tread", "tire_psi"))
                                if (brakeReadings != null) addAll(listOf("brake_pad_mm", "brake_rotor_mm"))
                            }
                        }
                        val packSections = remember(attrsObj, translator, currencyFormat, readingKeys) {
                            PackDisplay.build(
                                attrs = attrsObj,
                                t = translator,
                                formatCurrency = { currencyFormat.format(it) },
                                formatDate = { iso -> formatIsoDate(iso) },
                                skipKeys = readingKeys,
                            )
                        }
                        PackDisplay.SectionId.entries.forEach { id ->
                            val section = packSections.firstOrNull { it.id == id }
                            val readings = when (id) {
                                PackDisplay.SectionId.TIRE -> tireReadings
                                PackDisplay.SectionId.BRAKE -> brakeReadings
                                else -> null
                            }
                            if (section == null && readings == null) return@forEach
                            val title = section?.title ?: translator.t("pack_${id.name.lowercase()}", null)
                            Spacer(Modifier.height(16.dp))
                            DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                                SectionHeader(title.uppercase(LocalConfiguration.current.locales[0]))
                                if (section != null && section.rows.isNotEmpty()) {
                                    KeyValueRows(section.rows.map { it.label to it.value })
                                }
                                if (readings != null) {
                                    PositionReadingsMap(
                                        assetType = assetType,
                                        panel = readings,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                }
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
                    val performedByName = log.performedByName
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
                                itemsIndexed(state.photos, key = { _, p -> p.id }) { index, photo ->
                                    // The first photo (lowest sort order) is the entry's primary; long-press promotes another.
                                    val setPrimaryLabel = stringResource(R.string.log_detail_photo_set_primary)
                                    Box(
                                        modifier = Modifier
                                            .size(width = 140.dp, height = 105.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClickLabel = setPrimaryLabel,
                                                onLongClick = { viewModel.setPrimaryPhoto(photo.id) },
                                            ),
                                    ) {
                                        StoredFileImage(
                                            file = photo.file,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            contentDescription = stringResource(R.string.log_detail_photo_cd),
                                        )
                                        if (index == 0 && state.photos.size > 1) {
                                            Badge(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp),
                                            ) { Text("\u2605") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 6. Cost breakdown (itemized) ──────────────────────────────
                    if (state.costLines.isNotEmpty()) {
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
                                        currency = log.currency,
                                    )
                                    if (line != state.costLines.last()) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                                if (state.totalCost.signum() > 0) {
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
                                            text = currencyFormat.format(state.totalCost),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 7. Documents filed on the record ──────────────────────────
                    val addDocument = onAddDocument?.takeIf { canAddDocument }
                    if (state.documents.isNotEmpty() || addDocument != null) {
                        Spacer(Modifier.height(16.dp))
                        DetailCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SectionHeader(stringResource(R.string.log_detail_section_documents))
                            state.documents.forEach { doc ->
                                DocumentRow(doc = doc, onClick = { onOpenDocument(doc.id) })
                            }
                            if (addDocument != null) {
                                TextButton(
                                    onClick = { addDocument(log.id) },
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.log_detail_add_document))
                                }
                            }
                        }
                    }

                    // ── 9. Inspection card ────────────────────────────────────────
                    if (isInspection && hasInspectionContent) {
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
                                InspectionEnvelopeDetail(data = inspectionData, strings = inspectionStrings, assetType = assetType)
                            }
                        }
                    }

                    state.audit?.let { audit ->
                        Spacer(Modifier.height(16.dp))
                        AuditFooter(audit = audit, modifier = Modifier.padding(horizontal = 16.dp))
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

/** "Created by … on …" and "Last edited by … on …" lines. */
@Composable
private fun AuditFooter(audit: LogAudit, modifier: Modifier = Modifier) {
    val format = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
    val created = format.format(Date(audit.createdAt))
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = audit.createdBy?.let { stringResource(R.string.log_detail_audit_created_by, it, created) }
                ?: stringResource(R.string.log_detail_audit_created, created),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        audit.editedAt?.let { at ->
            val edited = format.format(Date(at))
            Text(
                text = audit.editedBy?.let { stringResource(R.string.log_detail_audit_edited_by, it, edited) }
                    ?: stringResource(R.string.log_detail_audit_edited, edited),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One document: its title, then kind, issue date and issuer. */
@Composable
private fun DocumentRow(doc: Document, onClick: () -> Unit) {
    val details = listOfNotNull(
        stringResource(documentKindLabel(doc.kind)),
        doc.issuedOn?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
        doc.issuerName,
    ).joinToString(" \u00b7 ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(doc.displayTitle ?: stringResource(AssetsR.string.asset_detail_document_untitled), style = MaterialTheme.typography.bodyMedium)
        Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun documentKindLabel(kind: DocumentKind): Int = when (kind) {
    DocumentKind.RECEIPT -> AssetsR.string.asset_document_kind_receipt
    DocumentKind.INVOICE -> AssetsR.string.asset_document_kind_invoice
    DocumentKind.WARRANTY -> AssetsR.string.asset_document_kind_warranty
    DocumentKind.HOWTO -> AssetsR.string.asset_document_kind_howto
    DocumentKind.OTHER -> AssetsR.string.asset_document_kind_other
}

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
private fun CostLineDetailRow(
    line: CostLine,
    currencyFormat: NumberFormat,
    currency: String?,
    modifier: Modifier = Modifier,
) {
    val tax = LogDetailUiState.taxOf(line, currency)
    val lineTotal = line.total + tax
    val isPart = line.kind == CostLineType.PART
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val kindLabel = stringResource(if (isPart) R.string.log_cost_line_part else R.string.log_cost_line_labor).uppercase(LocalConfiguration.current.locales[0])
            val badgeColor = if (isPart)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.tertiaryContainer
            val badgeTextColor = if (isPart)
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
                text = line.description ?: stringResource(if (isPart) R.string.log_cost_line_part else R.string.log_cost_line_labor),
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
                text = "${line.quantity.stripTrailingZeros().toPlainString()} × ${currencyFormat.format(line.unitCost ?: BigDecimal.ZERO)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (tax.signum() > 0) {
                Text(
                    text = stringResource(R.string.log_cost_line_tax, currencyFormat.format(tax)),
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

/** Inspection records render the inspection card and can share the web report. */
private fun isInspectionLog(log: Activity): Boolean =
    log.type == ActivityType.INSPECTION ||
        log.categoryLabel?.lowercase()?.contains("inspect") == true ||
        (InspectionV2.parseDataObject(log.payload)?.get("log_type") as? JsonPrimitive)?.content == "inspection"
