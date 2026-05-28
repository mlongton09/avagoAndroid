package com.avago.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.BulkInvitation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

data class InviteEntry(
    val email: String = "",
    val displayName: String = "",
    val role: String = "technician",
)

@HiltViewModel
class InviteUsersViewModel @Inject constructor(
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _entries = MutableStateFlow(listOf(InviteEntry()))
    val entries: StateFlow<List<InviteEntry>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    fun updateEntry(index: Int, entry: InviteEntry) {
        _entries.value = _entries.value.toMutableList().also { it[index] = entry }
    }

    fun addEntry() {
        _entries.value = _entries.value + InviteEntry()
    }

    fun removeEntry(index: Int) {
        if (_entries.value.size > 1) {
            _entries.value = _entries.value.toMutableList().also { it.removeAt(index) }
        }
    }

    fun sendInvites() {
        val accountId = identityManager.getActiveAccountId() ?: return
        val validEntries = _entries.value.filter { it.email.isNotBlank() }
        if (validEntries.isEmpty()) {
            _result.value = "Please enter at least one email address"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val invitations = validEntries.map { entry ->
                    BulkInvitation(
                        email = entry.email.trim(),
                        display_name = entry.displayName.trim().ifBlank { null },
                        role = entry.role,
                    )
                }
                when (val outcome = serviceClient.bulkInviteUsers(accountId, invitations)) {
                    is NetworkResult.Success -> {
                        val count = validEntries.size
                        _result.value = "Sent $count invite${if (count > 1) "s" else ""}"
                        _entries.value = listOf(InviteEntry())
                    }
                    is NetworkResult.Error -> {
                        _result.value = "Failed to send invites: ${outcome.message}"
                    }
                    NetworkResult.Unauthorized -> {
                        _result.value = "Unauthorized — please sign in again"
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "InviteUsersViewModel: sendInvites failed")
                _result.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearResult() {
        _result.value = null
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

private val ROLES = listOf(
    "admin" to "Admin",
    "manager" to "Manager",
    "dispatcher" to "Dispatcher",
    "technician" to "Technician",
    "operator" to "Operator",
    "reader" to "Reader",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteUsersScreen(
    onBack: () -> Unit,
    viewModel: InviteUsersViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(result) {
        result?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearResult()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Invite Team Members") },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                InviteEntryRow(
                    entry = entry,
                    showDelete = entries.size > 1,
                    onUpdate = { viewModel.updateEntry(index, it) },
                    onDelete = { viewModel.removeEntry(index) },
                )
            }

            OutlinedButton(
                onClick = viewModel::addEntry,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add another person")
            }

            Button(
                onClick = viewModel::sendInvites,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLoading) "Sending…" else "Send Invite${if (entries.size > 1) "s" else ""}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteEntryRow(
    entry: InviteEntry,
    showDelete: Boolean,
    onUpdate: (InviteEntry) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = entry.email,
                    onValueChange = { onUpdate(entry.copy(email = it)) },
                    label = { Text("Email address") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                if (showDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = entry.displayName,
                onValueChange = { onUpdate(entry.copy(displayName = it)) },
                label = { Text("Display name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            RoleDropdown(
                selected = entry.role,
                onSelect = { onUpdate(entry.copy(role = it)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleDropdown(
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = ROLES.firstOrNull { it.first == selected }?.second ?: selected

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Role") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ROLES.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
