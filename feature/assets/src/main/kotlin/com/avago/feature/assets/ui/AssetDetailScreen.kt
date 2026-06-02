package com.avago.feature.assets.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.DocEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.core.ui.CategoryBadge
import com.avago.core.ui.EmptyState
import com.avago.feature.assets.R
import com.avago.feature.assets.model.AssetTypes
import com.avago.feature.assets.viewmodel.AssetDetailViewModel
import com.avago.feature.assets.viewmodel.LogsByYear
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ASSET_DETAIL_TABS = listOf("Log", "Docs", "Work Orders")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    assetId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddLogEntry: () -> Unit,
    onLogEntryClick: (entryId: String) -> Unit,
    onOpenPhotoGallery: (initialIndex: Int) -> Unit = {},
    onOpenWorkOrders: () -> Unit = {},
    onOpenNotes: (initialText: String) -> Unit = {},
    onOpenWheelConfig: () -> Unit = {},
    onOpenWheelDataInput: () -> Unit = {},
    onOpenRentals: () -> Unit = {},
    onOpenAsset: (assetId: String) -> Unit = {},
    viewModel: AssetDetailViewModel = hiltViewModel(),
) {
    val asset by viewModel.asset.collectAsStateWithLifecycle()
    val logsByYear by viewModel.logsByYear.collectAsStateWithLifecycle()
    val entryCount by viewModel.entryCount.collectAsStateWithLifecycle()
    val lastServiceDate by viewModel.lastServiceDate.collectAsStateWithLifecycle()
    val openWorkOrderCount by viewModel.openWorkOrderCount.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()
    val latestMeterReading by viewModel.latestMeterReading.collectAsStateWithLifecycle()
    val showMeterDialog by viewModel.showMeterDialog.collectAsStateWithLifecycle()
    val isSavingMeter by viewModel.isSavingMeter.collectAsStateWithLifecycle()
    val canEditAsset by viewModel.canEditAsset.collectAsStateWithLifecycle()

    var showOverflowMenu by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState { ASSET_DETAIL_TABS.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Collapsing header — mirrors iOS AssetDetailViewController collapse behavior:
    // scroll down past threshold → snap-collapse with 250ms animation;
    // overscroll at top → snap-expand.
    var isHeaderCollapsed by remember { mutableStateOf(false) }
    var downAccumPx by remember { mutableFloatStateOf(0f) }
    val collapseThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && !isHeaderCollapsed) {
                    downAccumPx -= available.y  // accumulate positive distance scrolled down
                    if (downAccumPx >= collapseThresholdPx) {
                        isHeaderCollapsed = true
                        downAccumPx = 0f
                    }
                } else if (available.y > 0f) {
                    downAccumPx = 0f  // reset accumulator when scrolling back up
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // available.y > 0 means list is at top and user is still pulling down (overscroll)
                if (available.y > 0f && isHeaderCollapsed) {
                    isHeaderCollapsed = false
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        // Avago app logo — 22dp red rounded tile with white wrench,
                        // mirrors iOS AssetDetailViewController navLogoView.
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFFE53935)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(com.avago.core.design.R.drawable.ic_app_logo),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = asset?.name ?: stringResource(R.string.asset_detail_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(weight = 1f, fill = false),
                        )
                        Text(
                            text = "Log",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.asset_detail_back),
                        )
                    }
                },
                actions = {
                    if (canEditAsset) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.asset_detail_edit),
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.asset_detail_overflow_menu),
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.asset_detail_share_pdf)) },
                                leadingIcon = {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    scope.launch {
                                        val uri = viewModel.generatePdf(context)
                                        if (uri != null) {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share Maintenance Report"))
                                        }
                                    }
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        floatingActionButton = {
            // FAB only visible on the Log tab (index 0)
            if (pagerState.currentPage == 0) {
                FloatingActionButton(
                    onClick = onAddLogEntry,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.asset_detail_add_log),
                    )
                }
            }
        },
    ) { paddingValues ->
        if (asset == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.asset_detail_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }

        val safeAsset = asset ?: error("unreachable: null guard above already returned")

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(nestedScrollConnection),
        ) {
            AnimatedVisibility(
                visible = !isHeaderCollapsed,
                enter = expandVertically(animationSpec = tween(250)) + fadeIn(tween(250)),
                exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(tween(150)),
            ) {
                Column {
                    AssetDetailHeader(
                        asset = safeAsset,
                        photos = photos,
                        onAddPhoto = { onOpenPhotoGallery(0) },
                        onPhotoTap = { index -> onOpenPhotoGallery(index) },
                    )
                    AssetStatsRow(
                        entryCount = entryCount,
                        lastServiceDate = lastServiceDate,
                        openWorkOrderCount = openWorkOrderCount,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // Tabs sit between the (collapsible) header and the pager — iOS parity.
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
            ) {
                ASSET_DETAIL_TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> LogTab(
                        asset = safeAsset,
                        logsByYear = logsByYear,
                        latestMeterReading = latestMeterReading,
                        showMeterDialog = showMeterDialog,
                        isSavingMeter = isSavingMeter,
                        availableCategories = availableCategories,
                        categoryFilter = categoryFilter,
                        onCategoryFilterChanged = { viewModel.onCategoryFilterChanged(it) },
                        onAddMeterReading = { viewModel.onAddMeterReadingTapped() },
                        onDismissMeterDialog = { viewModel.onDismissMeterDialog() },
                        onSaveMeterReading = { viewModel.saveMeterReading(it) },
                        onOpenWheelConfig = onOpenWheelConfig,
                        onOpenWheelDataInput = onOpenWheelDataInput,
                        onLogEntryClick = onLogEntryClick,
                        onCloneLogEntry = { viewModel.cloneLogEntry(it) },
                        currencyCode = currencyCode,
                    )
                    1 -> DocumentsTab(documents = documents)
                    2 -> WorkOrdersTab(onOpenWorkOrders = onOpenWorkOrders)
                }
            }
        }
    }
}

// ── Tab content ──────────────────────────────────────────────────────────────

@Composable
private fun LogTab(
    asset: AssetEntity,
    logsByYear: List<LogsByYear>,
    latestMeterReading: Double?,
    showMeterDialog: Boolean,
    isSavingMeter: Boolean,
    availableCategories: List<String>,
    categoryFilter: String?,
    onCategoryFilterChanged: (String?) -> Unit,
    onAddMeterReading: () -> Unit,
    onDismissMeterDialog: () -> Unit,
    onSaveMeterReading: (Double) -> Unit,
    onOpenWheelConfig: () -> Unit,
    onOpenWheelDataInput: () -> Unit,
    onLogEntryClick: (entryId: String) -> Unit,
    onCloneLogEntry: (LogEntity) -> Unit,
    currencyCode: String = "USD",
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        if (!asset.meterType.isNullOrBlank()) {
            item(key = "meter_card") {
                MeterCard(
                    meterType = asset.meterType,
                    latestMeterReading = latestMeterReading,
                    onAddReading = onAddMeterReading,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (availableCategories.isNotEmpty()) {
            item(key = "category_filter") {
                CategoryFilterRow(
                    categories = availableCategories,
                    selected = categoryFilter,
                    onSelect = onCategoryFilterChanged,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (logsByYear.isEmpty()) {
            item(key = "log_empty") {
                EmptyState(
                    message = stringResource(R.string.asset_detail_no_logs),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            }
        } else {
            logsByYear.forEach { group ->
                item(key = "year_${group.year}") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = group.year.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                items(items = group.entries, key = { it.entryId }) { entry ->
                    LogEntryRow(
                        entry = entry,
                        onClick = { onLogEntryClick(entry.entryId) },
                        onClone = { onCloneLogEntry(entry) },
                        currencyCode = currencyCode,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

    }

    if (showMeterDialog) {
        MeterReadingDialog(
            meterType = asset.meterType,
            isSaving = isSavingMeter,
            onDismiss = onDismissMeterDialog,
            onConfirm = onSaveMeterReading,
        )
    }
}

@Composable
private fun InfoTab(
    asset: AssetEntity,
    photos: List<PhotoEntity>,
    onOpenPhotoGallery: (Int) -> Unit,
    onOpenNotes: (String) -> Unit,
    onOpenWheelConfig: () -> Unit,
    onOpenWheelDataInput: () -> Unit,
    onOpenRentals: () -> Unit,
    onOpenAsset: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (photos.isNotEmpty()) {
            item(key = "photos_title") {
                Text(
                    text = stringResource(R.string.asset_detail_photos),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "photos_strip") {
                AssetPhotoStrip(
                    photos = photos,
                    onPhotoClick = onOpenPhotoGallery,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        if (asset.parentAssetId != null || asset.childCount > 0) {
            item(key = "hierarchy_card") {
                HierarchyCard(
                    asset = asset,
                    onOpenAsset = onOpenAsset,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        item(key = "location_card") {
            LocationCard(
                asset = asset,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        item(key = "details_card") {
            DetailsCard(
                asset = asset,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        item(key = "qr_code_card") {
            QrCodeCard(
                assetId = asset.assetId,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val notesText = asset.attributes
            ?.let { attrs -> Regex("\"notes\"\\s*:\\s*\"([^\"]*)\"").find(attrs)?.groupValues?.getOrNull(1) }
            ?: ""
        item(key = "notes_card") {
            NotesCard(
                notesPreview = notesText,
                onClick = { onOpenNotes(notesText) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DocumentsTab(documents: List<DocEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (documents.isEmpty()) {
            item {
                EmptyState(
                    message = "No documents yet.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            }
        } else {
            items(documents, key = { it.docId }) { doc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(doc.name, style = MaterialTheme.typography.bodyMedium)
                        doc.docType?.takeIf { it.isNotBlank() }?.let { type ->
                            Text(
                                text = type.replace("_", " ").replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun WorkOrdersTab(onOpenWorkOrders: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WorkOrdersCard(
            onClick = onOpenWorkOrders,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetDetailHeader(
    asset: AssetEntity,
    photos: List<PhotoEntity>,
    modifier: Modifier = Modifier,
    onAddPhoto: () -> Unit = {},
    onPhotoTap: (Int) -> Unit = {},
) {
    val bannerHeight = 210.dp
    val infoRowHeight = 64.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight),
        ) {
            if (photos.isNotEmpty()) {
                // ── Photo mode: paged horizontal scroller of asset photos
                val photoPager = rememberPagerState(pageCount = { photos.size })
                HorizontalPager(
                    state = photoPager,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val photo = photos[page]
                    val model: Any? = photo.localPath?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
                        ?: photo.downloadUrl
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .combinedClickable(
                                onClick = { onPhotoTap(page) },
                                onLongClick = { onPhotoTap(page) },
                            ),
                    ) {
                        if (model != null) {
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.30f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        photos.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == photoPager.currentPage) Color.White
                                        else Color.White.copy(alpha = 0.45f),
                                    ),
                            )
                        }
                    }
                }
            } else {
                // ── Color mode: asset-type tinted bg + hero icon watermark + name overlay
                val bgColor = rememberParsedColor(AssetTypes.colorHexFor(asset.assetType))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgColor),
                )
                Icon(
                    painter = painterResource(AssetTypes.iconResFor(asset.assetType)),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.40f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(110.dp),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, end = 134.dp, bottom = 12.dp),
                ) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = listOfNotNull(asset.year?.toString(), asset.make, asset.model)
                        .joinToString(" ")
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // ── Camera button — top-right of banner, always visible
            IconButton(
                onClick = onAddPhoto,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.30f)),
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Add photo",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // ── Info row — photo mode only (name + subtitle below the photo)
        if (photos.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(infoRowHeight)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(asset.year?.toString(), asset.make, asset.model)
                    .joinToString(" ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun rememberParsedColor(hex: String): Color = try {
    Color(0xFF000000L or hex.removePrefix("#").toLong(16))
} catch (_: Exception) {
    MaterialTheme.colorScheme.primary
}

@Composable
private fun AssetStatsRow(
    entryCount: Int,
    lastServiceDate: Long?,
    openWorkOrderCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(
            label = stringResource(R.string.asset_detail_log_entries),
            value = entryCount.toString(),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .height(28.dp)
                .width(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        StatCell(
            label = stringResource(R.string.asset_detail_last_service),
            value = if (lastServiceDate != null) formatShortDate(lastServiceDate) else "Never",
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .height(28.dp)
                .width(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        StatCell(
            label = stringResource(R.string.asset_detail_since_service),
            value = if (lastServiceDate != null) sinceService(lastServiceDate) else "—",
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .height(28.dp)
                .width(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        StatCell(
            label = stringResource(R.string.asset_detail_open_wos),
            value = openWorkOrderCount.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.asset_detail_filter_all)) },
            )
        }
        items(categories) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = {
                    Text(category.replace("_", " ").replaceFirstChar { it.uppercase() })
                },
            )
        }
    }
}

@Composable
private fun LogEntryRow(
    entry: LogEntity,
    onClick: () -> Unit,
    onClone: () -> Unit,
    currencyCode: String = "USD",
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryBadge(categoryId = entry.category)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            entry.category?.takeIf { it.isNotBlank() }?.let { category ->
                Text(
                    text = category.replace("_", " ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = formatDate(entry.entryDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entry.cost?.takeIf { it > 0.0 }?.let { cost ->
            Text(
                text = formatCurrency(cost, currencyCode),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.asset_detail_overflow_menu),
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.asset_detail_action_clone)) },
                    onClick = {
                        showMenu = false
                        onClone()
                    },
                )
            }
        }
    }
}

@Composable
private fun AssetPhotoStrip(
    photos: List<PhotoEntity>,
    onPhotoClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        itemsIndexed(photos) { index, photo ->
            val url = photo.downloadUrl
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPhotoClick(index) },
                contentAlignment = Alignment.Center,
            ) {
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkOrdersCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.asset_detail_work_orders_card),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotesCard(
    notesPreview: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.asset_detail_notes_card),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                if (notesPreview.isNotBlank()) {
                    Text(
                        text = notesPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MeterCard(
    meterType: String?,
    latestMeterReading: Double?,
    onAddReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = meterLabelFor(meterType),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatMeterReading(latestMeterReading, meterType),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            OutlinedButton(onClick = onAddReading) {
                Text("Add Reading")
            }
        }
    }
}

@Composable
private fun MeterReadingDialog(
    meterType: String?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf("") }
    val parsedValue = value.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(meterLabelFor(meterType)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Reading") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsedValue?.let(onConfirm) },
                enabled = parsedValue != null && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        },
    )
}

// ---------------------------------------------------------------------------
// New cards: Hierarchy, Location, Details (custom attributes), Documents
// ---------------------------------------------------------------------------

/** Known attribute keys that are displayed via dedicated UI fields elsewhere. */
private val KNOWN_ATTRIBUTE_KEYS = setOf(
    "name", "make", "model", "year", "color", "license_plate", "vin",
    "purchase_date", "purchase_price", "notes",
    "street_address", "city", "state", "zip_code", "country",
    "fleet_number",
)

/**
 * Parses a JSON-like attributes string (simple key-value pairs) into a map.
 * Only handles flat string values (no nested objects/arrays).
 */
private fun parseAttributes(json: String?): Map<String, String> {
    if (json.isNullOrBlank()) return emptyMap()
    val result = mutableMapOf<String, String>()
    val pattern = Regex(""""(\w+)"\s*:\s*"([^"]*)"""")
    pattern.findAll(json).forEach { match ->
        result[match.groupValues[1]] = match.groupValues[2]
    }
    return result
}

@Composable
private fun HierarchyCard(
    asset: AssetEntity,
    onOpenAsset: (assetId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Hierarchy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            asset.parentAssetId?.let { parentId ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAsset(parentId) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Parent: $parentId",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Open parent asset",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (asset.childCount > 0) {
                    HorizontalDivider()
                }
            }

            if (asset.childCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAsset(asset.assetId) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${asset.childCount} child asset${if (asset.childCount != 1L) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Open child assets",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationCard(
    asset: AssetEntity,
    modifier: Modifier = Modifier,
) {
    val attributes = parseAttributes(asset.attributes)
    val street = asset.addressLine1 ?: attributes["street_address"]
    val latitude = attributes["latitude"]?.toDoubleOrNull()
    val longitude = attributes["longitude"]?.toDoubleOrNull()
    if (street == null && (latitude == null || longitude == null)) return
    val context = LocalContext.current

    val line2 = asset.addressLine2
    val city = asset.city ?: attributes["city"]
    val state = asset.state ?: attributes["state"]
    val zip = asset.postalCode ?: attributes["zip_code"]
    val country = asset.country ?: attributes["country"]

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            street?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!line2.isNullOrBlank()) {
                Text(
                    text = line2,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val cityStateZip = listOfNotNull(city, state, zip).joinToString(", ")
            if (cityStateZip.isNotBlank()) {
                Text(
                    text = cityStateZip,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!country.isNullOrBlank()) {
                Text(
                    text = country,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (latitude != null && longitude != null) {
                MapCard(
                    latitude = latitude,
                    longitude = longitude,
                    modifier = Modifier.padding(top = 12.dp),
                    onOpenMaps = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude"),
                        )
                        context.startActivity(intent)
                    },
                )
            }
        }
    }
}

@Composable
private fun MapCard(
    latitude: Double,
    longitude: Double,
    onOpenMaps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Coordinates",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$latitude, $longitude",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onOpenMaps) {
                Text("Open in Maps")
            }
        }
    }
}

@Composable
private fun DetailsCard(
    asset: AssetEntity,
    modifier: Modifier = Modifier,
) {
    val customAttrs = parseAttributes(asset.attributes)
        .filterKeys { it !in KNOWN_ATTRIBUTE_KEYS }
    if (customAttrs.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            customAttrs.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = key.replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}


// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

/**
 * Formats a meter reading with the appropriate unit suffix based on [meterType].
 * Returns "—" when [value] is null.
 */
fun formatMeterReading(value: Double?, meterType: String?): String {
    if (value == null) return "—"
    val formatted = NumberFormat.getNumberInstance().format(value)
    return when (meterType) {
        "odometer" -> "$formatted mi"
        "hours" -> "$formatted hrs"
        else -> formatted
    }
}

private fun meterLabelFor(meterType: String?): String {
    val normalized = meterType
        ?.replace("_", " ")
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        ?: "Meter"
    return "$normalized Reading"
}

private fun isWheelAsset(assetType: String?): Boolean =
    assetType?.contains("vehicle", ignoreCase = true) == true ||
        assetType?.contains("truck", ignoreCase = true) == true ||
        assetType?.contains("trailer", ignoreCase = true) == true

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))

private fun formatShortDate(epochMs: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))

private fun sinceService(epochMs: Long): String {
    val days = ((System.currentTimeMillis() - epochMs) / 86_400_000L).toInt()
    return when {
        days < 1  -> "Today"
        days == 1 -> "1 day"
        days < 30 -> "$days days"
        days < 60 -> "1 month"
        days < 365 -> "${days / 30} months"
        days < 730 -> "1 year"
        else -> "${days / 365} years"
    }
}

private fun formatCurrency(amount: Double, currencyCode: String = "USD"): String = try {
    com.avago.core.data.Formatters.formatCurrency(amount, currencyCode)
} catch (_: Exception) {
    "%.2f".format(amount)
}
