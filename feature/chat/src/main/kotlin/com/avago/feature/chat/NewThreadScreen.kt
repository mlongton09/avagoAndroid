package com.avago.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.network.model.UserResponse
import com.avago.feature.chat.data.ChatRepository
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
class NewThreadViewModel @Inject constructor(
    private val repository: ChatRepository,
) : ViewModel() {

    private val _members = MutableStateFlow<List<UserResponse>>(emptyList())
    val members: StateFlow<List<UserResponse>> = _members.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _createdThreadId = MutableStateFlow<String?>(null)
    val createdThreadId: StateFlow<String?> = _createdThreadId.asStateFlow()

    init {
        viewModelScope.launch {
            _isLoading.value = true
            _members.value = repository.getThreadMembers()
            _isLoading.value = false
        }
    }

    fun createDirect(memberId: String, memberName: String?) {
        viewModelScope.launch {
            repository.createThread(
                type = "direct",
                displayName = memberName,
                memberIds = listOf(memberId),
            ).onSuccess { threadId ->
                _createdThreadId.value = threadId
            }.onFailure { e ->
                Timber.e(e, "createDirect failed")
            }
        }
    }

    fun createGroup(name: String, memberIds: List<String>) {
        viewModelScope.launch {
            repository.createThread(
                type = "group",
                displayName = name.trim().ifBlank { null },
                memberIds = memberIds,
            ).onSuccess { threadId ->
                _createdThreadId.value = threadId
            }.onFailure { e ->
                Timber.e(e, "createGroup failed")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewThreadScreen(
    onBack: () -> Unit,
    onThreadCreated: (threadId: String) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    viewModel: NewThreadViewModel = hiltViewModel(),
) {
    val members by viewModel.members.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val createdThreadId by viewModel.createdThreadId.collectAsStateWithLifecycle()

    LaunchedEffect(createdThreadId) {
        createdThreadId?.let { onThreadCreated(it) }
    }

    // 0 = Direct, 1 = Group. The section "+" headers on the chat list deep-link
    // straight to the matching tab (Group "+" → Group tab), mirroring iOS where
    // each section header's + opens that thread type's create flow directly.
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    val tabs = listOf(
        stringResource(R.string.new_thread_tab_direct),
        stringResource(R.string.new_thread_tab_group),
    )
    val title = if (selectedTab == 0) {
        stringResource(R.string.new_thread_title)
    } else {
        stringResource(R.string.new_thread_title_group)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> DirectMessageTab(
                    members = members,
                    isLoading = isLoading,
                    onMemberClick = { member ->
                        viewModel.createDirect(member.user_id, member.display_name)
                    },
                )
                1 -> GroupTab(
                    members = members,
                    isLoading = isLoading,
                    onCreateGroup = { name, selectedIds ->
                        viewModel.createGroup(name, selectedIds)
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Direct Message Tab
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectMessageTab(
    members: List<UserResponse>,
    isLoading: Boolean,
    onMemberClick: (UserResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search members") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val filtered = if (query.isBlank()) members else members.filter {
                it.display_name?.contains(query, ignoreCase = true) == true ||
                    it.email?.contains(query, ignoreCase = true) == true
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No members found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(filtered, key = { it.user_id }) { member ->
                        ListItem(
                            headlineContent = { Text(member.display_name ?: member.user_id) },
                            supportingContent = member.email?.let { email -> { Text(email) } },
                            leadingContent = {
                                MemberAvatar(
                                    initial = (member.display_name ?: member.user_id)
                                        .first()
                                        .uppercaseChar(),
                                )
                            },
                            modifier = Modifier.clickable { onMemberClick(member) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Group Tab
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupTab(
    members: List<UserResponse>,
    isLoading: Boolean,
    onCreateGroup: (name: String, memberIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Thread Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (selectedIds.isNotEmpty()) {
                val selectedMembers = members.filter { it.user_id in selectedIds }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(selectedMembers, key = { it.user_id }) { member ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = member.display_name ?: member.user_id,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search members") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val filtered = if (query.isBlank()) members else members.filter {
                    it.display_name?.contains(query, ignoreCase = true) == true ||
                        it.email?.contains(query, ignoreCase = true) == true
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(filtered, key = { it.user_id }) { member ->
                        val checked = member.user_id in selectedIds
                        ListItem(
                            headlineContent = { Text(member.display_name ?: member.user_id) },
                            supportingContent = member.email?.let { email -> { Text(email) } },
                            leadingContent = {
                                MemberAvatar(
                                    initial = (member.display_name ?: member.user_id)
                                        .first()
                                        .uppercaseChar(),
                                )
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { on ->
                                        if (on) selectedIds.add(member.user_id)
                                        else selectedIds.remove(member.user_id)
                                    },
                                )
                            },
                            modifier = Modifier.clickable {
                                if (checked) selectedIds.remove(member.user_id)
                                else selectedIds.add(member.user_id)
                            },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if (selectedIds.isNotEmpty()) {
                    onCreateGroup(groupName, selectedIds.toList())
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create group")
        }
    }
}

// ---------------------------------------------------------------------------
// Shared composable
// ---------------------------------------------------------------------------

@Composable
private fun MemberAvatar(initial: Char, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
