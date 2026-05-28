package com.avago.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.avago.core.data.db.dao.ChatAccountRosterDao
import com.avago.core.data.db.dao.ChatMentionDao
import com.avago.core.data.db.dao.ChatMessageDao
import com.avago.core.data.db.dao.ChatOutboxDao
import com.avago.core.data.db.dao.ChatPresenceDao
import com.avago.core.data.db.dao.ChatReactionDao
import com.avago.core.data.db.dao.ChatSyncStateDao
import com.avago.core.data.db.dao.ChatThreadDao
import com.avago.core.data.db.dao.ChatThreadLastReadDao
import com.avago.core.data.db.dao.ChatThreadMemberDao
import com.avago.core.data.db.entity.ChatAccountRosterEntity
import com.avago.core.data.db.entity.ChatMentionEntity
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.data.db.entity.ChatOutboxEntity
import com.avago.core.data.db.entity.ChatPresenceEntity
import com.avago.core.data.db.entity.ChatReactionEntity
import com.avago.core.data.db.entity.ChatSyncStateEntity
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.core.data.db.entity.ChatThreadLastReadEntity
import com.avago.core.data.db.entity.ChatThreadMemberEntity

@Database(
    entities = [
        ChatThreadEntity::class,
        ChatMessageEntity::class,
        ChatThreadMemberEntity::class,
        ChatReactionEntity::class,
        ChatMentionEntity::class,
        ChatPresenceEntity::class,
        ChatSyncStateEntity::class,
        ChatAccountRosterEntity::class,
        ChatOutboxEntity::class,
        ChatThreadLastReadEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatThreadMemberDao(): ChatThreadMemberDao
    abstract fun chatReactionDao(): ChatReactionDao
    abstract fun chatMentionDao(): ChatMentionDao
    abstract fun chatPresenceDao(): ChatPresenceDao
    abstract fun chatSyncStateDao(): ChatSyncStateDao
    abstract fun chatAccountRosterDao(): ChatAccountRosterDao
    abstract fun chatOutboxDao(): ChatOutboxDao
    abstract fun chatThreadLastReadDao(): ChatThreadLastReadDao
}
