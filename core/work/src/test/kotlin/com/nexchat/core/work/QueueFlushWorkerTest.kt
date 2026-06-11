package com.nexchat.core.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.nexchat.core.auth.AuthManager
import com.nexchat.core.auth.AuthState
import com.nexchat.core.db.dao.MessageDao
import com.nexchat.core.db.dao.ParticipantDao
import com.nexchat.core.db.entity.MessageEntity
import com.nexchat.core.db.entity.ParticipantEntity
import com.nexchat.core.network.websocket.WebSocketManager
import com.nexchat.core.network.websocket.WsState
import com.nexchat.core.security.E2EEManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QueueFlushWorkerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val participantDao = mockk<ParticipantDao>(relaxed = true)
    private val wsManager = mockk<WebSocketManager>(relaxed = true)
    private val e2eeManager = mockk<E2EEManager>(relaxed = true)
    private val authManager = mockk<AuthManager>(relaxed = true)

    private val myUserId = "user-me"

    private fun buildMessage(
        localId: String,
        content: String = "hello",
        createdAt: Long,
    ) = MessageEntity(
        id = localId,
        localId = localId,
        chatId = "chat-1",
        senderId = myUserId,
        type = "text",
        content = content,
        status = "queued",
        createdAt = createdAt,
    )

    @Before
    fun setUp() {
        every { authManager.authState } returns MutableStateFlow(AuthState.LoggedIn(myUserId, "device-me"))
        every { wsManager.state } returns MutableStateFlow(WsState.CONNECTED)
        every { wsManager.sendMessage(any()) } returns true
    }

    private fun buildWorker(): QueueFlushWorker {
        return TestListenableWorkerBuilder<QueueFlushWorker>(context)
            .setWorkerFactory(
                com.nexchat.core.work.di.HiltWorkerFactory_QueueFlushWorkerFactory(
                    messageDao = messageDao,
                    participantDao = participantDao,
                    webSocketManager = wsManager,
                    e2eeManager = e2eeManager,
                    authManager = authManager,
                ).let {
                    // Fallback: create worker directly for testing.
                    object : androidx.work.WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker = QueueFlushWorker(
                            context = appContext,
                            workerParams = workerParameters,
                            messageDao = messageDao,
                            participantDao = participantDao,
                            webSocketManager = wsManager,
                            e2eeManager = e2eeManager,
                            authManager = authManager,
                        )
                    }
                }
            )
            .build() as QueueFlushWorker
    }

    // ─── Order test ──────────────────────────────────────────────────────────

    @Test
    fun `TestFlushesInOrder_ByCreatedAt — messages sent in created_at ASC order`() = runTest {
        val participants = listOf(ParticipantEntity("chat-1", "user-other", joinedAt = 0L))
        every { participantDao.getParticipants(any()) } returns flowOf(participants)
        coEvery { e2eeManager.encryptMessage(any(), any(), any()) } returns Result.success("c".toByteArray())

        val sentOrder = mutableListOf<String>()
        every { wsManager.sendMessage(any()) } answers {
            sentOrder += firstArg<com.nexchat.core.network.websocket.MessageSendPayload>().localId
            true
        }

        // DB returns messages in ASC order (MessageDao guarantees ORDER BY created_at ASC).
        coEvery { messageDao.getQueuedMessages() } returns listOf(
            buildMessage("msg-1", createdAt = 1000L),
            buildMessage("msg-2", createdAt = 2000L),
            buildMessage("msg-3", createdAt = 3000L),
        )

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf("msg-1", "msg-2", "msg-3"), sentOrder)
    }

    // ─── Failure after 3 retries ─────────────────────────────────────────────

    @Test
    fun `TestMarksFailed_After3Retries — markFailed called after all attempts exhausted`() = runTest {
        val participants = listOf(ParticipantEntity("chat-1", "user-other", joinedAt = 0L))
        every { participantDao.getParticipants(any()) } returns flowOf(participants)
        coEvery { e2eeManager.encryptMessage(any(), any(), any()) } throws RuntimeException("signal error")
        coEvery { messageDao.getQueuedMessages() } returns listOf(buildMessage("fail-msg", createdAt = 1L))

        val worker = buildWorker()
        val result = worker.doWork()

        coVerify(exactly = 1) { messageDao.markFailed("fail-msg") }
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    // ─── Partial failure → retry ──────────────────────────────────────────────

    @Test
    fun `TestRetry_WhenSomeFail — worker returns retry when at least one message fails`() = runTest {
        val participants = listOf(ParticipantEntity("chat-1", "user-other", joinedAt = 0L))
        every { participantDao.getParticipants(any()) } returns flowOf(participants)

        var callCount = 0
        coEvery { e2eeManager.encryptMessage(any(), any(), any()) } answers {
            callCount++
            if (callCount <= 3) throw RuntimeException("fail") // first message always fails
            Result.success("c".toByteArray()) // second message succeeds
        }
        every { wsManager.sendMessage(any()) } returns true

        coEvery { messageDao.getQueuedMessages() } returns listOf(
            buildMessage("bad-msg", createdAt = 1L),
            buildMessage("good-msg", createdAt = 2L),
        )

        val worker = buildWorker()
        val result = worker.doWork()

        coVerify { messageDao.markFailed("bad-msg") }
        verify { wsManager.sendMessage(match { it.localId == "good-msg" }) }
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    // ─── Empty queue ──────────────────────────────────────────────────────────

    @Test
    fun `returns success immediately when queue is empty`() = runTest {
        coEvery { messageDao.getQueuedMessages() } returns emptyList()

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { wsManager.sendMessage(any()) }
    }
}
