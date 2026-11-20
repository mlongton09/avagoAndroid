package com.avago.feature.assets.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.Asset
import com.avago.core.data.rental.FleetStatus
import com.avago.core.ui.AvagoSearchBar
import com.avago.core.ui.EmptyState
import com.avago.core.ui.rememberScrollAwareHeaderState
import com.avago.feature.assets.R
import com.avago.feature.assets.model.AssetTypes
import com.avago.feature.assets.viewmodel.AssetListViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AssetListScreen(
    onAssetClick: (assetId: String) -> Unit,
    onAssetLongPress: (assetId: String) -> Unit = {},
    onAddAsset: () -> Unit,
    onScanBarcode: () -> Unit = {},
    /** Optional content above the list (e.g. the rentals fleet summary card). */
    header: (@Composable () -> Unit)? = null,
    viewModel: AssetListViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val presentTypes by viewModel.presentTypes.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val showOnboarding by onboardingViewModel.showBanner.collectAsStateWithLifecycle()
    val canCreateAsset by viewModel.canCreateAsset.collectAsStateWithLifecycle()
    val openWoCounts by viewModel.openWoCounts.collectAsStateWithLifecycle()
    val rentalStatuses by viewModel.rentalStatuses.collectAsStateWithLifecycle()
    val rentalFilter by viewModel.rentalFilter.collectAsStateWithLifecycle()
    val canPrintQrLabels by viewModel.canPrintQrLabels.collectAsStateWithLifecycle()

    var showFilterMenu by remember { mutableStateOf(false) }
    var showQrLabels by remember { mutableStateOf(false) }

    if (showQrLabels) {
        QrLabelSheetDialog(assetIds = assets.map { it.id }, onDismiss = { showQrLabels = false })
    }

    val scrollAwareState = rememberScrollAwareHeaderState()
    val headerVisible by scrollAwareState.headerVisible
    val headerProgress by animateFloatAsState(
        targetValue = if (headerVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "asset_header_progress",
    )
    // Search row: 36dp bar + 8dp top + 8dp bottom = 52dp total
    val searchRowHeightDp = 52.dp
    val density = LocalDensity.current
    val searchRowHeightPx = with(density) { searchRowHeightDp.toPx() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (canCreateAsset) {
                FloatingActionButton(
                    onClick = onAddAsset,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.assets_add_content_description),
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollAwareState),
        ) {
            // Build type-grouped sectioned list matching iOS section order
            val typeOrder = AssetTypes.all.map { it.key }
            val grouped = assets.groupBy { it.assetTypeCode ?: "other" }
            val sectionedItems: List<Any> = buildList {
                for (typeKey in typeOrder) {
                    val items = grouped[typeKey] ?: continue
                    add(typeKey)
                    addAll(items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
                }
                for ((typeKey, items) in grouped) {
                    if (typeKey !in typeOrder) {
                        add(typeKey)
                        addAll(items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (assets.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Reserve space for the header even when empty
                        Spacer(modifier = Modifier.height(with(density) { (searchRowHeightPx * headerProgress).toDp() }))
                        EmptyState(
                            message = stringResource(R.string.assets_empty),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = with(density) { (searchRowHeightPx * headerProgress).toDp() },
                            bottom = 80.dp,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        header?.let { content ->
                            item(key = "list_header") { content() }
                        }
                        // Sync error banner
                        syncError?.let { msg ->
                            item(key = "sync_error") {
                                Surface(
                                    onClick = { viewModel.refresh() },
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = msg,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        Text(
                                            text = stringResource(R.string.asset_detail_retry),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                            }
                        }

                        items(
                            items = sectionedItems,
                            key = { item ->
                                when (item) {
                                    is String -> "header_$item"
                                    is Asset -> item.id
                                    else -> item.hashCode()
                                }
                            },
                        ) { item ->
                            when (item) {
                                is String -> AssetSectionHeader(typeKey = item)
                                is Asset -> {
                                    // Swipe-left to edit — mirrors iOS AssetsListViewController
                                    // trailing swipe action that reveals Edit.
                                    SwipeToDismissBox(
                                        state = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { value ->
                                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                                    onAssetLongPress(item.id)
                                                }
                                                false // keep the row in place
                                            },
                                            positionalThreshold = { it * 0.35f },
                                        ),
                                        enableDismissFromEndToStart = true,
                                        enableDismissFromStartToEnd = false,
                                        backgroundContent = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.secondary),
                                                contentAlignment = Alignment.CenterEnd,
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(end = 20.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.common_edit),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                    )
                                                }
                                            }
                                        },
                                    ) {
                                        AssetRow(
                                            asset = item,
                                            openWoCount = openWoCounts[item.id] ?: 0,
                                            rentalStatus = rentalStatuses[item.id],
                                            onClick = { onAssetClick(item.id) },
                                            onLongClick = { onAssetLongPress(item.id) },
                                        )
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 72.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }

                    }
                }
                if (showOnboarding) {
                    val canLoadSample by onboardingViewModel.canLoadSampleData.collectAsStateWithLifecycle()
                    val sampleState by onboardingViewModel.sampleDataState.collectAsStateWithLifecycle()
                    FreWelcomeOverlay(
                        onDismiss = { onboardingViewModel.dismiss() },
                        onLoadSampleData = if (canLoadSample) onboardingViewModel::loadSampleData else null,
                        sampleState = sampleState,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            // Floating search + filter header — slides up and fades out on scroll down
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = -searchRowHeightPx * (1f - headerProgress)
                        alpha = headerProgress
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvagoSearchBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = stringResource(R.string.assets_search_placeholder),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    )

                    if (canPrintQrLabels && assets.isNotEmpty()) {
                        IconButton(onClick = { showQrLabels = true }) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = stringResource(R.string.asset_qr_labels_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Filter button — icon when inactive, pill with × when active
                    Box {
                        if (filterType != null || rentalFilter != null) {
                            val typeLabel = filterType?.let { ft ->
                                val labelResId = AssetTypes.labelResIdFor(ft)
                                if (labelResId != null) stringResource(labelResId)
                                else ft.replace("_", " ").replaceFirstChar { it.uppercase() }
                            }
                            val rentalLabel = rentalFilter?.let { stringResource(rentalStatusLabel(it)) }
                            val label = listOfNotNull(typeLabel, rentalLabel).joinToString(" · ")
                            Surface(
                                onClick = {
                                    viewModel.onFilterTypeChanged(null)
                                    viewModel.onRentalFilterChanged(null)
                                },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text(
                                    text = "$label  ×",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        } else {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter by asset type",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (filterType != null) R.string.assets_filter_reset
                                            else R.string.assets_filter_all,
                                        ),
                                    )
                                },
                                onClick = {
                                    viewModel.onFilterTypeChanged(null)
                                    showFilterMenu = false
                                },
                            )
                            presentTypes.forEach { typeKey ->
                                val labelResId = AssetTypes.labelResIdFor(typeKey)
                                val label = if (labelResId != null) stringResource(labelResId)
                                else typeKey.replace("_", " ")
                                    .replaceFirstChar { it.uppercase() }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (filterType == typeKey) "✓ $label" else label,
                                        )
                                    },
                                    onClick = {
                                        viewModel.onFilterTypeChanged(typeKey)
                                        showFilterMenu = false
                                    },
                                )
                            }
                            // Rental status filter (spec §11.1) — only when the account
                            // has rentable units in the offline rentals store.
                            if (rentalStatuses.isNotEmpty()) {
                                HorizontalDivider()
                                Text(
                                    text = stringResource(R.string.assets_rental_status_filter),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                                val present = rentalStatuses.values.toSet()
                                FleetStatus.entries.filter { it in present }.forEach { status ->
                                    val label = stringResource(rentalStatusLabel(status))
                                    DropdownMenuItem(
                                        text = { Text(if (rentalFilter == status) "✓ $label" else label) },
                                        onClick = {
                                            viewModel.onRentalFilterChanged(
                                                if (rentalFilter == status) null else status,
                                            )
                                            showFilterMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FreWelcomeOverlay(
    onDismiss: () -> Unit,
    onLoadSampleData: (() -> Unit)?,
    sampleState: SampleDataState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.fre_welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.fre_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.fre_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.fre_button),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            if (onLoadSampleData != null) {
                when (sampleState) {
                    SampleDataState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.fre_sample_loading), style = MaterialTheme.typography.bodySmall)
                    }
                    else -> TextButton(onClick = onLoadSampleData) { Text(stringResource(R.string.fre_sample_load)) }
                }
                (sampleState as? SampleDataState.Failed)?.let {
                    Text(
                        text = scopeErrorText(LocalResources.current, it.error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                text = stringResource(R.string.fre_fab_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AssetSectionHeader(typeKey: String) {
    val labelResId = AssetTypes.labelResIdFor(typeKey)
    val label = if (labelResId != null) stringResource(labelResId)
    else typeKey.replace("_", " ").replaceFirstChar { it.uppercase() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetRow(
    asset: Asset,
    openWoCount: Int = 0,
    rentalStatus: FleetStatus? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AssetAvatar(
                initial = asset.initial.ifBlank { "A" },
                assetType = asset.assetTypeCode,
                size = 40,
            )
            if (openWoCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (openWoCount > 99) "99+" else openWoCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitle = asset.makeModel.orEmpty()
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (rentalStatus != null) {
            RentalStatusPill(rentalStatus, modifier = Modifier.padding(end = 8.dp))
        }

        asset.locationName?.let { location ->
            Text(
                text = location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
}

@Composable
internal fun AssetAvatar(
    initial: String,
    assetType: String?,
    size: Int,
    modifier: Modifier = Modifier,
) {
    val knownType = AssetTypes.isKnownType(assetType)
    val bgColor = rememberParsedColor(AssetTypes.colorHexFor(assetType))
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        if (knownType) {
            Icon(
                painter = painterResource(AssetTypes.iconResFor(assetType)),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((size * 0.55f).dp),
            )
        } else {
            Text(
                text = initial,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun rememberParsedColorLocal(hex: String): Color {
    return rememberParsedColor(hex)
}
