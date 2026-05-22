package com.avago.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.avago.core.data.db.dao.ChatMessageDao
import com.avago.core.data.db.dao.ChatThreadDao
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.data.db.entity.ChatThreadEntity

@Database(
    entities = [
        ChatThreadEntity::class,
        ChatMessageEntity::class,
    ],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
    exportSchema = true,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao
}
