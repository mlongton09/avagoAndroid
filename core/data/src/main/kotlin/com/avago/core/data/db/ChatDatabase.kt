package com.avago.core.data.db

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
    version = 1,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao
}
