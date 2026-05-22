package com.avago.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@HiltViewModel
class InviteUsersViewModel @Inject constructor(
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    val email = MutableStateFlow("")
    val role = MutableStateFlow("technician")

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    fun sendInvite() {
        val accountId = identityManager.getActiveAccountId() ?: return
        val currentEmail = email.value.trim()
        if (currentEmail.isBlank()) {
            _result.value = "Please enter an email address"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val outcome = serviceClient.inviteUser(accountId, currentEmail, role.value)) {
                    is NetworkResult.Success -> {
                        _result.value = "Invite sent to $currentEmail"
                        email.value = ""
                    }
                    is NetworkResult.Error -> {
                        _result.value = "Failed to send invite: ${outcome.message}"
                    }
                    NetworkResult.Unauthorized -> {
                        _result.value = "Unauthorized — please sign in again"
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "InviteUsersViewModel: sendInvite failed")
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
    "technician" to "Technician",
    "viewer" to "Viewer",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteUsersScreen(
    onBack: () -> Unit,
    viewModel: InviteUsersViewModel = hiltViewModel(),
) {
    val email by viewModel.email.collectAsStateWithLifecycle()
    val role by viewModel.role.collectAsStateWithLifecycle()
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
            TopAppBar(
                title = { Text("Invite Team Member") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { viewModel.email.value = it },
                label = { Text("Email address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            RoleDropdown(
                selected = role,
                onSelect = { viewModel.role.value = it },
            )

            Button(
                onClick = viewModel::sendInvite,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLoading) "Sending…" else "Send Invite")
            }
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
                .menuAnchor(),
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
