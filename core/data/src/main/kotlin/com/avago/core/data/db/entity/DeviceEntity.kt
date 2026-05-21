package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String?,

    @ColumnInfo(name = "platform")
    val platform: String,

    @ColumnInfo(name = "push_token")
    val pushToken: String?,

    @ColumnInfo(name = "app_version")
    val appVersion: String?,

    @ColumnInfo(name = "os_version")
    val osVersion: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
