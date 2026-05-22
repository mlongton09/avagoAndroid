package com.avago.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.feature.chat.data.ChatRepository
import com.avago.feature.chat.ui.MessageBubble
import com.avago.feature.chat.ui.MessageComposer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
)

@HiltViewModel
class SubthreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val identity: IdentityManager,
) : ViewModel() {

    private val threadId: String = requireNotNull(savedStateHandle["threadId"])
    private val messageId: String = requireNotNull(savedStateHandle["messageId"])

    private val _parentMessage = MutableStateFlow<ChatMessageEntity?>(null)
    private val _replies = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private val _isSending = MutableStateFlow(false)

    val uiState: StateFlow<SubthreadUiState> = combine(
        _parentMessage,
        _replies,
        _isSending,
    ) { parent, replies, sending ->
        SubthreadUiState(
            parentMessage = parent,
            replies = replies,
            myUserId = identity.activeAccountId.value ?: "",
            isSending = sending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SubthreadUiState(myUserId = identity.activeAccountId.value ?: ""),
    )

    init {
        loadParentMessage()
        observeReplies()
        viewModelScope.launch { repository.syncReplies(threadId, messageId) }
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
            } catch (e: Exception) {
                Timber.e(e, "sendReply failed")
            } finally {
                _isSending.value = false
            }
        }
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

    // Scroll to bottom when replies arrive.
    LaunchedEffect(uiState.replies.size) {
        if (uiState.replies.isNotEmpty()) {
            listState.animateScrollToItem(uiState.replies.size - 1)
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
                val replies = uiState.replies
                items(replies, key = { it.messageId }) { reply ->
                    val index = replies.indexOf(reply)
                    val prev = replies.getOrNull(index - 1)
                    val isGroupStart = prev == null || prev.senderId != reply.senderId

                    MessageBubble(
                        message = reply,
                        myUserId = uiState.myUserId,
                        isGroupStart = isGroupStart,
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
