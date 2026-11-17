package com.avago.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.AccountManifest
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.model.SampleWorkspaceProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

sealed interface SampleWorkspacesList {
    data object Loading : SampleWorkspacesList
    /** GET returned 401/403 — caller is not a system admin, so the section is hidden. */
    data object Hidden : SampleWorkspacesList
    data class Failed(val message: String) : SampleWorkspacesList
    data class Loaded(val profiles: List<SampleWorkspaceProfile>) : SampleWorkspacesList
}

sealed interface SampleWorkspaceError {
    data object RequiresAdmin : SampleWorkspaceError
    data object AccountMissing : SampleWorkspaceError
    data class Server(val message: String) : SampleWorkspaceError
}

data class SampleWorkspacesUiState(
    val list: SampleWorkspacesList = SampleWorkspacesList.Loading,
    val switching: SampleWorkspaceProfile? = null,
    val error: SampleWorkspaceError? = null,
    /** Label of the profile just switched into; consumed by the screen as a snackbar. */
    val switchedLabel: String? = null,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * Developer "Sample workspaces" switcher (SystemAdmin only). The server creates or reopens a
 * demo account owned by the caller; the app then refreshes its account list and switches into
 * that account through [IdentityManager.switchAccount] — the same path as the account switcher.
 */
@HiltViewModel
class SampleWorkspacesViewModel @Inject constructor(
    private val client: AvagoServiceClient,
    private val identity: IdentityManager,
    private val manifest: AccountManifest,
) : ViewModel() {

    private val _state = MutableStateFlow(SampleWorkspacesUiState())
    val state: StateFlow<SampleWorkspacesUiState> = _state.asStateFlow()

    val activeAccountId: StateFlow<String?> = identity.activeAccountId

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val list = when (val result = client.getSampleWorkspaces()) {
                is NetworkResult.Success -> SampleWorkspacesList.Loaded(result.data.profiles)
                is NetworkResult.Unauthorized -> SampleWorkspacesList.Hidden
                is NetworkResult.Error ->
                    if (result.code == 401 || result.code == 403) {
                        SampleWorkspacesList.Hidden
                    } else {
                        SampleWorkspacesList.Failed(serverMessage(result.code, result.message))
                    }
            }
            _state.update { it.copy(list = list) }
        }
    }

    fun switchTo(profile: SampleWorkspaceProfile) {
        if (_state.value.switching != null) return
        _state.update { it.copy(switching = profile, error = null, switchedLabel = null) }
        viewModelScope.launch {
            val error = try {
                when (val result = client.switchSampleWorkspace(profile.key)) {
                    is NetworkResult.Success -> {
                        Timber.d("[SampleWorkspaces] ${profile.key} -> ${result.data.accountId} (${result.data.action})")
                        // The server already committed; finish the local switch even if the
                        // screen is left meanwhile so manifest/active account stay consistent.
                        withContext(NonCancellable) { enterAccount(result.data.accountId) }
                    }
                    is NetworkResult.Unauthorized -> SampleWorkspaceError.RequiresAdmin
                    is NetworkResult.Error ->
                        if (result.code == 401 || result.code == 403) {
                            SampleWorkspaceError.RequiresAdmin
                        } else {
                            SampleWorkspaceError.Server(serverMessage(result.code, result.message))
                        }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[SampleWorkspaces] switch to ${profile.key} failed")
                SampleWorkspaceError.Server(e.message ?: e.javaClass.simpleName)
            }
            _state.update {
                it.copy(
                    switching = null,
                    error = error,
                    switchedLabel = if (error == null) profile.label.ifBlank { profile.key } else null,
                )
            }
            if (error == null) load()
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun consumeSwitched() = _state.update { it.copy(switchedLabel = null) }

    /** Refresh the account list, then switch exactly like the account switcher does. */
    private suspend fun enterAccount(accountId: String): SampleWorkspaceError? {
        identity.refreshAccountsFromServer()
        if (manifest.allAccounts().none { it.accountId == accountId }) {
            return SampleWorkspaceError.AccountMissing
        }
        identity.switchAccount(accountId)
        return null
    }

    private fun serverMessage(code: Int, body: String): String =
        JsonApi.parseError(code, body).message
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

/** Adds the "Sample workspaces" section to the Developer screen list; nothing when hidden. */
fun LazyListScope.sampleWorkspacesSection(
    state: SampleWorkspacesUiState,
    activeAccountId: String?,
    onSelect: (SampleWorkspaceProfile) -> Unit,
    header: @Composable (String) -> Unit,
) {
    if (state.list is SampleWorkspacesList.Hidden) return
    item(key = "sample_workspaces_header") { header(stringResource(R.string.sample_workspaces_title)) }
    when (val list = state.list) {
        SampleWorkspacesList.Loading -> item(key = "sample_workspaces_loading") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
        }
        is SampleWorkspacesList.Failed -> item(key = "sample_workspaces_failed") {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.sample_workspaces_load_failed, list.message),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
        is SampleWorkspacesList.Loaded -> {
            if (list.profiles.isEmpty()) {
                item(key = "sample_workspaces_empty") {
                    ListItem(headlineContent = { Text(stringResource(R.string.sample_workspaces_empty)) })
                }
            }
            items(list.profiles, key = { "sample_ws_${it.key}" }) { profile ->
                SampleWorkspaceRow(
                    profile = profile,
                    isCurrent = profile.workspace?.accountId != null &&
                        profile.workspace?.accountId == activeAccountId,
                    enabled = state.switching == null && profile.available,
                    onClick = { onSelect(profile) },
                )
            }
            item(key = "sample_workspaces_footer") {
                Text(
                    text = stringResource(R.string.sample_workspaces_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        SampleWorkspacesList.Hidden -> Unit
    }
    item(key = "sample_workspaces_divider") { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
}

@Composable
private fun SampleWorkspaceRow(
    profile: SampleWorkspaceProfile,
    isCurrent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val workspace = profile.workspace
    val dim = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ListItem(
        headlineContent = { Text(profile.label.ifBlank { profile.key }, color = dim) },
        supportingContent = {
            Column {
                Text(
                    pluralStringResource(
                        R.plurals.sample_workspaces_profile_meta,
                        profile.assetCount.toInt(),
                        profile.locale?.ifBlank { null } ?: "—",
                        profile.assetCount.toInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = when {
                        !profile.available -> stringResource(R.string.sample_workspaces_unavailable)
                        workspace != null -> pluralStringResource(
                            R.plurals.sample_workspaces_saved,
                            workspace.assetCount.toInt(),
                            workspace.accountName?.ifBlank { null } ?: workspace.accountId,
                            workspace.assetCount.toInt(),
                        )
                        else -> stringResource(R.string.sample_workspaces_not_created)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = if (isCurrent) {
            {
                Text(
                    stringResource(R.string.sample_workspaces_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

/** Blocking progress while switching, and the error dialog with the server's message. */
@Composable
fun SampleWorkspacesDialogs(
    state: SampleWorkspacesUiState,
    onDismissError: () -> Unit,
) {
    state.switching?.let { profile ->
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.sample_workspaces_switching, profile.label.ifBlank { profile.key })) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(R.string.sample_workspaces_switching_hint),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            },
            confirmButton = {},
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.sample_workspaces_error_title)) },
            text = {
                Text(
                    when (error) {
                        SampleWorkspaceError.RequiresAdmin -> stringResource(R.string.sample_workspaces_requires_admin)
                        SampleWorkspaceError.AccountMissing -> stringResource(R.string.sample_workspaces_account_missing)
                        is SampleWorkspaceError.Server -> error.message
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissError) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}
