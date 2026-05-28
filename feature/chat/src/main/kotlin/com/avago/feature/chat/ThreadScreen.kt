package com.avago.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.avago.feature.chat.ui.SystemMessageBubble
import com.avago.feature.chat.ui.PinnedMessageBanner
import com.avago.feature.chat.ui.SubjectSummaryCard
import com.avago.feature.chat.ui.TypingIndicator
import com.avago.feature.chat.ui.displayTitle
import com.avago.feature.chat.viewmodel.ThreadViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
    onOpenWorkOrder: (woId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface one-shot errors via Snackbar.
    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    // Which message has the action sheet open.
    var actionSheetMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }

    // Body pending broadcast confirmation (set when threadType is "broadcast" or "team").
    var pendingBroadcastBody by remember { mutableStateOf<String?>(null) }

    // Incrementing this key forces MessageComposer to recreate with the restored draft text.
    var composerRevision by remember { mutableIntStateOf(0) }

    // Resume-draft dialog: shown when a saved draft is recovered from DataStore on cold start.
    if (uiState.resumeDraft != null) {
        AlertDialog(
            onDismissRequest = { viewModel.discardResumeDraft() },
            title = { Text("Resume draft?") },
            text = { Text("You have an unsent draft for this thread.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.acceptResumeDraft()
                    composerRevision++
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.discardResumeDraft() }) { Text("Discard") }
            },
        )
    }

    // Acknowledge most recent message when it arrives (matches iOS behavior).
    LaunchedEffect(uiState.messages.lastOrNull()?.messageId) {
        uiState.messages.lastOrNull()?.let { viewModel.acknowledgeMessage(it.messageId) }
    }

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
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.animateScrollToItem(total - 1)
        }
    }

    // Pagination: trigger load-more when scrolled to the very top.
    // drop(1) skips the initial emission at index=0 so we don't fire a spurious
    // load-more the moment the screen opens before the user has scrolled.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .drop(1)
            .distinctUntilChanged()
            .filter { it == 0 }
            .collect { viewModel.loadMoreMessages() }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            Column {
                QuickReplyBar(
                    onQuickReply = { reply ->
                        val threadType = uiState.thread?.threadType ?: ""
                        if (threadType == "broadcast" || threadType == "team") {
                            pendingBroadcastBody = reply
                        } else {
                            viewModel.sendMessage(reply)
                        }
                    },
                )
                androidx.compose.runtime.key(composerRevision) {
                MessageComposer(
                    editingMessage = uiState.editingMessage,
                    members = uiState.roster,
                    initialText = uiState.draft,
                    onSend = { body ->
                        if (uiState.editingMessage != null) {
                            viewModel.submitEdit(body)
                        } else {
                            val threadType = uiState.thread?.threadType ?: ""
                            if (threadType == "broadcast" || threadType == "team") {
                                pendingBroadcastBody = body
                            } else {
                                viewModel.sendMessage(body)
                            }
                        }
                    },
                    onCancelEdit = viewModel::cancelEditing,
                    onTyping = viewModel::onUserTyped,
                    onTextChanged = viewModel::saveDraft,
                    replyingToMessage = uiState.replyingToMessage,
                    onCancelReply = viewModel::cancelReply,
                    linkPreview = uiState.linkPreview,
                    onUrlDetected = viewModel::setDetectedUrl,
                    onDismissLinkPreview = viewModel::dismissLinkPreview,
                )
                } // key(composerRevision)
            }
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
                    SubjectSummaryCard(
                        subjectSummaryJson = summaryJson,
                        onOpenEntity = { id, type ->
                            if (type == "work_order" || type == "wo") onOpenWorkOrder(id)
                        },
                    )
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
                val messages = uiState.messages
                val messagesWithSeparators = remember(messages) {
                    buildList {
                        var lastDateLabel: String? = null
                        messages.forEach { msg ->
                            val label = msg.createdAt.toDateLabel()
                            if (label != lastDateLabel) {
                                add("date:$label")
                                lastDateLabel = label
                            }
                            add(msg)
                        }
                    }
                }
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

                    items(messagesWithSeparators, key = { item ->
                        when (item) {
                            is String -> item
                            is ChatMessageEntity -> item.messageId
                            else -> item.hashCode()
                        }
                    }) { item ->
                        when (item) {
                            is String -> {
                                val label = (item as String).removePrefix("date:")
                                DateSeparatorItem(label = label)
                            }
                            is ChatMessageEntity -> {
                                val msgIndex = messages.indexOf(item)
                                val prevMsg = messages.getOrNull(msgIndex - 1)
                                val nextMsg = messages.getOrNull(msgIndex + 1)
                                val isGroupStart = prevMsg == null || prevMsg.senderId != item.senderId
                                val isGroupEnd = nextMsg == null || nextMsg.senderId != item.senderId

                                // Unread divider
                                val unreadCount = uiState.thread?.unreadCount ?: 0
                                val unreadBoundaryIndex = if (unreadCount > 0 && unreadCount < messages.size) {
                                    messages.size - unreadCount
                                } else -1

                                if (msgIndex == unreadBoundaryIndex) {
                                    UnreadDivider()
                                }

                                if (item.isSystem) {
                                    SystemMessageBubble(
                                        message = item,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                } else {
                                    MessageBubble(
                                        message = item,
                                        myUserId = uiState.myUserId,
                                        isGroupStart = isGroupStart,
                                        isGroupEnd = isGroupEnd,
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = if (isGroupStart) 4.dp else 1.dp,
                                        ),
                                        onLongPress = { actionSheetMessage = it },
                                        onOpenSubthread = if (item.replyCount > 0) { msg -> onOpenSubthread(msg.messageId) } else null,
                                        onReply = { msg -> viewModel.startReply(msg) },
                                    )
                                }
                            }
                        }
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
                        val total = listState.layoutInfo.totalItemsCount
                        if (total > 0) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(total - 1)
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
        val role = uiState.userRole
        MessageActionSheet(
            message = msg,
            myUserId = uiState.myUserId,
            isAdminOrRoot = role == "admin" || role == "root",
            onDismiss = { actionSheetMessage = null },
            onEdit = { viewModel.startEditing(msg) },
            onDelete = { viewModel.deleteMessage(msg.messageId) },
            onReact = { emoji -> viewModel.reactToMessage(msg.messageId, emoji) },
            onReply = { viewModel.startReply(msg) },
            onReplyInThread = { onOpenSubthread(msg.messageId) },
            onPin = { viewModel.pinMessage(msg.messageId) },
            onUnpin = { viewModel.unpinMessage(msg.messageId) },
            onReport = { viewModel.reportMessage(msg.messageId) },
        )
    }

    // Broadcast confirm dialog
    pendingBroadcastBody?.let { body ->
        AlertDialog(
            onDismissRequest = { pendingBroadcastBody = null },
            title = { Text("Send to everyone?") },
            text = { Text("This message will be sent to all members of this thread.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendMessage(body)
                        pendingBroadcastBody = null
                    },
                ) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBroadcastBody = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Converts an epoch-millis timestamp to a human-readable date label:
 * "Today", "Yesterday", or "MMM d" (e.g. "Jan 5").
 */
private fun Long.toDateLabel(): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = this@toDateLabel }
    val today = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    return when {
        cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) &&
            cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) -> "Today"
        cal.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR) &&
            cal.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) -> "Yesterday"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(cal.time)
    }
}

@Composable
private fun DateSeparatorItem(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** Quick-reply suggestions bar shown above the composer. */
private val quickReplies = listOf("👍 OK", "On my way", "Done", "Thanks")

@Composable
private fun QuickReplyBar(
    onQuickReply: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        quickReplies.forEach { reply ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .wrapContentWidth()
                    .clickable { onQuickReply(reply) },
            ) {
                Text(
                    text = reply,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
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
