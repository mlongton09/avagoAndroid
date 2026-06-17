package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 100: PO comments
@Entity(
    tableName = "po_comments",
    indices = [Index(value = ["po_id"])]
)
data class PoCommentEntity(
    @PrimaryKey
    @ColumnInfo(name = "comment_id")
    val commentId: String,

    @ColumnInfo(name = "po_id")
    val poId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "author_id")
    val authorId: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long = 0L,

    @ColumnInfo(name = "seq")
    val seq: Long? = null,
)
