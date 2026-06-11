package com.nexchat.core.network.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DevicePayload(
    @SerialName("device_id") val deviceId: String,
    @SerialName("ciphertext") val ciphertext: String // Base64
)

@Serializable
data class DeltaEntry(
    @SerialName("type") val type: String,
    @SerialName("payload") val payload: String // Base64 or stringified JSON depending on server
)

/** Outbound payload for message:send */
@Serializable
data class MessageSendPayload(
    @SerialName("local_id") val localId: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("payloads") val payloads: List<DevicePayload>,
    @SerialName("created_at") val createdAt: Long
)

sealed interface WsEvent {
    
    @Serializable
    @SerialName("message:receive")
    data class MessageReceive(
        @SerialName("local_id") val localId: String,
        @SerialName("chat_id") val chatId: String,
        @SerialName("sender_id") val senderId: String,
        @SerialName("type") val type: String,
        @SerialName("payloads") val payloads: List<DevicePayload>,
        @SerialName("created_at") val createdAt: Long,
        @SerialName("server_id") val serverId: String,
        @SerialName("server_ts") val serverTs: Long
    ) : WsEvent

    @Serializable
    @SerialName("message:ack:update")
    data class MessageAckUpdate(
        @SerialName("local_id") val localId: String,
        @SerialName("server_id") val serverId: String,
        @SerialName("server_ts") val serverTs: Long,
        @SerialName("status") val status: String
    ) : WsEvent

    @Serializable
    @SerialName("message:delete")
    data class MessageDelete(
        @SerialName("message_id") val messageId: String,
        @SerialName("chat_id") val chatId: String
    ) : WsEvent

    @Serializable
    @SerialName("reaction:update")
    data class ReactionUpdate(
        @SerialName("message_id") val messageId: String,
        @SerialName("user_id") val userId: String,
        @SerialName("emoji") val emoji: String,
        @SerialName("chat_id") val chatId: String
    ) : WsEvent

    @Serializable
    @SerialName("typing:update")
    data class TypingUpdate(
        @SerialName("user_id") val userId: String,
        @SerialName("chat_id") val chatId: String,
        @SerialName("is_typing") val isTyping: Boolean
    ) : WsEvent

    @Serializable
    @SerialName("user:online")
    data class UserOnline(
        @SerialName("user_id") val userId: String
    ) : WsEvent

    @Serializable
    @SerialName("user:offline")
    data class UserOffline(
        @SerialName("user_id") val userId: String,
        @SerialName("last_seen") val lastSeen: Long? = null
    ) : WsEvent

    @Serializable
    @SerialName("sync:response")
    data class SyncResponse(
        @SerialName("entries") val entries: List<DeltaEntry>
    ) : WsEvent

    @Serializable
    @SerialName("key:updated")
    data class KeyUpdated(
        @SerialName("user_id") val userId: String,
        @SerialName("key_version") val keyVersion: Int
    ) : WsEvent

    @Serializable
    @SerialName("keys:replenish_otk")
    data object KeysReplenishOtk : WsEvent

    @Serializable
    @SerialName("degradation:update")
    data class DegradationUpdate(
        @SerialName("level") val level: String
    ) : WsEvent
}
