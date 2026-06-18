package com.avago.feature.chat.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.ChatAccountRosterEntity
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.network.model.LinkPreviewResponse
import com.avago.feature.chat.data.ChatRepository
import com.avago.feature.chat.realtime.ChatRealtimeClient
import com.avago.feature.chat.realtime.OutboxRetryCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLConnection
import javax.inject.Inject

data class ThreadUiState(
    val thread: ChatThreadEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val myUserId: String = "",
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val isTypingRemote: Boolean = false,
    val editingMessage: ChatMessageEntity? = null,
    val pinnedMessage: ChatMessageEntity? = null,
    val errorMessage: String? = null,
    val roster: List<ChatAccountRosterEntity> = emptyList(),
    val typingUserNames: List<String> = emptyList(),
    val draft: String = "",
    val resumeDraft: String? = null,
    val replyingToMessage: ChatMessageEntity? = null,
    val userRole: String? = null,
    val linkPreview: LinkPreviewResponse? = null,
    val needsReply: Boolean = false,
)

@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val identity: IdentityManager,
    private val realtimeClient: ChatRealtimeClient,
    private val outbox: OutboxRetryCoordinator,
    private val userPrefs: UserPreferencesRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val threadId: String = requireNotNull(savedStateHandle["threadId"])

    private val _messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private val _thread = MutableStateFlow<ChatThreadEntity?>(null)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _hasMore = MutableStateFlow(true)
    private val _isTypingRemote = MutableStateFlow(false)
    private val _editingMessage = MutableStateFlow<ChatMessageEntity?>(null)
    private val _pinnedMessage = MutableStateFlow<ChatMessageEntity?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _roster = MutableStateFlow<List<ChatAccountRosterEntity>>(emptyList())
    private val _typingUserNames = MutableStateFlow<List<String>>(emptyList())
    private val _draft = MutableStateFlow("")
    private val _isSending = MutableStateFlow(false)
    private val _resumeDraft = MutableStateFlow<String?>(null)
    private val _replyingToMessage = MutableStateFlow<ChatMessageEntity?>(null)
    private val _userRole = MutableStateFlow<String?>(null)
    private val _linkPreview = MutableStateFlow<LinkPreviewResponse?>(null)
    private val _needsReply = MutableStateFlow(false)

    val aiSummary: MutableStateFlow<String?> = MutableStateFlow(null)
    val isLoadingAiSummary: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val uiState: StateFlow<ThreadUiState> = combine(
        combine(_thread, _messages, _isLoadingMore, _hasMore, _editingMessage) { thread, messages, isLoadingMore, hasMore, editingMessage ->
            ThreadUiState5A(thread, messages, isLoadingMore, hasMore, editingMessage)
        },
        combine(_isTypingRemote, _pinnedMessage, _errorMessage, _roster, _typingUserNames) { isTypingRemote, pinnedMessage, errorMessage, roster, typingUserNames ->
            ThreadUiState5B(isTypingRemote, pinnedMessage, errorMessage, roster, typingUserNames)
        },
        combine(_draft, _resumeDraft, _replyingToMessage, _userRole, _linkPreview) { draft, resumeDraft, replyingToMessage, userRole, linkPreview ->
            ThreadUiState5C(draft, resumeDraft, replyingToMessage, userRole, linkPreview)
        },
        _needsReply,
    ) { a, b, c, needsReply ->
        ThreadUiState(
            thread = a.thread,
            messages = a.messages,
            myUserId = identity.activeUserId.value ?: "",
            isLoadingMore = a.isLoadingMore,
            hasMore = a.hasMore,
            editingMessage = a.editingMessage,
            isTypingRemote = b.isTypingRemote,
            pinnedMessage = b.pinnedMessage,
            errorMessage = b.errorMessage,
            roster = b.roster,
            typingUserNames = b.typingUserNames,
            draft = c.draft,
            resumeDraft = c.resumeDraft,
            replyingToMessage = c.replyingToMessage,
            userRole = c.userRole,
            linkPreview = c.linkPreview,
            needsReply = needsReply,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThreadUiState(myUserId = identity.activeUserId.value ?: ""),
    )

    init {
        observeThread()
        observeMessages()
        observePinnedMessage()
        observeRoster()
        viewModelScope.launch {
            repository.syncMessages(threadId)
            val lastMessage = repository.getLastMessage(threadId)
            if (lastMessage != null) {
                repository.markThreadRead(threadId, lastMessage.messageId)
                repository.markAllMentionsReadForThread(threadId)
            }
        }
        viewModelScope.launch { repository.syncRoster() }
        connectRealtime()
        val accountId = identity.activeAccountId.value
        if (accountId != null) outbox.startRetrying(accountId)
        loadSavedDraft()
    }

    private fun observeThread() {
        viewModelScope.launch {
            repository.observeThread(threadId)
                .catch { e -> Timber.e(e, "observeThread error") }
                .collect { _thread.value = it }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            repository.observeMessages(threadId)
                .catch { e -> Timber.e(e, "observeMessages error") }
                .collect { _messages.value = it }
        }
    }

    private fun observePinnedMessage() {
        viewModelScope.launch {
            repository.observePinnedMessage(threadId)
                .catch { e -> Timber.e(e, "observePinnedMessage error") }
                .collect { _pinnedMessage.value = it }
        }
    }

    private fun observeRoster() {
        val accountId = identity.activeAccountId.value ?: return
        viewModelScope.launch {
            repository.observeRoster(accountId)
                .catch { e -> Timber.e(e, "observeRoster error") }
                .collect { roster ->
                    _roster.value = roster
                    val userId = identity.activeUserId.value
                    _userRole.value = roster.firstOrNull { it.userId == userId }?.role
                }
        }
    }

    private fun connectRealtime() {
        viewModelScope.launch {
            realtimeClient.typingChangedFlow.collect { event ->
                if (event.threadId == threadId) {
                    _typingUserNames.value = event.typingUserIds
                    _isTypingRemote.value = event.typingUserIds.isNotEmpty()
                }
            }
        }
    }

    private fun loadSavedDraft() {
        viewModelScope.launch {
            val saved = runCatching { userPrefs.getChatDraft(threadId) }.getOrDefault("")
            if (saved.isNotBlank()) {
                _resumeDraft.value = saved
            }
        }
    }

    // ── Typing indicator ──────────────────────────────────────────────────────

    private var typingStopJob: Job? = null

    fun onUserTyped() {
        realtimeClient.sendTyping(threadId, true)
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(3_000)
            realtimeClient.sendTyping(threadId, false)
        }
    }

    // ── Draft ─────────────────────────────────────────────────────────────────

    private var draftSaveJob: Job? = null

    fun acceptResumeDraft(): String {
        val text = _resumeDraft.value ?: ""
        _draft.value = text
        _resumeDraft.value = null
        return text
    }

    fun discardResumeDraft() {
        _resumeDraft.value = null
        viewModelScope.launch { runCatching { userPrefs.setChatDraft(threadId, "") } }
    }

    fun saveDraft(text: String) {
        _draft.value = text
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(500)
            runCatching { userPrefs.setChatDraft(threadId, text) }
        }
    }

    // ── Link preview ─────────────────────────────────────────────────────────

    private var linkPreviewJob: Job? = null

    fun setDetectedUrl(url: String?) {
        linkPreviewJob?.cancel()
        if (url.isNullOrBlank()) {
            _linkPreview.value = null
            return
        }
        linkPreviewJob = viewModelScope.launch {
            delay(600)
            _linkPreview.value = runCatching { repository.fetchLinkPreview(url) }.getOrNull()
        }
    }

    fun dismissLinkPreview() {
        linkPreviewJob?.cancel()
        _linkPreview.value = null
    }

    // ── Inline reply ──────────────────────────────────────────────────────────

    fun startReply(message: ChatMessageEntity) {
        _replyingToMessage.value = message
    }

    fun cancelReply() {
        _replyingToMessage.value = null
    }

    // ── Message actions ───────────────────────────────────────────────────────

    fun sendMessage(body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        if (_isSending.value) return
        val accountId = identity.activeAccountId.value ?: return
        val needsReply = _needsReply.value
        viewModelScope.launch {
            _isSending.value = true
            try {
                outbox.send(
                    accountId = accountId,
                    threadId = threadId,
                    body = trimmed,
                    needsReply = needsReply,
                )
                _replyingToMessage.value = null
                _linkPreview.value = null
                _needsReply.value = false
                saveDraft("")
            } catch (e: Exception) {
                Timber.e(e, "sendMessage failed")
            } finally {
                _isSending.value = false
            }
        }
    }

    fun toggleNeedsReply() {
        _needsReply.value = !_needsReply.value
    }

    fun sendFile(uri: Uri) {
        sendMedia(uri)
    }

    fun sendAudio(uri: Uri) {
        sendMedia(uri)
    }

    private fun sendMedia(uri: Uri) {
        val fileName = resolveFileName(uri)
        val mimeType = context.contentResolver.getType(uri)
            ?: fileName?.let { URLConnection.guessContentTypeFromName(it) }
            ?: "application/octet-stream"
        viewModelScope.launch {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to read file")
                repository.uploadAndSendMedia(
                    threadId = threadId,
                    bytes = bytes,
                    contentType = mimeType,
                    fileName = fileName,
                    needsReply = _needsReply.value,
                ).getOrThrow()
            }.onSuccess {
                _linkPreview.value = null
                _needsReply.value = false
            }.onFailure { e ->
                Timber.e(e, "sendMedia failed")
                _errorMessage.value = e.message ?: "Failed to send attachment"
            }
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        ) ?: return uri.lastPathSegment?.substringAfterLast('/')
        cursor.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return it.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
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
                .onFailure { e ->
                    Timber.e(e, "submitEdit failed")
                    _errorMessage.value = e.message ?: "Failed to edit message"
                }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(threadId, messageId)
                .onFailure { e ->
                    Timber.e(e, "deleteMessage failed")
                    _errorMessage.value = e.message ?: "Failed to delete message"
                }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun reportMessage(messageId: String) {
        viewModelScope.launch {
            repository.reportMessage(threadId, messageId).onFailure { e ->
                Timber.e(e, "reportMessage failed")
                _errorMessage.value = "Failed to report message"
            }
        }
    }

    fun reactToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            repository.toggleReaction(threadId, messageId, emoji)
        }
    }

    fun acknowledgeMessage(messageId: String) {
        viewModelScope.launch {
            repository.acknowledgeMessage(messageId)
        }
    }

    fun renameGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.renameGroup(threadId, trimmed).onFailure { e ->
                Timber.e(e, "renameGroup failed")
                _errorMessage.value = "Failed to rename group"
            }
        }
    }

    fun pinMessage(messageId: String) {
        viewModelScope.launch {
            val ok = repository.pinMessage(threadId, messageId)
            if (!ok) {
                Timber.w("pinMessage failed: threadId=$threadId messageId=$messageId")
                _errorMessage.value = "Failed to pin message"
            }
        }
    }

    fun unpinMessage(messageId: String) {
        viewModelScope.launch {
            val ok = repository.unpinMessage(threadId, messageId)
            if (!ok) {
                Timber.w("unpinMessage failed: threadId=$threadId messageId=$messageId")
                _errorMessage.value = "Failed to unpin message"
            }
        }
    }

    fun loadMoreMessages() {
        if (_isLoadingMore.value || !_hasMore.value) return
        val oldest = _messages.value.firstOrNull()?.createdAt ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val hasMore = repository.loadMoreMessages(threadId, oldest)
                _hasMore.value = hasMore
            } catch (e: Exception) {
                Timber.e(e, "loadMoreMessages failed")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun fetchAiSummary() {
        if (isLoadingAiSummary.value) return
        viewModelScope.launch {
            isLoadingAiSummary.value = true
            aiSummary.value = null
            try {
                val result = repository.getThreadSummary(threadId)
                aiSummary.value = result?.get("summary") as? String
                    ?: "No summary available."
            } catch (e: Exception) {
                Timber.e(e, "fetchAiSummary failed")
                aiSummary.value = "Unable to generate summary. Please try again."
            } finally {
                isLoadingAiSummary.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        typingStopJob?.cancel()
        realtimeClient.sendTyping(threadId, false)
    }
}

private data class ThreadUiState5A(
    val thread: ChatThreadEntity?,
    val messages: List<ChatMessageEntity>,
    val isLoadingMore: Boolean,
    val hasMore: Boolean,
    val editingMessage: ChatMessageEntity?,
)

private data class ThreadUiState5B(
    val isTypingRemote: Boolean,
    val pinnedMessage: ChatMessageEntity?,
    val errorMessage: String?,
    val roster: List<ChatAccountRosterEntity>,
    val typingUserNames: List<String>,
)

private data class ThreadUiState5C(
    val draft: String,
    val resumeDraft: String?,
    val replyingToMessage: ChatMessageEntity?,
    val userRole: String?,
    val linkPreview: LinkPreviewResponse?,
)
