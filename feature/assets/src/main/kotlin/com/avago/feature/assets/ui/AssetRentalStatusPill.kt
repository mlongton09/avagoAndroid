package com.avago.feature.assets.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avago.core.data.rental.FleetStatus
import com.avago.feature.assets.R

/** Localised label for a rental fleet status (spec §11.1). */
@StringRes
internal fun rentalStatusLabel(status: FleetStatus): Int = when (status) {
    FleetStatus.AVAILABLE -> R.string.assets_rental_status_available
    FleetStatus.RESERVED -> R.string.assets_rental_status_reserved
    FleetStatus.ON_RENT -> R.string.assets_rental_status_on_rent
    FleetStatus.OVERDUE -> R.string.assets_rental_status_overdue
    FleetStatus.IN_MAINTENANCE -> R.string.assets_rental_status_in_maintenance
    FleetStatus.NOT_READY -> R.string.assets_rental_status_not_ready
}

/**
 * Compact rental status chip for the asset list. Always carries the text label (status is
 * never conveyed by colour alone); colours are tuned for contrast in light and dark theme.
 */
@Composable
internal fun RentalStatusPill(status: FleetStatus, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val (bg, fg) = when (status) {
        FleetStatus.AVAILABLE -> if (dark) Color(0xFF14532D) to Color(0xFFBBF7D0) else Color(0xFFDCFCE7) to Color(0xFF166534)
        FleetStatus.RESERVED -> if (dark) Color(0xFF1E3A8A) to Color(0xFFBFDBFE) else Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        FleetStatus.ON_RENT -> if (dark) Color(0xFF3B0764) to Color(0xFFE9D5FF) else Color(0xFFF3E8FF) to Color(0xFF6B21A8)
        FleetStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        FleetStatus.IN_MAINTENANCE -> if (dark) Color(0xFF78350F) to Color(0xFFFDE68A) else Color(0xFFFEF3C7) to Color(0xFF92400E)
        FleetStatus.NOT_READY -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = bg, modifier = modifier) {
        Text(
            text = stringResource(rentalStatusLabel(status)),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
