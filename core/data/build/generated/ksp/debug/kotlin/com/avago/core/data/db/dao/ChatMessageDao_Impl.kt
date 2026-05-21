package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.ChatMessageEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ChatMessageDao_Impl(
  __db: RoomDatabase,
) : ChatMessageDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfChatMessageEntity: EntityInsertAdapter<ChatMessageEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfChatMessageEntity = object : EntityInsertAdapter<ChatMessageEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `chat_messages` (`message_id`,`thread_id`,`account_id`,`sender_id`,`sender_name`,`body_md`,`body_preview`,`edited_at`,`link_preview_title`,`link_preview_description`,`link_preview_image_url`,`link_preview_url`,`photo_url`,`reactions`,`outbox_status`,`server_version`,`deleted_at`,`created_at`,`updated_at`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ChatMessageEntity) {
        statement.bindText(1, entity.messageId)
        statement.bindText(2, entity.threadId)
        statement.bindText(3, entity.accountId)
        statement.bindText(4, entity.senderId)
        val _tmpSenderName: String? = entity.senderName
        if (_tmpSenderName == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpSenderName)
        }
        statement.bindText(6, entity.bodyMd)
        val _tmpBodyPreview: String? = entity.bodyPreview
        if (_tmpBodyPreview == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpBodyPreview)
        }
        val _tmpEditedAt: Long? = entity.editedAt
        if (_tmpEditedAt == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpEditedAt)
        }
        val _tmpLinkPreviewTitle: String? = entity.linkPreviewTitle
        if (_tmpLinkPreviewTitle == null) {
          statement.bindNull(9)
        } else {
          statement.bindText(9, _tmpLinkPreviewTitle)
        }
        val _tmpLinkPreviewDescription: String? = entity.linkPreviewDescription
        if (_tmpLinkPreviewDescription == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpLinkPreviewDescription)
        }
        val _tmpLinkPreviewImageUrl: String? = entity.linkPreviewImageUrl
        if (_tmpLinkPreviewImageUrl == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpLinkPreviewImageUrl)
        }
        val _tmpLinkPreviewUrl: String? = entity.linkPreviewUrl
        if (_tmpLinkPreviewUrl == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpLinkPreviewUrl)
        }
        val _tmpPhotoUrl: String? = entity.photoUrl
        if (_tmpPhotoUrl == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpPhotoUrl)
        }
        val _tmpReactions: String? = entity.reactions
        if (_tmpReactions == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpReactions)
        }
        val _tmpOutboxStatus: String? = entity.outboxStatus
        if (_tmpOutboxStatus == null) {
          statement.bindNull(15)
        } else {
          statement.bindText(15, _tmpOutboxStatus)
        }
        statement.bindLong(16, entity.serverVersion)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(17)
        } else {
          statement.bindLong(17, _tmpDeletedAt)
        }
        statement.bindLong(18, entity.createdAt)
        statement.bindLong(19, entity.updatedAt)
      }
    }
  }

  public override suspend fun upsert(message: ChatMessageEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfChatMessageEntity.insert(_connection, message)
  }

  public override suspend fun upsertAll(messages: List<ChatMessageEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfChatMessageEntity.insert(_connection, messages)
  }

  public override fun observeByThread(threadId: String): Flow<List<ChatMessageEntity>> {
    val _sql: String =
        "SELECT * FROM chat_messages WHERE thread_id = ? AND deleted_at IS NULL ORDER BY created_at ASC"
    return createFlow(__db, false, arrayOf("chat_messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, threadId)
        val _columnIndexOfMessageId: Int = getColumnIndexOrThrow(_stmt, "message_id")
        val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "thread_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfSenderId: Int = getColumnIndexOrThrow(_stmt, "sender_id")
        val _columnIndexOfSenderName: Int = getColumnIndexOrThrow(_stmt, "sender_name")
        val _columnIndexOfBodyMd: Int = getColumnIndexOrThrow(_stmt, "body_md")
        val _columnIndexOfBodyPreview: Int = getColumnIndexOrThrow(_stmt, "body_preview")
        val _columnIndexOfEditedAt: Int = getColumnIndexOrThrow(_stmt, "edited_at")
        val _columnIndexOfLinkPreviewTitle: Int = getColumnIndexOrThrow(_stmt, "link_preview_title")
        val _columnIndexOfLinkPreviewDescription: Int = getColumnIndexOrThrow(_stmt,
            "link_preview_description")
        val _columnIndexOfLinkPreviewImageUrl: Int = getColumnIndexOrThrow(_stmt,
            "link_preview_image_url")
        val _columnIndexOfLinkPreviewUrl: Int = getColumnIndexOrThrow(_stmt, "link_preview_url")
        val _columnIndexOfPhotoUrl: Int = getColumnIndexOrThrow(_stmt, "photo_url")
        val _columnIndexOfReactions: Int = getColumnIndexOrThrow(_stmt, "reactions")
        val _columnIndexOfOutboxStatus: Int = getColumnIndexOrThrow(_stmt, "outbox_status")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<ChatMessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChatMessageEntity
          val _tmpMessageId: String
          _tmpMessageId = _stmt.getText(_columnIndexOfMessageId)
          val _tmpThreadId: String
          _tmpThreadId = _stmt.getText(_columnIndexOfThreadId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpSenderId: String
          _tmpSenderId = _stmt.getText(_columnIndexOfSenderId)
          val _tmpSenderName: String?
          if (_stmt.isNull(_columnIndexOfSenderName)) {
            _tmpSenderName = null
          } else {
            _tmpSenderName = _stmt.getText(_columnIndexOfSenderName)
          }
          val _tmpBodyMd: String
          _tmpBodyMd = _stmt.getText(_columnIndexOfBodyMd)
          val _tmpBodyPreview: String?
          if (_stmt.isNull(_columnIndexOfBodyPreview)) {
            _tmpBodyPreview = null
          } else {
            _tmpBodyPreview = _stmt.getText(_columnIndexOfBodyPreview)
          }
          val _tmpEditedAt: Long?
          if (_stmt.isNull(_columnIndexOfEditedAt)) {
            _tmpEditedAt = null
          } else {
            _tmpEditedAt = _stmt.getLong(_columnIndexOfEditedAt)
          }
          val _tmpLinkPreviewTitle: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewTitle)) {
            _tmpLinkPreviewTitle = null
          } else {
            _tmpLinkPreviewTitle = _stmt.getText(_columnIndexOfLinkPreviewTitle)
          }
          val _tmpLinkPreviewDescription: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewDescription)) {
            _tmpLinkPreviewDescription = null
          } else {
            _tmpLinkPreviewDescription = _stmt.getText(_columnIndexOfLinkPreviewDescription)
          }
          val _tmpLinkPreviewImageUrl: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewImageUrl)) {
            _tmpLinkPreviewImageUrl = null
          } else {
            _tmpLinkPreviewImageUrl = _stmt.getText(_columnIndexOfLinkPreviewImageUrl)
          }
          val _tmpLinkPreviewUrl: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewUrl)) {
            _tmpLinkPreviewUrl = null
          } else {
            _tmpLinkPreviewUrl = _stmt.getText(_columnIndexOfLinkPreviewUrl)
          }
          val _tmpPhotoUrl: String?
          if (_stmt.isNull(_columnIndexOfPhotoUrl)) {
            _tmpPhotoUrl = null
          } else {
            _tmpPhotoUrl = _stmt.getText(_columnIndexOfPhotoUrl)
          }
          val _tmpReactions: String?
          if (_stmt.isNull(_columnIndexOfReactions)) {
            _tmpReactions = null
          } else {
            _tmpReactions = _stmt.getText(_columnIndexOfReactions)
          }
          val _tmpOutboxStatus: String?
          if (_stmt.isNull(_columnIndexOfOutboxStatus)) {
            _tmpOutboxStatus = null
          } else {
            _tmpOutboxStatus = _stmt.getText(_columnIndexOfOutboxStatus)
          }
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item =
              ChatMessageEntity(_tmpMessageId,_tmpThreadId,_tmpAccountId,_tmpSenderId,_tmpSenderName,_tmpBodyMd,_tmpBodyPreview,_tmpEditedAt,_tmpLinkPreviewTitle,_tmpLinkPreviewDescription,_tmpLinkPreviewImageUrl,_tmpLinkPreviewUrl,_tmpPhotoUrl,_tmpReactions,_tmpOutboxStatus,_tmpServerVersion,_tmpDeletedAt,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(messageId: String): ChatMessageEntity? {
    val _sql: String = "SELECT * FROM chat_messages WHERE message_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, messageId)
        val _columnIndexOfMessageId: Int = getColumnIndexOrThrow(_stmt, "message_id")
        val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "thread_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfSenderId: Int = getColumnIndexOrThrow(_stmt, "sender_id")
        val _columnIndexOfSenderName: Int = getColumnIndexOrThrow(_stmt, "sender_name")
        val _columnIndexOfBodyMd: Int = getColumnIndexOrThrow(_stmt, "body_md")
        val _columnIndexOfBodyPreview: Int = getColumnIndexOrThrow(_stmt, "body_preview")
        val _columnIndexOfEditedAt: Int = getColumnIndexOrThrow(_stmt, "edited_at")
        val _columnIndexOfLinkPreviewTitle: Int = getColumnIndexOrThrow(_stmt, "link_preview_title")
        val _columnIndexOfLinkPreviewDescription: Int = getColumnIndexOrThrow(_stmt,
            "link_preview_description")
        val _columnIndexOfLinkPreviewImageUrl: Int = getColumnIndexOrThrow(_stmt,
            "link_preview_image_url")
        val _columnIndexOfLinkPreviewUrl: Int = getColumnIndexOrThrow(_stmt, "link_preview_url")
        val _columnIndexOfPhotoUrl: Int = getColumnIndexOrThrow(_stmt, "photo_url")
        val _columnIndexOfReactions: Int = getColumnIndexOrThrow(_stmt, "reactions")
        val _columnIndexOfOutboxStatus: Int = getColumnIndexOrThrow(_stmt, "outbox_status")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: ChatMessageEntity?
        if (_stmt.step()) {
          val _tmpMessageId: String
          _tmpMessageId = _stmt.getText(_columnIndexOfMessageId)
          val _tmpThreadId: String
          _tmpThreadId = _stmt.getText(_columnIndexOfThreadId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpSenderId: String
          _tmpSenderId = _stmt.getText(_columnIndexOfSenderId)
          val _tmpSenderName: String?
          if (_stmt.isNull(_columnIndexOfSenderName)) {
            _tmpSenderName = null
          } else {
            _tmpSenderName = _stmt.getText(_columnIndexOfSenderName)
          }
          val _tmpBodyMd: String
          _tmpBodyMd = _stmt.getText(_columnIndexOfBodyMd)
          val _tmpBodyPreview: String?
          if (_stmt.isNull(_columnIndexOfBodyPreview)) {
            _tmpBodyPreview = null
          } else {
            _tmpBodyPreview = _stmt.getText(_columnIndexOfBodyPreview)
          }
          val _tmpEditedAt: Long?
          if (_stmt.isNull(_columnIndexOfEditedAt)) {
            _tmpEditedAt = null
          } else {
            _tmpEditedAt = _stmt.getLong(_columnIndexOfEditedAt)
          }
          val _tmpLinkPreviewTitle: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewTitle)) {
            _tmpLinkPreviewTitle = null
          } else {
            _tmpLinkPreviewTitle = _stmt.getText(_columnIndexOfLinkPreviewTitle)
          }
          val _tmpLinkPreviewDescription: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewDescription)) {
            _tmpLinkPreviewDescription = null
          } else {
            _tmpLinkPreviewDescription = _stmt.getText(_columnIndexOfLinkPreviewDescription)
          }
          val _tmpLinkPreviewImageUrl: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewImageUrl)) {
            _tmpLinkPreviewImageUrl = null
          } else {
            _tmpLinkPreviewImageUrl = _stmt.getText(_columnIndexOfLinkPreviewImageUrl)
          }
          val _tmpLinkPreviewUrl: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewUrl)) {
            _tmpLinkPreviewUrl = null
          } else {
            _tmpLinkPreviewUrl = _stmt.getText(_columnIndexOfLinkPreviewUrl)
          }
          val _tmpPhotoUrl: String?
          if (_stmt.isNull(_columnIndexOfPhotoUrl)) {
            _tmpPhotoUrl = null
          } else {
            _tmpPhotoUrl = _stmt.getText(_columnIndexOfPhotoUrl)
          }
          val _tmpReactions: String?
          if (_stmt.isNull(_columnIndexOfReactions)) {
            _tmpReactions = null
          } else {
            _tmpReactions = _stmt.getText(_columnIndexOfReactions)
          }
          val _tmpOutboxStatus: String?
          if (_stmt.isNull(_columnIndexOfOutboxStatus)) {
            _tmpOutboxStatus = null
          } else {
            _tmpOutboxStatus = _stmt.getText(_columnIndexOfOutboxStatus)
          }
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result =
              ChatMessageEntity(_tmpMessageId,_tmpThreadId,_tmpAccountId,_tmpSenderId,_tmpSenderName,_tmpBodyMd,_tmpBodyPreview,_tmpEditedAt,_tmpLinkPreviewTitle,_tmpLinkPreviewDescription,_tmpLinkPreviewImageUrl,_tmpLinkPreviewUrl,_tmpPhotoUrl,_tmpReactions,_tmpOutboxStatus,_tmpServerVersion,_tmpDeletedAt,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPage(
    threadId: String,
    beforeCreatedAt: Long,
    limit: Int,
  ): List<ChatMessageEntity> {
    val _sql: String =
        "SELECT * FROM chat_messages WHERE thread_id = ? AND created_at < ? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, threadId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, beforeCreatedAt)
        _argIndex = 3
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfMessageId: Int = getColumnIndexOrThrow(_stmt, "message_id")
        val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "thread_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfSenderId: Int = getColumnIndexOrThrow(_stmt, "sender_id")
        val _columnIndexOfSenderName: Int = getColumnIndexOrThrow(_stmt, "sender_name")
        val _columnIndexOfBodyMd: Int = getColumnIndexOrThrow(_stmt, "body_md")
        val _columnIndexOfBodyPreview: Int = getColumnIndexOrThrow(_stmt, "body_preview")
        val _columnIndexOfEditedAt: Int = getColumnIndexOrThrow(_stmt, "edited_at")
        val _columnIndexOfLinkPreviewTitle: Int = getColumnIndexOrThrow(_stmt, "link_preview_title")
        val _columnIndexOfLinkPreviewDescription: Int = getColumnIndexOrThrow(_stmt,
            "link_preview_description")
        val _columnIndexOfLinkPreviewImageUrl: Int = getColumnIndexOrThrow(_stmt,
            "link_preview_image_url")
        val _columnIndexOfLinkPreviewUrl: Int = getColumnIndexOrThrow(_stmt, "link_preview_url")
        val _columnIndexOfPhotoUrl: Int = getColumnIndexOrThrow(_stmt, "photo_url")
        val _columnIndexOfReactions: Int = getColumnIndexOrThrow(_stmt, "reactions")
        val _columnIndexOfOutboxStatus: Int = getColumnIndexOrThrow(_stmt, "outbox_status")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<ChatMessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChatMessageEntity
          val _tmpMessageId: String
          _tmpMessageId = _stmt.getText(_columnIndexOfMessageId)
          val _tmpThreadId: String
          _tmpThreadId = _stmt.getText(_columnIndexOfThreadId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpSenderId: String
          _tmpSenderId = _stmt.getText(_columnIndexOfSenderId)
          val _tmpSenderName: String?
          if (_stmt.isNull(_columnIndexOfSenderName)) {
            _tmpSenderName = null
          } else {
            _tmpSenderName = _stmt.getText(_columnIndexOfSenderName)
          }
          val _tmpBodyMd: String
          _tmpBodyMd = _stmt.getText(_columnIndexOfBodyMd)
          val _tmpBodyPreview: String?
          if (_stmt.isNull(_columnIndexOfBodyPreview)) {
            _tmpBodyPreview = null
          } else {
            _tmpBodyPreview = _stmt.getText(_columnIndexOfBodyPreview)
          }
          val _tmpEditedAt: Long?
          if (_stmt.isNull(_columnIndexOfEditedAt)) {
            _tmpEditedAt = null
          } else {
            _tmpEditedAt = _stmt.getLong(_columnIndexOfEditedAt)
          }
          val _tmpLinkPreviewTitle: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewTitle)) {
            _tmpLinkPreviewTitle = null
          } else {
            _tmpLinkPreviewTitle = _stmt.getText(_columnIndexOfLinkPreviewTitle)
          }
          val _tmpLinkPreviewDescription: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewDescription)) {
            _tmpLinkPreviewDescription = null
          } else {
            _tmpLinkPreviewDescription = _stmt.getText(_columnIndexOfLinkPreviewDescription)
          }
          val _tmpLinkPreviewImageUrl: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewImageUrl)) {
            _tmpLinkPreviewImageUrl = null
          } else {
            _tmpLinkPreviewImageUrl = _stmt.getText(_columnIndexOfLinkPreviewImageUrl)
          }
          val _tmpLinkPreviewUrl: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewUrl)) {
            _tmpLinkPreviewUrl = null
          } else {
            _tmpLinkPreviewUrl = _stmt.getText(_columnIndexOfLinkPreviewUrl)
          }
          val _tmpPhotoUrl: String?
          if (_stmt.isNull(_columnIndexOfPhotoUrl)) {
            _tmpPhotoUrl = null
          } else {
            _tmpPhotoUrl = _stmt.getText(_columnIndexOfPhotoUrl)
          }
          val _tmpReactions: String?
          if (_stmt.isNull(_columnIndexOfReactions)) {
            _tmpReactions = null
          } else {
            _tmpReactions = _stmt.getText(_columnIndexOfReactions)
          }
          val _tmpOutboxStatus: String?
          if (_stmt.isNull(_columnIndexOfOutboxStatus)) {
            _tmpOutboxStatus = null
          } else {
            _tmpOutboxStatus = _stmt.getText(_columnIndexOfOutboxStatus)
          }
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item =
              ChatMessageEntity(_tmpMessageId,_tmpThreadId,_tmpAccountId,_tmpSenderId,_tmpSenderName,_tmpBodyMd,_tmpBodyPreview,_tmpEditedAt,_tmpLinkPreviewTitle,_tmpLinkPreviewDescription,_tmpLinkPreviewImageUrl,_tmpLinkPreviewUrl,_tmpPhotoUrl,_tmpReactions,_tmpOutboxStatus,_tmpServerVersion,_tmpDeletedAt,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeFailedOutbox(): Flow<List<ChatMessageEntity>> {
    val _sql: String =
        "SELECT * FROM chat_messages WHERE outbox_status = 'failed' AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("chat_messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfMessageId: Int = getColumnIndexOrThrow(_stmt, "message_id")
        val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "thread_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfSenderId: Int = getColumnIndexOrThrow(_stmt, "sender_id")
        val _columnIndexOfSenderName: Int = getColumnIndexOrThrow(_stmt, "sender_name")
        val _columnIndexOfBodyMd: Int = getColumnIndexOrThrow(_stmt, "body_md")
        val _columnIndexOfBodyPreview: Int = getColumnIndexOrThrow(_stmt, "body_preview")
        val _columnIndexOfEditedAt: Int = getColumnIndexOrThrow(_stmt, "edited_at")
        val _columnIndexOfLinkPreviewTitle: Int = getColumnIndexOrThrow(_stmt, "link_preview_title")
        val _columnIndexOfLinkPreviewDescription: Int = getColumnIndexOrThrow(_stmt,
            "link_preview_description")
        val _columnIndexOfLinkPreviewImageUrl: Int = getColumnIndexOrThrow(_stmt,
            "link_preview_image_url")
        val _columnIndexOfLinkPreviewUrl: Int = getColumnIndexOrThrow(_stmt, "link_preview_url")
        val _columnIndexOfPhotoUrl: Int = getColumnIndexOrThrow(_stmt, "photo_url")
        val _columnIndexOfReactions: Int = getColumnIndexOrThrow(_stmt, "reactions")
        val _columnIndexOfOutboxStatus: Int = getColumnIndexOrThrow(_stmt, "outbox_status")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<ChatMessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChatMessageEntity
          val _tmpMessageId: String
          _tmpMessageId = _stmt.getText(_columnIndexOfMessageId)
          val _tmpThreadId: String
          _tmpThreadId = _stmt.getText(_columnIndexOfThreadId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpSenderId: String
          _tmpSenderId = _stmt.getText(_columnIndexOfSenderId)
          val _tmpSenderName: String?
          if (_stmt.isNull(_columnIndexOfSenderName)) {
            _tmpSenderName = null
          } else {
            _tmpSenderName = _stmt.getText(_columnIndexOfSenderName)
          }
          val _tmpBodyMd: String
          _tmpBodyMd = _stmt.getText(_columnIndexOfBodyMd)
          val _tmpBodyPreview: String?
          if (_stmt.isNull(_columnIndexOfBodyPreview)) {
            _tmpBodyPreview = null
          } else {
            _tmpBodyPreview = _stmt.getText(_columnIndexOfBodyPreview)
          }
          val _tmpEditedAt: Long?
          if (_stmt.isNull(_columnIndexOfEditedAt)) {
            _tmpEditedAt = null
          } else {
            _tmpEditedAt = _stmt.getLong(_columnIndexOfEditedAt)
          }
          val _tmpLinkPreviewTitle: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewTitle)) {
            _tmpLinkPreviewTitle = null
          } else {
            _tmpLinkPreviewTitle = _stmt.getText(_columnIndexOfLinkPreviewTitle)
          }
          val _tmpLinkPreviewDescription: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewDescription)) {
            _tmpLinkPreviewDescription = null
          } else {
            _tmpLinkPreviewDescription = _stmt.getText(_columnIndexOfLinkPreviewDescription)
          }
          val _tmpLinkPreviewImageUrl: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewImageUrl)) {
            _tmpLinkPreviewImageUrl = null
          } else {
            _tmpLinkPreviewImageUrl = _stmt.getText(_columnIndexOfLinkPreviewImageUrl)
          }
          val _tmpLinkPreviewUrl: String?
          if (_stmt.isNull(_columnIndexOfLinkPreviewUrl)) {
            _tmpLinkPreviewUrl = null
          } else {
            _tmpLinkPreviewUrl = _stmt.getText(_columnIndexOfLinkPreviewUrl)
          }
          val _tmpPhotoUrl: String?
          if (_stmt.isNull(_columnIndexOfPhotoUrl)) {
            _tmpPhotoUrl = null
          } else {
            _tmpPhotoUrl = _stmt.getText(_columnIndexOfPhotoUrl)
          }
          val _tmpReactions: String?
          if (_stmt.isNull(_columnIndexOfReactions)) {
            _tmpReactions = null
          } else {
            _tmpReactions = _stmt.getText(_columnIndexOfReactions)
          }
          val _tmpOutboxStatus: String?
          if (_stmt.isNull(_columnIndexOfOutboxStatus)) {
            _tmpOutboxStatus = null
          } else {
            _tmpOutboxStatus = _stmt.getText(_columnIndexOfOutboxStatus)
          }
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item =
              ChatMessageEntity(_tmpMessageId,_tmpThreadId,_tmpAccountId,_tmpSenderId,_tmpSenderName,_tmpBodyMd,_tmpBodyPreview,_tmpEditedAt,_tmpLinkPreviewTitle,_tmpLinkPreviewDescription,_tmpLinkPreviewImageUrl,_tmpLinkPreviewUrl,_tmpPhotoUrl,_tmpReactions,_tmpOutboxStatus,_tmpServerVersion,_tmpDeletedAt,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateOutboxStatus(messageId: String, status: String?) {
    val _sql: String = "UPDATE chat_messages SET outbox_status = ? WHERE message_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        if (status == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, status)
        }
        _argIndex = 2
        _stmt.bindText(_argIndex, messageId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateEdited(
    messageId: String,
    bodyMd: String,
    editedAt: Long,
  ) {
    val _sql: String =
        "UPDATE chat_messages SET body_md = ?, edited_at = ?, updated_at = ? WHERE message_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bodyMd)
        _argIndex = 2
        _stmt.bindLong(_argIndex, editedAt)
        _argIndex = 3
        _stmt.bindLong(_argIndex, editedAt)
        _argIndex = 4
        _stmt.bindText(_argIndex, messageId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
