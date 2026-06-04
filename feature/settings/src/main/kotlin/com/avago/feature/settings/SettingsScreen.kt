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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Warning
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
import com.avago.feature.settings.R

private const val SHOW_DELETE_ACCOUNT = false

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
    onNavigateToTechProfile: () -> Unit = {},
    onNavigateToSyncConflicts: () -> Unit = {},
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" }.getOrDefault("?")
    }
    val theme by viewModel.theme.collectAsState()
    val distanceUnit by viewModel.distanceUnit.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val fuelVolumeUnit by viewModel.fuelVolumeUnit.collectAsState()
    val temperatureUnit by viewModel.temperatureUnit.collectAsState()
    val disableQuotes by viewModel.disableQuotes.collectAsState()
    val enableHumanInLoop by viewModel.enableHumanInLoop.collectAsState()
    val activeAccountId by viewModel.activeAccountId.collectAsState()
    val effectiveRole by viewModel.effectiveRole.collectAsState()

    // Dialog state
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (showCurrencyDialog) {
        CurrencyPickerDialog(
            current = currency,
            onSelect = { selected ->
                viewModel.setCurrency(selected)
                showCurrencyDialog = false
            },
            onDismiss = { showCurrencyDialog = false },
        )
    }

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

    if (SHOW_DELETE_ACCOUNT && showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_delete_account)) },
            text = {
                Text("Soft delete revokes this device/account access. Hard delete removes the account from the server immediately.")
            },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteAccount(hard = true)
                        showDeleteDialog = false
                    },
                ) { Text("Hard Delete") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount(hard = false)
                    showDeleteDialog = false
                }) { Text("Soft Delete") }
            },
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
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            CurrencyRow(
                current = currency,
                onClick = { showCurrencyDialog = true },
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            FuelVolumeUnitRow(
                selected = fuelVolumeUnit,
                onSelect = viewModel::setFuelVolumeUnit,
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            TemperatureUnitRow(
                selected = temperatureUnit,
                onSelect = viewModel::setTemperatureUnit,
            )
        }
        item { SectionDivider() }

        // ── AI ────────────────────────────────────────────────────────────────
        item {
            SectionHeader(text = stringResource(R.string.settings_ai))
        }
        item {
            SwitchRow(
                label = stringResource(R.string.settings_human_in_loop),
                description = stringResource(R.string.settings_human_in_loop_desc),
                checked = enableHumanInLoop,
                onCheckedChange = viewModel::setEnableHumanInLoop,
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            SwitchRow(
                label = stringResource(R.string.settings_disable_quotes),
                checked = disableQuotes,
                onCheckedChange = viewModel::setDisableQuotes,
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
        effectiveRole?.takeIf { it.isNotBlank() }?.let { role ->
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.account_role_label)) },
                    supportingContent = {
                        Text(
                            text = roleDisplayName(role),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        item {
            NavigationRow(
                label = stringResource(R.string.settings_my_tech_profile),
                leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                onClick = onNavigateToTechProfile,
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
            NavigationRow(
                label = "Sync Conflicts",
                leadingIcon = { Icon(Icons.Default.Error, contentDescription = null) },
                onClick = onNavigateToSyncConflicts,
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
                        Icons.AutoMirrored.Filled.Logout,
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
        if (SHOW_DELETE_ACCOUNT) {
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
                            Icons.Default.Warning,
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
        }

        // ── App ───────────────────────────────────────────────────────────────
        item {
            SectionHeader(text = stringResource(R.string.settings_app))
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                trailingContent = {
                    Text(
                        text = versionName,
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
            val termsUrl = stringResource(R.string.settings_terms_of_service_url)
            NavigationRow(
                label = stringResource(R.string.settings_terms_of_service),
                leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(termsUrl)),
                    )
                },
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            NavigationRow(
                label = stringResource(R.string.about_title),
                leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                onClick = onNavigateToAbout,
            )
        }
        // ── Developer ─────────────────────────────────────────────────────────
        item { SectionDivider() }
        if (BuildConfig.DEBUG) {
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
        else    -> stringResource(R.string.settings_theme_system)
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
private fun CurrencyRow(
    current: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_currency)) },
        trailingContent = {
            FilterChip(
                selected = false,
                onClick = onClick,
                label = { Text(current) },
            )
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun CurrencyPickerDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val commonCurrencies = listOf(
        "USD" to "US Dollar", "EUR" to "Euro", "GBP" to "British Pound",
        "CAD" to "Canadian Dollar", "AUD" to "Australian Dollar",
        "JPY" to "Japanese Yen", "CHF" to "Swiss Franc", "CNY" to "Chinese Yuan",
        "INR" to "Indian Rupee", "MXN" to "Mexican Peso", "BRL" to "Brazilian Real",
        "KRW" to "Korean Won", "SGD" to "Singapore Dollar", "NZD" to "New Zealand Dollar",
        "NOK" to "Norwegian Krone", "SEK" to "Swedish Krona", "DKK" to "Danish Krone",
        "PLN" to "Polish Zloty", "CZK" to "Czech Koruna", "HUF" to "Hungarian Forint",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_currency)) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                items(commonCurrencies) { currency ->
                    val (code, name) = currency
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { onSelect(code) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = current == code,
                            onClick = { onSelect(code) },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(text = code, style = MaterialTheme.typography.bodyMedium)
                            Text(text = name, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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

@Composable
private fun FuelVolumeUnitRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_fuel_volume_unit)) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selected == "gallon",
                    onClick = { onSelect("gallon") },
                    label = { Text("gal") },
                )
                FilterChip(
                    selected = selected == "liter",
                    onClick = { onSelect("liter") },
                    label = { Text("L") },
                )
            }
        },
    )
}

@Composable
private fun TemperatureUnitRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_temperature_unit)) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selected == "F",
                    onClick = { onSelect("F") },
                    label = { Text("°F") },
                )
                FilterChip(
                    selected = selected == "C",
                    onClick = { onSelect("C") },
                    label = { Text("°C") },
                )
            }
        },
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = if (description != null) {
            { Text(description, style = MaterialTheme.typography.bodySmall) }
        } else null,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) },
    )
}

// ---------------------------------------------------------------------------
@Composable
private fun roleDisplayName(role: String): String = when (role.lowercase()) {
    "root" -> stringResource(R.string.role_root)
    "admin" -> stringResource(R.string.role_admin)
    "manager" -> stringResource(R.string.role_manager)
    "dispatcher" -> stringResource(R.string.role_dispatcher)
    "technician" -> stringResource(R.string.role_technician)
    "operator" -> stringResource(R.string.role_operator)
    "reader" -> stringResource(R.string.role_reader)
    else -> role.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

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
