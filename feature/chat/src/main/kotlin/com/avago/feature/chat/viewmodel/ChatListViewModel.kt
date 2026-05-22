package com.avago.feature.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.feature.chat.data.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isRefreshing: Boolean = false,
    // TODO: compute from per-thread mention counts once ChatThreadEntity gains a mentionCount field.
    val unreadMentionCount: Int = 0,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repository: ChatRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(ThreadFilter.ALL)
    private val _isRefreshing = MutableStateFlow(false)
    private val _allThreads = MutableStateFlow<List<ChatThreadEntity>>(emptyList())

    val uiState: StateFlow<ChatListUiState> = combine(
        _allThreads,
        _filter,
        _isRefreshing,
    ) { threads, filter, refreshing ->
        ChatListUiState(
            threads = threads.filter { it.matchesFilter(filter) },
            filter = filter,
            isRefreshing = refreshing,
            // TODO: replace with sum of per-thread mentionCount once field exists on ChatThreadEntity.
            unreadMentionCount = 0,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatListUiState(),
    )

    init {
        observeThreads()
        refresh()
    }

    private fun observeThreads() {
        viewModelScope.launch {
            repository.observeThreads()
                .catch { e -> Timber.e(e, "observeThreads error") }
                .collect { _allThreads.value = it }
        }
    }

    fun setFilter(filter: ThreadFilter) {
        _filter.value = filter
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.syncThreads().onFailure { e ->
                Timber.e(e, "syncThreads failed")
            }
            _isRefreshing.value = false
        }
    }

    private fun ChatThreadEntity.matchesFilter(filter: ThreadFilter): Boolean = when (filter) {
        ThreadFilter.ALL -> true
        ThreadFilter.DIRECT -> threadType == "direct" || threadType == "group"
        ThreadFilter.WORK_ORDERS -> threadType.startsWith("wo")
        ThreadFilter.ASSETS -> threadType.startsWith("asset")
    }
}
