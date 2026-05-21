package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.WoTemplateEntity
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
public class WoTemplateDao_Impl(
  __db: RoomDatabase,
) : WoTemplateDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfWoTemplateEntity: EntityInsertAdapter<WoTemplateEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfWoTemplateEntity = object : EntityInsertAdapter<WoTemplateEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `wo_templates` (`template_id`,`account_id`,`title`,`description`,`category`,`checklist_items`,`estimated_effort_minutes`,`created_at`,`updated_at`,`deleted_at`,`server_version`) VALUES (?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: WoTemplateEntity) {
        statement.bindText(1, entity.templateId)
        statement.bindText(2, entity.accountId)
        statement.bindText(3, entity.title)
        val _tmpDescription: String? = entity.description
        if (_tmpDescription == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpDescription)
        }
        val _tmpCategory: String? = entity.category
        if (_tmpCategory == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpCategory)
        }
        val _tmpChecklistItems: String? = entity.checklistItems
        if (_tmpChecklistItems == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpChecklistItems)
        }
        val _tmpEstimatedEffortMinutes: Long? = entity.estimatedEffortMinutes
        if (_tmpEstimatedEffortMinutes == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpEstimatedEffortMinutes)
        }
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(10)
        } else {
          statement.bindLong(10, _tmpDeletedAt)
        }
        statement.bindLong(11, entity.serverVersion)
      }
    }
  }

  public override suspend fun upsert(entity: WoTemplateEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfWoTemplateEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<WoTemplateEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfWoTemplateEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<WoTemplateEntity>> {
    val _sql: String = "SELECT * FROM wo_templates WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("wo_templates")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfTemplateId: Int = getColumnIndexOrThrow(_stmt, "template_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfChecklistItems: Int = getColumnIndexOrThrow(_stmt, "checklist_items")
        val _columnIndexOfEstimatedEffortMinutes: Int = getColumnIndexOrThrow(_stmt,
            "estimated_effort_minutes")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: MutableList<WoTemplateEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: WoTemplateEntity
          val _tmpTemplateId: String
          _tmpTemplateId = _stmt.getText(_columnIndexOfTemplateId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpCategory: String?
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _tmpCategory = null
          } else {
            _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          }
          val _tmpChecklistItems: String?
          if (_stmt.isNull(_columnIndexOfChecklistItems)) {
            _tmpChecklistItems = null
          } else {
            _tmpChecklistItems = _stmt.getText(_columnIndexOfChecklistItems)
          }
          val _tmpEstimatedEffortMinutes: Long?
          if (_stmt.isNull(_columnIndexOfEstimatedEffortMinutes)) {
            _tmpEstimatedEffortMinutes = null
          } else {
            _tmpEstimatedEffortMinutes = _stmt.getLong(_columnIndexOfEstimatedEffortMinutes)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _item =
              WoTemplateEntity(_tmpTemplateId,_tmpAccountId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpChecklistItems,_tmpEstimatedEffortMinutes,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): WoTemplateEntity? {
    val _sql: String = "SELECT * FROM wo_templates WHERE template_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfTemplateId: Int = getColumnIndexOrThrow(_stmt, "template_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfChecklistItems: Int = getColumnIndexOrThrow(_stmt, "checklist_items")
        val _columnIndexOfEstimatedEffortMinutes: Int = getColumnIndexOrThrow(_stmt,
            "estimated_effort_minutes")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: WoTemplateEntity?
        if (_stmt.step()) {
          val _tmpTemplateId: String
          _tmpTemplateId = _stmt.getText(_columnIndexOfTemplateId)
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpCategory: String?
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _tmpCategory = null
          } else {
            _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          }
          val _tmpChecklistItems: String?
          if (_stmt.isNull(_columnIndexOfChecklistItems)) {
            _tmpChecklistItems = null
          } else {
            _tmpChecklistItems = _stmt.getText(_columnIndexOfChecklistItems)
          }
          val _tmpEstimatedEffortMinutes: Long?
          if (_stmt.isNull(_columnIndexOfEstimatedEffortMinutes)) {
            _tmpEstimatedEffortMinutes = null
          } else {
            _tmpEstimatedEffortMinutes = _stmt.getLong(_columnIndexOfEstimatedEffortMinutes)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _result =
              WoTemplateEntity(_tmpTemplateId,_tmpAccountId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpChecklistItems,_tmpEstimatedEffortMinutes,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun softDelete(id: String, now: Long) {
    val _sql: String =
        "UPDATE wo_templates SET deleted_at = ?, updated_at = ? WHERE template_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, now)
        _argIndex = 2
        _stmt.bindLong(_argIndex, now)
        _argIndex = 3
        _stmt.bindText(_argIndex, id)
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
