package com.avago.core.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.avago.core.`data`.db.entity.DocEntity
import javax.`annotation`.processing.Generated
import kotlin.Double
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
public class DocDao_Impl(
  __db: RoomDatabase,
) : DocDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfDocEntity: EntityInsertAdapter<DocEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfDocEntity = object : EntityInsertAdapter<DocEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `docs` (`doc_id`,`asset_id`,`account_id`,`name`,`doc_type`,`mime_type`,`storage_key`,`download_url`,`ocr_raw_text`,`ocr_extracted_json`,`vendor`,`total`,`currency`,`purchase_date`,`uploaded_by`,`uploaded_at`,`created_at`,`updated_at`,`deleted_at`,`server_version`,`seq`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: DocEntity) {
        statement.bindText(1, entity.docId)
        val _tmpAssetId: String? = entity.assetId
        if (_tmpAssetId == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpAssetId)
        }
        statement.bindText(3, entity.accountId)
        statement.bindText(4, entity.name)
        statement.bindText(5, entity.docType)
        val _tmpMimeType: String? = entity.mimeType
        if (_tmpMimeType == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpMimeType)
        }
        val _tmpStorageKey: String? = entity.storageKey
        if (_tmpStorageKey == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpStorageKey)
        }
        val _tmpDownloadUrl: String? = entity.downloadUrl
        if (_tmpDownloadUrl == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpDownloadUrl)
        }
        val _tmpOcrRawText: String? = entity.ocrRawText
        if (_tmpOcrRawText == null) {
          statement.bindNull(9)
        } else {
          statement.bindText(9, _tmpOcrRawText)
        }
        val _tmpOcrExtractedJson: String? = entity.ocrExtractedJson
        if (_tmpOcrExtractedJson == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpOcrExtractedJson)
        }
        val _tmpVendor: String? = entity.vendor
        if (_tmpVendor == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpVendor)
        }
        val _tmpTotal: Double? = entity.total
        if (_tmpTotal == null) {
          statement.bindNull(12)
        } else {
          statement.bindDouble(12, _tmpTotal)
        }
        val _tmpCurrency: String? = entity.currency
        if (_tmpCurrency == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpCurrency)
        }
        val _tmpPurchaseDate: Long? = entity.purchaseDate
        if (_tmpPurchaseDate == null) {
          statement.bindNull(14)
        } else {
          statement.bindLong(14, _tmpPurchaseDate)
        }
        val _tmpUploadedBy: String? = entity.uploadedBy
        if (_tmpUploadedBy == null) {
          statement.bindNull(15)
        } else {
          statement.bindText(15, _tmpUploadedBy)
        }
        val _tmpUploadedAt: Long? = entity.uploadedAt
        if (_tmpUploadedAt == null) {
          statement.bindNull(16)
        } else {
          statement.bindLong(16, _tmpUploadedAt)
        }
        statement.bindLong(17, entity.createdAt)
        statement.bindLong(18, entity.updatedAt)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(19)
        } else {
          statement.bindLong(19, _tmpDeletedAt)
        }
        statement.bindLong(20, entity.serverVersion)
        val _tmpSeq: Long? = entity.seq
        if (_tmpSeq == null) {
          statement.bindNull(21)
        } else {
          statement.bindLong(21, _tmpSeq)
        }
      }
    }
  }

  public override suspend fun upsert(entity: DocEntity): Unit = performSuspending(__db, false, true)
      { _connection ->
    __insertAdapterOfDocEntity.insert(_connection, entity)
  }

  public override suspend fun upsertAll(entities: List<DocEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfDocEntity.insert(_connection, entities)
  }

  public override fun observeAll(accountId: String): Flow<List<DocEntity>> {
    val _sql: String = "SELECT * FROM docs WHERE account_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("docs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, accountId)
        val _columnIndexOfDocId: Int = getColumnIndexOrThrow(_stmt, "doc_id")
        val _columnIndexOfAssetId: Int = getColumnIndexOrThrow(_stmt, "asset_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDocType: Int = getColumnIndexOrThrow(_stmt, "doc_type")
        val _columnIndexOfMimeType: Int = getColumnIndexOrThrow(_stmt, "mime_type")
        val _columnIndexOfStorageKey: Int = getColumnIndexOrThrow(_stmt, "storage_key")
        val _columnIndexOfDownloadUrl: Int = getColumnIndexOrThrow(_stmt, "download_url")
        val _columnIndexOfOcrRawText: Int = getColumnIndexOrThrow(_stmt, "ocr_raw_text")
        val _columnIndexOfOcrExtractedJson: Int = getColumnIndexOrThrow(_stmt, "ocr_extracted_json")
        val _columnIndexOfVendor: Int = getColumnIndexOrThrow(_stmt, "vendor")
        val _columnIndexOfTotal: Int = getColumnIndexOrThrow(_stmt, "total")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfPurchaseDate: Int = getColumnIndexOrThrow(_stmt, "purchase_date")
        val _columnIndexOfUploadedBy: Int = getColumnIndexOrThrow(_stmt, "uploaded_by")
        val _columnIndexOfUploadedAt: Int = getColumnIndexOrThrow(_stmt, "uploaded_at")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: MutableList<DocEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: DocEntity
          val _tmpDocId: String
          _tmpDocId = _stmt.getText(_columnIndexOfDocId)
          val _tmpAssetId: String?
          if (_stmt.isNull(_columnIndexOfAssetId)) {
            _tmpAssetId = null
          } else {
            _tmpAssetId = _stmt.getText(_columnIndexOfAssetId)
          }
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDocType: String
          _tmpDocType = _stmt.getText(_columnIndexOfDocType)
          val _tmpMimeType: String?
          if (_stmt.isNull(_columnIndexOfMimeType)) {
            _tmpMimeType = null
          } else {
            _tmpMimeType = _stmt.getText(_columnIndexOfMimeType)
          }
          val _tmpStorageKey: String?
          if (_stmt.isNull(_columnIndexOfStorageKey)) {
            _tmpStorageKey = null
          } else {
            _tmpStorageKey = _stmt.getText(_columnIndexOfStorageKey)
          }
          val _tmpDownloadUrl: String?
          if (_stmt.isNull(_columnIndexOfDownloadUrl)) {
            _tmpDownloadUrl = null
          } else {
            _tmpDownloadUrl = _stmt.getText(_columnIndexOfDownloadUrl)
          }
          val _tmpOcrRawText: String?
          if (_stmt.isNull(_columnIndexOfOcrRawText)) {
            _tmpOcrRawText = null
          } else {
            _tmpOcrRawText = _stmt.getText(_columnIndexOfOcrRawText)
          }
          val _tmpOcrExtractedJson: String?
          if (_stmt.isNull(_columnIndexOfOcrExtractedJson)) {
            _tmpOcrExtractedJson = null
          } else {
            _tmpOcrExtractedJson = _stmt.getText(_columnIndexOfOcrExtractedJson)
          }
          val _tmpVendor: String?
          if (_stmt.isNull(_columnIndexOfVendor)) {
            _tmpVendor = null
          } else {
            _tmpVendor = _stmt.getText(_columnIndexOfVendor)
          }
          val _tmpTotal: Double?
          if (_stmt.isNull(_columnIndexOfTotal)) {
            _tmpTotal = null
          } else {
            _tmpTotal = _stmt.getDouble(_columnIndexOfTotal)
          }
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpPurchaseDate: Long?
          if (_stmt.isNull(_columnIndexOfPurchaseDate)) {
            _tmpPurchaseDate = null
          } else {
            _tmpPurchaseDate = _stmt.getLong(_columnIndexOfPurchaseDate)
          }
          val _tmpUploadedBy: String?
          if (_stmt.isNull(_columnIndexOfUploadedBy)) {
            _tmpUploadedBy = null
          } else {
            _tmpUploadedBy = _stmt.getText(_columnIndexOfUploadedBy)
          }
          val _tmpUploadedAt: Long?
          if (_stmt.isNull(_columnIndexOfUploadedAt)) {
            _tmpUploadedAt = null
          } else {
            _tmpUploadedAt = _stmt.getLong(_columnIndexOfUploadedAt)
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
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _item =
              DocEntity(_tmpDocId,_tmpAssetId,_tmpAccountId,_tmpName,_tmpDocType,_tmpMimeType,_tmpStorageKey,_tmpDownloadUrl,_tmpOcrRawText,_tmpOcrExtractedJson,_tmpVendor,_tmpTotal,_tmpCurrency,_tmpPurchaseDate,_tmpUploadedBy,_tmpUploadedAt,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): DocEntity? {
    val _sql: String = "SELECT * FROM docs WHERE doc_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfDocId: Int = getColumnIndexOrThrow(_stmt, "doc_id")
        val _columnIndexOfAssetId: Int = getColumnIndexOrThrow(_stmt, "asset_id")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "account_id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDocType: Int = getColumnIndexOrThrow(_stmt, "doc_type")
        val _columnIndexOfMimeType: Int = getColumnIndexOrThrow(_stmt, "mime_type")
        val _columnIndexOfStorageKey: Int = getColumnIndexOrThrow(_stmt, "storage_key")
        val _columnIndexOfDownloadUrl: Int = getColumnIndexOrThrow(_stmt, "download_url")
        val _columnIndexOfOcrRawText: Int = getColumnIndexOrThrow(_stmt, "ocr_raw_text")
        val _columnIndexOfOcrExtractedJson: Int = getColumnIndexOrThrow(_stmt, "ocr_extracted_json")
        val _columnIndexOfVendor: Int = getColumnIndexOrThrow(_stmt, "vendor")
        val _columnIndexOfTotal: Int = getColumnIndexOrThrow(_stmt, "total")
        val _columnIndexOfCurrency: Int = getColumnIndexOrThrow(_stmt, "currency")
        val _columnIndexOfPurchaseDate: Int = getColumnIndexOrThrow(_stmt, "purchase_date")
        val _columnIndexOfUploadedBy: Int = getColumnIndexOrThrow(_stmt, "uploaded_by")
        val _columnIndexOfUploadedAt: Int = getColumnIndexOrThrow(_stmt, "uploaded_at")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfServerVersion: Int = getColumnIndexOrThrow(_stmt, "server_version")
        val _columnIndexOfSeq: Int = getColumnIndexOrThrow(_stmt, "seq")
        val _result: DocEntity?
        if (_stmt.step()) {
          val _tmpDocId: String
          _tmpDocId = _stmt.getText(_columnIndexOfDocId)
          val _tmpAssetId: String?
          if (_stmt.isNull(_columnIndexOfAssetId)) {
            _tmpAssetId = null
          } else {
            _tmpAssetId = _stmt.getText(_columnIndexOfAssetId)
          }
          val _tmpAccountId: String
          _tmpAccountId = _stmt.getText(_columnIndexOfAccountId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDocType: String
          _tmpDocType = _stmt.getText(_columnIndexOfDocType)
          val _tmpMimeType: String?
          if (_stmt.isNull(_columnIndexOfMimeType)) {
            _tmpMimeType = null
          } else {
            _tmpMimeType = _stmt.getText(_columnIndexOfMimeType)
          }
          val _tmpStorageKey: String?
          if (_stmt.isNull(_columnIndexOfStorageKey)) {
            _tmpStorageKey = null
          } else {
            _tmpStorageKey = _stmt.getText(_columnIndexOfStorageKey)
          }
          val _tmpDownloadUrl: String?
          if (_stmt.isNull(_columnIndexOfDownloadUrl)) {
            _tmpDownloadUrl = null
          } else {
            _tmpDownloadUrl = _stmt.getText(_columnIndexOfDownloadUrl)
          }
          val _tmpOcrRawText: String?
          if (_stmt.isNull(_columnIndexOfOcrRawText)) {
            _tmpOcrRawText = null
          } else {
            _tmpOcrRawText = _stmt.getText(_columnIndexOfOcrRawText)
          }
          val _tmpOcrExtractedJson: String?
          if (_stmt.isNull(_columnIndexOfOcrExtractedJson)) {
            _tmpOcrExtractedJson = null
          } else {
            _tmpOcrExtractedJson = _stmt.getText(_columnIndexOfOcrExtractedJson)
          }
          val _tmpVendor: String?
          if (_stmt.isNull(_columnIndexOfVendor)) {
            _tmpVendor = null
          } else {
            _tmpVendor = _stmt.getText(_columnIndexOfVendor)
          }
          val _tmpTotal: Double?
          if (_stmt.isNull(_columnIndexOfTotal)) {
            _tmpTotal = null
          } else {
            _tmpTotal = _stmt.getDouble(_columnIndexOfTotal)
          }
          val _tmpCurrency: String?
          if (_stmt.isNull(_columnIndexOfCurrency)) {
            _tmpCurrency = null
          } else {
            _tmpCurrency = _stmt.getText(_columnIndexOfCurrency)
          }
          val _tmpPurchaseDate: Long?
          if (_stmt.isNull(_columnIndexOfPurchaseDate)) {
            _tmpPurchaseDate = null
          } else {
            _tmpPurchaseDate = _stmt.getLong(_columnIndexOfPurchaseDate)
          }
          val _tmpUploadedBy: String?
          if (_stmt.isNull(_columnIndexOfUploadedBy)) {
            _tmpUploadedBy = null
          } else {
            _tmpUploadedBy = _stmt.getText(_columnIndexOfUploadedBy)
          }
          val _tmpUploadedAt: Long?
          if (_stmt.isNull(_columnIndexOfUploadedAt)) {
            _tmpUploadedAt = null
          } else {
            _tmpUploadedAt = _stmt.getLong(_columnIndexOfUploadedAt)
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
          val _tmpSeq: Long?
          if (_stmt.isNull(_columnIndexOfSeq)) {
            _tmpSeq = null
          } else {
            _tmpSeq = _stmt.getLong(_columnIndexOfSeq)
          }
          _result =
              DocEntity(_tmpDocId,_tmpAssetId,_tmpAccountId,_tmpName,_tmpDocType,_tmpMimeType,_tmpStorageKey,_tmpDownloadUrl,_tmpOcrRawText,_tmpOcrExtractedJson,_tmpVendor,_tmpTotal,_tmpCurrency,_tmpPurchaseDate,_tmpUploadedBy,_tmpUploadedAt,_tmpCreatedAt,_tmpUpdatedAt,_tmpDeletedAt,_tmpServerVersion,_tmpSeq)
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
    val _sql: String = "UPDATE docs SET deleted_at = ?, updated_at = ? WHERE doc_id = ?"
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
