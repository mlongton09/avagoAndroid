package com.avago.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avago.feature.chat.ui.MessageActionSheet
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.feature.chat.data.ChatRepository
import com.avago.feature.chat.realtime.ChatRealtimeClient
import com.avago.feature.chat.realtime.OutboxRetryCoordinator
import com.avago.feature.chat.ui.MessageBubble
import com.avago.feature.chat.ui.MessageComposer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

data class SubthreadUiState(
    val parentMessage: ChatMessageEntity? = null,
    val replies: List<ChatMessageEntity> = emptyList(),
    val myUserId: String = "",
    val isSending: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
)

@HiltViewModel
class SubthreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val identity: IdentityManager,
    private val outbox: OutboxRetryCoordinator,
    private val realtimeClient: ChatRealtimeClient,
) : ViewModel() {

    private val threadId: String = requireNotNull(savedStateHandle["threadId"])
    private val messageId: String = requireNotNull(savedStateHandle["messageId"])

    private val _parentMessage = MutableStateFlow<ChatMessageEntity?>(null)
    private val _replies = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private val _isSending = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _hasMore = MutableStateFlow(true)

    val uiState: StateFlow<SubthreadUiState> = combine(
        _parentMessage,
        _replies,
        _isSending,
        _isLoadingMore,
        _hasMore,
    ) { parent, replies, sending, loadingMore, hasMore ->
        SubthreadUiState(
            parentMessage = parent,
            replies = replies,
            myUserId = identity.activeUserId.value ?: "",
            isSending = sending,
            isLoadingMore = loadingMore,
            hasMore = hasMore,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SubthreadUiState(myUserId = identity.activeAccountId.value ?: ""),
    )

    init {
        loadParentMessage()
        observeReplies()
        viewModelScope.launch {
            repository.syncReplies(threadId, messageId)
        }
        val accountId = identity.activeAccountId.value
        if (accountId != null) outbox.startRetrying(accountId)
    }

    private fun loadParentMessage() {
        viewModelScope.launch {
            // Watch the main thread message list; the parent is already in local DB.
            repository.observeMessages(threadId)
                .catch { e -> Timber.e(e, "SubthreadViewModel observeMessages error") }
                .collect { messages ->
                    _parentMessage.value = messages.firstOrNull { it.messageId == messageId }
                }
        }
    }

    private fun observeReplies() {
        viewModelScope.launch {
            repository.observeReplies(threadId, messageId)
                .catch { e -> Timber.e(e, "SubthreadViewModel observeReplies error") }
                .collect { _replies.value = it }
        }
    }

    fun sendReply(body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _isSending.value = true
            try {
                repository.sendReply(threadId, messageId, trimmed)
                realtimeClient.sendTyping(threadId, false)
            } catch (e: Exception) {
                Timber.e(e, "sendReply failed")
            } finally {
                _isSending.value = false
            }
        }
    }

    fun loadOlderReplies() {
        if (_isLoadingMore.value || !_hasMore.value) return
        val oldest = _replies.value.firstOrNull()?.createdAt ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val hasMore = repository.loadMoreMessages(threadId, oldest, limit = 20)
                _hasMore.value = hasMore
            } catch (e: Exception) {
                Timber.e(e, "loadOlderReplies failed")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun acknowledgeMessage(messageId: String) {
        viewModelScope.launch { repository.acknowledgeMessage(messageId) }
    }

    fun toggleReaction(replyMessageId: String, emoji: String) {
        viewModelScope.launch { repository.toggleReaction(threadId, replyMessageId, emoji) }
    }

    private var typingStopJob: Job? = null

    fun onUserTyped() {
        realtimeClient.sendTyping(threadId, true)
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(3_000)
            realtimeClient.sendTyping(threadId, false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        typingStopJob?.cancel()
        realtimeClient.sendTyping(threadId, false)
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubthreadScreen(
    threadId: String,
    messageId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubthreadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var actionSheetMessage by remember { mutableStateOf<com.avago.core.data.db.entity.ChatMessageEntity?>(null) }

    actionSheetMessage?.let { msg ->
        MessageActionSheet(
            message = msg,
            myUserId = uiState.myUserId,
            onDismiss = { actionSheetMessage = null },
            onEdit = { actionSheetMessage = null },
            onDelete = { actionSheetMessage = null },
            onReact = { emoji -> viewModel.toggleReaction(msg.messageId, emoji) },
            onReply = { actionSheetMessage = null },
            isInSubthread = true,
        )
    }

    // Scroll to bottom when replies arrive.
    LaunchedEffect(uiState.replies.size) {
        if (uiState.replies.isNotEmpty()) {
            listState.animateScrollToItem(uiState.replies.size - 1)
        }
    }

    // Acknowledge the most recent reply when it loads.
    LaunchedEffect(uiState.replies.lastOrNull()?.messageId) {
        uiState.replies.lastOrNull()?.let { viewModel.acknowledgeMessage(it.messageId) }
    }

    // Load older replies when scrolled to the top.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstIndex ->
                if (firstIndex == 0 && uiState.hasMore && !uiState.isLoadingMore) {
                    viewModel.loadOlderReplies()
                }
            }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Thread") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            MessageComposer(
                editingMessage = null,
                members = emptyList(),
                onSend = { body -> viewModel.sendReply(body) },
                onCancelEdit = {},
                onTyping = viewModel::onUserTyped,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            // Parent message card
            uiState.parentMessage?.let { parent ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(vertical = 8.dp),
                ) {
                    MessageBubble(
                        message = parent,
                        myUserId = uiState.myUserId,
                        isGroupStart = true,
                        isGroupEnd = true,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            // Reply list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (uiState.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
                    }
                }
                val replies = uiState.replies
                items(replies, key = { it.messageId }) { reply ->
                    val index = replies.indexOf(reply)
                    val prev = replies.getOrNull(index - 1)
                    val next = replies.getOrNull(index + 1)
                    val isGroupStart = prev == null || prev.senderId != reply.senderId
                    val isGroupEnd = next == null || next.senderId != reply.senderId

                    MessageBubble(
                        message = reply,
                        myUserId = uiState.myUserId,
                        isGroupStart = isGroupStart,
                        isGroupEnd = isGroupEnd,
                        onLongPress = { msg -> actionSheetMessage = msg },
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = if (isGroupStart) 4.dp else 1.dp,
                        ),
                    )
                }
            }
        }
    }
}
