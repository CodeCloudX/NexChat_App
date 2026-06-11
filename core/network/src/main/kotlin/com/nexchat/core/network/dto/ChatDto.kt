package com.nexchat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.nexchat.core.network.util.Iso8601TimestampSerializer

@Serializable
data class DataWrapper<T>(
    @SerialName("data") val data: T
)

@Serializable
data class ChatResponse(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @Serializable(with = Iso8601TimestampSerializer::class) @SerialName("created_at") val createdAt: Long,
    @SerialName("existing") val existing: Boolean? = null
)

/** Participant entry inside ChatDetailResponse. */
@Serializable
data class ParticipantInfo(
    @SerialName("user_id") val userId: String,
    @SerialName("role") val role: String,
    @Serializable(with = Iso8601TimestampSerializer::class) @SerialName("joined_at") val joinedAt: Long? = null
)

/** Full payload for GET /api/v1/chats/:id. */
@Serializable
data class ChatDetailResponse(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @Serializable(with = Iso8601TimestampSerializer::class) @SerialName("created_at") val createdAt: Long,
    @SerialName("participants") val participants: List<ParticipantInfo>
)

@Serializable
data class MessageDto(
    @SerialName("id") val id: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("type") val type: String, // "text", "image", etc.
    @SerialName("content") val content: String, // Encrypted content
    @Serializable(with = Iso8601TimestampSerializer::class) @SerialName("created_at") val createdAt: Long,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

@Serializable
data class CreateChatRequest(
    @SerialName("participant_ids") val participantIds: List<String>,
    @SerialName("type") val type: String = "direct"
)

@Serializable
data class ReactionRequest(
    @SerialName("emoji") val emoji: String
)
