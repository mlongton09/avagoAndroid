package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.converters.Converters
import com.avago.core.`data`.db.entity.WoChecklistItemEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class WoChecklistItemDao_Impl(
  __db: RoomDatabase,
) : WoChecklistItemDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfWoChecklistItemEntity: EntityInsertAdapter<WoChecklistItemEntity>

  private val __converters: Converters = Converters()
  init {
    this.__db = __db
    this.__insertAdapterOfWoChecklistItemEntity = object :
        EntityInsertAdapter<WoChecklistItemEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `wo_checklist_items` (`item_id`,`wo_id`,`title`,`is_completed`,`completed_at`,`display_order`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: WoChecklistItemEntity) {
        statement.bindText(1, entity.itemId)
        statement.bindText(2, entity.woId)
        statement.bindText(3, entity.title)
        val _tmp: Int = __converters.fromBooleanToInt(entity.isCompleted)
        statement.bindLong(4, _tmp.toLong())
        val _tmpCompletedAt: Long? = entity.completedAt
        if (_tmpCompletedAt == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpCompletedAt)
        }
        statement.bindLong(6, entity.displayOrder)
        statement.bindLong(7, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: WoChecklistItemEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfWoChecklistItemEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<WoChecklistItemEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfWoChecklistItemEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<WoChecklistItemEntity>> {
    val _sql: String =
        "SELECT * FROM wo_checklist_items WHERE wo_id IN (SELECT wo_id FROM work_orders WHERE account_id = ?)"
    return createFlow(__db, false, arrayOf("wo_checklist_items", "work_orders")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfItemId: Int = getColumnIndexOrThrow(_stmt, "item_id")
        val _columnIndexOfWoId: Int = getColumnIndexOrThrow(_stmt, "wo_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfIsCompleted: Int = getColumnIndexOrThrow(_stmt, "is_completed")
        val _columnIndexOfCompletedAt: Int = getColumnIndexOrThrow(_stmt, "completed_at")
        val _columnIndexOfDisplayOrder: Int = getColumnIndexOrThrow(_stmt, "display_order")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<WoChecklistItemEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: WoChecklistItemEntity
          val _tmpItemId: String
          _tmpItemId = _stmt.getText(_columnIndexOfItemId)
          val _tmpWoId: String
          _tmpWoId = _stmt.getText(_columnIndexOfWoId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpIsCompleted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCompleted).toInt()
          _tmpIsCompleted = __converters.fromIntToBoolean(_tmp)
          val _tmpCompletedAt: Long?
          if (_stmt.isNull(_columnIndexOfCompletedAt)) {
            _tmpCompletedAt = null
          } else {
            _tmpCompletedAt = _stmt.getLong(_columnIndexOfCompletedAt)
          }
          val _tmpDisplayOrder: Long
          _tmpDisplayOrder = _stmt.getLong(_columnIndexOfDisplayOrder)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _item =
              WoChecklistItemEntity(_tmpItemId,_tmpWoId,_tmpTitle,_tmpIsCompleted,_tmpCompletedAt,_tmpDisplayOrder,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): WoChecklistItemEntity? {
    val _sql: String = "SELECT * FROM wo_checklist_items WHERE item_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfItemId: Int = getColumnIndexOrThrow(_stmt, "item_id")
        val _columnIndexOfWoId: Int = getColumnIndexOrThrow(_stmt, "wo_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfIsCompleted: Int = getColumnIndexOrThrow(_stmt, "is_completed")
        val _columnIndexOfCompletedAt: Int = getColumnIndexOrThrow(_stmt, "completed_at")
        val _columnIndexOfDisplayOrder: Int = getColumnIndexOrThrow(_stmt, "display_order")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: WoChecklistItemEntity?
        if (_stmt.step()) {
          val _tmpItemId: String
          _tmpItemId = _stmt.getText(_columnIndexOfItemId)
          val _tmpWoId: String
          _tmpWoId = _stmt.getText(_columnIndexOfWoId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpIsCompleted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCompleted).toInt()
          _tmpIsCompleted = __converters.fromIntToBoolean(_tmp)
          val _tmpCompletedAt: Long?
          if (_stmt.isNull(_columnIndexOfCompletedAt)) {
            _tmpCompletedAt = null
          } else {
            _tmpCompletedAt = _stmt.getLong(_columnIndexOfCompletedAt)
          }
          val _tmpDisplayOrder: Long
          _tmpDisplayOrder = _stmt.getLong(_columnIndexOfDisplayOrder)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _result =
              WoChecklistItemEntity(_tmpItemId,_tmpWoId,_tmpTitle,_tmpIsCompleted,_tmpCompletedAt,_tmpDisplayOrder,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "DELETE FROM wo_checklist_items WHERE item_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
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
