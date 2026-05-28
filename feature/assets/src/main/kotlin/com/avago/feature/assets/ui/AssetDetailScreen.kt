package com.avago.feature.assets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.DocEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.PhotoEntity
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
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()

    var showOverflowMenu by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState { ASSET_DETAIL_TABS.size }
    val scope = rememberCoroutineScope()

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
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(asset?.name ?: stringResource(R.string.asset_detail_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.asset_detail_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.asset_detail_edit),
                            )
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
                                    onClick = { showOverflowMenu = false },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(),
                )
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    indicator = { tabPositions ->
                        // Live-track the swipe using currentPageOffsetFraction so the indicator
                        // follows the user's finger — mirrors iOS AVTabStrip content animation.
                        val fraction = pagerState.currentPageOffsetFraction
                        val currentIndex = pagerState.currentPage
                        val targetIndex = when {
                            fraction > 0f -> (currentIndex + 1).coerceAtMost(tabPositions.lastIndex)
                            fraction < 0f -> (currentIndex - 1).coerceAtLeast(0)
                            else -> currentIndex
                        }
                        val currentPos = tabPositions[currentIndex]
                        val targetPos = tabPositions[targetIndex]
                        val absFraction = kotlin.math.abs(fraction)
                        val indicatorLeft = lerp(currentPos.left, targetPos.left, absFraction)
                        val indicatorRight = lerp(currentPos.right, targetPos.right, absFraction)
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.wrapContentSize(Alignment.BottomStart)
                                .offset(x = indicatorLeft)
                                .width(indicatorRight - indicatorLeft),
                        )
                    },
                ) {
                    ASSET_DETAIL_TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title) },
                        )
                    }
                }
            }
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
                    AssetDetailHeader(asset = safeAsset, onAddPhoto = { onOpenPhotoGallery(0) })
                    AssetStatsRow(
                        entryCount = entryCount,
                        lastServiceDate = lastServiceDate,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    HorizontalDivider()
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> LogTab(
                        logsByYear = logsByYear,
                        availableCategories = availableCategories,
                        categoryFilter = categoryFilter,
                        onCategoryFilterChanged = { viewModel.onCategoryFilterChanged(it) },
                        onLogEntryClick = onLogEntryClick,
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
    logsByYear: List<LogsByYear>,
    availableCategories: List<String>,
    categoryFilter: String?,
    onCategoryFilterChanged: (String?) -> Unit,
    onLogEntryClick: (entryId: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
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
                    message = "No log entries yet. Tap + to add one.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            }
        } else {
            logsByYear.forEach { group ->
                stickyHeader(key = "year_${group.year}") {
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
                    LogEntryRow(entry = entry, onClick = { onLogEntryClick(entry.entryId) })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
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

        item(key = "rentals_card") {
            RentalsCard(
                onClick = onOpenRentals,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (asset.assetType?.contains("vehicle", ignoreCase = true) == true ||
            asset.assetType?.contains("truck", ignoreCase = true) == true ||
            asset.assetType?.contains("trailer", ignoreCase = true) == true
        ) {
            item(key = "wheel_data_card") {
                WheelDataCard(
                    onConfigClick = onOpenWheelConfig,
                    onDataClick = onOpenWheelDataInput,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
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

@Composable
private fun AssetDetailHeader(
    asset: AssetEntity,
    modifier: Modifier = Modifier,
    onAddPhoto: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AssetAvatar(
                initial = asset.avatarInitial ?: asset.name.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                assetType = asset.assetType,
                size = 64,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                asset.assetType?.let { assetType ->
                    val labelResId = AssetTypes.labelResIdFor(assetType)
                    Text(
                        text = if (labelResId != null) stringResource(labelResId)
                        else assetType.replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val makeModelYear = listOfNotNull(
                    asset.year?.toString(),
                    asset.make,
                    asset.model,
                ).joinToString(" ")
                if (makeModelYear.isNotBlank()) {
                    Text(
                        text = makeModelYear,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Fleet number badge
                val fleetNumber = parseAttributes(asset.attributes)["fleet_number"]
                if (!fleetNumber.isNullOrBlank()) {
                    Text(
                        text = "Fleet #$fleetNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        IconButton(
            onClick = onAddPhoto,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f)),
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Add photo",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun AssetStatsRow(
    entryCount: Int,
    lastServiceDate: Long?,
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
            label = "ENTRIES",
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
            label = "LAST SERVICE",
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
            label = "SINCE SERVICE",
            value = if (lastServiceDate != null) sinceService(lastServiceDate) else "—",
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                text = formatCurrency(cost),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
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
private fun RentalsCard(
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
                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.rental_asset_detail_action),
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
private fun WheelDataCard(
    onConfigClick: () -> Unit,
    onDataClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.asset_detail_wheel_tire_card),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onConfigClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.asset_detail_wheel_configuration))
                }
                OutlinedButton(
                    onClick = onDataClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.asset_detail_wheel_data_input))
                }
            }
        }
    }
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
    // Prefer dedicated entity columns; fall back to attributes map for street_address
    val street = asset.addressLine1
        ?: parseAttributes(asset.attributes)["street_address"]
        ?: return

    val line2 = asset.addressLine2
    val city = asset.city ?: parseAttributes(asset.attributes)["city"]
    val state = asset.state ?: parseAttributes(asset.attributes)["state"]
    val zip = asset.postalCode ?: parseAttributes(asset.attributes)["zip_code"]
    val country = asset.country ?: parseAttributes(asset.attributes)["country"]

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
            Text(
                text = street,
                style = MaterialTheme.typography.bodyMedium,
            )
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

private fun formatCurrency(amount: Double): String = try {
    NumberFormat.getCurrencyInstance(Locale.getDefault()).format(amount)
} catch (_: Exception) {
    "%.2f".format(amount)
}
