package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reorder_suggestions",
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["part_id"]),
        Index(value = ["status"]),
    ],
)
data class ReorderSuggestionEntity(
    @PrimaryKey
    @ColumnInfo(name = "suggestion_id")
    val suggestionId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "part_id")
    val partId: String,

    @ColumnInfo(name = "quantity_on_hand")
    val quantityOnHand: Double,

    @ColumnInfo(name = "reorder_qty")
    val reorderQty: Double?,

    @ColumnInfo(name = "suggested_qty")
    val suggestedQty: Double,

    @ColumnInfo(name = "preferred_vendor_id")
    val preferredVendorId: String?,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "reason")
    val reason: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
