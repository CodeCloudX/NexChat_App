package com.nexchat.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nexchat.core.db.dao.ChatDao
import com.nexchat.core.db.dao.ContactDao
import com.nexchat.core.db.dao.MediaDao
import com.nexchat.core.db.dao.MessageAckDao
import com.nexchat.core.db.dao.MessageDao
import com.nexchat.core.db.dao.ParticipantDao
import com.nexchat.core.db.dao.SignalDao
import com.nexchat.core.db.entity.ChatEntity
import com.nexchat.core.db.entity.ContactEntity
import com.nexchat.core.db.entity.MediaEntity
import com.nexchat.core.db.entity.MessageAckEntity
import com.nexchat.core.db.entity.MessageEntity
import com.nexchat.core.db.entity.ParticipantEntity
import com.nexchat.core.db.entity.SignalIdentityKeyEntity
import com.nexchat.core.db.entity.SignalPreKeyEntity
import com.nexchat.core.db.entity.SignalSessionEntity
import com.nexchat.core.db.entity.SignalSignedPreKeyEntity
import androidx.room.RoomDatabase.Callback
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MessageEntity::class,
        ChatEntity::class,
        ContactEntity::class,
        ParticipantEntity::class,
        MessageAckEntity::class,
        MediaEntity::class,
        SignalSessionEntity::class,
        SignalPreKeyEntity::class,
        SignalSignedPreKeyEntity::class,
        SignalIdentityKeyEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NexChatDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun contactDao(): ContactDao
    abstract fun participantDao(): ParticipantDao
    abstract fun messageAckDao(): MessageAckDao
    abstract fun signalDao(): SignalDao
    abstract fun mediaDao(): MediaDao

    companion object {
        // FTS5 cannot be declared as a Room @Entity because Room's KSP processor cannot resolve
        // contentEntity class references across entities in the same module (KSP 2.1.x limitation).
        // A Callback is the production-correct pattern used by Signal/WhatsApp for FTS5 search.
        val FTS_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts
                    USING fts5(content, content='messages', content_rowid='rowid')
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS messages_fts_ai
                    AFTER INSERT ON messages BEGIN
                        INSERT INTO messages_fts(rowid, content) VALUES (new.rowid, new.content);
                    END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS messages_fts_ad
                    AFTER DELETE ON messages BEGIN
                        INSERT INTO messages_fts(messages_fts, rowid, content)
                        VALUES ('delete', old.rowid, old.content);
                    END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS messages_fts_au
                    AFTER UPDATE ON messages BEGIN
                        INSERT INTO messages_fts(messages_fts, rowid, content)
                        VALUES ('delete', old.rowid, old.content);
                        INSERT INTO messages_fts(rowid, content) VALUES (new.rowid, new.content);
                    END
                """.trimIndent())
            }
        }
    }
}
