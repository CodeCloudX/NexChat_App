package com.nexchat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.db.dao.ContactDao
import com.nexchat.core.db.dao.MessageDao
import com.nexchat.core.db.entity.ChatEntity
import com.nexchat.core.network.NetworkState
import com.nexchat.core.network.NetworkMonitor
import com.nexchat.core.network.websocket.WebSocketManager
import com.nexchat.core.network.websocket.WsEvent
import com.nexchat.core.network.websocket.WsState
import com.nexchat.feature.chat.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Stable projection of a Room ChatEntity for Compose. */
@Immutable
data class ChatUiModel(
    val chatId: String,
    val name: String,
    val avatarPath: String?,
    val accentColor: String,
    val isOnline: Boolean,
    val lastMessagePreview: String?,
    val lastMessageTime: String?,
    val lastMessageStatus: String?,
    val unreadCount: Int,
    val draftText: String?,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isMuted: Boolean,
    val isQueued: Boolean,
)

sealed interface ChatListSideEffect {
    data class ShowSnackbar(val message: String) : ChatListSideEffect
    data class NavigateToChat(val chatId: String) : ChatListSideEffect
    data class NavigateToProfile(val userId: String) : ChatListSideEffect
    data object ShowArchiveUndo : ChatListSideEffect
}

/** Three-state banner matching WhatsApp UX: hidden when healthy. */
enum class ConnectivityBanner { NONE, NO_NETWORK, CONNECTING }

data class ChatListUiState(
    val chats: PersistentList<ChatUiModel> = persistentListOf(),
    val banner: ConnectivityBanner = ConnectivityBanner.NONE,
    val searchQuery: String = "",
    val searchResults: PersistentList<ChatUiModel> = persistentListOf(),
    val isSyncing: Boolean = false,
) {
    val isOffline: Boolean get() = banner == ConnectivityBanner.NO_NETWORK
}

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val networkMonitor: NetworkMonitor,
    private val wsManager: WebSocketManager,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private val _sideEffects = MutableSharedFlow<ChatListSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<ChatListSideEffect> = _sideEffects.asSharedFlow()

    init {
        // 1. Room flow → instant load with zero network wait
        chatRepository.getChats()
            .map { list -> list.map { it.toChatUiModel() }.toPersistentList() }
            .onEach { chats -> _uiState.update { it.copy(chats = chats) } }
            .launchIn(viewModelScope)

        // 2. Three-state connectivity banner: NO_NETWORK → CONNECTING → NONE
        combine(networkMonitor.networkState, wsManager.state) { net, ws ->
            when {
                net == NetworkState.OFFLINE -> ConnectivityBanner.NO_NETWORK
                ws != WsState.CONNECTED -> ConnectivityBanner.CONNECTING
                else -> ConnectivityBanner.NONE
            }
        }
            .distinctUntilChanged()
            .onEach { banner -> _uiState.update { it.copy(banner = banner) } }
            .launchIn(viewModelScope)

        // 3. Fire-and-forget delta-sync — Room Flow auto-refreshes UI when DB changes
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isSyncing = true) }
            chatRepository.syncChats()
            _uiState.update { it.copy(isSyncing = false) }
        }

        // 4. WebSocket events → patch DB → Room Flow auto-refreshes UI
        collectWsEvents()
    }

    /**
     * Debounced FTS5 search: 150ms debounce prevents hammering SQLite
     * on every keystroke. Empty query clears results instantly.
     */
    fun onSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = persistentListOf()) }
            return
        }
        viewModelScope.launch(dispatchers.io) {
            try {
                // Wrap the FTS5 query in quotes for exact phrase matching.
                val results = messageDao.searchFts("\"$query\"")
                val chatIds = results.map { it.chatId }.distinct()
                val matchingChats = _uiState.value.chats.filter { it.chatId in chatIds }
                _uiState.update { it.copy(searchResults = matchingChats.toPersistentList()) }
            } catch (e: Exception) {
                Timber.e(e, "onSearch: FTS5 error for query=$query")
            }
        }
    }

    fun onChatTap(chatId: String) {
        viewModelScope.launch { _sideEffects.emit(ChatListSideEffect.NavigateToChat(chatId)) }
    }

    fun onAvatarTap(userId: String) {
        viewModelScope.launch { _sideEffects.emit(ChatListSideEffect.NavigateToProfile(userId)) }
    }

    fun onArchiveChat(chatId: String) {
        viewModelScope.launch(dispatchers.io) {
            // Archive is local-only state — no server round-trip needed.
            // Server-side archiving not in current backend contract.
            _sideEffects.emit(ChatListSideEffect.ShowArchiveUndo)
        }
    }

    private fun collectWsEvents() {
        // New message → update the chat's lastMessage pointer so the list re-orders.
        wsManager.events
            .filterIsInstance<WsEvent.MessageReceive>()
            .onEach { ev ->
                viewModelScope.launch(dispatchers.io) {
                    try {
                        chatRepository.syncChats() // re-sync to pick up unread counts
                    } catch (e: Exception) {
                        Timber.e(e, "collectWsEvents: syncChats on MessageReceive failed")
                    }
                }
            }
            .launchIn(viewModelScope)

        // Message deleted → soft-delete reflected via Room Flow automatically.
        wsManager.events
            .filterIsInstance<WsEvent.MessageDelete>()
            .onEach { ev ->
                viewModelScope.launch(dispatchers.io) {
                    try {
                        messageDao.softDelete(ev.messageId)
                    } catch (e: Exception) {
                        Timber.e(e, "collectWsEvents: softDelete failed msgId=${ev.messageId}")
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private fun ChatEntity.toChatUiModel(): ChatUiModel {
        val timeLabel = lastMessageAt?.let { formatTimestamp(it) }
        return ChatUiModel(
            chatId = id,
            name = name ?: id,
            avatarPath = avatarPath,
            accentColor = accentColor,
            isOnline = false, // resolved via presence events — initially false
            lastMessagePreview = null, // resolved from MessageDao in a separate query if needed
            lastMessageTime = timeLabel,
            lastMessageStatus = null,
            unreadCount = unreadCount,
            draftText = draftText,
            isPinned = isPinned,
            isArchived = isArchived,
            isMuted = isMuted,
            isQueued = false,
        )
    }

    private fun formatTimestamp(epochMs: Long): String {
        val now = Instant.now().toEpochMilli()
        val diffMs = now - epochMs
        val instant = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())

        return when {
            diffMs < 60_000 -> "now"
            diffMs < 3_600_000 -> "${diffMs / 60_000}m"
            diffMs < 86_400_000 -> DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(instant)
            diffMs < 604_800_000 -> DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(instant)
            else -> DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(instant)
        }
    }
}
