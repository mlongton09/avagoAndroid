package com.avago.feature.chat.data

import com.avago.core.auth.IdentityManager
import com.avago.core.data.chat.ChatLocalStore
import com.avago.core.data.chat.ChatStore
import com.avago.core.data.db.entity.ChatAccountRosterEntity
import com.avago.core.data.db.entity.ChatMentionEntity
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.data.db.entity.ChatPresenceEntity
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.core.data.db.entity.ChatThreadMemberEntity
import com.avago.core.data.v2.V2MediaType
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.network.model.ChatPrefsRequest
import com.avago.core.network.model.ChatPrefsResponse
import com.avago.core.network.model.ChatRosterEntry
import com.avago.core.network.model.ChatThreadResponse
import com.avago.core.network.model.LinkPreviewResponse
import com.avago.core.network.model.UserResponse
import com.avago.feature.chat.data.ChatMessageMapping.buildJsonStringArray
import com.avago.feature.chat.data.ChatMessageMapping.buildReactionCountsJson
import com.avago.feature.chat.data.ChatMessageMapping.epochMillisOrNull
import com.avago.feature.chat.data.ChatMessageMapping.toEntity
import com.avago.feature.chat.realtime.OutboxRetryCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chat for the active account: REST and realtime results are cached in the account's chat file
 * (`chat-<user>-<account>.sqlite`, through [ChatLocalStore]) and writes go through the ordered
 * outbox of [OutboxRetryCoordinator]. Every observation follows account switches and is empty
 * while the account's stores are not open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ChatRepository @Inject constructor(
    private val chatStores: ChatLocalStore,
    private val outbox: OutboxRetryCoordinator,
    private val client: AvagoServiceClient,
    private val identity: IdentityManager,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _presence = MutableStateFlow<Map<String, ChatPresenceEntity>>(emptyMap())

    /** Last realtime presence per user id (online/offline), for the active session only. */
    val presence: StateFlow<Map<String, ChatPresenceEntity>> = _presence.asStateFlow()

    private suspend fun store(accountId: String? = identity.activeAccountId.value): ChatStore? =
        accountId?.let { chatStores.forAccount(it) }

    private fun <T> observeActive(empty: T, query: (ChatStore) -> Flow<T>): Flow<T> =
        identity.activeAccountId.flatMapLatest { accountId -> chatStores.observeWith(accountId, empty, query) }

    // ---------------------------------------------------------------------------
    // Thread list
    // ---------------------------------------------------------------------------

    fun observeThreads(): Flow<List<ChatThreadEntity>> = observeActive(emptyList()) { it.cache.observeThreads() }

    fun observeThread(threadId: String): Flow<ChatThreadEntity?> =
        observeActive(null) { it.cache.observeThread(threadId) }

    /** Pull threads from server and upsert into the chat cache. */
    suspend fun syncThreads(): Result<Unit> {
        val store = store() ?: return Result.failure(Exception("No active account"))
        return when (val result = client.getThreads()) {
            is NetworkResult.Success -> {
                result.data.forEach { remote ->
                    val existing = store.cache.thread(remote.thread_id)
                    store.cache.putThread(
                        remote.toEntity().copy(
                            // Preserve local-only fields that the list endpoint doesn't return.
                            notificationPref = existing?.notificationPref,
                            isArchived = existing?.isArchived ?: false,
                        )
                    )
                }
                Result.success(Unit)
            }
            is NetworkResult.Error -> {
                Timber.w("syncThreads failed: ${result.message}")
                Result.failure(Exception(result.message))
            }
            is NetworkResult.Unauthorized -> {
                Timber.w("syncThreads: unauthorized")
                Result.failure(Exception("Unauthorized"))
            }
        }
    }

    suspend fun getChatPrefs(): NetworkResult<ChatPrefsResponse> =
        client.getChatPrefs()

    suspend fun updateChatPrefs(request: ChatPrefsRequest): NetworkResult<Unit> =
        client.updateChatPrefs(request)

    suspend fun createThread(
        type: String,
        displayName: String?,
        memberIds: List<String>,
    ): Result<String> {
        val store = store() ?: return Result.failure(Exception("No active account"))
        val result = if (type == "direct") {
            val otherId = memberIds.firstOrNull()
                ?: return Result.failure(Exception("No member specified for direct thread"))
            client.createDirectThread(otherId)
        } else {
            client.createGroupThread(name = displayName ?: "", memberIds = memberIds)
        }
        return when (result) {
            is NetworkResult.Success -> {
                store.cache.putThread(result.data.toEntity())
                Result.success(result.data.thread_id)
            }
            is NetworkResult.Error -> {
                Timber.w("createThread failed: ${result.message}")
                Result.failure(Exception(result.message))
            }
            is NetworkResult.Unauthorized -> {
                Timber.w("createThread: unauthorized")
                Result.failure(Exception("Unauthorized"))
            }
        }
    }

    suspend fun getThreadMembers(): List<UserResponse> {
        val accountId = identity.activeAccountId.value ?: return emptyList()
        return runCatching { client.getMembers(accountId) }.getOrDefault(emptyList())
    }

    // ---------------------------------------------------------------------------
    // Messages
    // ---------------------------------------------------------------------------

    fun observeMessages(threadId: String): Flow<List<ChatMessageEntity>> =
        observeActive(emptyList()) { it.cache.observeMessages(threadId) }

    /**
     * Fetch a page of older messages (cursor-based pagination).
     * [beforeCreatedAt] is the epoch-ms timestamp of the oldest message currently loaded.
     * Returns true if there are more pages to load (server returned a full page).
     */
    suspend fun loadMoreMessages(threadId: String, beforeCreatedAt: Long, limit: Int = 50): Boolean {
        val store = store() ?: return false
        val beforeIso = java.time.Instant.ofEpochMilli(beforeCreatedAt).toString()
        return when (val result = client.getMessages(threadId = threadId, before = beforeIso, limit = limit)) {
            is NetworkResult.Success -> {
                cacheMessages(store, result.data.messages)
                result.data.messages.size >= limit
            }
            is NetworkResult.Error -> {
                Timber.w("loadMoreMessages failed: ${result.message}")
                false
            }
            is NetworkResult.Unauthorized -> {
                Timber.w("loadMoreMessages: unauthorized")
                false
            }
        }
    }

    /** Returns the most recent non-deleted message in a thread from the chat cache (no network). */
    suspend fun getLastMessage(threadId: String): ChatMessageEntity? = store()?.cache?.lastMessage(threadId)

    /** Initial sync of a thread's messages (most-recent page). */
    suspend fun syncMessages(threadId: String) {
        val store = store() ?: return
        when (val result = client.getMessages(threadId = threadId, limit = 50)) {
            is NetworkResult.Success -> cacheMessages(store, result.data.messages)
            is NetworkResult.Error -> Timber.w("syncMessages failed: ${result.message}")
            is NetworkResult.Unauthorized -> Timber.w("syncMessages: unauthorized")
        }
    }

    /** Edits a message through the outbox: shown at once, delivered after every earlier write. */
    suspend fun editMessage(threadId: String, messageId: String, newBody: String): Result<Unit> {
        val accountId = identity.activeAccountId.value
            ?: return Result.failure(Exception("No active account"))
        return runCatching { outbox.edit(accountId, threadId, messageId, newBody) }
            .onFailure { Timber.w(it, "editMessage failed") }
    }

    /** Deletes a message through the outbox: hidden at once, delivered after every earlier write. */
    suspend fun deleteMessage(threadId: String, messageId: String): Result<Unit> {
        val accountId = identity.activeAccountId.value
            ?: return Result.failure(Exception("No active account"))
        return runCatching { outbox.delete(accountId, threadId, messageId) }
            .onFailure { Timber.w(it, "deleteMessage failed") }
    }

    suspend fun reactToMessage(threadId: String, messageId: String, emoji: String): Boolean {
        return when (client.reactToMessage(threadId, messageId, emoji)) {
            is NetworkResult.Success -> true
            else -> false
        }
    }

    // ---------------------------------------------------------------------------
    // Subthread replies
    // ---------------------------------------------------------------------------

    fun observeReplies(threadId: String, parentMessageId: String): Flow<List<ChatMessageEntity>> =
        observeActive(emptyList()) { it.cache.observeReplies(threadId, parentMessageId) }

    suspend fun syncReplies(threadId: String, parentMessageId: String) {
        val store = store() ?: return
        when (val result = client.getReplies(threadId, parentMessageId)) {
            is NetworkResult.Success -> cacheMessages(store, result.data.messages)
            is NetworkResult.Error -> Timber.w("syncReplies failed: ${result.message}")
            is NetworkResult.Unauthorized -> Timber.w("syncReplies: unauthorized")
        }
    }

    /** Queues a subthread reply through the outbox; false when the chat store is not open. */
    suspend fun sendReply(threadId: String, parentMessageId: String, body: String): Boolean {
        val accountId = identity.activeAccountId.value ?: return false
        return runCatching { outbox.sendReply(accountId, threadId, parentMessageId, body) }
            .onFailure { Timber.w(it, "sendReply failed") }
            .isSuccess
    }

    // ---------------------------------------------------------------------------
    // Pin / unpin
    // ---------------------------------------------------------------------------

    fun observePinnedMessage(threadId: String): Flow<ChatMessageEntity?> =
        observeActive(null) { it.cache.observePinnedMessage(threadId) }

    suspend fun pinMessage(threadId: String, messageId: String): Boolean {
        val store = store() ?: return false
        return when (client.pinMessage(threadId, messageId)) {
            is NetworkResult.Success -> {
                store.cache.updateMessage(messageId) { it.copy(isPinned = true) }
                true
            }
            else -> false
        }
    }

    suspend fun unpinMessage(threadId: String, messageId: String): Boolean {
        val store = store() ?: return false
        return when (client.unpinMessage(threadId, messageId)) {
            is NetworkResult.Success -> {
                store.cache.updateMessage(messageId) { it.copy(isPinned = false) }
                true
            }
            else -> false
        }
    }

    fun observeMentions(username: String): Flow<List<ChatMessageEntity>> =
        observeActive(emptyList()) { it.cache.observeMessagesMentioning(username) }

    /** Upsert a message received via WebSocket realtime event or delta sync. */
    suspend fun handleRealtimeMessage(msg: ChatMessageResponse) {
        val store = store() ?: return
        cacheRealtimeMessage(store, msg)
    }

    private suspend fun cacheRealtimeMessage(store: ChatStore, msg: ChatMessageResponse) {
        val now = System.currentTimeMillis()
        cacheMessages(store, listOf(msg))
        store.cache.updateThread(msg.thread_id) { thread ->
            thread.copy(
                lastMessagePreview = msg.body_md.take(120),
                lastMessageAt = epochMillisOrNull(msg.created_at) ?: now,
                updatedAt = now,
            )
        }
    }

    /**
     * Caches [messages] and writes a `mentions_of_me` row for any message where the current user
     * appears in `mentioned_user_ids` and is not the author.
     *
     * Mirrors iOS ChatStore.upsertMessage, which performs this side-effect on EVERY message upsert
     * path (REST pull, delta sync, WebSocket realtime). Without it, the chat-tab unread @-mention
     * badge stays at 0 until the user happens to be online with the WebSocket connected when a
     * mention arrives. The mention row is keyed by messageId, so re-caching is idempotent.
     */
    private suspend fun cacheMessages(store: ChatStore, messages: List<ChatMessageResponse>) {
        store.cache.putMessages(messages.map { it.toEntity(store.accountId) })
        val myUserId = identity.getActiveUserId() ?: return
        val now = System.currentTimeMillis()
        val mentions = messages.filter { msg ->
            val isOwnMessage = msg.author_id == myUserId || msg.author?.id == myUserId
            msg.mentioned_user_ids.contains(myUserId) && !isOwnMessage
        }.map { msg ->
            ChatMentionEntity(
                mentionId = msg.message_id,
                threadId = msg.thread_id,
                messageId = msg.message_id,
                accountId = store.accountId,
                mentionedBy = msg.author_id ?: msg.author?.id,
                isRead = false,
                createdAt = epochMillisOrNull(msg.created_at) ?: now,
            )
        }
        if (mentions.isNotEmpty()) store.cache.putMentions(mentions)
    }

    /** Mark a message as deleted in the chat cache when a `message.deleted` WS event arrives. */
    suspend fun handleRealtimeMessageDeleted(messageId: String) {
        val store = store() ?: return
        markMessageDeleted(store, messageId)
    }

    private suspend fun markMessageDeleted(store: ChatStore, messageId: String) {
        val now = System.currentTimeMillis()
        store.cache.updateMessage(messageId) { it.copy(deletedAt = now, updatedAt = now) }
    }

    /** Update pinned state in the chat cache when a `message.pinned/unpinned` WS event arrives. */
    suspend fun handleRealtimeMessagePinned(messageId: String, pinned: Boolean) {
        store()?.cache?.updateMessage(messageId) { it.copy(isPinned = pinned) }
    }

    /** Update the unread count on a thread when a `thread.unread_increment` WS event arrives. */
    suspend fun handleThreadUnreadIncrement(threadId: String, unreadCount: Long) {
        val now = System.currentTimeMillis()
        store()?.cache?.updateThread(threadId) { it.copy(unreadCount = unreadCount.toInt(), updatedAt = now) }
    }

    // ---------------------------------------------------------------------------
    // Delta sync — cursor persistence
    // ---------------------------------------------------------------------------

    suspend fun getChatSyncCursor(accountId: String): String? = store(accountId)?.cache?.restSyncCursor()

    suspend fun saveChatSyncCursor(accountId: String, cursor: String) {
        store(accountId)?.cache?.putRestSyncCursor(cursor)
    }

    // ---------------------------------------------------------------------------
    // Delta sync — op applier
    // ---------------------------------------------------------------------------

    // Server op shape: {"kind": "thread.upserted"|"message.created"|"message.updated"|"message.deleted", ...}
    suspend fun applyChatSyncOp(accountId: String, op: com.avago.core.network.model.ChatSyncOp) {
        try {
            val store = store(accountId) ?: return
            when (op.kind) {
                "thread.upserted" -> {
                    val thread = op.thread?.let { json.decodeFromJsonElement<ChatThreadResponse>(it) } ?: return
                    val existing = store.cache.thread(thread.thread_id)
                    val newEntity = thread.toEntity()
                    store.cache.putThread(
                        newEntity.copy(
                            notificationPref = existing?.notificationPref,
                            // Bootstrap payload includes is_archived; preserve if already set locally.
                            isArchived = newEntity.isArchived || (existing?.isArchived ?: false),
                            // Bootstrap payload lacks name/members for direct/group threads; keep
                            // the display name already resolved by syncThreads() if available.
                            displayName = newEntity.displayName ?: existing?.displayName,
                        )
                    )
                }
                "message.created", "message.updated" -> {
                    val msg = op.message?.let { json.decodeFromJsonElement<ChatMessageResponse>(it) } ?: return
                    cacheRealtimeMessage(store, msg)
                }
                "message.deleted" -> markMessageDeleted(store, op.message_id ?: return)
                else -> Timber.d("[ChatSync] unhandled kind=${op.kind}")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[ChatSync] applyChatSyncOp failed for kind=${op.kind}")
        }
    }

    // ---------------------------------------------------------------------------
    // Reaction handling
    // ---------------------------------------------------------------------------

    suspend fun handleRealtimeReaction(messageId: String, emoji: String, userId: String, added: Boolean) {
        val store = store() ?: return
        val myUserId = identity.getActiveUserId()
        val now = System.currentTimeMillis()
        store.cache.updateMessage(messageId) { msg ->
            val counts = parseReactionCounts(msg.reactionCounts).toMutableMap()
            val current = counts[emoji] ?: 0
            if (added) {
                counts[emoji] = current + 1
            } else if (current > 1) {
                counts[emoji] = current - 1
            } else {
                counts.remove(emoji)
            }
            val newMyReactions = if (userId == myUserId) {
                val mine = parseJsonStringArray(msg.myReactions).toMutableList()
                if (added) {
                    if (!mine.contains(emoji)) mine.add(emoji)
                } else {
                    mine.remove(emoji)
                }
                buildJsonStringArray(mine)
            } else {
                msg.myReactions
            }
            msg.copy(reactionCounts = buildReactionCountsJson(counts), myReactions = newMyReactions, updatedAt = now)
        }
    }

    private fun parseReactionCounts(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.parseToJsonElement(raw).jsonObject.entries
                .associate { (k, v) -> k to v.jsonPrimitive.int }
        } catch (_: Exception) { emptyMap() }
    }

    private fun parseJsonStringArray(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.parseToJsonElement(raw).jsonArray.map { it.jsonPrimitive.content }
        } catch (_: Exception) { emptyList() }
    }

    // ---------------------------------------------------------------------------
    // Thread event handlers
    // ---------------------------------------------------------------------------

    suspend fun handleRealtimeThreadCreated(threadId: String) {
        val store = store() ?: return
        when (val result = client.getThread(threadId)) {
            is NetworkResult.Success -> store.cache.putThread(result.data.toEntity())
            else -> Timber.w("handleRealtimeThreadCreated: failed to fetch thread $threadId")
        }
    }

    suspend fun handleRealtimeThreadArchived(threadId: String) {
        val now = System.currentTimeMillis()
        store()?.cache?.updateThread(threadId) { it.copy(isArchived = true, updatedAt = now) }
    }

    suspend fun handleRealtimeThreadRenamed(threadId: String, newName: String) {
        val now = System.currentTimeMillis()
        store()?.cache?.updateThread(threadId) { it.copy(displayName = newName, updatedAt = now) }
    }

    suspend fun handleRealtimeNotifPrefChanged(threadId: String, pref: String) {
        val now = System.currentTimeMillis()
        store()?.cache?.updateThread(threadId) { it.copy(notificationPref = pref, updatedAt = now) }
    }

    suspend fun markDelivered(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        when (val result = client.markMessagesDelivered(messageIds)) {
            is NetworkResult.Error -> Timber.w("markDelivered failed: ${result.message}")
            else -> {}
        }
    }

    // ---------------------------------------------------------------------------
    // Mapping helpers
    // ---------------------------------------------------------------------------

    private fun ChatThreadResponse.toEntity(): ChatThreadEntity {
        val now = System.currentTimeMillis()
        val myUserId = identity.getActiveUserId()
        // For direct threads show the other participant; for group use the thread name or member list.
        val resolvedDisplayName = when (thread_type) {
            "direct" -> members.firstOrNull { it.user_id != myUserId }?.display_name ?: name
            "group" -> name ?: members.joinToString(", ") { it.display_name ?: "?" }.ifBlank { null }
            else -> name
        }
        return ChatThreadEntity(
            threadId = thread_id,
            accountId = account_id,
            threadType = thread_type,
            displayName = resolvedDisplayName,
            lastMessagePreview = last_message_preview,
            lastMessageAt = last_activity_at?.let(::epochMillisOrNull),
            unreadCount = unread_count,
            subjectSummary = subject_summary?.toString(),
            serverVersion = 0,
            deletedAt = null,
            isFavorite = is_favorite,
            isArchived = false, // server doesn't send is_archived in list endpoint
            createdAt = epochMillisOrNull(created_at) ?: now,
            updatedAt = now,
        )
    }

    // ---------------------------------------------------------------------------
    // Account roster (for @mention autocomplete)
    // ---------------------------------------------------------------------------

    fun observeRoster(accountId: String): Flow<List<ChatAccountRosterEntity>> =
        chatStores.observeWith(accountId, emptyList()) { it.cache.observeRoster() }

    suspend fun syncRoster() {
        val store = store() ?: return
        when (val result = client.getChatRoster()) {
            is NetworkResult.Success -> store.cache.putRoster(result.data.map { it.toRosterEntity(store.accountId) })
            is NetworkResult.Error -> Timber.w("syncRoster failed: ${result.message}")
            is NetworkResult.Unauthorized -> Timber.w("syncRoster: unauthorized")
        }
    }

    // ---------------------------------------------------------------------------
    // Thread members
    // ---------------------------------------------------------------------------

    fun observeThreadMembers(threadId: String): Flow<List<ChatThreadMemberEntity>> =
        observeActive(emptyList()) { it.cache.observeThreadMembers(threadId) }

    suspend fun syncThreadMembers(threadId: String) {
        val store = store() ?: return
        when (val result = client.getThreadMembers(threadId)) {
            is NetworkResult.Success -> {
                val entities = result.data.map { member ->
                    ChatThreadMemberEntity(
                        threadId = threadId,
                        userId = member.user_id,
                        accountId = store.accountId,
                        displayName = member.display_name,
                        role = member.role,
                        joinedAt = null,
                        leftAt = null,
                        isMuted = false,
                    )
                }
                store.cache.putThreadMembers(entities)
            }
            is NetworkResult.Error -> Timber.w("syncThreadMembers failed: ${result.message}")
            is NetworkResult.Unauthorized -> Timber.w("syncThreadMembers: unauthorized")
        }
    }

    // ---------------------------------------------------------------------------
    // Mentions inbox
    // ---------------------------------------------------------------------------

    /**
     * Observes the count of unread @-mentions for the *currently active* account, reacting to
     * account switches: per-account flows are always derived by flatMapping over
     * `identity.activeAccountId`, never by reading `.value` once, or an account switch would
     * leave the badge stuck on the previous account's count.
     */
    fun observeUnreadMentionCount(): Flow<Int> = observeActive(0) { it.cache.observeUnreadMentionCount() }

    fun observeUnreadMentions(): Flow<List<ChatMentionEntity>> =
        observeActive(emptyList()) { it.cache.observeUnreadMentions() }

    suspend fun markMentionRead(mentionId: String) {
        store()?.cache?.markMentionRead(mentionId)
    }

    suspend fun markAllMentionsReadForThread(threadId: String) {
        store()?.cache?.markAllMentionsReadForThread(threadId)
    }

    // ---------------------------------------------------------------------------
    // Thread read state
    // ---------------------------------------------------------------------------

    /** Zeroes the thread's local unread count once its latest message has been seen. */
    suspend fun markThreadRead(threadId: String) {
        val now = System.currentTimeMillis()
        store()?.cache?.updateThread(threadId) { it.copy(unreadCount = 0, updatedAt = now) }
    }

    suspend fun getTeamThread(accountId: String): Result<ChatThreadResponse> {
        return when (val r = client.getTeamThread(accountId)) {
            is NetworkResult.Success -> Result.success(r.data)
            is NetworkResult.Error -> Result.failure(Exception(r.message))
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }
    }

    /**
     * Resolve (lazy-create) the chat thread for an asset and cache it locally.
     * Backs the chat list's "Favorite Assets" + button → asset picker flow.
     */
    suspend fun resolveAssetThread(accountId: String, assetId: String): Result<ChatThreadResponse> {
        return when (val r = client.resolveAssetThread(accountId, assetId)) {
            is NetworkResult.Success -> {
                store(accountId)?.cache?.putThread(r.data.toEntity())
                Result.success(r.data)
            }
            is NetworkResult.Error -> Result.failure(Exception(r.message))
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }
    }

    // ---------------------------------------------------------------------------
    // Media
    // ---------------------------------------------------------------------------

    /**
     * Uploads [bytes] (presign + PUT, online) and then queues the message carrying the media URL
     * through the ordered outbox, so it is delivered after anything the user sent before it.
     */
    suspend fun uploadAndSendMedia(
        threadId: String,
        bytes: ByteArray,
        contentType: String,
        fileName: String? = null,
        needsReply: Boolean = false,
    ): Result<Unit> {
        val accountId = identity.activeAccountId.value
            ?: return Result.failure(Exception("No active account"))
        // Storage checks the bytes against the registered type (P3-20), so register what they are.
        val safeContentType = V2MediaType.sniff(bytes)
            ?: contentType.ifBlank { "application/octet-stream" }
        val resolvedFileName = fileName?.takeIf { it.isNotBlank() }
            ?: ChatMessageMapping.defaultUploadFileName(safeContentType)

        val presignedMedia = when (val presign = client.presignChatMedia(safeContentType, bytes.size.toLong())) {
            is NetworkResult.Success -> presign.data
            is NetworkResult.Error -> return Result.failure(Exception(presign.message))
            is NetworkResult.Unauthorized -> return Result.failure(Exception("Unauthorized"))
        }

        when (val upload = client.uploadChatMedia(presignedMedia.upload_url, bytes, safeContentType)) {
            is NetworkResult.Success -> Unit
            is NetworkResult.Error -> return Result.failure(Exception(upload.message))
            is NetworkResult.Unauthorized -> return Result.failure(Exception("Unauthorized"))
        }

        val mediaUrl = presignedMedia.media_url
        return runCatching {
            when {
                ChatMessageMapping.isAudioMedia(mediaUrl, safeContentType, resolvedFileName) ->
                    outbox.send(accountId, threadId, body = "", needsReply = needsReply, audioUrl = mediaUrl)
                ChatMessageMapping.isImageMedia(mediaUrl, safeContentType, resolvedFileName) ->
                    outbox.send(accountId, threadId, body = "", imageUrls = listOf(mediaUrl), needsReply = needsReply)
                else -> outbox.send(
                    accountId,
                    threadId,
                    body = "",
                    needsReply = needsReply,
                    attachmentUrl = mediaUrl,
                    attachmentName = resolvedFileName,
                    attachmentSize = bytes.size.toLong(),
                )
            }
            Unit
        }
    }

    // ---------------------------------------------------------------------------
    // Presence
    // ---------------------------------------------------------------------------

    fun updatePresence(userId: String, status: String) {
        val accountId = identity.activeAccountId.value ?: return
        val now = System.currentTimeMillis()
        _presence.update { current ->
            val presence = ChatPresenceEntity(
                userId = userId,
                accountId = accountId,
                status = status,
                lastSeenAt = now,
                updatedAt = now,
            )
            current + (userId to presence)
        }
    }

    suspend fun fetchLinkPreview(url: String): LinkPreviewResponse? =
        when (val result = client.fetchLinkPreview(url)) {
            is NetworkResult.Success -> result.data
            else -> null
        }

    // ---------------------------------------------------------------------------
    // Thread management actions
    // ---------------------------------------------------------------------------

    suspend fun setFavorite(threadId: String, favorite: Boolean): Result<Unit> {
        val store = store() ?: return Result.failure(Exception("No active account"))
        return when (val r = client.setThreadFavorite(threadId, favorite)) {
            is NetworkResult.Success -> {
                val now = System.currentTimeMillis()
                store.cache.updateThread(threadId) { it.copy(isFavorite = favorite, updatedAt = now) }
                Result.success(Unit)
            }
            is NetworkResult.Error -> Result.failure(Exception(r.message))
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }
    }

    suspend fun muteThread(threadId: String, muted: Boolean, untilEpochMs: Long? = null): Result<Unit> =
        when (val r = client.setThreadMute(threadId, muted, hardMute = false, until = untilEpochMs)) {
            is NetworkResult.Success -> Result.success(Unit)
            is NetworkResult.Error -> Result.failure(Exception(r.message))
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }

    suspend fun leaveThread(threadId: String): Result<Unit> =
        when (val r = client.leaveThread(threadId)) {
            is NetworkResult.Success -> Result.success(Unit)
            is NetworkResult.Error -> Result.failure(Exception(r.message))
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }

    suspend fun addMembers(threadId: String, userIds: List<String>): Result<Unit> =
        when (val r = client.addThreadMembers(threadId, userIds)) {
            is NetworkResult.Success -> {
                syncThreadMembers(threadId)
                Result.success(Unit)
            }
            is NetworkResult.Error -> Result.failure(Exception(r.message))
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }

    suspend fun removeMember(threadId: String, userId: String): Result<Unit> {
        val store = store() ?: return Result.failure(Exception("No active account"))
        return when (val r = client.removeThreadMember(threadId, userId)) {
            is NetworkResult.Success -> {
                store.cache.deleteThreadMember(threadId, userId)
                Result.success(Unit)
            }
            is NetworkResult.Error -> Result.failure(Exception(r.message))
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }
    }

    suspend fun renameGroup(threadId: String, name: String): Result<Unit> {
        val store = store() ?: return Result.failure(Exception("No active account"))
        return when (val r = client.renameGroupThread(threadId, name)) {
            is NetworkResult.Success -> {
                val now = System.currentTimeMillis()
                store.cache.updateThread(threadId) { it.copy(displayName = name, updatedAt = now) }
                Result.success(Unit)
            }
            is NetworkResult.Error -> Result.failure(Exception(r.message))
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }
    }

    suspend fun setNotificationPref(threadId: String, pref: String): Result<Unit> {
        val store = store() ?: return Result.failure(Exception("No active account"))
        return when (val r = client.setThreadNotificationPref(threadId, pref)) {
            is NetworkResult.Success -> {
                val now = System.currentTimeMillis()
                store.cache.updateThread(threadId) { it.copy(notificationPref = pref, updatedAt = now) }
                Result.success(Unit)
            }
            is NetworkResult.Error -> Result.failure(Exception(r.message))
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }
    }

    suspend fun acknowledgeMessage(messageId: String) {
        when (val r = client.acknowledgeMessage(messageId)) {
            is NetworkResult.Error -> Timber.w("acknowledgeMessage failed: ${r.message}")
            else -> {}
        }
    }

    suspend fun reportMessage(threadId: String, messageId: String): Result<Unit> =
        when (val r = client.reportMessage(threadId, messageId)) {
            is NetworkResult.Success -> Result.success(Unit)
            is NetworkResult.Error -> Result.failure(Exception(r.message))
            is NetworkResult.Unauthorized -> Result.failure(Exception("Unauthorized"))
        }

    suspend fun toggleReaction(threadId: String, messageId: String, emoji: String) {
        val store = store() ?: return
        val msg = store.cache.message(messageId) ?: return
        val me = identity.getActiveUserId() ?: ""
        if (parseJsonStringArray(msg.myReactions).contains(emoji)) {
            // Optimistic remove
            handleRealtimeReaction(messageId, emoji, me, added = false)
            when (val r = client.removeReaction(messageId, emoji)) {
                is NetworkResult.Error -> {
                    Timber.w("removeReaction failed: ${r.message}")
                    handleRealtimeReaction(messageId, emoji, me, added = true)
                }
                else -> {}
            }
        } else {
            // Optimistic add
            handleRealtimeReaction(messageId, emoji, me, added = true)
            when (val r = client.reactToMessage(threadId, messageId, emoji)) {
                is NetworkResult.Error -> {
                    Timber.w("reactToMessage failed: ${r.message}")
                    handleRealtimeReaction(messageId, emoji, me, added = false)
                }
                else -> {}
            }
        }
    }

    suspend fun getThreadSummary(threadId: String): Map<String, Any>? {
        val result = client.getThreadSummary(threadId)
        return if (result is NetworkResult.Success) result.data else null
    }

    // ---------------------------------------------------------------------------
    // Mapping helpers (roster)
    // ---------------------------------------------------------------------------

    private fun ChatRosterEntry.toRosterEntity(accountId: String) = ChatAccountRosterEntity(
        rosterId = user_id,
        accountId = accountId,
        userId = user_id,
        displayName = display_name,
        email = email,
        photoUrl = avatar_url,
        role = role,
        isActive = presence != "offline",
        updatedAt = System.currentTimeMillis(),
    )
}
