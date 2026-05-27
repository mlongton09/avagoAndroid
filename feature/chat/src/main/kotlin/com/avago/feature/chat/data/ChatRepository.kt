package com.avago.feature.chat.data

import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.ChatDatabaseFactory
import com.avago.core.data.db.entity.ChatAccountRosterEntity
import com.avago.core.data.db.entity.ChatMentionEntity
import com.avago.core.data.db.entity.ChatOutboxEntity
import com.avago.core.data.db.entity.ChatPresenceEntity
import com.avago.core.data.db.entity.ChatThreadLastReadEntity
import com.avago.core.data.db.entity.ChatThreadMemberEntity
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.ChatMemberResponse
import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.network.model.ChatRosterEntry
import com.avago.core.network.model.ChatThreadResponse
import com.avago.core.network.model.UserResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDbFactory: ChatDatabaseFactory,
    private val client: AvagoServiceClient,
    private val identity: IdentityManager,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---------------------------------------------------------------------------
    // Thread list
    // ---------------------------------------------------------------------------

    suspend fun observeThreads(): Flow<List<ChatThreadEntity>> {
        val accountId = identity.activeAccountId.value ?: return emptyFlow()
        return chatDbFactory.get(accountId).chatThreadDao().observeAll(accountId)
    }

    fun observeThread(threadId: String): Flow<ChatThreadEntity?> {
        val accountId = identity.activeAccountId.value ?: return emptyFlow()
        return flow {
            emitAll(chatDbFactory.get(accountId).chatThreadDao().observeById(threadId))
        }
    }

    /** Pull threads from server and upsert into local DB. */
    suspend fun syncThreads(): Result<Unit> {
        val accountId = identity.activeAccountId.value
            ?: return Result.failure(Exception("No active account"))
        return when (val result = client.getThreads()) {
            is NetworkResult.Success -> {
                val db = chatDbFactory.get(accountId)
                result.data.forEach { remote ->
                    db.chatThreadDao().upsert(remote.toEntity())
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

    suspend fun createThread(
        type: String,
        displayName: String?,
        memberIds: List<String>,
    ): Result<String> {
        val accountId = identity.activeAccountId.value
            ?: return Result.failure(Exception("No active account"))
        val result = if (type == "direct") {
            val otherId = memberIds.firstOrNull()
                ?: return Result.failure(Exception("No member specified for direct thread"))
            client.createDirectThread(otherId)
        } else {
            client.createGroupThread(name = displayName ?: "", memberIds = memberIds)
        }
        return when (result) {
            is NetworkResult.Success -> {
                val db = chatDbFactory.get(accountId)
                db.chatThreadDao().upsert(result.data.toEntity())
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

    suspend fun observeMessages(threadId: String): Flow<List<ChatMessageEntity>> {
        val accountId = identity.activeAccountId.value ?: return emptyFlow()
        return chatDbFactory.get(accountId).chatMessageDao().observeByThread(threadId)
    }

    /**
     * Fetch a page of older messages (cursor-based pagination).
     * [beforeCreatedAt] is the epoch-ms timestamp of the oldest message currently loaded.
     */
    /** Returns true if there are more pages to load (server returned a full page). */
    suspend fun loadMoreMessages(threadId: String, beforeCreatedAt: Long, limit: Int = 50): Boolean {
        val accountId = identity.activeAccountId.value ?: return false
        val beforeIso = java.time.Instant.ofEpochMilli(beforeCreatedAt).toString()
        return when (val result = client.getMessages(threadId = threadId, before = beforeIso, limit = limit)) {
            is NetworkResult.Success -> {
                val db = chatDbFactory.get(accountId)
                db.chatMessageDao().upsertAll(result.data.messages.map { it.toEntity(accountId) })
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

    /** Initial sync of a thread's messages (most-recent page). */
    suspend fun syncMessages(threadId: String) {
        val accountId = identity.activeAccountId.value ?: return
        when (val result = client.getMessages(threadId = threadId, limit = 50)) {
            is NetworkResult.Success -> {
                val db = chatDbFactory.get(accountId)
                db.chatMessageDao().upsertAll(result.data.messages.map { it.toEntity(accountId) })
            }
            is NetworkResult.Error -> Timber.w("syncMessages failed: ${result.message}")
            is NetworkResult.Unauthorized -> Timber.w("syncMessages: unauthorized")
        }
    }

    suspend fun editMessage(threadId: String, messageId: String, newBody: String): Result<Unit> {
        val accountId = identity.activeAccountId.value
            ?: return Result.failure(Exception("No active account"))
        return when (val result = client.editMessage(threadId, messageId, newBody)) {
            is NetworkResult.Success -> {
                val db = chatDbFactory.get(accountId)
                val edited = result.data
                val editedAt = edited.edited_at?.let {
                    runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
                } ?: System.currentTimeMillis()
                db.chatMessageDao().updateEdited(messageId, edited.body_md, editedAt)
                Result.success(Unit)
            }
            is NetworkResult.Error -> {
                Timber.w("editMessage failed: ${result.message}")
                Result.failure(Exception(result.message))
            }
            is NetworkResult.Unauthorized -> {
                Timber.w("editMessage: unauthorized")
                Result.failure(Exception("Unauthorized"))
            }
        }
    }

    suspend fun deleteMessage(threadId: String, messageId: String): Result<Unit> {
        val accountId = identity.activeAccountId.value
            ?: return Result.failure(Exception("No active account"))
        return when (val outcome = client.deleteMessage(threadId, messageId)) {
            is NetworkResult.Success -> {
                val db = chatDbFactory.get(accountId)
                val now = System.currentTimeMillis()
                db.chatMessageDao().getById(messageId)?.let { entity ->
                    db.chatMessageDao().upsert(entity.copy(deletedAt = now, updatedAt = now))
                }
                Result.success(Unit)
            }
            is NetworkResult.Error -> {
                Timber.w("deleteMessage failed: ${outcome.message}")
                Result.failure(Exception(outcome.message))
            }
            is NetworkResult.Unauthorized -> {
                Timber.w("deleteMessage: unauthorized")
                Result.failure(Exception("Unauthorized"))
            }
        }
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

    fun observeReplies(threadId: String, parentMessageId: String): Flow<List<ChatMessageEntity>> {
        val accountId = identity.activeAccountId.value ?: return emptyFlow()
        return flow {
            emitAll(chatDbFactory.get(accountId).chatMessageDao()
                .observeByThreadAndParent(threadId, parentMessageId))
        }
    }

    suspend fun syncReplies(threadId: String, parentMessageId: String) {
        val accountId = identity.activeAccountId.value ?: return
        when (val result = client.getReplies(threadId, parentMessageId)) {
            is NetworkResult.Success -> {
                val db = chatDbFactory.get(accountId)
                db.chatMessageDao().upsertAll(result.data.messages.map { it.toEntity(accountId) })
            }
            is NetworkResult.Error -> Timber.w("syncReplies failed: ${result.message}")
            is NetworkResult.Unauthorized -> Timber.w("syncReplies: unauthorized")
        }
    }

    suspend fun sendReply(threadId: String, parentMessageId: String, body: String): Boolean {
        val accountId = identity.activeAccountId.value ?: return false
        return when (val result = client.sendReply(threadId, parentMessageId, body)) {
            is NetworkResult.Success -> {
                chatDbFactory.get(accountId).chatMessageDao().upsert(result.data.toEntity(accountId))
                true
            }
            else -> false
        }
    }

    // ---------------------------------------------------------------------------
    // Pin / unpin
    // ---------------------------------------------------------------------------

    fun observePinnedMessage(threadId: String): Flow<ChatMessageEntity?> {
        val accountId = identity.activeAccountId.value ?: return emptyFlow()
        return flow {
            emitAll(chatDbFactory.get(accountId).chatMessageDao().observePinnedMessage(threadId))
        }
    }

    suspend fun pinMessage(threadId: String, messageId: String): Boolean {
        val accountId = identity.activeAccountId.value ?: return false
        return when (client.pinMessage(threadId, messageId)) {
            is NetworkResult.Success -> {
                chatDbFactory.get(accountId).chatMessageDao().updatePinned(messageId, true)
                true
            }
            else -> false
        }
    }

    suspend fun unpinMessage(threadId: String, messageId: String): Boolean {
        val accountId = identity.activeAccountId.value ?: return false
        return when (client.unpinMessage(threadId, messageId)) {
            is NetworkResult.Success -> {
                chatDbFactory.get(accountId).chatMessageDao().updatePinned(messageId, false)
                true
            }
            else -> false
        }
    }

    suspend fun observeMentions(username: String): Flow<List<ChatMessageEntity>> {
        val accountId = identity.activeAccountId.value ?: return emptyFlow()
        return chatDbFactory.get(accountId).chatMessageDao().observeMentions(username)
    }

    /** Upsert a message received via WebSocket realtime event. */
    suspend fun handleRealtimeMessage(msg: ChatMessageResponse) {
        val accountId = identity.activeAccountId.value ?: return
        val db = chatDbFactory.get(accountId)
        val now = System.currentTimeMillis()
        db.chatMessageDao().upsert(msg.toEntity(accountId))
        db.chatThreadDao().getById(msg.thread_id)?.let { thread ->
            db.chatThreadDao().upsert(
                thread.copy(
                    lastMessagePreview = msg.body_md.take(120),
                    lastMessageAt = runCatching {
                        java.time.Instant.parse(msg.created_at).toEpochMilli()
                    }.getOrDefault(now),
                    updatedAt = now,
                )
            )
        }
    }

    /** Mark a message as deleted in local DB when a `message.deleted` WS event arrives. */
    suspend fun handleRealtimeMessageDeleted(messageId: String) {
        val accountId = identity.activeAccountId.value ?: return
        val db = chatDbFactory.get(accountId)
        val now = System.currentTimeMillis()
        db.chatMessageDao().getById(messageId)?.let { entity ->
            db.chatMessageDao().upsert(entity.copy(deletedAt = now, updatedAt = now))
        }
    }

    /** Update pinned state in local DB when a `message.pinned/unpinned` WS event arrives. */
    suspend fun handleRealtimeMessagePinned(messageId: String, pinned: Boolean) {
        val accountId = identity.activeAccountId.value ?: return
        chatDbFactory.get(accountId).chatMessageDao().updatePinned(messageId, pinned)
    }

    /** Update the unread count on a thread when a `thread.unread_increment` WS event arrives. */
    suspend fun handleThreadUnreadIncrement(threadId: String, unreadCount: Long) {
        val accountId = identity.activeAccountId.value ?: return
        val db = chatDbFactory.get(accountId)
        val now = System.currentTimeMillis()
        db.chatThreadDao().getById(threadId)?.let { thread ->
            db.chatThreadDao().upsert(thread.copy(unreadCount = unreadCount.toInt(), updatedAt = now))
        }
    }

    // ---------------------------------------------------------------------------
    // Delta sync — cursor persistence
    // ---------------------------------------------------------------------------

    suspend fun getChatSyncCursor(accountId: String): String? {
        return chatDbFactory.get(accountId).chatSyncStateDao()
            .getValue("chat_sync_cursor_$accountId")
    }

    suspend fun saveChatSyncCursor(accountId: String, cursor: String) {
        val now = System.currentTimeMillis()
        chatDbFactory.get(accountId).chatSyncStateDao().setValue(
            com.avago.core.data.db.entity.ChatSyncStateEntity(
                key = "chat_sync_cursor_$accountId",
                value = cursor,
                updatedAt = now,
            )
        )
    }

    // ---------------------------------------------------------------------------
    // Delta sync — op applier
    // ---------------------------------------------------------------------------

    suspend fun applyChatSyncOp(accountId: String, op: com.avago.core.network.model.ChatSyncOp) {
        val payload = op.payload ?: return
        try {
            when (op.op) {
                "upsert" -> when (op.entity_type) {
                    "message" -> {
                        val msg = json.decodeFromString<com.avago.core.network.model.ChatMessageResponse>(payload)
                        handleRealtimeMessage(msg)
                    }
                    "thread" -> {
                        val thread = json.decodeFromString<com.avago.core.network.model.ChatThreadResponse>(payload)
                        chatDbFactory.get(accountId).chatThreadDao().upsert(thread.toEntity())
                    }
                    else -> Timber.d("[ChatSync] unhandled upsert entity_type=${op.entity_type}")
                }
                "delete" -> when (op.entity_type) {
                    "message" -> {
                        val msgId = json.parseToJsonElement(payload).jsonObject["message_id"]
                            ?.jsonPrimitive?.content ?: return
                        handleRealtimeMessageDeleted(msgId)
                    }
                    else -> Timber.d("[ChatSync] unhandled delete entity_type=${op.entity_type}")
                }
                else -> Timber.d("[ChatSync] unhandled op=${op.op}")
            }
        } catch (e: Exception) {
            Timber.e(e, "[ChatSync] applyChatSyncOp failed for op=${op.op} entity=${op.entity_type}")
        }
    }

    // ---------------------------------------------------------------------------
    // Reaction handling
    // ---------------------------------------------------------------------------

    suspend fun handleRealtimeReaction(messageId: String, emoji: String, userId: String, added: Boolean) {
        val accountId = identity.activeAccountId.value ?: return
        val db = chatDbFactory.get(accountId)
        val msg = db.chatMessageDao().getById(messageId) ?: return
        val now = System.currentTimeMillis()
        // Parse existing reactions JSON, apply delta, write back
        val reactions = parseReactions(msg.reactions)
        val users = reactions[emoji]?.toMutableList() ?: mutableListOf()
        if (added) { if (!users.contains(userId)) users.add(userId) }
        else users.remove(userId)
        val updated = reactions.toMutableMap()
        if (users.isEmpty()) updated.remove(emoji) else updated[emoji] = users
        val newReactionsJson = buildReactionsJson(updated)
        db.chatMessageDao().upsert(msg.copy(reactions = newReactionsJson, updatedAt = now))
    }

    private fun parseReactions(raw: String?): Map<String, List<String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            obj.entries.associate { (emoji, ids) ->
                emoji to ids.jsonObject.keys.toList()
            }
        } catch (_: Exception) { emptyMap() }
    }

    private fun buildReactionsJson(reactions: Map<String, List<String>>): String {
        val sb = StringBuilder("{")
        reactions.entries.forEachIndexed { i, (emoji, users) ->
            if (i > 0) sb.append(",")
            sb.append("\"$emoji\":{")
            users.forEachIndexed { j, uid ->
                if (j > 0) sb.append(",")
                sb.append("\"$uid\":true")
            }
            sb.append("}")
        }
        sb.append("}")
        return sb.toString()
    }

    // ---------------------------------------------------------------------------
    // Thread event handlers
    // ---------------------------------------------------------------------------

    suspend fun handleRealtimeThreadCreated(threadId: String) {
        val accountId = identity.activeAccountId.value ?: return
        when (val result = client.getThread(threadId)) {
            is NetworkResult.Success -> {
                chatDbFactory.get(accountId).chatThreadDao().upsert(result.data.toEntity())
            }
            else -> Timber.w("handleRealtimeThreadCreated: failed to fetch thread $threadId")
        }
    }

    suspend fun handleRealtimeThreadArchived(threadId: String) {
        val accountId = identity.activeAccountId.value ?: return
        chatDbFactory.get(accountId).chatThreadDao()
            .updateArchived(threadId, true, System.currentTimeMillis())
    }

    suspend fun handleRealtimeThreadRenamed(threadId: String, newName: String) {
        val accountId = identity.activeAccountId.value ?: return
        chatDbFactory.get(accountId).chatThreadDao()
            .updateDisplayName(threadId, newName, System.currentTimeMillis())
    }

    suspend fun handleRealtimeNotifPrefChanged(threadId: String, pref: String) {
        val accountId = identity.activeAccountId.value ?: return
        chatDbFactory.get(accountId).chatThreadDao()
            .updateNotificationPref(threadId, pref, System.currentTimeMillis())
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
            lastMessageAt = last_activity_at?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            },
            unreadCount = unread_count,
            subjectSummary = subject_summary?.toString(),
            serverVersion = 0,
            deletedAt = null,
            isFavorite = is_favorite,
            isArchived = false, // server doesn't send is_archived in list endpoint
            createdAt = runCatching { java.time.Instant.parse(created_at).toEpochMilli() }.getOrDefault(now),
            updatedAt = now,
        )
    }

    // ---------------------------------------------------------------------------
    // Account roster (for @mention autocomplete)
    // ---------------------------------------------------------------------------

    fun observeRoster(accountId: String): Flow<List<ChatAccountRosterEntity>> = flow {
        emitAll(chatDbFactory.get(accountId).chatAccountRosterDao().observeAll(accountId))
    }

    suspend fun syncRoster() {
        val accountId = identity.activeAccountId.value ?: return
        when (val result = client.getChatRoster()) {
            is NetworkResult.Success -> {
                val db = chatDbFactory.get(accountId)
                val entities = result.data.map { it.toRosterEntity(accountId) }
                db.chatAccountRosterDao().upsertAll(entities)
            }
            is NetworkResult.Error -> Timber.w("syncRoster failed: ${result.message}")
            is NetworkResult.Unauthorized -> Timber.w("syncRoster: unauthorized")
            else -> {}
        }
    }

    // ---------------------------------------------------------------------------
    // Thread members
    // ---------------------------------------------------------------------------

    fun observeThreadMembers(threadId: String): Flow<List<ChatThreadMemberEntity>> {
        val accountId = identity.activeAccountId.value ?: return emptyFlow()
        return flow {
            emitAll(chatDbFactory.get(accountId).chatThreadMemberDao().observeByThread(threadId))
        }
    }

    suspend fun syncThreadMembers(threadId: String) {
        val accountId = identity.activeAccountId.value ?: return
        when (val result = client.getThreadMembers(threadId)) {
            is NetworkResult.Success -> {
                val db = chatDbFactory.get(accountId)
                val entities = result.data.map { member ->
                    ChatThreadMemberEntity(
                        threadId = threadId,
                        userId = member.user_id,
                        accountId = accountId,
                        displayName = member.display_name,
                        role = member.role,
                        joinedAt = null,
                        leftAt = null,
                        isMuted = false,
                    )
                }
                db.chatThreadMemberDao().upsertAll(entities)
            }
            is NetworkResult.Error -> Timber.w("syncThreadMembers failed: ${result.message}")
            is NetworkResult.Unauthorized -> Timber.w("syncThreadMembers: unauthorized")
            else -> {}
        }
    }

    // ---------------------------------------------------------------------------
    // Mentions inbox
    // ---------------------------------------------------------------------------

    fun observeUnreadMentionCount(): Flow<Int> {
        val accountId = identity.activeAccountId.value ?: return emptyFlow()
        return flow {
            emitAll(chatDbFactory.get(accountId).chatMentionDao().observeUnreadCount(accountId))
        }
    }

    fun observeUnreadMentions(): Flow<List<ChatMentionEntity>> {
        val accountId = identity.activeAccountId.value ?: return emptyFlow()
        return flow {
            emitAll(chatDbFactory.get(accountId).chatMentionDao().observeUnread(accountId))
        }
    }

    suspend fun markMentionRead(mentionId: String) {
        val accountId = identity.activeAccountId.value ?: return
        chatDbFactory.get(accountId).chatMentionDao().markRead(mentionId)
    }

    suspend fun markAllMentionsReadForThread(threadId: String) {
        val accountId = identity.activeAccountId.value ?: return
        chatDbFactory.get(accountId).chatMentionDao().markAllReadForThread(threadId)
    }

    // ---------------------------------------------------------------------------
    // Thread last-read tracking
    // ---------------------------------------------------------------------------

    suspend fun markThreadRead(threadId: String, lastMessageId: String) {
        val accountId = identity.activeAccountId.value ?: return
        val userId = identity.getActiveUserId() ?: return
        val now = System.currentTimeMillis()
        val db = chatDbFactory.get(accountId)
        db.chatThreadLastReadDao().upsert(
            ChatThreadLastReadEntity(
                threadId = threadId,
                userId = userId,
                accountId = accountId,
                lastReadMessageId = lastMessageId,
                lastReadAt = now,
                updatedAt = now,
            )
        )
        // Zero out the local unread count on the thread entity
        db.chatThreadDao().getById(threadId)?.let { thread ->
            db.chatThreadDao().upsert(thread.copy(unreadCount = 0, updatedAt = now))
        }
    }

    // ---------------------------------------------------------------------------
    // Outbox — persistent send queue
    // ---------------------------------------------------------------------------

    suspend fun enqueueOutbox(accountId: String, threadId: String, senderId: String, body: String): String {
        val localId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        chatDbFactory.get(accountId).chatOutboxDao().upsert(
            ChatOutboxEntity(
                localId = localId,
                threadId = threadId,
                accountId = accountId,
                senderId = senderId,
                bodyMd = body,
                photoLocalPath = null,
                parentMessageId = null,
                status = "pending",
                attempts = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        return localId
    }

    suspend fun markOutboxSent(localId: String) {
        val accountId = identity.activeAccountId.value ?: return
        chatDbFactory.get(accountId).chatOutboxDao().delete(localId)
    }

    suspend fun markOutboxFailed(localId: String, error: String) {
        val accountId = identity.activeAccountId.value ?: return
        chatDbFactory.get(accountId).chatOutboxDao().updateStatus(
            localId = localId,
            status = "failed",
            error = error,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun getPendingOutbox(): List<ChatOutboxEntity> {
        val accountId = identity.activeAccountId.value ?: return emptyList()
        return chatDbFactory.get(accountId).chatOutboxDao().getPendingAndFailed()
    }

    // ---------------------------------------------------------------------------
    // Presence
    // ---------------------------------------------------------------------------

    suspend fun updatePresence(userId: String, status: String) {
        val accountId = identity.activeAccountId.value ?: return
        val now = System.currentTimeMillis()
        chatDbFactory.get(accountId).chatPresenceDao().upsert(
            ChatPresenceEntity(
                userId = userId,
                accountId = accountId,
                status = status,
                lastSeenAt = now,
                updatedAt = now,
            )
        )
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

    private fun ChatMessageResponse.toEntity(accountId: String): ChatMessageEntity {
        val now = System.currentTimeMillis()
        return ChatMessageEntity(
            messageId = message_id,
            threadId = thread_id,
            accountId = accountId,
            senderId = author_id ?: author?.id ?: "",
            senderName = author?.display_name,
            bodyMd = body_md,
            bodyPreview = body_md.take(120),
            editedAt = edited_at?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            },
            linkPreviewTitle = link_preview?.title,
            linkPreviewDescription = link_preview?.description,
            linkPreviewImageUrl = link_preview?.image_url,
            linkPreviewUrl = link_preview?.url,
            photoUrl = photo_url,
            reactions = reactions,
            outboxStatus = null,
            serverVersion = server_version,
            deletedAt = null,
            createdAt = runCatching { java.time.Instant.parse(created_at).toEpochMilli() }.getOrDefault(now),
            updatedAt = runCatching { java.time.Instant.parse(updated_at).toEpochMilli() }.getOrDefault(now),
            parentMessageId = parent_message_id,
            isPinned = is_pinned,
        )
    }
}
