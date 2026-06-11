package com.nexchat.core.network.websocket

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nexchat.core.common.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class WsState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val cborParser: CborParser,
    private val dispatchers: AppDispatchers
) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val _state = MutableStateFlow(WsState.DISCONNECTED)
    val state: StateFlow<WsState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WsEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private val backoffMs = listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L)
    private var retryAttempt = 0
    private var retryJob: Job? = null
    private var currentSocket: WebSocket? = null
    private var currentToken: String? = null

    init {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this@WebSocketManager)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        val token = currentToken ?: return
        if (_state.value == WsState.DISCONNECTED) {
            Timber.tag("WebSocket").d("App entered foreground. Reconnecting WS.")
            retryAttempt = 0
            connect(token)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        Timber.tag("WebSocket").d("App entered background. Disconnecting WS gracefully.")
        retryJob?.cancel()
        currentSocket?.close(1000, "app_backgrounded")
        _state.value = WsState.DISCONNECTED
    }

    @Synchronized
    fun connect(token: String) {
        if (_state.value == WsState.CONNECTED || _state.value == WsState.CONNECTING) return
        
        currentToken = token
        _state.value = WsState.CONNECTING

        val request = Request.Builder()
            .url(com.nexchat.core.network.BuildConfig.WS_BASE_URL)
            .header("Authorization", "Bearer $token")
            .build()

        currentSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.tag("WebSocket").d("Connected successfully")
                retryAttempt = 0
                _state.value = WsState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    val event = cborParser.decode(bytes.toByteArray())
                    scope.launch { _events.emit(event) }
                } catch (e: Exception) {
                    Timber.tag("WebSocket").e(e, "Failed to decode incoming message")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.tag("WebSocket").e(t, "Connection failure")
                _state.value = WsState.DISCONNECTED
                reconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.tag("WebSocket").d("Connection closed: $code $reason")
                _state.value = WsState.DISCONNECTED
                // If code is 1000 (normal closure), we do NOT automatically reconnect.
                if (code != 1000) {
                    reconnect()
                }
            }
        })
    }

    @Synchronized
    fun reconnect() {
        if (_state.value == WsState.CONNECTING) return
        
        val token = currentToken ?: return
        retryJob?.cancel()

        val backoff = backoffMs[retryAttempt.coerceAtMost(backoffMs.lastIndex)]
        retryAttempt++

        Timber.tag("WebSocket").d("Scheduling reconnect in ${backoff}ms (Attempt $retryAttempt)")
        retryJob = scope.launch {
            delay(backoff)
            connect(token)
        }
    }

    @Synchronized
    fun reconnectImmediate() {
        Timber.tag("WebSocket").d("Immediate reconnect requested")
        retryJob?.cancel()
        retryAttempt = 0
        currentSocket?.cancel() // Force kill the current stale socket without a polite close
        _state.value = WsState.DISCONNECTED
        currentToken?.let { connect(it) }
    }

    @Synchronized
    fun disconnect(code: Int = 1000, reason: String = "User requested disconnect") {
        retryJob?.cancel()
        retryAttempt = 0
        currentToken = null
        currentSocket?.close(code, reason)
        currentSocket = null
        _state.value = WsState.DISCONNECTED
    }

    fun sendBinary(bytes: ByteArray): Boolean {
        return currentSocket?.send(ByteString.of(*bytes)) == true
    }

    /** Encodes [payload] as a CBOR message:send envelope and dispatches it. */
    fun sendMessage(payload: MessageSendPayload): Boolean {
        val bytes = cborParser.encode("message:send", payload)
        return sendBinary(bytes)
    }

    /** Sends a message:ack envelope telling the server the message was delivered/read. */
    fun sendAck(localId: String, status: String): Boolean {
        @kotlinx.serialization.Serializable
        data class AckPayload(
            @kotlinx.serialization.SerialName("local_id") val localId: String,
            @kotlinx.serialization.SerialName("status") val status: String
        )
        val bytes = cborParser.encode("message:ack", AckPayload(localId, status))
        return sendBinary(bytes)
    }

    /** Sends a typing:update envelope. */
    fun sendTyping(chatId: String, isTyping: Boolean): Boolean {
        @kotlinx.serialization.Serializable
        data class TypingPayload(
            @kotlinx.serialization.SerialName("chat_id") val chatId: String,
            @kotlinx.serialization.SerialName("is_typing") val isTyping: Boolean
        )
        val bytes = cborParser.encode("typing:update", TypingPayload(chatId, isTyping))
        return sendBinary(bytes)
    }
}
