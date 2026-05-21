package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.ChatThreadEntity
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
public class ChatThreadDao_Impl(
  __db: RoomDatabase,
) : ChatThreadDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfChatThreadEntity: EntityInsertAdapter<ChatThreadEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfChatThreadEntity = object : EntityInsertAdapter<ChatThreadEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `chat_threads` (`thread_id`,`account_id`,`thread_type`,`display_name`,`last_message_preview`,`last_message_at`,`unread_count`,`subject_summary`,`server_version`,`deleted_at`,`created_at`,`updated_at`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ChatThreadEntity) {
        statement.bindText(1, entity.threadId)
        statement.bindText(2, entity.accountId)
        statement.bindText(3, entity.threadType)
        val _tmpDisplayName: String? = entity.displayName
        if (_tmpDisplayName == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpDisplayName)
        }
        val _tmpLastMessagePreview: String? = entity.lastMessagePreview
        if (_tmpLastMessagePreview == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpLastMessagePreview)
        }
        val _tmpLastMessageAt: Long? = entity.lastMessageAt
        if (_tmpLastMessageAt == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpLastMessageAt)
        }
        statement.bindLong(7, entity.unreadCount.toLong())
        val _tmpSubjectSummary: String? = entity.subjectSummary
        if (_tmpSubjectSummary == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpSubjectSummary)
        }
        statement.bindLong(9, entity.serverVersion)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(10)
        } else {
          statement.bindLong(10, _tmpDeletedAt)
        }
        statement.bindLong(11, entity.createdAt)
        statement.bindLong(12, entity.updatedAt)
      }
    }
  }

  public override suspend fun upsert(thread: ChatThreadEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfChatThreadEntity.insert(_connection, thread)
  }

  public override fun observeAll(accountId: String): Flow<List<ChatThreadEntity>> {
    val _sql: String =
        "SELECT * FROM chat_threads WHERE account_id = ? AND deleted_at IS NULL ORDER BY last_message_at DESC"
    return createFlow(__db, false, arrayOf("chat_threads")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "thread_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfThreadType: Int = getColumnIndexOrThrow(_stmt, "thread_type")
        val _columnIndexOfDisplayName: Int = getColumnIndexOrThrow(_stmt, "display_name")
        val _columnIndexOfLastMessagePreview: Int = getColumnIndexOrThrow(_stmt,
            "last_message_preview")
        val _columnIndexOfLastMessageAt: Int = getColumnIndexOrThrow(_stmt, "last_message_at")
        val _columnIndexOfUnreadCount: Int = getColumnIndexOrThrow(_stmt, "unread_count")
        val _columnIndexOfSubjectSummary: Int = getColumnIndexOrThrow(_stmt, "subject_summary")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<ChatThreadEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChatThreadEntity
          val _tmpThreadId: String
          _tmpThreadId = _stmt.getText(_columnIndexOfThreadId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpThreadType: String
          _tmpThreadType = _stmt.getText(_columnIndexOfThreadType)
          val _tmpDisplayName: String?
          if (_stmt.isNull(_columnIndexOfDisplayName)) {
            _tmpDisplayName = null
          } else {
            _tmpDisplayName = _stmt.getText(_columnIndexOfDisplayName)
          }
          val _tmpLastMessagePreview: String?
          if (_stmt.isNull(_columnIndexOfLastMessagePreview)) {
            _tmpLastMessagePreview = null
          } else {
            _tmpLastMessagePreview = _stmt.getText(_columnIndexOfLastMessagePreview)
          }
          val _tmpLastMessageAt: Long?
          if (_stmt.isNull(_columnIndexOfLastMessageAt)) {
            _tmpLastMessageAt = null
          } else {
            _tmpLastMessageAt = _stmt.getLong(_columnIndexOfLastMessageAt)
          }
          val _tmpUnreadCount: Int
          _tmpUnreadCount = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
          val _tmpSubjectSummary: String?
          if (_stmt.isNull(_columnIndexOfSubjectSummary)) {
            _tmpSubjectSummary = null
          } else {
            _tmpSubjectSummary = _stmt.getText(_columnIndexOfSubjectSummary)
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
              ChatThreadEntity(_tmpThreadId,_tmpAccountId,_tmpThreadType,_tmpDisplayName,_tmpLastMessagePreview,_tmpLastMessageAt,_tmpUnreadCount,_tmpSubjectSummary,_tmpServerVersion,_tmpDeletedAt,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(threadId: String): ChatThreadEntity? {
    val _sql: String = "SELECT * FROM chat_threads WHERE thread_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, threadId)
        val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "thread_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfThreadType: Int = getColumnIndexOrThrow(_stmt, "thread_type")
        val _columnIndexOfDisplayName: Int = getColumnIndexOrThrow(_stmt, "display_name")
        val _columnIndexOfLastMessagePreview: Int = getColumnIndexOrThrow(_stmt,
            "last_message_preview")
        val _columnIndexOfLastMessageAt: Int = getColumnIndexOrThrow(_stmt, "last_message_at")
        val _columnIndexOfUnreadCount: Int = getColumnIndexOrThrow(_stmt, "unread_count")
        val _columnIndexOfSubjectSummary: Int = getColumnIndexOrThrow(_stmt, "subject_summary")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: ChatThreadEntity?
        if (_stmt.step()) {
          val _tmpThreadId: String
          _tmpThreadId = _stmt.getText(_columnIndexOfThreadId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpThreadType: String
          _tmpThreadType = _stmt.getText(_columnIndexOfThreadType)
          val _tmpDisplayName: String?
          if (_stmt.isNull(_columnIndexOfDisplayName)) {
            _tmpDisplayName = null
          } else {
            _tmpDisplayName = _stmt.getText(_columnIndexOfDisplayName)
          }
          val _tmpLastMessagePreview: String?
          if (_stmt.isNull(_columnIndexOfLastMessagePreview)) {
            _tmpLastMessagePreview = null
          } else {
            _tmpLastMessagePreview = _stmt.getText(_columnIndexOfLastMessagePreview)
          }
          val _tmpLastMessageAt: Long?
          if (_stmt.isNull(_columnIndexOfLastMessageAt)) {
            _tmpLastMessageAt = null
          } else {
            _tmpLastMessageAt = _stmt.getLong(_columnIndexOfLastMessageAt)
          }
          val _tmpUnreadCount: Int
          _tmpUnreadCount = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
          val _tmpSubjectSummary: String?
          if (_stmt.isNull(_columnIndexOfSubjectSummary)) {
            _tmpSubjectSummary = null
          } else {
            _tmpSubjectSummary = _stmt.getText(_columnIndexOfSubjectSummary)
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
              ChatThreadEntity(_tmpThreadId,_tmpAccountId,_tmpThreadType,_tmpDisplayName,_tmpLastMessagePreview,_tmpLastMessageAt,_tmpUnreadCount,_tmpSubjectSummary,_tmpServerVersion,_tmpDeletedAt,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateUnreadCount(threadId: String, count: Int) {
    val _sql: String = "UPDATE chat_threads SET unread_count = ? WHERE thread_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, count.toLong())
        _argIndex = 2
        _stmt.bindText(_argIndex, threadId)
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
