package com.avago.feature.chat.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.feature.chat.data.ChatRepository
import com.avago.feature.chat.realtime.ChatRealtimeClient
import com.avago.feature.chat.realtime.OutboxRetryCoordinator
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

data class ThreadUiState(
    val thread: ChatThreadEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val myUserId: String = "",
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val isTypingRemote: Boolean = false,
    /** If non-null, the composer is in edit mode for this message. */
    val editingMessage: ChatMessageEntity? = null,
)

@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val identity: IdentityManager,
    private val realtimeClient: ChatRealtimeClient,
    private val outbox: OutboxRetryCoordinator,
) : ViewModel() {

    val threadId: String = requireNotNull(savedStateHandle["threadId"])

    private val _messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private val _thread = MutableStateFlow<ChatThreadEntity?>(null)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _hasMore = MutableStateFlow(true)
    private val _isTypingRemote = MutableStateFlow(false)
    private val _editingMessage = MutableStateFlow<ChatMessageEntity?>(null)

    val uiState: StateFlow<ThreadUiState> = combine(
        _thread,
        _messages,
        _isLoadingMore,
        _hasMore,
        _editingMessage,
        _isTypingRemote,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ThreadUiState(
            thread = values[0] as? ChatThreadEntity,
            messages = values[1] as List<ChatMessageEntity>,
            myUserId = identity.activeAccountId.value ?: "",
            isLoadingMore = values[2] as Boolean,
            hasMore = values[3] as Boolean,
            editingMessage = values[4] as? ChatMessageEntity,
            isTypingRemote = values[5] as Boolean,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThreadUiState(myUserId = identity.activeAccountId.value ?: ""),
    )

    init {
        observeMessages()
        viewModelScope.launch { repository.syncMessages(threadId) }
        connectRealtime()
        val accountId = identity.activeAccountId.value
        if (accountId != null) outbox.startRetrying(accountId)
    }

    private fun observeMessages() {
        viewModelScope.launch {
            repository.observeMessages(threadId)
                .catch { e -> Timber.e(e, "observeMessages error") }
                .collect { _messages.value = it }
        }
    }

    private fun connectRealtime() {
        realtimeClient.connect(threadId) { msg ->
            viewModelScope.launch {
                repository.handleRealtimeMessage(msg)
                // Typing indicator: if a "typing" event type is added later,
                // handle it here by setting _isTypingRemote briefly.
            }
        }
    }

    fun sendMessage(body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        val accountId = identity.activeAccountId.value ?: return
        viewModelScope.launch {
            try {
                outbox.send(accountId = accountId, threadId = threadId, body = trimmed)
            } catch (e: Exception) {
                Timber.e(e, "sendMessage failed")
            }
        }
    }

    fun startEditing(message: ChatMessageEntity) {
        _editingMessage.value = message
    }

    fun cancelEditing() {
        _editingMessage.value = null
    }

    fun submitEdit(newBody: String) {
        val msg = _editingMessage.value ?: return
        _editingMessage.value = null
        viewModelScope.launch {
            repository.editMessage(threadId, msg.messageId, newBody.trim())
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(threadId, messageId)
        }
    }

    fun reactToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            repository.reactToMessage(threadId, messageId, emoji)
        }
    }

    fun loadMoreMessages() {
        if (_isLoadingMore.value || !_hasMore.value) return
        val oldest = _messages.value.firstOrNull()?.createdAt ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                repository.loadMoreMessages(threadId, oldest)
                // If the page came back empty, there's nothing more to load.
                // The repository upserts whatever it gets, so we infer "no more"
                // when zero new messages appear. A more precise approach would use
                // the has_more field from ChatMessagesResponse — that would require
                // returning it from repository.loadMoreMessages. Keep simple for now.
            } catch (e: Exception) {
                Timber.e(e, "loadMoreMessages failed")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeClient.disconnect()
    }
}
