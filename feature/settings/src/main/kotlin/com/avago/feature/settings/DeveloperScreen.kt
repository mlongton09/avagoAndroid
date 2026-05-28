package com.avago.feature.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.sync.SyncEngine
import com.avago.feature.settings.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    val activeAccountId: StateFlow<String?> = identityManager.activeAccountId
    val activeUserId: StateFlow<String?> = identityManager.activeUserId

    val devRoleOverride: StateFlow<String?> = identityManager.devRoleOverride

    val canOverride: StateFlow<Boolean> = MutableStateFlow(identityManager.hasRootOnCurrentAccount())
        .also { flow ->
            viewModelScope.launch {
                identityManager.activeAccountId.collect {
                    flow.value = identityManager.hasRootOnCurrentAccount()
                }
            }
        }

    val navigateToSignIn = MutableStateFlow(false)

    fun setRoleOverride(role: String?) {
        identityManager.setDevRoleOverride(role)
    }

    fun wipeAllData() {
        viewModelScope.launch {
            identityManager.wipeAllForTesting()
            navigateToSignIn.value = true
        }
    }

    fun clearSyncCache() {
        Timber.w("[DeveloperVM] clearSyncCache requested — stub (no public API yet)")
    }

    fun forceFullSync() {
        viewModelScope.launch {
            try {
                syncEngine.sync()
                Timber.d("[DeveloperVM] Full sync triggered")
            } catch (e: Exception) {
                Timber.e(e, "[DeveloperVM] forceFullSync failed")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeveloperViewModel = hiltViewModel(),
) {
    val accountId by viewModel.activeAccountId.collectAsStateWithLifecycle()
    val userId by viewModel.activeUserId.collectAsStateWithLifecycle()
    val devRoleOverride by viewModel.devRoleOverride.collectAsStateWithLifecycle()
    val canOverride by viewModel.canOverride.collectAsStateWithLifecycle()
    var showWipeConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" }.getOrDefault("?")
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Developer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Environment ──────────────────────────────────────────────────
            item { DevSectionHeader("Environment") }
            item { InfoRow(label = "Base URL", value = BuildConfig.BASE_URL) }
            item { InfoRow(label = "Build Type", value = BuildConfig.BUILD_TYPE) }
            item {
                InfoRow(
                    label = "Version",
                    value = versionName,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // ── Auth ─────────────────────────────────────────────────────────
            item { DevSectionHeader("Auth") }
            item { InfoRow(label = "Account ID", value = accountId ?: "none") }
            item { InfoRow(label = "User ID", value = userId ?: "none") }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // ── Role Override ─────────────────────────────────────────────────
            if (canOverride) {
                item { DevSectionHeader("Role Override") }
                item {
                    val roleOptions = listOf("None (actual)", "admin", "technician", "viewer")
                    val selectedIndex = when (devRoleOverride) {
                        "admin" -> 1
                        "technician" -> 2
                        "viewer" -> 3
                        else -> 0
                    }
                    ListItem(
                        headlineContent = {
                            SingleChoiceSegmentedButtonRow {
                                roleOptions.forEachIndexed { index, label ->
                                    SegmentedButton(
                                        selected = selectedIndex == index,
                                        onClick = {
                                            viewModel.setRoleOverride(
                                                when (index) {
                                                    1 -> "admin"
                                                    2 -> "technician"
                                                    3 -> "viewer"
                                                    else -> null
                                                }
                                            )
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = roleOptions.size),
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        },
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            }

            // ── Feature Flags ─────────────────────────────────────────────────
            item { DevSectionHeader("Feature Flags") }
            item {
                FeatureToggle(
                    label = "Unified WO View",
                    prefKey = "ff_unified_wo",
                    defaultValue = true,
                    context = context,
                )
            }
            item {
                FeatureToggle(
                    label = "AI Scout",
                    prefKey = "ff_scout",
                    defaultValue = true,
                    context = context,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // ── Actions ───────────────────────────────────────────────────────
            item { DevSectionHeader("Actions") }
            item {
                ActionRow(label = "Clear Sync Cache") {
                    viewModel.clearSyncCache()
                    scope.launch {
                        snackbarHostState.showSnackbar("Sync cache cleared")
                    }
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
            item {
                ActionRow(label = "Force Full Sync") {
                    viewModel.forceFullSync()
                    scope.launch {
                        snackbarHostState.showSnackbar("Full sync triggered")
                    }
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
            item {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Wipe All Data",
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { showWipeConfirm = true },
                )
            }
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Wipe All Data?") },
            text = { Text("This will delete all tokens, accounts, and local databases. The app will return to sign-in. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirm = false
                        viewModel.wipeAllData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Wipe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

@Composable
private fun DevSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp, end = 16.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun FeatureToggle(
    label: String,
    prefKey: String,
    defaultValue: Boolean,
    context: Context,
) {
    val prefs = remember {
        context.getSharedPreferences("avago_feature_flags", Context.MODE_PRIVATE)
    }
    var checked by remember { mutableStateOf(prefs.getBoolean(prefKey, defaultValue)) }

    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { newValue ->
                    checked = newValue
                    prefs.edit().putBoolean(prefKey, newValue).apply()
                },
            )
        },
    )
}

@Composable
private fun ActionRow(
    label: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    )
}
