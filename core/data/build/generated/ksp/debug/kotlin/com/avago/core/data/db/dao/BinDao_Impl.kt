package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.BinEntity
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
public class BinDao_Impl(
  __db: RoomDatabase,
) : BinDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfBinEntity: EntityInsertAdapter<BinEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfBinEntity = object : EntityInsertAdapter<BinEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `bins` (`bin_id`,`location_id`,`name`,`aisle`,`shelf`,`slot`,`created_at`,`updated_at`,`server_version`) VALUES (?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: BinEntity) {
        statement.bindText(1, entity.binId)
        statement.bindText(2, entity.locationId)
        statement.bindText(3, entity.name)
        val _tmpAisle: String? = entity.aisle
        if (_tmpAisle == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpAisle)
        }
        val _tmpShelf: String? = entity.shelf
        if (_tmpShelf == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpShelf)
        }
        val _tmpSlot: String? = entity.slot
        if (_tmpSlot == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpSlot)
        }
        statement.bindLong(7, entity.createdAt)
        statement.bindLong(8, entity.updatedAt)
        statement.bindLong(9, entity.serverVersion)
      }
    }
  }

  public override suspend fun upsert(entity: BinEntity): Unit = performSuspending(__db, false, true)
      { _connection ->
    __insertAdapterOfBinEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<BinEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfBinEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<BinEntity>> {
    val _sql: String =
        "SELECT * FROM bins WHERE location_id IN (SELECT location_id FROM locations WHERE account_id = ?)"
    return createFlow(__db, false, arrayOf("bins", "locations")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfBinId: Int = getColumnIndexOrThrow(_stmt, "bin_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfAisle: Int = getColumnIndexOrThrow(_stmt, "aisle")
        val _columnIndexOfShelf: Int = getColumnIndexOrThrow(_stmt, "shelf")
        val _columnIndexOfSlot: Int = getColumnIndexOrThrow(_stmt, "slot")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: MutableList<BinEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BinEntity
          val _tmpBinId: String
          _tmpBinId = _stmt.getText(_columnIndexOfBinId)
          val _tmpLocationId: String
          _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpAisle: String?
          if (_stmt.isNull(_columnIndexOfAisle)) {
            _tmpAisle = null
          } else {
            _tmpAisle = _stmt.getText(_columnIndexOfAisle)
          }
          val _tmpShelf: String?
          if (_stmt.isNull(_columnIndexOfShelf)) {
            _tmpShelf = null
          } else {
            _tmpShelf = _stmt.getText(_columnIndexOfShelf)
          }
          val _tmpSlot: String?
          if (_stmt.isNull(_columnIndexOfSlot)) {
            _tmpSlot = null
          } else {
            _tmpSlot = _stmt.getText(_columnIndexOfSlot)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _item =
              BinEntity(_tmpBinId,_tmpLocationId,_tmpName,_tmpAisle,_tmpShelf,_tmpSlot,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): BinEntity? {
    val _sql: String = "SELECT * FROM bins WHERE bin_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfBinId: Int = getColumnIndexOrThrow(_stmt, "bin_id")
        val _columnIndexOfLocationId: Int = getColumnIndexOrThrow(_stmt, "location_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfAisle: Int = getColumnIndexOrThrow(_stmt, "aisle")
        val _columnIndexOfShelf: Int = getColumnIndexOrThrow(_stmt, "shelf")
        val _columnIndexOfSlot: Int = getColumnIndexOrThrow(_stmt, "slot")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _result: BinEntity?
        if (_stmt.step()) {
          val _tmpBinId: String
          _tmpBinId = _stmt.getText(_columnIndexOfBinId)
          val _tmpLocationId: String
          _tmpLocationId = _stmt.getText(_columnIndexOfLocationId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpAisle: String?
          if (_stmt.isNull(_columnIndexOfAisle)) {
            _tmpAisle = null
          } else {
            _tmpAisle = _stmt.getText(_columnIndexOfAisle)
          }
          val _tmpShelf: String?
          if (_stmt.isNull(_columnIndexOfShelf)) {
            _tmpShelf = null
          } else {
            _tmpShelf = _stmt.getText(_columnIndexOfShelf)
          }
          val _tmpSlot: String?
          if (_stmt.isNull(_columnIndexOfSlot)) {
            _tmpSlot = null
          } else {
            _tmpSlot = _stmt.getText(_columnIndexOfSlot)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpServerVersion: Long
          _tmpServerVersion = _stmt.getLong(_columnIndexOfServerVersion)
          _result =
              BinEntity(_tmpBinId,_tmpLocationId,_tmpName,_tmpAisle,_tmpShelf,_tmpSlot,_tmpCreatedAt,_tmpUpdatedAt,_tmpServerVersion)
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
    val _sql: String = "DELETE FROM bins WHERE bin_id = ?"
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
