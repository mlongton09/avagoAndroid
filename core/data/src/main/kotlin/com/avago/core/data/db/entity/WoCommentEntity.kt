package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wo_comments",
    indices = [
        Index(value = ["wo_id", "deleted_at"]),
    ]
)
data class WoCommentEntity(
    @PrimaryKey
    @ColumnInfo(name = "comment_id")
    val commentId: String,

    @ColumnInfo(name = "wo_id")
    val woId: String,

    @ColumnInfo(name = "author_id")
    val authorId: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "seq")
    val seq: Long?,

    // Change 1: @mention user IDs stored as JSON array text e.g. ["user-uuid-1","user-uuid-2"]
    @ColumnInfo(name = "mentioned_user_ids")
    val mentionedUserIds: String? = null,

    // Change 5: internal-only visibility flag (hidden from requester-role views)
    @ColumnInfo(name = "is_internal", defaultValue = "0")
    val isInternal: Boolean = false,

    // Change 14: comment type enum e.g. "user" | "system" | "activity"
    @ColumnInfo(name = "comment_type")
    val commentType: String? = null,

    // Change 64: audit trail — who created / last updated the comment
    @ColumnInfo(name = "created_by_id")
    val createdById: String? = null,

    @ColumnInfo(name = "updated_by_id")
    val updatedById: String? = null,
)
