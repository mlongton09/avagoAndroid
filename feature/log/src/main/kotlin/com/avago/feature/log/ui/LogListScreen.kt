package com.avago.feature.log.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.avago.core.ai.ui.ScoutPaletteSheet
import com.avago.core.data.domain.Activity
import com.avago.core.ui.CategoryBadge
import com.avago.core.ui.categoryBadgeColor
import com.avago.core.ui.categoryIconName
import androidx.compose.foundation.shape.CircleShape
import com.avago.feature.log.R
import com.avago.feature.log.nav.DefectWorkOrderRequest
import com.avago.feature.log.viewmodel.LogListViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogListScreen(
    assetId: String? = null,
    onLogClick: (entryId: String) -> Unit,
    onAddLog: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Opens WO create prefilled from an open defect (open-defects panel); null hides the action. */
    onCreateWorkOrderFromDefect: ((DefectWorkOrderRequest) -> Unit)? = null,
    viewModel: LogListViewModel = hiltViewModel(),
) {
    LaunchedEffect(assetId) {
        viewModel.setAssetId(assetId)
    }

    var showScoutSheet by remember { mutableStateOf(false) }
    var showAnalytics by rememberSaveable { mutableStateOf(false) }

    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val asset by viewModel.asset.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val entryCount by viewModel.entryCount.collectAsStateWithLifecycle()
    val lastServiceDate by viewModel.lastServiceDate.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val categoryCounts by viewModel.categoryCounts.collectAsStateWithLifecycle()
    val hasAnyLogs by viewModel.hasAnyLogs.collectAsStateWithLifecycle()
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()
    val meter by viewModel.meter.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }

    val grouped = logs.groupBy { log ->
        Calendar.getInstance().apply { timeInMillis = log.occurredAt }.get(Calendar.YEAR)
    }.toSortedMap(reverseOrder())

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (assetId != null) {
                        // iOS-parity nav title: [22dp app logo · asset name · "Log" suffix]
                        // (AssetDetailViewController.setupCustomHeader navTitleStack)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFFE53935)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(
                                        com.avago.core.design.R.drawable.ic_app_logo,
                                    ),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Text(
                                text = asset?.name ?: "",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(weight = 1f, fill = false),
                            )
                            Text(
                                text = stringResource(R.string.log_list_title_suffix),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Text(text = stringResource(R.string.log_list_title_all))
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.log_action_back),
                            )
                        }
                    }
                },
                actions = {
                    // Web AssetAnalyticsPanel toggle — only once the asset has ≥ 2 entries.
                    if (analytics != null) {
                        IconButton(onClick = { showAnalytics = true }) {
                            Icon(
                                imageVector = Icons.Default.Insights,
                                contentDescription = stringResource(R.string.log_analytics_toggle),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(
                    onClick = { showScoutSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.log_action_scout))
                }
                FloatingActionButton(
                    onClick = onAddLog,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.log_action_add_entry))
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        if (showScoutSheet) {
            ScoutPaletteSheet(
                visible = true,
                onDismiss = { showScoutSheet = false },
                onNavigate = { _, _ -> showScoutSheet = false },
            )
        }

        val summary = analytics
        if (showAnalytics && summary != null) {
            LogAnalyticsSheet(
                summary = summary,
                currencyCode = currencyCode,
                onCategoryClick = { cat ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setFilter(cat.takeUnless { it == "other" })
                    showAnalytics = false
                },
                onDismiss = { showAnalytics = false },
            )
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column {
                // Asset header — only when launched from a specific asset and the asset
                // has finished loading. Mirrors iOS AssetDetailHeaderView.
                if (assetId != null && asset != null) {
                    AssetLogHeader(
                        asset = asset!!,
                        photos = photos,
                        entryCount = entryCount,
                        lastServiceDate = lastServiceDate,
                        onAddPhotoUri = { uri -> viewModel.addAssetPhoto(uri) },
                        onDeletePhoto = { id -> viewModel.deleteAssetPhoto(id) },
                    )
                }

                // Web AssetLogTab parity: search title + notes, category select with counts.
                if (hasAnyLogs) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.log_list_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.log_list_search_clear),
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    if (categoryCounts.size > 1) {
                        val total = categoryCounts.sumOf { it.second }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            item(key = "__all") {
                                FilterChip(
                                    selected = categoryFilter == null,
                                    onClick = { viewModel.setFilter(null) },
                                    label = { Text(stringResource(R.string.log_list_filter_all_count, total)) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                            items(categoryCounts, key = { it.first }) { (cat, count) ->
                                FilterChip(
                                    selected = categoryFilter == cat,
                                    onClick = { viewModel.setFilter(if (categoryFilter == cat) null else cat) },
                                    label = {
                                        Text(stringResource(R.string.log_list_filter_count, categoryDisplayLabel(cat), count))
                                    },
                                    leadingIcon = {
                                        Box(
                                            Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(categoryBadgeColor(categoryIconName(cat))),
                                        )
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                }

                when {
                    !hasAnyLogs -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.log_list_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    logs.isEmpty() -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.log_list_empty_filter_title),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.log_list_empty_filter_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = viewModel::clearFilters,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.log_list_clear_filters)) }
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 160.dp),
                    ) {
                        // Web AssetLogTab: open-defect register above the history (hidden when clean).
                        if (assetId != null) {
                            item(key = "open_defects") {
                                AssetOpenDefectsPanel(
                                    assetId = assetId,
                                    onOpenLog = onLogClick,
                                    onCreateWorkOrder = onCreateWorkOrderFromDefect,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        grouped.forEach { (year, yearLogs) ->
                            stickyHeader(key = "year_$year") {
                                YearHeader(year = year)
                            }
                            items(yearLogs, key = { it.id }) { log ->
                                LogListRow(
                                    log = log,
                                    onClick = { onLogClick(log.id) },
                                    meterUnit = meter?.unit,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "oil_change" → "Oil Change" (same presentation as the detail / form category pill). */
internal fun categoryDisplayLabel(category: String): String =
    category.replace('_', ' ').split(' ').filter { it.isNotEmpty() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }

@Composable
private fun YearHeader(year: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { heading() },
    ) {
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogListRow(
    log: Activity,
    onClick: () -> Unit,
    meterUnit: String?,
    modifier: Modifier = Modifier,
) {
    // iOS LogItemTableViewCell parity:
    // - 50pt min height
    // - 40dp category badge, 10dp gap to title
    // - Title font = AppTheme.bodyFont() → bodyLarge (17sp regular)
    // - Right meta font = AppTheme.smallFont() → bodyMedium (13sp regular)
    // - Right top: "<MMM d> | <ago>"
    // - Right bottom: "<odometer> <unit>" when present (alpha 0.72)
    // - No cost field on iOS
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 50.dp)
            .padding(start = 14.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(categoryId = log.categoryLabel)
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${dateFormatter.format(Date(log.occurredAt))} \u2002|\u2002 ${agoString(log.occurredAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val odo = log.meterValue?.toDouble()
            if (odo != null && odo > 0) {
                Text(
                    text = formatOdometer(odo, meterUnit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun agoString(epochMs: Long): String {
    val days = ((System.currentTimeMillis() - epochMs) / 86_400_000L).toInt()
    return when {
        days < 1 -> stringResource(R.string.log_ago_today)
        days == 1 -> stringResource(R.string.log_ago_yesterday)
        days < 30 -> pluralStringResource(R.plurals.log_ago_days, days, days)
        days < 365 -> (days / 30).coerceAtLeast(1).let { pluralStringResource(R.plurals.log_ago_months, it, it) }
        else -> (days / 365).let { pluralStringResource(R.plurals.log_ago_years, it, it) }
    }
}

private fun formatOdometer(value: Double, meterUnit: String?): String {
    val unit = when (meterUnit?.lowercase()) {
        "km", "kilometers" -> "km"
        "h", "hours", "hrs" -> "hrs"
        else -> "mi"
    }
    val n = NumberFormat.getIntegerInstance().format(value.toLong())
    return "$n\u202F$unit"
}
