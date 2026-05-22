package com.avago.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avago.core.push.NotificationPermissionHelper
import com.avago.feature.settings.BuildConfig
import com.avago.feature.settings.R

// ---------------------------------------------------------------------------
// Public entry point
// ---------------------------------------------------------------------------

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToMembers: () -> Unit = {},
    onNavigateToLicenses: () -> Unit = {},
    onNavigateToInvite: () -> Unit = {},
    onNavigateToDeveloper: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
) {
    val context = LocalContext.current
    val theme by viewModel.theme.collectAsState()
    val distanceUnit by viewModel.distanceUnit.collectAsState()
    val activeAccountId by viewModel.activeAccountId.collectAsState()

    // Notification permission — re-check on every recomposition so state is fresh
    // after the user returns from system settings.
    val notificationsGranted = remember(context) {
        mutableStateOf(NotificationPermissionHelper.isGranted(context))
    }
    // Refresh when screen is first composed (covers returning from system settings).
    LaunchedEffect(Unit) {
        notificationsGranted.value = NotificationPermissionHelper.isGranted(context)
    }

    // Dialog state
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (showThemeDialog) {
        ThemePickerDialog(
            current = theme,
            onSelect = { selected ->
                viewModel.setTheme(selected)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showSignOutDialog) {
        ConfirmDialog(
            title = stringResource(R.string.settings_sign_out),
            body = stringResource(R.string.settings_sign_out_confirm_body),
            confirmLabel = stringResource(R.string.settings_sign_out),
            confirmColors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            onConfirm = {
                viewModel.signOut()
                showSignOutDialog = false
            },
            onDismiss = { showSignOutDialog = false },
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_account),
            body = stringResource(R.string.settings_delete_account_body),
            confirmLabel = stringResource(R.string.delete),
            confirmColors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            onConfirm = {
                viewModel.deleteAccount()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    // ── Body ──────────────────────────────────────────────────────────────────

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {

        // ── Appearance ───────────────────────────────────────────────────────
        item {
            SectionHeader(text = stringResource(R.string.settings_appearance))
        }
        item {
            ThemeRow(
                current = theme,
                onClick = { showThemeDialog = true },
            )
        }
        item {
            LanguageRow(context = context)
        }
        item { SectionDivider() }

        // ── Units ─────────────────────────────────────────────────────────────
        item {
            SectionHeader(text = stringResource(R.string.settings_units))
        }
        item {
            DistanceUnitRow(
                selected = distanceUnit,
                onSelect = viewModel::setDistanceUnit,
            )
        }
        item { SectionDivider() }

        // ── Notifications ────────────────────────────────────────────────────
        item {
            SectionHeader(text = stringResource(R.string.settings_notifications))
        }
        item {
            NotificationsRow(
                granted = notificationsGranted.value,
                context = context,
            )
        }
        item { SectionDivider() }

        // ── Account ───────────────────────────────────────────────────────────
        item {
            SectionHeader(text = stringResource(R.string.settings_account))
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_account_id)) },
                supportingContent = {
                    Text(
                        text = activeAccountId ?: stringResource(R.string.settings_no_account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            NavigationRow(
                label = stringResource(R.string.settings_members),
                leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) },
                onClick = onNavigateToMembers,
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            NavigationRow(
                label = stringResource(R.string.settings_invite_team_member),
                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                onClick = onNavigateToInvite,
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.settings_sign_out),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable(role = Role.Button) { showSignOutDialog = true },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.settings_delete_account),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable(role = Role.Button) { showDeleteDialog = true },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
        item { SectionDivider() }

        // ── App ───────────────────────────────────────────────────────────────
        item {
            SectionHeader(text = stringResource(R.string.settings_app))
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                trailingContent = {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            NavigationRow(
                label = stringResource(R.string.settings_licenses),
                leadingIcon = { Icon(Icons.Default.Policy, contentDescription = null) },
                onClick = onNavigateToLicenses,
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            val privacyUrl = stringResource(R.string.settings_privacy_policy_url)
            NavigationRow(
                label = stringResource(R.string.settings_privacy_policy),
                leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)),
                    )
                },
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            NavigationRow(
                label = "About",
                leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                onClick = onNavigateToAbout,
            )
        }
        // ── Developer ─────────────────────────────────────────────────────────
        item { SectionDivider() }
        item {
            SectionHeader(text = "Developer")
        }
        item {
            NavigationRow(
                label = "Developer Options",
                leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                onClick = onNavigateToDeveloper,
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Section building blocks
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(text: String) {
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

@Composable
private fun NavigationRow(
    label: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = leadingIcon,
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

// ---------------------------------------------------------------------------
// Appearance rows
// ---------------------------------------------------------------------------

@Composable
private fun ThemeRow(
    current: String,
    onClick: () -> Unit,
) {
    val label = when (current) {
        "dark"  -> "Dark"
        "light" -> "Light"
        else    -> "System"
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_theme)) },
        trailingContent = {
            FilterChip(
                selected = false,
                onClick = onClick,
                label = { Text(label) },
            )
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun LanguageRow(context: Context) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_language)) },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(role = Role.Button) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+: use per-app language system UI
                val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            } else {
                // Below Android 13: per-app language is not supported via system UI.
                // In-app picker would go here; for now open app info as a fallback.
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Units row
// ---------------------------------------------------------------------------

@Composable
private fun DistanceUnitRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_distance_unit)) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selected == "mi",
                    onClick = { onSelect("mi") },
                    label = { Text("mi") },
                )
                FilterChip(
                    selected = selected == "km",
                    onClick = { onSelect("km") },
                    label = { Text("km") },
                )
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Notifications row
// ---------------------------------------------------------------------------

@Composable
private fun NotificationsRow(
    granted: Boolean,
    context: Context,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_notifications)) },
        supportingContent = if (!granted) {
            {
                Text(
                    text = stringResource(R.string.settings_notifications_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            null
        },
        leadingContent = {
            Icon(
                if (granted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            if (granted) {
                Switch(
                    checked = true,
                    onCheckedChange = null, // permission is system-controlled
                    enabled = false,
                )
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = if (!granted) {
            Modifier.clickable(role = Role.Button) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        } else {
            Modifier
        },
    )
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
private fun ThemePickerDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        "system" to "System default",
        "light"  to "Light",
        "dark"   to "Dark",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { onSelect(value) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = current == value,
                            onClick = { onSelect(value) },
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmColors: androidx.compose.material3.ButtonColors = ButtonDefaults.textButtonColors(),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = confirmColors) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
