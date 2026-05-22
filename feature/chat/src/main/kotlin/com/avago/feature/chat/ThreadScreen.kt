package com.avago.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.feature.chat.ui.MessageActionSheet
import com.avago.feature.chat.ui.MessageBubble
import com.avago.feature.chat.ui.MessageComposer
import com.avago.feature.chat.ui.PinnedMessageBanner
import com.avago.feature.chat.ui.SubjectSummaryCard
import com.avago.feature.chat.ui.TypingIndicator
import com.avago.feature.chat.ui.displayTitle
import com.avago.feature.chat.viewmodel.ThreadViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    threadId: String,
    onBack: () -> Unit,
    onMembers: () -> Unit,
    onMedia: () -> Unit,
    onSettings: () -> Unit,
    onOpenSubthread: (messageId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Which message has the action sheet open.
    var actionSheetMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }

    // Scroll to bottom when new messages arrive and user is near bottom.
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems == 0 || lastVisible >= totalItems - 2
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (isAtBottom && uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Pagination: trigger load-more when scrolled to the very top.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .filter { it == 0 }
            .collect { viewModel.loadMoreMessages() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.thread?.displayTitle() ?: "Chat",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Members") },
                            onClick = { showMenu = false; onMembers() },
                        )
                        DropdownMenuItem(
                            text = { Text("Media") },
                            onClick = { showMenu = false; onMedia() },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { showMenu = false; onSettings() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            MessageComposer(
                editingMessage = uiState.editingMessage,
                members = emptyList(), // TODO: load account members for @ autocomplete
                onSend = { body ->
                    if (uiState.editingMessage != null) {
                        viewModel.submitEdit(body)
                    } else {
                        viewModel.sendMessage(body)
                    }
                },
                onCancelEdit = viewModel::cancelEditing,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Subject summary card (WO / asset threads)
                uiState.thread?.subjectSummary?.let { summaryJson ->
                    SubjectSummaryCard(subjectSummaryJson = summaryJson)
                }

                // Pinned message banner
                uiState.pinnedMessage?.let { pinned ->
                    PinnedMessageBanner(
                        message = pinned,
                        onTap = {
                            val idx = uiState.messages.indexOfFirst { it.messageId == pinned.messageId }
                            if (idx >= 0) {
                                coroutineScope.launch { listState.animateScrollToItem(idx) }
                            }
                        },
                        onDismiss = { viewModel.unpinMessage(pinned.messageId) },
                    )
                }

                // Message list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = false,
                ) {
                    // "Load more" indicator at top
                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Loading…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }

                    val messages = uiState.messages
                    items(messages, key = { it.messageId }) { message ->
                        val index = messages.indexOf(message)
                        val prev = messages.getOrNull(index - 1)
                        val isGroupStart = prev == null || prev.senderId != message.senderId

                        // Unread divider
                        val unreadCount = uiState.thread?.unreadCount ?: 0
                        val unreadBoundaryIndex = if (unreadCount > 0 && unreadCount < messages.size) {
                            messages.size - unreadCount
                        } else -1

                        if (index == unreadBoundaryIndex) {
                            UnreadDivider()
                        }

                        MessageBubble(
                            message = message,
                            myUserId = uiState.myUserId,
                            isGroupStart = isGroupStart,
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = if (isGroupStart) 4.dp else 1.dp,
                            ),
                            onLongPress = { actionSheetMessage = it },
                        )
                    }

                    // Typing indicator
                    if (uiState.isTypingRemote) {
                        item {
                            TypingIndicator(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // "New messages ↓" pill — shown when not at bottom and there are unread
            val showScrollPill = !isAtBottom && (uiState.thread?.unreadCount ?: 0) > 0
            AnimatedVisibility(
                visible = showScrollPill,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                Button(
                    onClick = {
                        val lastIndex = uiState.messages.size - 1
                        if (lastIndex >= 0) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(lastIndex)
                            }
                        }
                    },
                ) {
                    Text("New messages ↓")
                }
            }
        }
    }

    // Message action bottom sheet
    actionSheetMessage?.let { msg ->
        MessageActionSheet(
            message = msg,
            myUserId = uiState.myUserId,
            onDismiss = { actionSheetMessage = null },
            onEdit = { viewModel.startEditing(msg) },
            onDelete = { viewModel.deleteMessage(msg.messageId) },
            onReact = { emoji -> viewModel.reactToMessage(msg.messageId, emoji) },
            onReplyInThread = { onOpenSubthread(msg.messageId) },
            onPin = { viewModel.pinMessage(msg.messageId) },
            onUnpin = { viewModel.unpinMessage(msg.messageId) },
        )
    }
}

@Composable
private fun UnreadDivider() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 48.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "New messages",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(2.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 48.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
    }
}
