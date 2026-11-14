package com.avago.feature.rentals.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.ui.components.StatusTone

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Rental status pills – light", showBackground = true, widthDp = 380)
@Preview(name = "Rental status pills – dark", showBackground = true, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Rental status pills – 200% font", showBackground = true, widthDp = 380, fontScale = 2f)
@Composable
private fun RentalStatusPreview() {
    AvagoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(AvagoSpacing.lg), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.md)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    com.avago.core.data.rental.FleetStatus.entries.forEach { FleetStatusChip(it) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    listOf("quoted", "reserved", "on_rent", "off_rent_requested", "returned", "closed").forEach { OrderStatusPill(it) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    InitialsAvatar("ACME Builders", StatusTone.Info)
                    InitialsAvatar("Jane Doe", StatusTone.Accent)
                }
            }
        }
    }
}
