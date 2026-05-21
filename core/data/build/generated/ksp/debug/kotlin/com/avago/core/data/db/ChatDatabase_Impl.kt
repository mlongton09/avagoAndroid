package com.avago.core.`data`.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.avago.core.`data`.db.dao.ChatMessageDao
import com.avago.core.`data`.db.dao.ChatMessageDao_Impl
import com.avago.core.`data`.db.dao.ChatThreadDao
import com.avago.core.`data`.db.dao.ChatThreadDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ChatDatabase_Impl : ChatDatabase() {
  private val _chatThreadDao: Lazy<ChatThreadDao> = lazy {
    ChatThreadDao_Impl(this)
  }

  private val _chatMessageDao: Lazy<ChatMessageDao> = lazy {
    ChatMessageDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "d005e644dd83fce97ae00a7c353986f0", "1a61a2c991c01a10469f5a9287154020") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `chat_threads` (`thread_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `thread_type` TEXT NOT NULL, `display_name` TEXT, `last_message_preview` TEXT, `last_message_at` INTEGER, `unread_count` INTEGER NOT NULL, `subject_summary` TEXT, `server_version` INTEGER NOT NULL, `deleted_at` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`thread_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `chat_messages` (`message_id` TEXT NOT NULL, `thread_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `sender_id` TEXT NOT NULL, `sender_name` TEXT, `body_md` TEXT NOT NULL, `body_preview` TEXT, `edited_at` INTEGER, `link_preview_title` TEXT, `link_preview_description` TEXT, `link_preview_image_url` TEXT, `link_preview_url` TEXT, `photo_url` TEXT, `reactions` TEXT, `outbox_status` TEXT, `server_version` INTEGER NOT NULL, `deleted_at` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`message_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd005e644dd83fce97ae00a7c353986f0')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `chat_threads`")
        connection.execSQL("DROP TABLE IF EXISTS `chat_messages`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsChatThreads: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsChatThreads.put("thread_id", TableInfo.Column("thread_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChatThreads.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChatThreads.put("thread_type", TableInfo.Column("thread_type", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatThreads.put("display_name", TableInfo.Column("display_name", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatThreads.put("last_message_preview", TableInfo.Column("last_message_preview",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatThreads.put("last_message_at", TableInfo.Column("last_message_at", "INTEGER",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatThreads.put("unread_count", TableInfo.Column("unread_count", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatThreads.put("subject_summary", TableInfo.Column("subject_summary", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatThreads.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatThreads.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatThreads.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatThreads.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysChatThreads: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesChatThreads: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoChatThreads: TableInfo = TableInfo("chat_threads", _columnsChatThreads,
            _foreignKeysChatThreads, _indicesChatThreads)
        val _existingChatThreads: TableInfo = read(connection, "chat_threads")
        if (!_infoChatThreads.equals(_existingChatThreads)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |chat_threads(com.avago.core.data.db.entity.ChatThreadEntity).
              | Expected:
              |""".trimMargin() + _infoChatThreads + """
              |
              | Found:
              |""".trimMargin() + _existingChatThreads)
        }
        val _columnsChatMessages: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsChatMessages.put("message_id", TableInfo.Column("message_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("thread_id", TableInfo.Column("thread_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("account_id", TableInfo.Column("account_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("sender_id", TableInfo.Column("sender_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("sender_name", TableInfo.Column("sender_name", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("body_md", TableInfo.Column("body_md", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("body_preview", TableInfo.Column("body_preview", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("edited_at", TableInfo.Column("edited_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("link_preview_title", TableInfo.Column("link_preview_title",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("link_preview_description",
            TableInfo.Column("link_preview_description", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("link_preview_image_url",
            TableInfo.Column("link_preview_image_url", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("link_preview_url", TableInfo.Column("link_preview_url", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("photo_url", TableInfo.Column("photo_url", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("reactions", TableInfo.Column("reactions", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("outbox_status", TableInfo.Column("outbox_status", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("server_version", TableInfo.Column("server_version", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysChatMessages: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesChatMessages: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoChatMessages: TableInfo = TableInfo("chat_messages", _columnsChatMessages,
            _foreignKeysChatMessages, _indicesChatMessages)
        val _existingChatMessages: TableInfo = read(connection, "chat_messages")
        if (!_infoChatMessages.equals(_existingChatMessages)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |chat_messages(com.avago.core.data.db.entity.ChatMessageEntity).
              | Expected:
              |""".trimMargin() + _infoChatMessages + """
              |
              | Found:
              |""".trimMargin() + _existingChatMessages)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "chat_threads", "chat_messages")
  }

  public override fun clearAllTables() {
    super.performClear(false, "chat_threads", "chat_messages")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(ChatThreadDao::class, ChatThreadDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ChatMessageDao::class, ChatMessageDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun chatThreadDao(): ChatThreadDao = _chatThreadDao.value

  public override fun chatMessageDao(): ChatMessageDao = _chatMessageDao.value
}
