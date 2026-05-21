package com.avago.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Open-source licenses screen — placeholder.
 * A production implementation would use the OSS Licenses Gradle plugin
 * (com.google.android.gms:oss-licenses-plugin) to auto-generate this list.
 */
@Composable
fun LicensesScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        item {
            Text(
                text = "Open Source Licenses",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        item {
            Text(
                text = "This application uses open-source software. A full list of licenses will appear here once the OSS Licenses plugin is integrated.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
