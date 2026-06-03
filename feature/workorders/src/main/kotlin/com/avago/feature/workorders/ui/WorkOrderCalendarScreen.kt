package com.avago.feature.workorders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.ui.EmptyState
import com.avago.feature.workorders.R
import com.avago.feature.workorders.ui.components.WoCard
import com.avago.feature.workorders.viewmodel.WorkOrderCalendarViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderCalendarScreen(
    onWoClick: (woId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderCalendarViewModel = hiltViewModel(),
) {
    val wosByDate by viewModel.wosByDate.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val displayMonth by viewModel.displayMonth.collectAsStateWithLifecycle()
    val selectedDayWos by viewModel.selectedDayWos.collectAsStateWithLifecycle()
    val assetLabels by viewModel.assetLabels.collectAsStateWithLifecycle()
    val canSeeAllScope by viewModel.canSeeAllScope.collectAsStateWithLifecycle()
    val showAll by viewModel.showAll.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = { Text(stringResource(R.string.wo_calendar_title)) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            // Mine / All scope toggle (dispatcher tier only)
            if (canSeeAllScope) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !showAll,
                        onClick = { if (showAll) viewModel.toggleScope() },
                        label = { Text("Mine") },
                    )
                    FilterChip(
                        selected = showAll,
                        onClick = { if (!showAll) viewModel.toggleScope() },
                        label = { Text("All") },
                    )
                }
            }

            // Month navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.navigateMonth(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.wo_calendar_prev_month))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = displayMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    if (displayMonth.withDayOfMonth(1) != LocalDate.now().withDayOfMonth(1)) {
                        TextButton(
                            onClick = { viewModel.navigateToToday() },
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("Today", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                IconButton(onClick = { viewModel.navigateMonth(1) }) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.wo_calendar_next_month))
                }
            }

            // Day-of-week header
            val dayNames = DayOfWeek.values().map {
                it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                dayNames.forEach { name ->
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Month grid
            val yearMonth = YearMonth.of(displayMonth.year, displayMonth.month)
            val firstDayOfMonth = yearMonth.atDay(1)
            val dayOfWeekOffset = (firstDayOfMonth.dayOfWeek.value - 1) // Mon=0
            val totalCells = dayOfWeekOffset + yearMonth.lengthOfMonth()
            val cellCount = if (totalCells % 7 == 0) totalCells else totalCells + (7 - totalCells % 7)

            val cellDates: List<LocalDate?> = (0 until cellCount).map { idx ->
                val dayNum = idx - dayOfWeekOffset + 1
                if (dayNum in 1..yearMonth.lengthOfMonth()) {
                    yearMonth.atDay(dayNum)
                } else null
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            ) {
                items(cellDates) { date ->
                    if (date == null) {
                        Box(modifier = Modifier.aspectRatio(1f))
                    } else {
                        val wosOnDay = wosByDate[date] ?: emptyList()
                        val hasWos = wosOnDay.isNotEmpty()
                        val isSelected = date == selectedDate
                        val isToday = date == LocalDate.now()

                        // Dot color = worst status on this day (mirrors iOS pill coloring)
                        val dotColor = wosStatusDotColor(wosOnDay, date)

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { viewModel.selectDate(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                                if (hasWos) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else dotColor
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Selected day WOs
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (selectedDayWos.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.wo_calendar_no_wos),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(selectedDayWos, key = { it.woId }) { wo ->
                        WoCard(wo = wo, onClick = { onWoClick(wo.woId) }, assetLabel = assetLabels[wo.assetId])
                    }
                }
            }
        }
    }
}

/** Picks the dot color based on the worst-case status among WOs on a day. */
@Composable
private fun wosStatusDotColor(wos: List<WorkOrderEntity>, date: LocalDate): Color {
    val nowMs = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val dateMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val isOverdue = wos.any { wo ->
        val due = wo.dueDate
        due != null && due < nowMs && wo.status !in listOf("complete", "cancelled")
    }
    return when {
        isOverdue -> MaterialTheme.colorScheme.error
        wos.any { it.status == "in_progress" } -> MaterialTheme.colorScheme.secondary
        wos.any { it.status == "complete" } && wos.all { it.status == "complete" } ->
            MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
}
