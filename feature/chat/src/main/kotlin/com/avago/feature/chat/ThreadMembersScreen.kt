package com.avago.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.ChatMemberResponse
import com.avago.feature.chat.data.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class ThreadMembersUiState(
    val members: List<ChatMemberResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val myUserId: String = "",
    val myRole: String? = null,
    val groupName: String? = null,
    val notificationPref: String = "all",
    val actionError: String? = null,
    val leftThread: Boolean = false,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class ThreadMembersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serviceClient: AvagoServiceClient,
    private val repository: ChatRepository,
    private val identity: IdentityManager,
) : ViewModel() {

    private val threadId: String = requireNotNull(savedStateHandle["threadId"])

    private val _members = MutableStateFlow<List<ChatMemberResponse>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _myRole = MutableStateFlow<String?>(null)
    private val _groupName = MutableStateFlow<String?>(null)
    private val _notificationPref = MutableStateFlow("all")
    private val _actionError = MutableStateFlow<String?>(null)
    private val _leftThread = MutableStateFlow(false)

    val uiState: StateFlow<ThreadMembersUiState> = combine(
        listOf(_members, _isLoading, _error, _myRole, _groupName, _notificationPref, _actionError, _leftThread),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ThreadMembersUiState(
            members = values[0] as List<ChatMemberResponse>,
            isLoading = values[1] as Boolean,
            error = values[2] as? String,
            myUserId = identity.activeUserId.value ?: "",
            myRole = values[3] as? String,
            groupName = values[4] as? String,
            notificationPref = values[5] as String,
            actionError = values[6] as? String,
            leftThread = values[7] as Boolean,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThreadMembersUiState(myUserId = identity.activeUserId.value ?: ""),
    )

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val result = serviceClient.getThreadMembers(threadId)) {
                is NetworkResult.Success -> {
                    _members.value = result.data
                    val myId = identity.activeUserId.value
                    _myRole.value = result.data.firstOrNull { it.user_id == myId }?.role
                }
                is NetworkResult.Error -> {
                    Timber.w("ThreadMembersViewModel: load failed: ${result.message}")
                    _error.value = result.message ?: "Failed to load members"
                }
                is NetworkResult.Unauthorized -> _error.value = "Unauthorized"
            }
            _isLoading.value = false
        }
    }

    fun removeMember(userId: String) {
        val isAdmin = _myRole.value == "admin" || _myRole.value == "root"
        if (!isAdmin) return
        viewModelScope.launch {
            repository.removeMember(threadId, userId).fold(
                onSuccess = { _members.update { list -> list.filter { it.user_id != userId } } },
                onFailure = { e ->
                    Timber.e(e, "removeMember failed")
                    _actionError.value = "Failed to remove member"
                },
            )
        }
    }

    fun addMembers(userIds: List<String>) {
        if (userIds.isEmpty()) return
        viewModelScope.launch {
            repository.addMembers(threadId, userIds).fold(
                onSuccess = { load() },
                onFailure = { e ->
                    Timber.e(e, "addMembers failed")
                    _actionError.value = "Failed to add members"
                },
            )
        }
    }

    fun leaveThread() {
        viewModelScope.launch {
            repository.leaveThread(threadId).fold(
                onSuccess = { _leftThread.value = true },
                onFailure = { e ->
                    Timber.e(e, "leaveThread failed")
                    _actionError.value = "Failed to leave thread"
                },
            )
        }
    }

    fun renameGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.renameGroup(threadId, trimmed).fold(
                onSuccess = { _groupName.value = trimmed },
                onFailure = { e ->
                    Timber.e(e, "renameGroup failed")
                    _actionError.value = "Failed to rename group"
                },
            )
        }
    }

    fun setNotificationPref(pref: String) {
        viewModelScope.launch {
            repository.setNotificationPref(threadId, pref).fold(
                onSuccess = { _notificationPref.value = pref },
                onFailure = { e ->
                    Timber.e(e, "setNotificationPref failed")
                    _actionError.value = "Failed to update notification preference"
                },
            )
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadMembersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThreadMembersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showNotifMenu by remember { mutableStateOf(false) }

    val isAdmin = uiState.myRole == "admin" || uiState.myRole == "root"

    LaunchedEffect(uiState.leftThread) {
        if (uiState.leftThread) onBack()
    }

    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionError()
        }
    }

    if (showRenameDialog) {
        RenameGroupDialog(
            currentName = uiState.groupName ?: "",
            onConfirm = { name -> viewModel.renameGroup(name); showRenameDialog = false },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave thread?") },
            text = { Text("You will no longer receive messages from this thread.") },
            confirmButton = {
                TextButton(onClick = { viewModel.leaveThread(); showLeaveDialog = false }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.groupName ?: "Members") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename group")
                        }
                    }
                    Box {
                        IconButton(onClick = { showNotifMenu = true }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                        DropdownMenu(
                            expanded = showNotifMenu,
                            onDismissRequest = { showNotifMenu = false },
                        ) {
                            listOf("all" to "All messages", "mentions" to "Mentions only", "none" to "Off")
                                .forEach { (pref, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.setNotificationPref(pref)
                                            showNotifMenu = false
                                        },
                                    )
                                }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(onClick = { /* TODO: open member picker */ }) {
                    Icon(Icons.Default.Add, contentDescription = "Add members")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.error != null -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.error ?: "Error loading members",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
            ) {
                items(uiState.members, key = { it.user_id }) { member ->
                    if (isAdmin && member.user_id != uiState.myUserId) {
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.removeMember(member.user_id)
                                    true
                                } else false
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove member",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        ) {
                            MemberListItem(member = member)
                        }
                    } else {
                        MemberListItem(member = member)
                    }
                }

                // Leave thread footer
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    ListItem(
                        headlineContent = {
                            Text(
                                "Leave thread",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLeaveDialog = true },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberListItem(
    member: ChatMemberResponse,
    modifier: Modifier = Modifier,
) {
    val displayName = member.display_name ?: member.user_id
    ListItem(
        headlineContent = { Text(displayName) },
        supportingContent = member.role?.let { role -> { Text(role.replaceFirstChar { it.uppercaseChar() }) } },
        leadingContent = {
            val initial = displayName.first().uppercaseChar()
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun RenameGroupDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename group") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
