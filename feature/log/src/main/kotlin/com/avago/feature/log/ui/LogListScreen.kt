package com.avago.feature.log.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.ui.CategoryBadge
import com.avago.core.ui.ScoutFAB
import com.avago.core.ui.ScoutViewModel
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
    viewModel: LogListViewModel = hiltViewModel(),
    scoutViewModel: ScoutViewModel = hiltViewModel(),
) {
    LaunchedEffect(assetId) {
        viewModel.setAssetId(assetId)
    }

    val logs by viewModel.logs.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val asset by viewModel.asset.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val entryCount by viewModel.entryCount.collectAsState()
    val lastServiceDate by viewModel.lastServiceDate.collectAsState()

    val grouped = logs.groupBy { log ->
        Calendar.getInstance().apply { timeInMillis = log.entryDate }.get(Calendar.YEAR)
    }.toSortedMap(reverseOrder())

    Scaffold(
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
                                text = "Log",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Text(text = "All Logs")
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoutFAB(onQuery = { query -> scoutViewModel.query(query) })
                FloatingActionButton(
                    onClick = onAddLog,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add log entry")
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
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
                        onSetCoverPhoto = { id -> viewModel.setCoverPhoto(id) },
                    )
                }

                if (logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No log entries yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        grouped.forEach { (year, yearLogs) ->
                            stickyHeader(key = "year_$year") {
                                YearHeader(year = year)
                            }
                            items(yearLogs, key = { it.entryId }) { log ->
                                LogListRow(
                                    log = log,
                                    onClick = { onLogClick(log.entryId) },
                                    meterType = asset?.meterType,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearHeader(year: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
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
    log: LogEntity,
    onClick: () -> Unit,
    meterType: String?,
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
    val isPending = log.serverVersion == 0L && log.seq == null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 50.dp)
            .padding(start = 14.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(categoryId = log.category)
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isPending) {
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${dateFormatter.format(Date(log.entryDate))} \u2002|\u2002 ${agoString(log.entryDate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val odo = log.odometerValue
            if (odo != null && odo > 0) {
                Text(
                    text = formatOdometer(odo, meterType),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
        }
    }
}

private fun agoString(epochMs: Long): String {
    val days = ((System.currentTimeMillis() - epochMs) / 86_400_000L).toInt()
    return when {
        days < 1   -> "Today"
        days == 1  -> "Yesterday"
        days < 30  -> "$days days"
        days < 60  -> "1 month"
        days < 365 -> "${days / 30} months"
        days < 730 -> "1 year"
        else       -> "${days / 365} years"
    }
}

private fun formatOdometer(value: Double, meterType: String?): String {
    val unit = when (meterType?.lowercase()) {
        "km", "kilometers" -> "km"
        "hours", "hrs"     -> "hrs"
        else               -> "mi"
    }
    val n = NumberFormat.getIntegerInstance().format(value.toLong())
    return "$n\u202F$unit"
}

