package com.avago.feature.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.avago.feature.schedule.ui.ScheduleListScreen

/**
 * Top-level entry point kept for backward-compatibility with any host that already
 * references `ScheduleScreen()` directly.  New code should use [ScheduleListScreen]
 * or wire in [com.avago.feature.schedule.nav.ScheduleNavGraph] instead.
 */
@Composable
fun ScheduleScreen(
    onScheduleClick: (scheduleId: String) -> Unit = {},
    onAddSchedule: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ScheduleListScreen(
        assetId = null,
        onScheduleClick = onScheduleClick,
        onAddSchedule = onAddSchedule,
        modifier = modifier,
    )
}
