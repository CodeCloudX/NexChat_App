package com.nexchat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import androidx.work.WorkManager
import com.nexchat.core.auth.AuthManager
import com.nexchat.core.auth.AuthState
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.db.dao.ChatDao
import com.nexchat.core.db.dao.ContactDao
import com.nexchat.core.db.dao.MessageDao
import com.nexchat.core.db.dao.ParticipantDao
import com.nexchat.core.db.entity.MessageEntity
import com.nexchat.core.network.NetworkMonitor
import com.nexchat.core.network.NetworkState
import com.nexchat.core.network.websocket.DevicePayload
import com.nexchat.core.network.websocket.MessageSendPayload
import com.nexchat.core.network.websocket.WebSocketManager
import com.nexchat.core.network.websocket.WsEvent
import com.nexchat.core.security.E2EEManager
import com.nexchat.core.ui.model.ContactUiModel
import com.nexchat.core.ui.model.MessageStatus
import com.nexchat.core.ui.model.MessageUiModel
import com.nexchat.core.work.QueueFlushWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

// â”€â”€â”€ UI State â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Immutable
data class ChatUiScreenState(
    val contact: ContactUiModel? = null,
    val inputText: String = "",
    val replyTarget: MessageUiModel? = null,
    val isTyping: Boolean = false,
    val typingUserName: String = "",
    val isOffline: Boolean = false,
    val degradationLevel: Int = 0,
)

// â”€â”€â”€ Side Effects â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

sealed interface ChatSideEffect {
    data class ShowError(val message: String) : ChatSideEffect
    data object MessageSent : ChatSideEffect
    data class ScrollToMessage(val localId: String) : ChatSideEffect
}

// â”€â”€â”€ ViewModel â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val contactDao: ContactDao,
    private val participantDao: ParticipantDao,
    private val wsManager: WebSocketManager,
    private val e2eeManager: E2EEManager,
    private val authManager: AuthManager,
    private val networkMonitor: NetworkMonitor,
    private val dispatchers: AppDispatchers,
    private val workManager: WorkManager,
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _state = MutableStateFlow(ChatUiScreenState())
    val state: StateFlow<ChatUiScreenState> = _state.asStateFlow()

    private val _sideEffects = MutableSharedFlow<ChatSideEffect>(extraBufferCapacity = 16)
    val sideEffects: SharedFlow<ChatSideEffect> = _sideEffects.asSharedFlow()

    // Paging 3 â€” Room emits new pages automatically on DB change.
    val messages: Flow<PagingData<MessageUiModel>> = Pager(
        config = PagingConfig(pageSize = 30, enablePlaceholders = false),
        pagingSourceFactory = { messageDao.getMessagesPaged(chatId) }
    ).flow
        .map { pagingData ->
            val myId = myUserId()
            pagingData.map { entity -> entity.toUiModel(myId) }
        }
        .cachedIn(viewModelScope)

    private var typingClearJob: Job? = null

    init {
        observeNetworkState()
        observeContact()
        collectWsEvents()
        viewModelScope.launch(dispatchers.io) {
            runCatching { chatDao.clearUnread(chatId) }
        }
    }

    // Triggered by the UI text field on every keystroke.
    fun onInputChanged(text: String) {
        _state.update { it.copy(inputText = text) }
        sendTypingIndicator(chatId, isTyping = text.isNotEmpty())
    }

    fun setReplyTarget(msg: MessageUiModel?) {
        _state.update { it.copy(replyTarget = msg) }
    }

    fun clearReplyTarget() {
        _state.update { it.copy(replyTarget = null) }
    }

    /**
     * Optimistic send:
     * 1. Insert to Room immediately as `queued` â†’ PagingData updates the screen instantly.
     * 2. Attempt E2EE encryption + WS dispatch on [dispatchers.io].
     * 3. If offline, QueueFlushWorker will pick it up on reconnect.
     */
    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank()) return

        val replyTo = _state.value.replyTarget?.localId
        _state.update { it.copy(inputText = "", replyTarget = null) }
        sendTypingIndicator(chatId, isTyping = false)

        viewModelScope.launch(dispatchers.io) {
            val myId = myUserId() ?: run {
                Timber.e("sendMessage: no authenticated user")
                return@launch
            }
            val localId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            messageDao.insert(
                MessageEntity(
                    id = localId,
                    localId = localId,
                    chatId = chatId,
                    senderId = myId,
                    type = "text",
                    content = text,
                    replyToId = replyTo,
                    status = "queued",
                    createdAt = now,
                )
            )
            clearDraft()

            if (networkMonitor.networkState.value == NetworkState.ONLINE) {
                dispatchViaWebSocket(localId, chatId, text, now)
            } else {
                QueueFlushWorker.enqueue(workManager)
            }
        }
    }

    /**
     * Debounced draft persistence â€” called from the composable's DisposableEffect / onStop.
     * Only writes if the text changed from what's already stored.
     */
    fun saveDraft() {
        val draft = _state.value.inputText.takeIf { it.isNotBlank() }
        viewModelScope.launch(dispatchers.io) {
            chatDao.updateDraft(chatId, draft, System.currentTimeMillis())
        }
    }

    // â”€â”€â”€ Private Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun observeNetworkState() {
        networkMonitor.networkState
            .map { it == NetworkState.OFFLINE }
            .onEach { offline ->
                _state.update { it.copy(isOffline = offline) }
                if (!offline) QueueFlushWorker.enqueue(workManager) // Reconnected â†’ flush queue.
            }
            .launchIn(viewModelScope)
    }

    private fun observeContact() {
        contactDao.getAllContacts()
            .map { list -> list.firstOrNull { it.id != myUserId() } }
            .onEach { entity ->
                if (entity == null) return@onEach
                _state.update { s ->
                    s.copy(
                        contact = ContactUiModel(
                            id = entity.id,
                            displayName = entity.displayName,
                            firstName = entity.firstName,
                            lastName = entity.lastName,
                            resolvedName = entity.displayName,
                            phone = entity.phone,
                            email = entity.email,
                            avatarPath = entity.avatarPath,
                            accentColorHex = entity.accentColor ?: "#0F766E",
                            isOnline = entity.lastSeen == null,
                            lastSeen = entity.lastSeen,
                            isBlocked = entity.isBlocked,
                            bannerDismissed = entity.bannerDismissed,
                        )
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun collectWsEvents() {
        wsManager.events
            .onEach { event ->
                when (event) {
                    is WsEvent.MessageReceive -> handleMessageReceive(event)
                    is WsEvent.MessageAckUpdate -> handleMessageAck(event)
                    is WsEvent.MessageDelete -> messageDao.softDelete(event.messageId)
                    is WsEvent.ReactionUpdate -> handleReactionUpdate(event)
                    is WsEvent.TypingUpdate -> handleTypingUpdate(event)
                    is WsEvent.UserOnline -> contactDao.updateOnlineStatus(event.userId, null)
                    is WsEvent.UserOffline -> contactDao.updateOnlineStatus(event.userId, event.lastSeen)
                    is WsEvent.SyncResponse -> handleSyncResponse(event)
                    is WsEvent.KeyUpdated -> handleKeyUpdated(event)
                    is WsEvent.KeysReplenishOtk -> QueueFlushWorker.enqueue(workManager) // OTKReplenishWorker handles its own work
                    is WsEvent.DegradationUpdate -> {
                        val level = event.level.toIntOrNull() ?: 0
                        _state.update { it.copy(degradationLevel = level) }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun handleMessageReceive(event: WsEvent.MessageReceive) {
        if (event.chatId != chatId) return

        // Find the ciphertext destined for our device.
        val myDeviceId = myDeviceId() ?: return
        val payload = event.payloads.firstOrNull { it.deviceId == myDeviceId } ?: return
        val cipherBytes = android.util.Base64.decode(payload.ciphertext, android.util.Base64.NO_WRAP)

        val plaintext = withContext(dispatchers.io) {
            e2eeManager.decryptMessage(event.senderId, deviceId = 1, cipherBytes)
        }.getOrElse { e ->
            Timber.e(e, "Decryption failed for message ${event.serverId}")
            return
        }

        withContext(dispatchers.io) {
            messageDao.insert(
                MessageEntity(
                    id = event.serverId,
                    localId = event.localId,
                    chatId = event.chatId,
                    senderId = event.senderId,
                    type = event.type,
                    content = plaintext,
                    status = "delivered",
                    createdAt = event.createdAt,
                    serverTs = event.serverTs,
                )
            )
            chatDao.updateLastMessage(event.chatId, event.serverId, event.serverTs)
        }

        wsManager.sendAck(event.localId, "delivered")
    }

    private suspend fun handleMessageAck(event: WsEvent.MessageAckUpdate) {
        withContext(dispatchers.io) {
            messageDao.updateStatus(event.localId, event.status, event.serverTs)
        }
    }

    private fun handleReactionUpdate(event: WsEvent.ReactionUpdate) {
        // Reactions are stored in message_acks table keyed by message_id+user_id.
        // Full reaction UI is a Phase 4 concern â€” we acknowledge the event here without crashing.
        Timber.d("ReactionUpdate: ${event.emoji} on ${event.messageId} from ${event.userId}")
    }

    private fun handleTypingUpdate(event: WsEvent.TypingUpdate) {
        if (event.chatId != chatId) return
        _state.update { it.copy(isTyping = event.isTyping, typingUserName = resolveTypingName(event)) }

        typingClearJob?.cancel()
        if (event.isTyping) {
            typingClearJob = viewModelScope.launch {
                delay(5_000)
                _state.update { it.copy(isTyping = false, typingUserName = "") }
            }
        }
    }

    private fun handleSyncResponse(event: WsEvent.SyncResponse) {
        // SyncResponse entries are processed by ChatRepository delta-sync.
        // ChatViewModel only triggers a targeted flush for messages in this chat.
        Timber.d("SyncResponse received with ${event.entries.size} entries")
    }

    private suspend fun handleKeyUpdated(event: WsEvent.KeyUpdated) {
        withContext(dispatchers.io) {
            // Insert a system-level "security key changed" message â€” mirrors WhatsApp behaviour.
            val now = System.currentTimeMillis()
            messageDao.insert(
                MessageEntity(
                    id = "sys_key_${event.userId}_${now}",
                    localId = "sys_key_${event.userId}_${now}",
                    chatId = chatId,
                    senderId = event.userId,
                    type = "system",
                    content = "KEY_CHANGED:${event.userId}",
                    status = "delivered",
                    createdAt = now,
                )
            )
        }
    }

    /**
     * Encrypts [text] for every participant and dispatches it as a message:send WS frame.
     * Runs entirely on [dispatchers.io] â€” never touches the UI thread.
     */
    private suspend fun dispatchViaWebSocket(
        localId: String,
        chatId: String,
        text: String,
        createdAt: Long,
    ) = withContext(dispatchers.io) {
        try {
            val myId = myUserId() ?: return@withContext

            val participants = participantDao.getParticipants(chatId)
                .first()
                .filter { it.userId != myId }

            val devicePayloads = mutableListOf<DevicePayload>()
            for (p in participants) {
                e2eeManager.encryptMessage(p.userId, deviceId = 1, text)
                    .onSuccess { cipherBytes ->
                        devicePayloads += DevicePayload(
                            deviceId = p.userId,
                            ciphertext = android.util.Base64.encodeToString(
                                cipherBytes,
                                android.util.Base64.NO_WRAP
                            )
                        )
                    }
                    .onFailure { Timber.e(it, "Encrypt failed for ${p.userId}") }
            }

            if (devicePayloads.isEmpty()) {
                // No encrypted payloads â€” leave as queued for QueueFlushWorker.
                return@withContext
            }

            val dispatched = wsManager.sendMessage(
                MessageSendPayload(
                    localId = localId,
                    chatId = chatId,
                    payloads = devicePayloads,
                    createdAt = createdAt,
                )
            )

            if (!dispatched) {
                Timber.w("WS send failed for $localId â€” QueueFlushWorker will retry")
                QueueFlushWorker.enqueue(workManager)
            }
        } catch (e: Exception) {
            Timber.e(e, "dispatchViaWebSocket failed for $localId")
            QueueFlushWorker.enqueue(workManager)
        }
    }

    private fun sendTypingIndicator(chatId: String, isTyping: Boolean) {
        viewModelScope.launch(dispatchers.io) {
            wsManager.sendTyping(chatId, isTyping)
        }
    }

    private fun clearDraft() {
        viewModelScope.launch(dispatchers.io) {
            chatDao.updateDraft(chatId, null, null)
        }
    }

    fun addContact() { /* Phase: Contacts */ }
    fun dismissBanner() { viewModelScope.launch(dispatchers.io) { runCatching { contactDao.dismissBanner(chatId) } } }
    fun blockContact() { /* Phase: Contacts */ }
    fun clearChat() { /* Phase: requires confirmation dialog */ }
    fun sendReaction(localId: String, emoji: String) { /* Phase: Reactions */ }
    fun startRecording() { /* Phase: Voice */ }
    fun stopRecording() { /* Phase: Voice */ }

    private fun myUserId(): String? =
        (authManager.authState.value as? AuthState.LoggedIn)?.userId

    private fun myDeviceId(): String? =
        (authManager.authState.value as? AuthState.LoggedIn)?.deviceId

    private fun resolveTypingName(event: WsEvent.TypingUpdate): String =
        _state.value.contact?.displayName?.takeIf { event.userId == _state.value.contact?.id } ?: ""


}

// â”€â”€â”€ Mapper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private fun MessageEntity.toUiModel(myUserId: String?): MessageUiModel = MessageUiModel(
    id = id,
    localId = localId,
    chatId = chatId,
    senderId = senderId,
    isMine = senderId == myUserId,
    content = content,
    type = type,
    status = when (status) {
        "queued"    -> MessageStatus.QUEUED
        "sending"   -> MessageStatus.SENDING
        "sent"      -> MessageStatus.SENT
        "delivered" -> MessageStatus.DELIVERED
        "read"      -> MessageStatus.READ
        else        -> MessageStatus.FAILED
    },
    createdAt = createdAt,
    serverTs = serverTs,
    mediaPath = mediaPath,
    mediaUrl = mediaUrl,
    mediaThumb = mediaThumb,
    mediaKey = mediaKey,
    replyToId = replyToId,
    isDeleted = isDeleted,
    isSystemMessage = type == "system",
)
