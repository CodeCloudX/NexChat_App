package com.nexchat.core.db.di

import android.content.Context
import androidx.room.Room
import com.nexchat.core.db.DatabaseKeyManager
import com.nexchat.core.db.NexChatDatabase
import com.nexchat.core.db.dao.ChatDao
import com.nexchat.core.db.dao.ContactDao
import com.nexchat.core.db.dao.MediaDao
import com.nexchat.core.db.dao.MessageAckDao
import com.nexchat.core.db.dao.MessageDao
import com.nexchat.core.db.dao.ParticipantDao
import com.nexchat.core.db.dao.SignalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext ctx: Context,
        keyManager: DatabaseKeyManager,
    ): NexChatDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(keyManager.getSQLCipherPassphrase())
        return Room.databaseBuilder(ctx, NexChatDatabase::class.java, "nexchat.db")
            .openHelperFactory(factory)
            .addCallback(NexChatDatabase.FTS_CALLBACK)
            .build()
    }

    @Provides fun provideMessageDao(db: NexChatDatabase): MessageDao = db.messageDao()
    @Provides fun provideChatDao(db: NexChatDatabase): ChatDao = db.chatDao()
    @Provides fun provideContactDao(db: NexChatDatabase): ContactDao = db.contactDao()
    @Provides fun provideParticipantDao(db: NexChatDatabase): ParticipantDao = db.participantDao()
    @Provides fun provideMessageAckDao(db: NexChatDatabase): MessageAckDao = db.messageAckDao()
    @Provides fun provideSignalDao(db: NexChatDatabase): SignalDao = db.signalDao()
    @Provides fun provideMediaDao(db: NexChatDatabase): MediaDao = db.mediaDao()
}
