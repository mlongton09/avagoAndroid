package com.avago.feature.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.feature.chat.data.ChatRepository
import com.avago.feature.chat.realtime.ChatRealtimeClient
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

enum class ThreadFilter { ALL, DIRECT, WORK_ORDERS, ASSETS }

data class ChatListUiState(
    val threads: List<ChatThreadEntity> = emptyList(),
    val filter: ThreadFilter = ThreadFilter.ALL,
    val searchQuery: String = "",
    val unreadOnly: Boolean = false,
    val isRefreshing: Boolean = false,
    val unreadMentionCount: Int = 0,
    val syncError: String? = null,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val realtimeClient: ChatRealtimeClient,
    private val identity: IdentityManager,
) : ViewModel() {

    private val _filter = MutableStateFlow(ThreadFilter.ALL)
    private val _isRefreshing = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _unreadOnly = MutableStateFlow(false)
    private val _allThreads = MutableStateFlow<List<ChatThreadEntity>>(emptyList())
    private val _unreadMentionCount = MutableStateFlow(0)
    private val _syncError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ChatListUiState> = combine(
        _allThreads,
        _filter,
        _searchQuery,
        _unreadOnly,
        combine(_isRefreshing, _unreadMentionCount, _syncError) { refreshing, mentionCount, error ->
            Triple(refreshing, mentionCount, error)
        },
    ) { threads, filter, query, unreadOnly, (refreshing, mentionCount, syncError) ->
        val filtered = threads
            .filter { it.matchesFilter(filter) }
            .filter { query.isBlank() || it.matchesSearch(query) }
            .filter { !unreadOnly || it.unreadCount > 0 }
        val sorted = if (filter == ThreadFilter.ALL) {
            filtered.sortedWith(
                compareBy<ChatThreadEntity> { TYPE_ORDER[it.threadType] ?: 99 }
                    .thenByDescending { it.isFavorite }
                    .thenByDescending { it.lastMessageAt ?: 0L }
            )
        } else {
            filtered.sortedWith(
                compareByDescending<ChatThreadEntity> { it.isFavorite }
                    .thenByDescending { it.lastMessageAt ?: 0L }
            )
        }
        ChatListUiState(
            threads = sorted,
            filter = filter,
            searchQuery = query,
            unreadOnly = unreadOnly,
            isRefreshing = refreshing,
            unreadMentionCount = mentionCount,
            syncError = syncError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatListUiState(),
    )

    init {
        observeThreads()
        observeUnreadMentionCount()
        refresh()
        // Sync roster (GET /chat/me/roster) on load — mirrors iOS AppBootstrapCoordinator.startChatServices().
        viewModelScope.launch {
            repository.syncRoster()
        }
        // Ensure the WebSocket connection is live whenever the chat list is active.
        // WebSocket onOpen triggers BackgroundSyncCoordinator.runDelta() — matches iOS reconnect behavior.
        identity.activeAccountId.value?.let { realtimeClient.connect(it) }
    }

    private fun observeThreads() {
        viewModelScope.launch {
            if (identity.activeAccountId.value == null) {
                Timber.w("ChatListViewModel: observeThreads called with null accountId — DB observation skipped")
            }
            repository.observeThreads()
                .catch { e -> Timber.e(e, "observeThreads error") }
                .collect { threads ->
                    Timber.d("ChatListViewModel: observeThreads emitted ${threads.size} threads")
                    _allThreads.value = threads
                }
        }
    }

    private fun observeUnreadMentionCount() {
        viewModelScope.launch {
            repository.observeUnreadMentionCount()
                .catch { e -> Timber.e(e, "observeUnreadMentionCount error") }
                .collect { _unreadMentionCount.value = it }
        }
    }

    fun setFilter(filter: ThreadFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setUnreadOnly(value: Boolean) {
        _unreadOnly.value = value
    }

    fun refresh() {
        _syncError.value = null
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.syncThreads()
                .onSuccess { Timber.d("ChatListViewModel: syncThreads succeeded") }
                .onFailure { e ->
                    Timber.e(e, "ChatListViewModel: syncThreads failed")
                    _syncError.value = e.message
                }
            _isRefreshing.value = false
        }
    }

    fun setFavorite(threadId: String, favorite: Boolean) {
        viewModelScope.launch {
            repository.setFavorite(threadId, favorite).onFailure { e ->
                Timber.e(e, "setFavorite failed for threadId=$threadId")
            }
        }
    }

    /**
     * Mute [threadId] for [hours] hours (0 = unmute).
     * hours: 1, 8, 24 match iOS options; 0 = unmute.
     */
    fun muteThread(threadId: String, hours: Int) {
        viewModelScope.launch {
            val until = if (hours > 0) System.currentTimeMillis() + hours * 3_600_000L else null
            repository.muteThread(threadId, muted = hours > 0, untilEpochMs = until)
                .onFailure { e -> Timber.e(e, "muteThread failed for threadId=$threadId") }
        }
    }

    fun leaveThread(threadId: String) {
        viewModelScope.launch {
            repository.leaveThread(threadId).onFailure { e ->
                Timber.e(e, "leaveThread failed for threadId=$threadId")
            }
        }
    }

    companion object {
        // Defines display order when ALL filter is active — mirrors iOS chat list ordering.
        private val TYPE_ORDER = mapOf(
            "team" to 0,
            "asset" to 1,
            "direct" to 2,
            "group" to 3,
            "wo" to 4,
        )
    }

    private fun ChatThreadEntity.matchesFilter(filter: ThreadFilter): Boolean = when (filter) {
        ThreadFilter.ALL -> true
        ThreadFilter.DIRECT -> threadType == "direct" || threadType == "group"
        ThreadFilter.WORK_ORDERS -> threadType.startsWith("wo")
        ThreadFilter.ASSETS -> threadType.startsWith("asset")
    }

    private fun ChatThreadEntity.matchesSearch(query: String): Boolean {
        val q = query.lowercase()
        return displayName?.lowercase()?.contains(q) == true ||
            subjectSummary?.lowercase()?.contains(q) == true ||
            lastMessagePreview?.lowercase()?.contains(q) == true ||
            threadType.lowercase().contains(q)
    }
}
