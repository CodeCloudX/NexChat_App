package com.nexchat.feature.chat.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.paging.AsyncPagingDataDiffer
import app.cash.turbine.test
import com.nexchat.core.auth.AuthManager
import com.nexchat.core.auth.AuthState
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.db.dao.ChatDao
import com.nexchat.core.db.dao.ContactDao
import com.nexchat.core.db.dao.MessageDao
import com.nexchat.core.db.dao.ParticipantDao
import com.nexchat.core.db.entity.MessageEntity
import com.nexchat.core.db.entity.ParticipantEntity
import com.nexchat.core.network.NetworkMonitor
import com.nexchat.core.network.NetworkState
import com.nexchat.core.network.websocket.WebSocketManager
import com.nexchat.core.network.websocket.WsEvent
import com.nexchat.core.network.websocket.WsState
import com.nexchat.core.security.E2EEManager
import com.nexchat.core.work.QueueFlushWorker
import androidx.work.WorkManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)
    private val dispatchers = AppDispatchers(
        main = testDispatcher,
        io = testDispatcher,
        default = testDispatcher,
    )

    private val chatId = "chat-001"
    private val myUserId = "user-me"
    private val myDeviceId = "device-me"

    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val contactDao = mockk<ContactDao>(relaxed = true)
    private val participantDao = mockk<ParticipantDao>(relaxed = true)
    private val wsManager = mockk<WebSocketManager>(relaxed = true)
    private val e2eeManager = mockk<E2EEManager>(relaxed = true)
    private val authManager = mockk<AuthManager>(relaxed = true)
    private val networkMonitor = mockk<NetworkMonitor>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    private val wsEvents = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)

    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        every { authManager.authState } returns MutableStateFlow(AuthState.LoggedIn(myUserId, myDeviceId))
        every { wsManager.state } returns MutableStateFlow(WsState.CONNECTED)
        every { wsManager.events } returns wsEvents
        every { networkMonitor.networkState } returns MutableStateFlow(NetworkState.ONLINE)
        every { contactDao.getAllContacts() } returns flowOf(emptyList())
        coEvery { messageDao.getQueuedMessages() } returns emptyList()

        viewModel = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("chatId" to chatId)),
            messageDao = messageDao,
            chatDao = chatDao,
            contactDao = contactDao,
            participantDao = participantDao,
            wsManager = wsManager,
            e2eeManager = e2eeManager,
            authManager = authManager,
            networkMonitor = networkMonitor,
            dispatchers = dispatchers,
            workManager = workManager,
        )
    }

    // ─── sendMessage ─────────────────────────────────────────────────────────

    @Test
    fun `sendMessage offline inserts with status queued`() = runTest(scheduler) {
        every { networkMonitor.networkState } returns MutableStateFlow(NetworkState.OFFLINE)

        viewModel.onInputChanged("Hello offline")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            messageDao.insert(match { it.status == "queued" && it.content == "Hello offline" })
        }
    }

    @Test
    fun `sendMessage online dispatches via WebSocket`() = runTest(scheduler) {
        val participants = listOf(ParticipantEntity(chatId, "user-other", joinedAt = 0L))
        every { participantDao.getParticipants(chatId) } returns flowOf(participants)
        coEvery { e2eeManager.encryptMessage(any(), any(), any()) } returns Result.success("cipher".toByteArray())
        every { wsManager.sendMessage(any()) } returns true

        viewModel.onInputChanged("Hello online")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { wsManager.sendMessage(any()) }
    }

    @Test
    fun `sendMessage clears input immediately`() = runTest(scheduler) {
        viewModel.onInputChanged("typing...")
        viewModel.sendMessage()

        assertEquals("", viewModel.state.value.inputText)
    }

    // ─── Draft ───────────────────────────────────────────────────────────────

    @Test
    fun `saveDraft calls chatDao updateDraft with input text`() = runTest(scheduler) {
        viewModel.onInputChanged("draft text")
        viewModel.saveDraft()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { chatDao.updateDraft(chatId, "draft text", any()) }
    }

    @Test
    fun `sendMessage clears draft after send`() = runTest(scheduler) {
        every { networkMonitor.networkState } returns MutableStateFlow(NetworkState.OFFLINE)
        viewModel.onInputChanged("will be cleared")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { chatDao.updateDraft(chatId, null, null) }
    }

    // ─── WS Events ───────────────────────────────────────────────────────────

    @Test
    fun `WsEvent_MessageReceive decrypts stores and sends delivered ack`() = runTest(scheduler) {
        val cipher = "encrypted".toByteArray()
        val cipherB64 = android.util.Base64.encodeToString(cipher, android.util.Base64.NO_WRAP)
        coEvery { e2eeManager.decryptMessage(any(), any(), any()) } returns Result.success("hello world")

        wsEvents.emit(
            WsEvent.MessageReceive(
                localId = "local-1",
                chatId = chatId,
                senderId = "sender-x",
                type = "text",
                payloads = listOf(
                    com.nexchat.core.network.websocket.DevicePayload(myDeviceId, cipherB64)
                ),
                createdAt = 1000L,
                serverId = "server-1",
                serverTs = 2000L,
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { e2eeManager.decryptMessage("sender-x", 1, cipher) }
        coVerify { messageDao.insert(match { it.content == "hello world" && it.status == "delivered" }) }
        verify { wsManager.sendAck("local-1", "delivered") }
    }

    @Test
    fun `WsEvent_KeyUpdated inserts system message with KEY_CHANGED prefix`() = runTest(scheduler) {
        wsEvents.emit(WsEvent.KeyUpdated(userId = "alice", keyVersion = 2))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            messageDao.insert(match {
                it.type == "system" && it.content?.startsWith("KEY_CHANGED:alice") == true
            })
        }
    }

    @Test
    fun `WsEvent_TypingUpdate sets isTyping true then clears after 5s`() = runTest(scheduler) {
        wsEvents.emit(WsEvent.TypingUpdate(userId = "alice", chatId = chatId, isTyping = true))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isTyping)

        // Advance past the 5-second auto-clear.
        testDispatcher.scheduler.advanceTimeBy(5_100)
        assertFalse(viewModel.state.value.isTyping)
    }

    @Test
    fun `WsEvent_MessageAckUpdate updates local message status`() = runTest(scheduler) {
        wsEvents.emit(WsEvent.MessageAckUpdate(localId = "l1", serverId = "s1", serverTs = 999L, status = "read"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { messageDao.updateStatus("l1", "read", 999L) }
    }

    @Test
    fun `WsEvent_MessageDelete calls softDelete`() = runTest(scheduler) {
        wsEvents.emit(WsEvent.MessageDelete(messageId = "m1", chatId = chatId))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { messageDao.softDelete("m1") }
    }

    @Test
    fun `WsEvent_UserOnline sets lastSeen null in contactDao`() = runTest(scheduler) {
        wsEvents.emit(WsEvent.UserOnline(userId = "alice"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { contactDao.updateOnlineStatus("alice", null) }
    }

    @Test
    fun `WsEvent_UserOffline stores lastSeen timestamp`() = runTest(scheduler) {
        wsEvents.emit(WsEvent.UserOffline(userId = "alice", lastSeen = 12345L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { contactDao.updateOnlineStatus("alice", 12345L) }
    }
}
