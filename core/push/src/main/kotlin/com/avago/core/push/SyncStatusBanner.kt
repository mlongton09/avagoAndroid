package com.avago.core.push

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avago.core.sync.SyncState

/**
 * A floating pill that appears at the top of a screen while a sync is in progress.
 *
 * Place it in a `Box` or `Scaffold` overlay so it floats above content:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     // ...screen content...
 *     SyncStatusBanner(
 *         syncState = syncState,
 *         modifier = Modifier.align(Alignment.TopCenter),
 *     )
 * }
 * ```
 */
@Composable
fun SyncStatusBanner(
    syncState: SyncState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = syncState is SyncState.Pushing || syncState is SyncState.Pulling,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = if (syncState is SyncState.Pushing) "Syncing changes…" else "Updating…",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
