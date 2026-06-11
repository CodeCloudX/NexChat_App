package com.nexchat.core.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nexchat.core.db.dao.MessageDao
import com.nexchat.core.db.entity.MessageEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MessageDaoTest {

    private lateinit var db: NexChatDatabase
    private lateinit var messageDao: MessageDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // In-memory Room database for fast UI tests, using the same FTS callback.
        db = Room.inMemoryDatabaseBuilder(context, NexChatDatabase::class.java)
            .addCallback(NexChatDatabase.FTS_CALLBACK)
            .allowMainThreadQueries()
            .build()
        messageDao = db.messageDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun createDummyMessage(
        id: String = UUID.randomUUID().toString(),
        content: String = "Hello world",
        status: String = "queued",
        isDeleted: Boolean = false,
        createdAt: Long = System.currentTimeMillis()
    ) = MessageEntity(
        id = id,
        localId = "local_$id",
        chatId = "chat_1",
        senderId = "user_1",
        type = "text",
        content = content,
        status = status,
        isDeleted = isDeleted,
        createdAt = createdAt
    )

    @Test
    fun `TestSearchFts_MatchesContent`() = runTest {
        // Insert messages
        val msg1 = createDummyMessage(content = "This is a secret meeting")
        val msg2 = createDummyMessage(content = "Just a normal day")
        messageDao.insertAll(listOf(msg1, msg2))

        // Search using FTS
        val results = messageDao.searchFts("secret")

        assertThat(results).hasSize(1)
        assertThat(results.first().id).isEqualTo(msg1.id)
    }

    @Test
    fun `TestSoftDelete_FilteredOut`() = runTest {
        val msg1 = createDummyMessage(isDeleted = false)
        val msg2 = createDummyMessage(isDeleted = true)
        messageDao.insertAll(listOf(msg1, msg2))

        val results = messageDao.searchFts("world")
        
        // FTS query explicitly filters out is_deleted = 1
        assertThat(results).hasSize(1)
        assertThat(results.first().id).isEqualTo(msg1.id)
    }

    @Test
    fun `TestUpdateStatus_Queued_to_Sent`() = runTest {
        val msg = createDummyMessage(status = "queued")
        messageDao.insert(msg)

        val serverTs = System.currentTimeMillis()
        messageDao.updateStatus(msg.localId, "sent", serverTs)

        val queuedMessages = messageDao.getQueuedMessages()
        assertThat(queuedMessages).isEmpty()
    }

    @Test
    fun `TestGetQueued_OrderedByCreatedAt`() = runTest {
        val msg1 = createDummyMessage(createdAt = 1000)
        val msg2 = createDummyMessage(createdAt = 2000)
        val msg3 = createDummyMessage(createdAt = 500)
        messageDao.insertAll(listOf(msg1, msg2, msg3))

        val queued = messageDao.getQueuedMessages()
        
        assertThat(queued).hasSize(3)
        // Should be ordered by created_at ASC
        assertThat(queued[0].id).isEqualTo(msg3.id)
        assertThat(queued[1].id).isEqualTo(msg1.id)
        assertThat(queued[2].id).isEqualTo(msg2.id)
    }
}
