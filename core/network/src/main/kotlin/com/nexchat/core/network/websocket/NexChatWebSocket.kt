package com.nexchat.core.network.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NexChatWebSocket @Inject constructor(
    private val wsManager: WebSocketManager,
    private val cborParser: CborParser
) {
    fun sendMessage(
        localId: String,
        chatId: String,
        recipientUserId: String,
        payloads: List<DevicePayload>,
        createdAt: Long,
        mediaUrl: String? = null,
        replyToId: String? = null
    ) {
        val payload = SendMessagePayload(
            localId = localId,
            chatId = chatId,
            recipientUserId = recipientUserId,
            payloads = payloads,
            createdAt = createdAt,
            mediaUrl = mediaUrl,
            replyToId = replyToId
        )
        val bytes = cborParser.encode("message:send", payload)
        wsManager.sendBinary(bytes)
    }

    fun sendAck(messageId: String, status: String) {
        val payload = SendAckPayload(
            messageId = messageId,
            status = status
        )
        val bytes = cborParser.encode("message:ack", payload)
        wsManager.sendBinary(bytes)
    }

    fun sendTypingStart(chatId: String) {
        val payload = TypingPayload(chatId = chatId, isTyping = true)
        val bytes = cborParser.encode("typing:update", payload)
        wsManager.sendBinary(bytes)
    }

    fun sendTypingStop(chatId: String) {
        val payload = TypingPayload(chatId = chatId, isTyping = false)
        val bytes = cborParser.encode("typing:update", payload)
        wsManager.sendBinary(bytes)
    }

    fun sendSyncRequest(lastMessageId: String?) {
        val payload = SyncRequestPayload(lastMessageId = lastMessageId)
        val bytes = cborParser.encode("sync:request", payload)
        wsManager.sendBinary(bytes)
    }

    // --- Outbound Payload Encodings ---

    @Serializable
    private data class SendMessagePayload(
        @SerialName("local_id") val localId: String,
        @SerialName("chat_id") val chatId: String,
        @SerialName("recipient_user_id") val recipientUserId: String,
        @SerialName("payloads") val payloads: List<DevicePayload>,
        @SerialName("created_at") val createdAt: Long,
        @SerialName("media_url") val mediaUrl: String? = null,
        @SerialName("reply_to_id") val replyToId: String? = null
    )

    @Serializable
    private data class SendAckPayload(
        @SerialName("message_id") val messageId: String,
        @SerialName("status") val status: String
    )

    @Serializable
    private data class TypingPayload(
        @SerialName("chat_id") val chatId: String,
        @SerialName("is_typing") val isTyping: Boolean
    )

    @Serializable
    private data class SyncRequestPayload(
        @SerialName("last_message_id") val lastMessageId: String?
    )
}
