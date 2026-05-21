package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.WoCommentEntity
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
public class WoCommentDao_Impl(
  __db: RoomDatabase,
) : WoCommentDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfWoCommentEntity: EntityInsertAdapter<WoCommentEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfWoCommentEntity = object : EntityInsertAdapter<WoCommentEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `wo_comments` (`comment_id`,`wo_id`,`author_id`,`body`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: WoCommentEntity) {
        statement.bindText(1, entity.commentId)
        statement.bindText(2, entity.woId)
        statement.bindText(3, entity.authorId)
        statement.bindText(4, entity.body)
        statement.bindLong(5, entity.createdAt)
        statement.bindLong(6, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpDeletedAt)
        }
        statement.bindLong(8, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: WoCommentEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfWoCommentEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<WoCommentEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfWoCommentEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<WoCommentEntity>> {
    val _sql: String =
        "SELECT * FROM wo_comments WHERE wo_id IN (SELECT wo_id FROM work_orders WHERE account_id = ?) AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("wo_comments", "work_orders")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfCommentId: Int = getColumnIndexOrThrow(_stmt, "comment_id")
        val _columnIndexOfWoId: Int = getColumnIndexOrThrow(_stmt, "wo_id")
        val _columnIndexOfAuthorId: Int = getColumnIndexOrThrow(_stmt, "author_id")
        val _columnIndexOfBody: Int = getColumnIndexOrThrow(_stmt, "body")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<WoCommentEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: WoCommentEntity
          val _tmpCommentId: String
          _tmpCommentId = _stmt.getText(_columnIndexOfCommentId)
          val _tmpWoId: String
          _tmpWoId = _stmt.getText(_columnIndexOfWoId)
          val _tmpAuthorId: String
          _tmpAuthorId = _stmt.getText(_columnIndexOfAuthorId)
          val _tmpBody: String
          _tmpBody = _stmt.getText(_columnIndexOfBody)
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
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _item =
              WoCommentEntity(_tmpCommentId,_tmpWoId,_tmpAuthorId,_tmpBody,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): WoCommentEntity? {
    val _sql: String = "SELECT * FROM wo_comments WHERE comment_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfCommentId: Int = getColumnIndexOrThrow(_stmt, "comment_id")
        val _columnIndexOfWoId: Int = getColumnIndexOrThrow(_stmt, "wo_id")
        val _columnIndexOfAuthorId: Int = getColumnIndexOrThrow(_stmt, "author_id")
        val _columnIndexOfBody: Int = getColumnIndexOrThrow(_stmt, "body")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: WoCommentEntity?
        if (_stmt.step()) {
          val _tmpCommentId: String
          _tmpCommentId = _stmt.getText(_columnIndexOfCommentId)
          val _tmpWoId: String
          _tmpWoId = _stmt.getText(_columnIndexOfWoId)
          val _tmpAuthorId: String
          _tmpAuthorId = _stmt.getText(_columnIndexOfAuthorId)
          val _tmpBody: String
          _tmpBody = _stmt.getText(_columnIndexOfBody)
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
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _result =
              WoCommentEntity(_tmpCommentId,_tmpWoId,_tmpAuthorId,_tmpBody,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE wo_comments SET deleted_at = ?, updated_at = ? WHERE comment_id = ?"
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
