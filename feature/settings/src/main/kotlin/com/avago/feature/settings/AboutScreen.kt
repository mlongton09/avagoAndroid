package com.avago.feature.settings

import android.content.Intent
import android.net.Uri
import com.avago.feature.settings.BuildConfig
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateToLicenses: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {

            item {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Avago",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            item { SectionDivider() }

            item {
                SectionLabel("Legal")
            }

            item {
                ListItem(
                    headlineContent = { Text("Privacy Policy") },
                    leadingContent = {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable(role = Role.Button) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://avago.app/privacy")),
                        )
                    },
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            item {
                ListItem(
                    headlineContent = { Text("Terms of Service") },
                    leadingContent = {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable(role = Role.Button) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://avago.app/terms")),
                        )
                    },
                )
            }

            item { SectionDivider() }

            item {
                SectionLabel("Acknowledgements")
            }

            item {
                ListItem(
                    headlineContent = { Text("Open Source Licenses") },
                    leadingContent = {
                        Icon(Icons.Default.Policy, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable(role = Role.Button, onClick = onNavigateToLicenses),
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp, end = 16.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        thickness = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    )
}
