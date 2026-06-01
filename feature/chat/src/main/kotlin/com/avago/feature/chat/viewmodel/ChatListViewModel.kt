package com.avago.feature.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.ChatPrefsRequest
import com.avago.core.network.model.CustomSection
import com.avago.feature.chat.data.ChatRepository
import com.avago.feature.chat.realtime.ChatRealtimeClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

enum class ThreadFilter { ALL, DIRECT, WORK_ORDERS, ASSETS }

data class ChatListUiState(
    val threads: List<ChatThreadEntity> = emptyList(),
    val customSections: List<CustomSection> = emptyList(),
    val filter: ThreadFilter = ThreadFilter.ALL,
    val searchQuery: String = "",
    val unreadOnly: Boolean = false,
    val isRefreshing: Boolean = false,
    val unreadMentionCount: Int = 0,
    val syncError: String? = null,
    val teamThreadId: String? = null,
)

private data class ChatListInputs(
    val threads: List<ChatThreadEntity>,
    val customSections: List<CustomSection>,
    val filter: ThreadFilter,
    val query: String,
    val unreadOnly: Boolean,
)

private data class ChatListMeta(
    val refreshing: Boolean,
    val mentionCount: Int,
    val syncError: String?,
    val teamThreadId: String?,
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
    private val _customSections = MutableStateFlow<List<CustomSection>>(emptyList())
    val customSections: StateFlow<List<CustomSection>> = _customSections.asStateFlow()
    private var lastPopoutLayout: JsonObject = buildJsonObject { }
    private val _unreadMentionCount = MutableStateFlow(0)
    private val _syncError = MutableStateFlow<String?>(null)
    private val _teamThreadId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ChatListUiState> = combine(
        combine(_allThreads, _customSections, _filter, _searchQuery, _unreadOnly) { threads, customSections, filter, query, unreadOnly ->
            ChatListInputs(threads, customSections, filter, query, unreadOnly)
        },
        combine(_isRefreshing, _unreadMentionCount, _syncError, _teamThreadId) { refreshing, mentionCount, error, teamThreadId ->
            ChatListMeta(refreshing, mentionCount, error, teamThreadId)
        },
    ) { inputs, meta ->
        val filtered = inputs.threads
            .filter { it.matchesFilter(inputs.filter) }
            .filter { inputs.query.isBlank() || it.matchesSearch(inputs.query) }
            .filter { !inputs.unreadOnly || it.unreadCount > 0 }
        val sorted = if (inputs.filter == ThreadFilter.ALL) {
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
            customSections = inputs.customSections,
            filter = inputs.filter,
            searchQuery = inputs.query,
            unreadOnly = inputs.unreadOnly,
            isRefreshing = meta.refreshing,
            unreadMentionCount = meta.mentionCount,
            syncError = meta.syncError,
            teamThreadId = meta.teamThreadId,
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
        loadChatPrefs()
        loadTeamThread()
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

    private fun loadChatPrefs() {
        viewModelScope.launch {
            when (val result = repository.getChatPrefs()) {
                is NetworkResult.Success -> {
                    val layout = result.data.popout_layout?.jsonObjectOrNull() ?: buildJsonObject { }
                    lastPopoutLayout = layout
                    _customSections.value = layout["custom_sections"]
                        ?.jsonArrayOrNull()
                        ?.mapNotNull { it.jsonObjectOrNull()?.toCustomSection() }
                        ?: emptyList()
                }
                is NetworkResult.Error -> Timber.w("loadChatPrefs failed: ${result.message}")
                is NetworkResult.Unauthorized -> Timber.w("loadChatPrefs unauthorized")
            }
        }
    }

    fun createCustomSection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        saveCustomSections(
            _customSections.value + CustomSection(
                id = UUID.randomUUID().toString(),
                name = trimmed,
            )
        )
    }

    fun renameCustomSection(sectionId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        saveCustomSections(
            _customSections.value.map { section ->
                if (section.id == sectionId) section.copy(name = trimmed) else section
            }
        )
    }

    fun deleteCustomSection(sectionId: String) {
        saveCustomSections(_customSections.value.filterNot { it.id == sectionId })
    }

    fun toggleThreadInSection(threadId: String, sectionId: String) {
        val updated = _customSections.value.map { section ->
            if (section.id != sectionId) section
            else if (threadId in section.threadIds) {
                section.copy(threadIds = section.threadIds.filterNot { it == threadId })
            } else {
                section.copy(threadIds = section.threadIds + threadId)
            }
        }
        saveCustomSections(updated)
    }

    private fun saveCustomSections(sections: List<CustomSection>) {
        viewModelScope.launch {
            val mergedLayout = JsonObject(
                lastPopoutLayout.toMutableMap().apply {
                    put("custom_sections", sections.toJsonArray())
                }
            )
            when (val result = repository.updateChatPrefs(ChatPrefsRequest(popout_layout = mergedLayout))) {
                is NetworkResult.Success -> {
                    lastPopoutLayout = mergedLayout
                    _customSections.value = sections
                }
                is NetworkResult.Error -> {
                    Timber.w("saveCustomSections failed: ${result.message}")
                    _syncError.value = result.message
                }
                is NetworkResult.Unauthorized -> _syncError.value = "Unauthorized"
            }
        }
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

    fun loadTeamThread() {
        val accountId = identity.activeAccountId.value ?: return
        viewModelScope.launch {
            repository.getTeamThread(accountId)
                .onSuccess { _teamThreadId.value = it.thread_id }
                .onFailure { e -> Timber.w(e, "loadTeamThread failed") }
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

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? =
        this as? JsonObject

    private fun kotlinx.serialization.json.JsonElement.jsonArrayOrNull(): JsonArray? =
        this as? JsonArray

    private fun JsonObject.toCustomSection(): CustomSection? {
        val id = this["id"]?.jsonPrimitive?.content ?: return null
        val name = this["name"]?.jsonPrimitive?.content ?: return null
        val threadIds = this["thread_ids"]?.jsonArrayOrNull()
            ?.mapNotNull { it.jsonPrimitive.content }
            ?: emptyList()
        return CustomSection(id = id, name = name, threadIds = threadIds)
    }

    private fun List<CustomSection>.toJsonArray(): JsonArray = buildJsonArray {
        forEach { section ->
            add(buildJsonObject {
                put("id", JsonPrimitive(section.id))
                put("name", JsonPrimitive(section.name))
                put(
                    "thread_ids",
                    buildJsonArray { section.threadIds.forEach { add(JsonPrimitive(it)) } },
                )
            })
        }
    }
}
