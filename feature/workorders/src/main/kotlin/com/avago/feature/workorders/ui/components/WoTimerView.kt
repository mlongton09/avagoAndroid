package com.avago.feature.workorders.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Displays a live-ticking elapsed timer for in-progress work orders.
 *
 * @param startedAtMs  Epoch-millisecond timestamp when the timer started
 *                     (maps to [WorkOrderEntity.timerStartedAt]).
 */
@Composable
fun WoTimerView(
    startedAtMs: Long,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by rememberSaveable { mutableLongStateOf((System.currentTimeMillis() - startedAtMs) / 1_000L) }

    LaunchedEffect(startedAtMs) {
        // Recalculate on every tick so the display stays accurate even after process restore.
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - startedAtMs) / 1_000L
            delay(1_000L)
        }
    }

    val hours = elapsedSeconds / 3_600
    val minutes = (elapsedSeconds % 3_600) / 60
    val seconds = elapsedSeconds % 60
    val formatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Timer running",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = formatted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
