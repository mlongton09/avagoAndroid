package com.avago.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AvagoToastHost(
    toastManager: AvagoToast,
    content: @Composable () -> Unit
) {
    var current by remember { mutableStateOf<ToastEvent?>(null) }

    LaunchedEffect(toastManager) {
        toastManager.events.collect { event ->
            current = event
            delay(4000L)
            current = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
        ) {
            current?.let { toast ->
                ToastBanner(toast)
            }
        }
    }
}

@Composable
private fun ToastBanner(toast: ToastEvent) {
    val (bgColor, icon) = when (toast.style) {
        ToastStyle.Success -> MaterialTheme.colorScheme.primaryContainer to Icons.Default.CheckCircle
        ToastStyle.Warning -> Color(0xFFFFF3CD) to Icons.Default.Warning
        ToastStyle.Error   -> MaterialTheme.colorScheme.errorContainer to Icons.Default.Error
        ToastStyle.Info    -> MaterialTheme.colorScheme.surfaceVariant to Icons.Default.Info
    }
    val contentColor = when (toast.style) {
        ToastStyle.Error -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(toast.message, style = MaterialTheme.typography.bodyMedium, color = contentColor, modifier = Modifier.weight(1f))
    }
}
