package com.avago.feature.chat.data

import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.ChatDatabaseFactory
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.network.model.ChatThreadResponse
import com.avago.core.network.model.CreateThreadRequest
import com.avago.core.network.model.UserResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDbFactory: ChatDatabaseFactory,
    private val client: AvagoServiceClient,
    private val identity: IdentityManager,
) {

    // ---------------------------------------------------------------------------
    // Thread list
    // ---------------------------------------------------------------------------

    suspend fun observeThreads(): Flow<List<ChatThreadEntity>> {
        val accountId = identity.activeAccountId.value ?: return emptyFlow()
        return chatDbFactory.get(accountId).chatThreadDao().observeAll(accountId)
    }

    /** Pull threads from server and upsert into local DB. */
    suspend fun syncThreads(): Result<Unit> {
        val accountId = identity.activeAccountId.value
            ?: return Result.failure(Exception("No active account"))
        return when (val result = client.getThreads(accountId)) {
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
        return when (val result = client.createThread(
            accountId,
            CreateThreadRequest(thread_type = type, display_name = displayName, member_ids = memberIds),
        )) {
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
    suspend fun loadMoreMessages(threadId: String, beforeCreatedAt: Long, limit: Int = 50) {
        val accountId = identity.activeAccountId.value ?: return
        // Convert epoch ms to ISO-8601 for the server cursor parameter.
        val beforeIso = java.time.Instant.ofEpochMilli(beforeCreatedAt).toString()
        when (val result = client.getMessages(threadId = threadId, before = beforeIso, limit = limit)) {
            is NetworkResult.Success -> {
                val db = chatDbFactory.get(accountId)
                db.chatMessageDao().upsertAll(result.data.messages.map { it.toEntity(accountId) })
            }
            is NetworkResult.Error -> Timber.w("loadMoreMessages failed: ${result.message}")
            is NetworkResult.Unauthorized -> Timber.w("loadMoreMessages: unauthorized")
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
        return when (client.deleteMessage(threadId, messageId)) {
            is NetworkResult.Success -> {
                val db = chatDbFactory.get(accountId)
                val now = System.currentTimeMillis()
                db.chatMessageDao().getById(messageId)?.let { entity ->
                    db.chatMessageDao().upsert(entity.copy(deletedAt = now, updatedAt = now))
                }
                Result.success(Unit)
            }
            is NetworkResult.Error -> {
                Timber.w("deleteMessage failed: ${result.message}")
                Result.failure(Exception(result.message))
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

    /** Upsert a message received via SSE (real-time). */
    suspend fun handleRealtimeMessage(msg: ChatMessageResponse) {
        val accountId = identity.activeAccountId.value ?: return
        val db = chatDbFactory.get(accountId)
        val now = System.currentTimeMillis()
        db.chatMessageDao().upsert(msg.toEntity(accountId))
        // Update thread's last preview.
        db.chatThreadDao().getById(msg.thread_id)?.let { thread ->
            db.chatThreadDao().upsert(
                thread.copy(
                    lastMessagePreview = msg.body_preview ?: msg.body_md.take(120),
                    lastMessageAt = msg.created_at.let {
                        runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrDefault(now)
                    },
                    updatedAt = now,
                )
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Mapping helpers
    // ---------------------------------------------------------------------------

    private fun ChatThreadResponse.toEntity(): ChatThreadEntity {
        val now = System.currentTimeMillis()
        return ChatThreadEntity(
            threadId = thread_id,
            accountId = account_id,
            threadType = thread_type,
            displayName = display_name,
            lastMessagePreview = last_message_preview,
            lastMessageAt = last_message_at?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            },
            unreadCount = unread_count,
            subjectSummary = subject_summary,
            serverVersion = server_version,
            deletedAt = null,
            createdAt = runCatching { java.time.Instant.parse(created_at).toEpochMilli() }.getOrDefault(now),
            updatedAt = runCatching { java.time.Instant.parse(updated_at).toEpochMilli() }.getOrDefault(now),
        )
    }

    private fun ChatMessageResponse.toEntity(accountId: String): ChatMessageEntity {
        val now = System.currentTimeMillis()
        return ChatMessageEntity(
            messageId = message_id,
            threadId = thread_id,
            accountId = accountId,
            senderId = sender_id,
            senderName = sender_name,
            bodyMd = body_md,
            bodyPreview = body_preview,
            editedAt = edited_at?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            },
            linkPreviewTitle = link_preview_title,
            linkPreviewDescription = link_preview_description,
            linkPreviewImageUrl = link_preview_image_url,
            linkPreviewUrl = link_preview_url,
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
